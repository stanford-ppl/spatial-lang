#!/bin/bash

if [[ $1 = "zynq" ]]; then
	export CLOCK_FREQ_MHZ=125
	export PIR_HOME=${REGRESSION_HOME}
	# Prep the spreadsheet
	cd ${REGRESSION_HOME}
	tid=`python3 ${REGRESSION_HOME}/next-spatial/spatial-lang/bin/tid.py "$hash" "$apphash" "$timestamp" "Zynq"`
	echo $tid > ${REGRESSION_HOME}/data/tid
	echo $hash > ${REGRESSION_HOME}/data/hash
	echo $apphash > ${REGRESSION_HOME}/data/ahash
elif [[ $1 = "aws" ]]; then
	export PIR_HOME=${REGRESSION_HOME}
	export CLOCK_FREQ_MHZ=125
	# Prep the spreadsheet
	cd ${REGRESSION_HOME}
	tid=`python3 ${REGRESSION_HOME}/next-spatial/spatial-lang/bin/tid.py "$hash" "$apphash" "$timestamp" "AWS"`
	echo $tid > ${REGRESSION_HOME}/data/tid
	echo $hash > ${REGRESSION_HOME}/data/hash
	echo $apphash > ${REGRESSION_HOME}/data/ahash
fi

export PATH=/usr/bin:/local/ssd/home/mattfel/aws-fpga/hdk/common/scripts:/opt/Xilinx/SDx/2017.1/Vivado/bin:/opt/Xilinx/SDx/2017.1/SDK/bin:/opt/Xilinx/Vivado/2017.1/bin:/opt/Xilinx/SDK/2017.1/bin:$PATH

# Current hash matches previous hash, skip test
if [[ $tid = "-1" ]]; then
	sleep 3600 # Wait an hour
	rm -rf ${REGRESSION_HOME}/next-spatial
else 
	cd ${REGRESSION_HOME}
	rm -rf ${REGRESSION_HOME}/last-spatial
	mv ${REGRESSION_HOME}/spatial ${REGRESSION_HOME}/last-spatial
	mv ${REGRESSION_HOME}/next-spatial ${REGRESSION_HOME}/spatial

	echo "Moving to ${REGRESSION_HOME}/spatial/spatial-lang"
	cd ${REGRESSION_HOME}/spatial/spatial-lang

	if [[ $1 = "zynq" ]]; then
		bin/regression 3 nobranch Zynq Dense Sparse
	elif [[ $1 = "aws" ]]; then
		bin/regression 4 nobranch AWS Dense Sparse
	fi
fi
