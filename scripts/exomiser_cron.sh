#!/usr/bin/env bash

set -eu
set -o pipefail

# For each patient on server with a VCF file, check the VCF MD5 and and query all present phenotypes in the patient. If VCF file or phenotypes have changed, rerun exomiser and clear patient similarity cache.

function usage {
	cat <<EOF
Usage: $0 EXOMISER_JAR PT_CREDENTIALS_FILE

Check all patient VCF file attachments and update Exomiser files accordingly.

To be run from the root of the PhenomeCentral instance (with subdirectories: exomiser, storage, etc.).

EXOMISER_JAR: full path to Exomiser 3.0.2 JAR file
PT_CREDENTIALS_FILE: file containing PhenoTips ScriptService credentials (one line, in the format: 'username:password')
EOF
	exit 1
}



##### Command line and prereqs #####

if [[ $# -ne 2 ]]; then
	usage
fi
EXOMISER_JAR="$1"
CREDENTIALS=$(cat "$2")

which jq > /dev/null



##### Environment variables #####

EXOMISER_DIR=$(pwd)/exomiser
LOCK=${EXOMISER_DIR}/.cron-lock
CRON_DIR=${EXOMISER_DIR}/cron_$(date "+%F_%H%M%S")
RECORD_DIR=$(pwd)/storage/xwiki/data
ATTACH_SUBDIR=~this/attachments
SERVICE_URL_BASE=http://localhost:8080/bin/get/PhenoTips



##### Function definitions #####

function log {
	echo "[$(date "+%F %T")] $@" >&2
}

function cleanup {
	rm -f "${LOCK}"
}

function failure {
	trap - INT TERM EXIT
	log "ERROR: program terminated for unexpected reason."
	cleanup
}


function get_phenotypes {
	# Usage: get_phenotypes record
	local record="$1"
	curl -s -S -u "${CREDENTIALS}" "${SERVICE_URL_BASE}/PatientPhenotypeExportService?id=${record}&basicauth=1" \
		| jq '.rows[] | select(.type == "phenotype" and .is_present == "yes") | .phenotype_HP_id' \
		| sort | xargs echo | tr -d '"' | tr " " ","
}

function enqueue_exomiser {
	# Usage: enqueue_exomiser record vcf phenotypes outfile
	local record="$1"
	local vcf="$2"
	local phenotypes="$3"
	local outfile="$4"
	local settings="${CRON_DIR}/${record}.settings"

	# Create settings file
	cat > "$settings" <<EOF
#REQUIRED OPTIONS
vcf=$vcf
prioritiser=exomiser-allspecies

#SAMPLE DATA OPTIONS
ped=

#FILTER OPTIONS
# 1% frequency filtering
max-freq=1.0
restrict-interval=
min-qual=0
keep-non-pathogenic-missense=false
remove-dbsnp=false
remove-off-target-syn=true

#PRIORITISER OPTIONS
candidate-gene=
hpo-ids=${phenotypes}
seed-genes=
#disease-id option: OMIM identifier e.g. OMIM:101500
disease-id=
#inheritance-mode options: AD, AR or X
inheritance-mode=

#OUTPUT OPTIONS
#num-genes options: 0 = all or specify a limit e.g. 500 for the first 500 results
num-genes=0
#out-file options: specify the path/filename without an extension and this will be added
# according to the out-format option. If unspecified this will default to the following:
# {exomiserDir}/results/input-vcf-name-exomiser-results.html
# alternatively, specify a fully qualifed path only. e.g. /users/jules/exomes/analysis
out-file=${outfile}
#out-format options: HTML, TSV, VCF (default: HTML)
out-format=VCF
EOF
	
}

function run_exomisers {
	# Usage: run_exomisers
	# Runs all settings files in CRON_DIR as batch
	local batchfile="${CRON_DIR}/batch.txt"
	ls -1 "${CRON_DIR}"/*.settings 2> /dev/null > "${batchfile}" || true
	if [[ -s "${batchfile}" ]]; then
		log "Running Exomiser on batch file: ${batchfile}"
		java -Xms10g -Xmx10g -jar "${EXOMISER_JAR}" --batch-file "${batchfile}" 1>&2

		# Clear caches for processed records
		log "Clearing cache for changed records: ${batchfile}"
		for filename in "${CRON_DIR}"/*.settings; do
			local record="$(basename "${filename}" .settings)"
			clear_cache "$record"
		done
	else
		log "Exomiser jobs up-to-date."
	fi
}

function clear_cache {
	# Usage: clear_cache record
	curl -s -S -u "${CREDENTIALS}" "${SERVICE_URL_BASE}/ClearPatientCache?id=${record}&basicauth=1"
}

function list_vcfs {
	# Usage: list_vcfs record
	# vcf named like: F0000009/~this/attachments/exome.vcf/exome.vcf
	ls -1 "${RECORD_DIR}/${record}/${ATTACH_SUBDIR}"/*/* 2> /dev/null \
		| awk -F"/" '$(NF-1) == $(NF);'
}

function check_record {
	# Usage: check_record record vcf
	# e.g. check_record F0000009 storage/xwiki/data/~this/attachments/F0000009.vcf/F0000009.vcf
	local record="$1"
	local vcf="$2"
	local md5file="${EXOMISER_DIR}/${record}.vcf.md5"
	local phenotypefile="${EXOMISER_DIR}/${record}.hpo"
	local ezrfile="${EXOMISER_DIR}/${record}.vcf"

	local phenotypes="$(get_phenotypes "$record")"
	local mismatch=$(echo "$phenotypes" | cmp -s - "$phenotypefile" || echo "fail")
	if [[ ! -z "$mismatch" ]]; then
		# Phenotypes have changed (or new patient)
		echo "$phenotypes" > "$phenotypefile"
	fi

	# Check md5sum
	local mismatch=$(md5sum --status -c "$md5file" || echo "fail")
	if [[ ! -z "$mismatch" ]]; then
		# VCF file changed
		md5sum "$vcf" > "$md5file"
	fi

	if [[ "$phenotypefile" -nt "$ezrfile" || "$md5file" -nt "$ezrfile" ]]; then
		# Queue up exomiser job
		enqueue_exomiser "$record" "$vcf" "$phenotypes" "$ezrfile"
	fi
}


##### Begin main program #####

# Create directory for this job
mkdir -p "${CRON_DIR}"
log "Starting up Exomiser cronjob from directory: ${CRON_DIR}"

# Redirect all stderr to log file
log "Writing all output to: ${CRON_DIR}/log"
exec 2> "${CRON_DIR}/log"

# Set lock
touch "${LOCK}"

# Start critical block
trap failure INT TERM EXIT

# Investigate each record for changes
for recorddir in "${RECORD_DIR}"/*; do
	record="$(basename "$recorddir")"
	n_vcf_attachments=$(list_vcfs | wc -l)
	log "Found ${n_vcf_attachments} attachments for ${record}"
	if [[ ${n_vcf_attachments} -eq 0 ]]; then
		# VCF may have been deleted
		# Remove any exomiser files, and clear cache
		rm -f "${EXOMISER_DIR}/${record}".*
		clear_cache "${record}"
	elif [[ ${n_vcf_attachments} -eq 1 ]]; then
		# Check VCF file and phenotypes for updates
		vcf="$(list_vcfs $record | head -n 1)"
		check_record "$record" "$vcf"
	fi
done

# Run any queued jobs
run_exomisers


# End critical block
trap - INT TERM EXIT
cleanup
log "SUCCESS"
