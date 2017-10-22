#!/bin/bash

if [[ $1 = "zynq" ]]; then
	export CLOCK_FREQ_MHZ=125
	# Prep the spreadsheet
	cd ${REGRESSION_HOME}
	tid=`python3 tid.py "$hash" "$timestamp" "Zynq"`
	echo $tid > ${REGRESSION_HOME}/data/tid
elif [[ $1 = "aws" ]]; then
	export CLOCK_FREQ_MHZ=125
	# Prep the spreadsheet
	cd ${REGRESSION_HOME}
	tid=`python3 ${REGRESSION_HOME}/../zynq/tid.py "$hash" "$timestamp" "AWS"`
	echo $tid > ${REGRESSION_HOME}/data/tid
fi

# Current hash matches previous hash, skip test
if [[ $tid = "-1" ]]; then
	sleep 3600 # Wait an hour
	rm -rf ${REGRESSION_HOME}/next-spatial
	exit 0
else 
	rm -rf ${REGRESSION_HOME}/spatial
	mv ${REGRESSION_HOME}/next-spatial ${REGRESSION_HOME}/spatial
fi


cd ${REGRESSION_HOME}/spatial/spatial-lang

if [[ $1 = "Zynq" ]]; then
	bin/regression 4 nobranch Zynq Dense Sparse
elif [[ $1 = "AWS" ]]; then
	bin/regression 4 nobranch AWS Dense Sparse
fi