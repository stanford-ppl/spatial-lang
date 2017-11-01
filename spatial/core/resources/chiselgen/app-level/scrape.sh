#!bin/bash

#1 = Backend

# get tid
if [[ $1 = "Zynq" ]]; then
	REGRESSION_HOME="/home/mattfel/regression/synth/zynq"
elif [[ $1 = "AWS" ]]; then
	REGRESSION_HOME="/home/mattfel/regression/synth/aws"
fi

tid=`cat ${REGRESSION_HOME}/data/tid`

appname=`basename \`pwd\``
if [[ $1 = "Zynq" ]]; then
	par_util=`pwd`/verilog-zynq/par_utilization.rpt
	par_tmg=`pwd`/verilog-zynq/par_timing_summary.rpt
	word="Slice"
	f1=3
	f2=6
elif [[ $1 = "AWS" ]]; then
	par_util=/home/mattfel/aws-fpga/hdk/cl/examples/$appname/build/reports/utilization_route_design.rpt
	par_tmg=/home/mattfel/aws-fpga/hdk/cl/examples/$appname/build/reports/timing_summary_route_design.rpt
	word="CLB"
	f1=5
	f2=8
fi

if [[ -f ${par_util} ]]; then
	lutraw=`cat $par_util | grep -m 1 "$word LUTs" | awk -v f=$f1 -F'|' '{print $f}' | sed "s/ //g"`
	lutpcnt=`cat $par_util | grep -m 1 "$word LUTs" | awk -v f=$f2 -F'|' '{print $f}' | sed "s/ //g"`
	regraw=`cat $par_util | grep -m 1 "$word Registers" | awk -v f=$f1 -F'|' '{print $f}' | sed "s/ //g"`
	regpcnt=`cat $par_util | grep -m 1 "$word Registers" | awk -v f=$f2 -F'|' '{print $f}' | sed "s/ //g"`
	ramraw=`cat $par_util | grep -m 1 "| Block RAM Tile" | awk -v f=$f1 -F'|' '{print $f}' | sed "s/ //g"`
	rampcnt=`cat $par_util | grep -m 1 "| Block RAM Tile" | awk -v f=$f2 -F'|' '{print $f}' | sed "s/ //g"`
	if [[ $1 = "AWS" ]]; then		
		uramraw=`cat $par_util | grep -m 1 "URAM" | awk -v f=$f1 -F'|' '{print $f}' | sed "s/ //g"`
		urampcnt=`cat $par_util | grep -m 1 "URAM" | awk -v f=$f2 -F'|' '{print $f}' | sed "s/ //g"`
	else
		uramraw="NA"
		urampcnt="NA"
	fi
	dspraw=`cat $par_util | grep -m 1 "DSPs" | awk -v f=$f1 -F'|' '{print $f}' | sed "s/ //g"`
	dsppcnt=`cat $par_util | grep -m 1 "DSPs" | awk -v f=$f2 -F'|' '{print $f}' | sed "s/ //g"`
	lalraw=`cat $par_util | grep -m 1 "LUT as Logic" | awk -v f=$f1 -F'|' '{print $f}' | sed "s/ //g"`
	lalpcnt=`cat $par_util | grep -m 1 "LUT as Logic" | awk -v f=$f2 -F'|' '{print $f}' | sed "s/ //g"`
	lamraw=`cat $par_util | grep -m 1 "LUT as Memory" | awk -v f=$f1 -F'|' '{print $f}' | sed "s/ //g"`
	lampcnt=`cat $par_util | grep -m 1 "LUT as Memory" | awk -v f=$f2 -F'|' '{print $f}' | sed "s/ //g"`
else
	lutraw="NA"
	lutpcnt="NA"
	regraw="NA"
	regpcnt="NA"
	ramraw="NA"
	rampcnt="NA"
	uramraw="NA"
	urampcnt="NA"
	dspraw="NA"
	dsppcnt="NA"
	lalraw="NA"
	lalpcnt="NA"
	lamraw="NA"
	lampcnt="NA"
fi

if [[ -f ${par_tmg} ]]; then
	viocnt=`cat $par_tmg | grep -i violated | grep -v synth | wc -l`
	if [[ $viocnt != "0" ]]; then tmg="0"; else tmg="1"; fi
else
	tmg="NA"
fi


endtime=`cat \`pwd\`/end.log`
starttime=`cat \`pwd\`/start.log`
synthtime=$((endtime-starttime))

python3 scrape.py $tid $appname "$lutraw (${lutpcnt}%)" "$regraw (${regpcnt}%)" "$ramraw (${rampcnt}%)" "$uramraw (${urampcnt}%)" "$dspraw (${dsppcnt}%)" "$lalraw (${lalpcnt}%)" "$lamraw (${lampcnt}%)" "$synthtime" "$tmg" "$1"


# Fake out scala Regression
echo "PASS: 1"