#!/usr/bin/env python

"""
Usage: $0 PHENOTIPS_DIR EXOMISER_JAR CREDENTIALS_FILE

Check all patient VCF file attachments and update Exomiser files accordingly.

PHENOTIPS_DIR: path to the PhenoTips installation's data directory (with 'storage' and 'exomiser' subdirectories)
EXOMISER_JAR: path to Exomiser JAR file
CREDENTIALS_FILE: file containing PhenoTips ScriptService credentials (one line, in the format: 'username:password')
"""

from __future__ import with_statement

import sys
import os
import subprocess
import datetime
import glob
import hashlib
import json
import logging

from gzip import open as _gzip_open
from contextlib import closing
from urllib import urlencode
from string import Template

CACHED_DATA_FIELDS = ['phenotypes', 'inheritance', 'vcf_hash', 'min_qual', 'min_freq']
MODES_OF_INHERITANCE = {
    'HP:0000006': 'AD',
    'HP:0001470': 'AD',
    'HP:0001475': 'AD',
    'HP:0001444': 'AD',
    'HP:0001452': 'AD',
    'HP:0012274': 'AD',
    'HP:0012275': 'AD',

    'HP:0000007': 'AR',

    'HP:0001417': 'X',
    'HP:0001419': 'X',
    'HP:0001423': 'X',
    }
DEFAULT_EXOMISER_TEMPLATE = os.path.join(os.path.dirname(os.path.realpath(__file__)),
                                         'exomiser_settings.txt')

class RecordLockedException(Exception):
    pass

class Settings:
    DEFAULT_SETTINGS = {
        'date': datetime.datetime.now().strftime('%Y-%m-%d.%H%M%S'),
        'exomiser_template': DEFAULT_EXOMISER_TEMPLATE,
        'attach_subdir': '~this/attachments',
        'host': 'http://localhost:8080',
        'clear_cache_url': '/bin/get/PhenoTips/ClearPatientCache',
        'export_id_url': '/bin/get/PhenomeCentral/ExportIDs',
        'export_patient_url': '/bin/get/PhenoTips/ExportPatient',
        'export_vcf_url': '/bin/get/PhenomeCentral/ExportVCF',
        }
    _settings = {}
    def __init__(self, **kwargs):
        settings = dict(self.DEFAULT_SETTINGS)
        # Overwrite with any provided settings
        for key in kwargs:
            settings[key] = kwargs[key]

        assert os.path.isdir(settings['out_dir'])
        assert os.path.isdir(settings['record_dir'])
        assert os.path.isfile(settings['exomiser_jar'])
        assert os.path.isfile(settings['exomiser_template'])

        self._settings = settings

    def __getitem__(self, key):
        return self._settings[key]

    def get_record_prefix(self, record_id):
        record_dir = os.path.join(self._settings['out_dir'], record_id)
        make_directory(record_dir)
        return os.path.join(record_dir, record_id)

    def get_record_cache_filename(self, record_id):
        return self.get_record_prefix(record_id) + '.cached.json'

    def get_exomiser_out_filename(self, record_id):
        return self.get_record_prefix(record_id) + '.variants.tsv'

    def get_exomiser_out_prefix(self, record_id):
        return self.get_record_prefix(record_id)

    def get_out_filename(self, record_id):
        return self.get_record_prefix(record_id) + '.variants.tsv'

    def get_settings_filename(self, record_id):
        return os.path.join(self.get_job_dir(), record_id + '.settings')

    def get_lock_filename(self, record_id):
        return os.path.join(self._settings['out_dir'], record_id + '.lock')

    def get_job_dir(self):
        job_dir = os.path.join(self._settings['out_dir'], 'job_' + self._settings['date'])
        make_directory(job_dir)
        return job_dir

    def get_vcf_path(self, record_id, filename):
        return os.path.join(self._settings['record_dir'], record_id, self._settings['attach_subdir'], filename, filename)

class Lock(object):
    def __init__(self, record_id, settings):
        self._filename = settings.get_lock_filename(record_id)

    def __enter__(self):
        # Race condition still technically possible here
        if os.path.isfile(self._filename):
            raise RecordLockedException()
        else:
            open(self._filename, 'w').close()

    def __exit__(self, *args, **kwargs):
        if os.path.isfile(self._filename):
            os.remove(self._filename)

def make_directory(path):
    if not os.path.isdir(path):
        logging.info('Creating directory: {0}'.format(path))
        os.makedirs(path)

def load_credentials(filename):
    with open(filename) as ifp:
        return ifp.read().strip()

def maybe_gzip_open(filename, *args, **kwargs):
    if filename.lower().endswith('.gz'):
        return closing(_gzip_open(filename, *args, **kwargs))
    else:
        return open(filename, *args, **kwargs)

