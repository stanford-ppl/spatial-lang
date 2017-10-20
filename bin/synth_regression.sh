#!/bin/bash

if [[ $1 = "Zynq" ]]; then
	export CLOCK_FREQ_MHZ=125
	# Prep the spreadsheet
	cd ${REGRESSION_HOME}
	tid=`python3 tid.py "$hash" "$timestamp" "Zynq"`
	echo $tid > ${REGRESSION_HOME}/data/tid
elif [[ $1 = "F1" ]]; then
	export CLOCK_FREQ_MHZ=125
	# Prep the spreadsheet
	cd ${REGRESSION_HOME}
	tid=`python3 ${REGRESSION_HOME}/../zynq/tid.py "$hash" "$timestamp" "F1"`
	echo $tid > ${REGRESSION_HOME}/data/tid
fi



cd ${REGRESSION_HOME}/spatial/spatial-lang

if [[ $1 = "Zynq" ]]; then
	bin/regression 4 nobranch Zynq Dense Sparse
elif [[ $1 = "F1" ]]; then
	bin/regression 4 nobranch F1 Dense Sparse
fi