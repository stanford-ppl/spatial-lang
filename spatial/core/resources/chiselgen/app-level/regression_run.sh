#!/bin/bash

# $1 = branch
# $2+ = args
bash run.sh $2 $3 $4 $5 $6 $7 $8 $9 $10 | tee log

pass_line=`cat log | grep "PASS"`

if [[ ${pass_line} = *": 1"* ]]; then
	pass=1
elif [[ ${pass_line} = *": 0"* ]]; then
	pass=0
else
	pass="?"
fi

timeout_wc=`cat log | grep "TIMEOUT" | wc -l`
runtime_string=`cat log | grep "Design ran for" | sed "s/Design ran for //g" | sed "s/ cycles.*//g"`

if [[ ${timeout_wc} -gt 0 ]]; then
	runtime="TIMEOUT"
else 
	runtime=$runtime_string
fi

tid=`cat ${SPATIAL_HOME}/tid`
appname=`basename \`pwd\``

python3 regression_report.py $1 $tid $appname $pass $runtime