def fetch_patient_data(record_id, settings):
    logging.info('Fetching patient data for {0}'.format(record_id))
    url = '{0}{1}?id={2}&basicauth=1'.format(settings['host'], settings['export_patient_url'], record_id)
    proc = subprocess.Popen(['curl', '-s', '-S', '-u', settings['credentials'], url], stdout=subprocess.PIPE)
    output = proc.communicate()[0]
    return json.loads(output)

def clear_cache(record_id, settings):
    logging.info('Clearing cache for {0}'.format(record_id))
    url = '{0}{1}?id={2}&basicauth=1'.format(settings['host'], settings['clear_cache_url'], record_id)
    retcode = subprocess.call(['curl', '-s', '-S', '-u', settings['credentials'], url], stdout=subprocess.PIPE)
    if retcode != 0:
        logging.error('Attempt to clear cache for {0} failed'.format(record_id))

def fetch_changed_records(settings, since=None):
    fields = {}
    if since:
        fields['since'] = since

    logging.info('Fetching records changed since: {0}'.format(since))
    url = '{0}{1}?basicauth=1'.format(settings['host'], settings['export_id_url'])
    command = ['curl', '-s', '-S', '-u', settings['credentials'], url, '--data', urlencode(fields)]
    proc = subprocess.Popen(command, stdout=subprocess.PIPE)
    stdout = proc.communicate()[0]

    data = stdout.strip().splitlines()
    logging.info('Found {0} records'.format(len(data)))
    record_ids = []
    for report_id in data:
        if report_id.startswith('data.'):
            report_id = report_id[5:]
        record_ids.append(report_id.strip())

    return sorted(record_ids)

def get_record_vcf(record_id, settings):
    # vcf named like: F0000009/~this/attachments/exome.vcf/exome.vcf
    logging.info('Fetching VCF for record: {0}'.format(record_id))
    fields = {'id': record_id}
    url = '{0}{1}?basicauth=1'.format(settings['host'], settings['export_vcf_url'])
    command = ['curl', '-s', '-S', '-u', settings['credentials'], url, '--data', urlencode(fields)]
    proc = subprocess.Popen(command, stdout=subprocess.PIPE)
    stdout = proc.communicate()[0]

    filename = stdout.strip()
    if not filename:
        return

    logging.info('Found VCF file selected: {0}'.format(filename))

    filepath = settings.get_vcf_path(record_id, filename)
    if not os.path.isfile(filepath):
        logging.warning('VCF file missing: {0}'.format(filepath))
        return

    # Detect vcf format from header
    with open(filepath) as ifp:
        header = ifp.readline()

    if not header.startswith('##fileformat=VCF'):
        logging.warning('VCF is not in VCF format: {0}'.format(filepath))
        return

    logging.info('Found VCF attachment: {0}'.format(filepath))
    return filepath

# Hashing
def get_file_hash(filename, hash_func=hashlib.md5, blocksize=65536):
    if not os.path.isfile(filename):
        return None

    hasher = hash_func()
    with open(filename) as ifp:
        buf = ifp.read(blocksize)
        while len(buf) > 0:
            hasher.update(buf)
            buf = ifp.read(blocksize)

    return hasher.hexdigest()

# Caching
def get_cached_data(record_id, settings):
    filename = settings.get_record_cache_filename(record_id)
    cache = {}
    if os.path.isfile(filename):
        with open(filename) as ifp:
            cache = json.load(ifp)

    return cache

def cache_data(record_id, cache, settings):
    filename = settings.get_record_cache_filename(record_id)
    with open(filename, 'w') as ofp:
        json.dump(cache, ofp)

# Parse record
def parse_phenotypes(data):
    terms = set()
    features = data.get('features', [])
    # Add in prenatal phenotype
    features.extend(data.get('prenatal_perinatal_phenotype', {}).get('prenatal_phenotype', []))
    for feature in features:
        if feature.get('observed') == 'yes':
            terms.add(feature['id'])

    return sorted(terms)

def phenotypes_to_exomiser(phenotypes):
    return ','.join(phenotypes)

def parse_inheritance(data):
    inheritances = data.get('global_mode_of_inheritance', [])
    if len(inheritances) == 1:
        return inheritances[0].get('id')

def inheritance_to_exomiser(inheritance):
    """Convert HPO terms to Exomiser-supported inheritance codes

    If the code is unsupported, returns ''
    """
    return MODES_OF_INHERITANCE.get(inheritance, '')


