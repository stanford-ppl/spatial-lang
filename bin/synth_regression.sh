#!/bin/bash

if [[ $1 = "Zynq" ]]; then
	export CLOCK_FREQ_MHZ=125
elif [[ $1 = "F1" ]]; then
	export CLOCK_FREQ_MHZ=125
fi

cd ${REGRESSION_HOME}/spatial/spatial-lang

if [[ $1 = "Zynq" ]]; then
	bin/regression 4 nobranch Zynq Dense Sparse
elif [[ $1 = "F1" ]]; then
	bin/regression 4 nobranch F1 Dense Sparse
