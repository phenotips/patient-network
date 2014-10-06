#!/usr/bin/env bash

set -eu
set -o pipefail

function usage {
	cat <<EOF
Usage: $0 EXOMISER_JAR PT_CREDENTIALS_FILE

Check all patient VCF file attachments and update Exomiser files accordingly.

To be run from the root of the PhenomeCentral instance (with subdirectories: exomiser, storage, etc.).

EXOMISER_JAR: full path to Exomiser 3.0.1 JAR file
PT_CREDENTIALS_FILE: file containing PhenoTips ScriptService credentials (one line, in the format: 'username:password')
EOF
	exit 1
}

if [[ $# -ne 2 ]]; then
	usage
fi

EXOMISER_JAR="$1"
CREDENTIALS=$(cat "$2")

EXOMISER_DIR=exomiser
LOCK=${EXOMISER_DIR}/.cron-lock
CRON_DIR=${EXOMISER_DIR}/cron_$(date "+%Y-%m-%d_%H%M%S")
RECORD_DIR=storage/xwiki/data
ATTACH_SUDIR=~this/attachments

mkdir -pv "${CRON_DIR}"

# Check lock
if [[ -e "${LOCK}" ]]; then
	echo "Exomiser cron already running (found lock file: ${LOCK})" >&2
	exit 1
fi

# Set lock
touch "${LOCK}"
function cleanup {
	rm -f "${LOCK}"
}
function failure {
	trap - INT TERM EXIT
	echo "ERROR: program terminated for unexpected reason." >&2
	cleanup
}


# For each patient on server with a VCF file, check the VCF MD5 and and query all present phenotypes in the patient. If VCF file or phenotypes have changed, rerun exomiser and clear patient similarity cache.


function get_phenotypes {
	# Usage: get_phenotypes record
	local record="$1"
	curl -s -S -u "${CREDENTIALS}" "http://localhost:8080/get/PhenoTips/PatientPhenotypeExportService?id=${record}&basicauth=1" \
		| ~/jq '.rows[] | select(.type == "phenotype" and .is_present == "yes") | .phenotype_HP_id' \
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
vcf="$vcf"
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
	ls -1 "${CRON_DIR}"/*.settings > "${batchfile}"

	echo "Running Exomiser on batch file: ${batchfile}" >&2
	java -Xms10g -Xmx10g -jar "${EXOMISER_JAR}" --batch-file "${batchfile}"

	# Clear caches for processed records
	echo "Clearing cache for changed records: ${batchfile}" >&2
	for filename in "${CRON_DIR}"/*.settings; do
		local record="$(basename "${filename}" .settings)"
		clear_cache "$record"
	done
}

function clear_cache {
	# Usage: clear_cache record
	curl -s -S -u "${CREDENTIALS}" "http://localhost:8080/get/PhenoTips/ClearPatientCache?id=${record}&basicauth=1"
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
	local ezrfile="${EXOMISER_DIR}/${record}.ezr"

	local phenotypes="$(get_phenotypes "$record")"
	echo "$phenotypes" | cmp -s - "$phenotypefile"
	if [[ $? -ne 0 ]]; then
		# Phenotypes have changed (or new patient)
		echo "$phenotypes" > "$phenotypefile"
	fi

	# Check md5sum
	md5sum --status -c "$md5file"
	if [[ $? -ne 0 ]]; then
		# VCF file changed
		md5sum "$vcf" > "$md5file"
	fi

	if [[ "$phenotypefile" -nt "$ezrfile" || "$md5file" -nt "$ezrfile" ]]; then
		# Queue up exomiser job
		enqueue_exomiser "$record" "$vcf" "$phenotypes" "$ezrfile"
	fi
}



# Start critical block
trap failure INT TERM EXIT

# Investigate each record for changes
for recorddir in "${RECORD_DIR}"/*; do
	record="$(basename "$recorddir")"
    n_vcf_attachments=$(list_vcfs | wc -l)
	echo "Found ${n_vcf_attachments} attachments for ${record}" >&2
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

run_exomisers
# End critical block
trap - INT TERM EXIT
cleanup