def get_record_data(record_id, vcf_filepath, settings):
    patient_data = fetch_patient_data(record_id, settings)
    phenotypes = parse_phenotypes(patient_data)
    inheritance = parse_inheritance(patient_data)
    data = {
        'vcf_filepath': vcf_filepath,
        'vcf_hash': get_file_hash(vcf_filepath),
        'phenotypes': phenotypes,
        'inheritance': inheritance,
        'min_qual': '30',
        'min_freq': '1.0',
        'exomiser_phenotypes': phenotypes_to_exomiser(phenotypes),
        'exomiser_inheritance': inheritance_to_exomiser(inheritance),
        'exomiser_out_prefix': settings.get_exomiser_out_prefix(record_id),
        'exomiser_out_filename': settings.get_exomiser_out_filename(record_id),
        'out_filename': settings.get_out_filename(record_id),
    }
    return data

def delete_exomiser(record_id, settings):
    any_removed = False
    for filename in glob.glob(os.path.join(settings['out_dir'], record_id, record_id + '.*')):
        any_removed = True
        logging.warning('Removing: {0}'.format(filename))
        os.remove(filename)

    if any_removed:
        clear_cache(record_id, settings)

def enqueue_exomiser(record_id, record_data, settings):
    # Create settings file
    with open(settings['exomiser_template']) as ifp:
        template = Template(ifp.read())

    settings_text = template.substitute(**record_data)
    settings_filename = settings.get_settings_filename(record_id)
    logging.info('Writing settings for {0} to: {1}'.format(record_id, settings_filename))
    with open(settings_filename, 'w') as ofp:
        ofp.write(settings_text)

    return settings_filename

def run_exomiser(record_id, new_data, settings):
    settings_filename = enqueue_exomiser(record_id, new_data, settings)
    logging.info('Running Exomiser on settings file: {0}'.format(settings_filename))
    command = ['java', '-Xms5g', '-Xmx10g', '-jar', settings['exomiser_jar'], '--settings-file', settings_filename]
    logging.info('Command: {0!r}'.format(' '.join(command)))
    retcode = subprocess.call(command)
    if retcode == 0:
        # Success!
        # Cache updated data and clear patient cache.
        cache_data(record_id, new_data, settings)
        clear_cache(record_id, settings)

def script(settings, start_time=None):
    changed_records = fetch_changed_records(settings, since=start_time)
    for record_id in changed_records:
        try:
            with Lock(record_id, settings):
                logging.info('Processing patient: {0!r}'.format(record_id))
                vcf_filepath = get_record_vcf(record_id, settings)

                # If the record doesn't have a VCF file (anymore), clean up record
                if not vcf_filepath:
                    delete_exomiser(record_id, settings)
                    continue

                # If the record has a VCF file, compare to see if it changed
                new_data = get_record_data(record_id, vcf_filepath, settings)
                old_data = get_cached_data(record_id, settings)

                if not old_data:
                    logging.info("Processing {0} for first time".format(record_id))
                    changed = True
                else:
                    changed = False
                    for field in CACHED_DATA_FIELDS:
                        if new_data[field] != old_data.get(field):
                            changed = True

                if not changed:
                    logging.info("{0} hasn't changed".format(record_id))
                    continue

                # Patient record has changed, (re-)run Exomiser
                run_exomiser(record_id, new_data, settings)
        except RecordLockedException:
            logging.error('Skipping locked record: {0}'.format(record_id))

def parse_args(args):
    from optparse import OptionParser
    usage = "usage: %prog [options] PHENOTIPS_DIR EXOMISER_JAR CREDENTIALS_FILE"
    parser = OptionParser(usage=usage)
    parser.add_option("--since", dest="start_time",
                      help="Check exomiser for records updated since the"
                      " provided ISO datetime (default: all records)",
                      metavar="ISO")
    parser.add_option("--exomiser-template", dest="exomiser_template",
                      help="Exomiser settings file template (default: %default)",
                      metavar="FILE", default=DEFAULT_EXOMISER_TEMPLATE)
    (options, args) = parser.parse_args()

    if len(args) == 3:
        phenotips_dir, exomiser_jar, credentials_file = args
    else:
        parser.error('Invalid number of arguments')

    # Populate settings from arguments
    exomiser_template = options.__dict__.pop('exomiser_template')
    settings = Settings(out_dir=os.path.join(phenotips_dir, 'exomiser'),
                        record_dir=os.path.join(phenotips_dir, 'storage/xwiki/data'),
                        exomiser_jar=exomiser_jar,
                        credentials=load_credentials(credentials_file),
                        exomiser_template=exomiser_template)

    return (options, settings)

def main(args=sys.argv[1:]):
    options, settings = parse_args(args)
    kwargs = dict(options.__dict__)
    logging.basicConfig(level=logging.DEBUG)
    script(settings, **kwargs)

if __name__ == '__main__':
    sys.exit(main())
