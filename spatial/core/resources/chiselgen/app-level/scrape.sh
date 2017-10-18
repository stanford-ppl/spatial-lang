#!bin/bash

# get tid
REGRESSION_HOME="/home/mattfel/regression/zynq"
tid=`cat ${REGRESSION_HOME}/data/tid`

appname=`basename \`pwd\``
par_util=${REGRESSION_HOME}/spatial/spatial-lang/gen/$appname/verilog-zynq/par_utilization.rpt
lutraw=`cat $par_util | grep "Slice LUTs" | awk -F'|' '{print $3}' | sed "s/ //g"`
lutpcnt=`cat $par_util | grep "Slice LUTs" | awk -F'|' '{print $6}' | sed "s/ //g"`
regraw=`cat $par_util | grep "Slice Registers" | awk -F'|' '{print $3}' | sed "s/ //g"`
regpcnt=`cat $par_util | grep "Slice Registers" | awk -F'|' '{print $6}' | sed "s/ //g"`
ramraw=`cat $par_util | grep "Block RAM Tile" | awk -F'|' '{print $3}' | sed "s/ //g"`
rampcnt=`cat $par_util | grep "Block RAM Tile" | awk -F'|' '{print $6}' | sed "s/ //g"`
dspraw=`cat $par_util | grep "DSPs" | awk -F'|' '{print $3}' | sed "s/ //g"`
dsppcnt=`cat $par_util | grep "DSPs" | awk -F'|' '{print $6}' | sed "s/ //g"`

python3 scrape.py $tid $appname "$lutraw (${lutpcnt}%)" "$regraw (${regpcnt}%)" "$ramraw (${rampcnt}%)" "$dspraw (${dsppcnt}%)"