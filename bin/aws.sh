#!bin/bash

# $1 = retime (1 on, 0 off)

cd /home/mattfel/aws-fpga/
source /home/mattfel/aws-fpga/hdk_setup.sh
source /home/mattfel/aws-fpga/sdk_setup.sh

cd $SPATIAL_HOME
annotated_list=(`cat ${SPATIAL_HOME}/apps/src/MachSuite.scala | grep "// Regression" | sed 's/object //g' | sed 's/ extends.*//g'`)
for a in ${annotated_list[@]}; do
	if [[ $1 = "1" ]]; then
		rt="_retime_"
	else 
		rt=""
	fi
	echo $rt$a
	cd $SPATIAL_HOME
	rm -rf out$rt$a
	bin/spatial $a --synth --out=out$rt$a
	cd out$rt$a
	make aws-F1-afi
done
