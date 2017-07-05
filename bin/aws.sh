#!bin/bash

cd /home/mattfel/aws-fpga/
source /home/mattfel/aws-fpga/hdk_setup.sh
source /home/mattfel/aws-fpga/sdk_setup.sh

cd $SPATIAL_HOME
annotated_list=(`cat ${SPATIAL_HOME}/apps/src/MachSuite.scala | grep "// Regression" | sed 's/object //g' | sed 's/ extends.*//g'`)
for a in ${annotated_list[@]}; do
	echo $a
	cd $SPATIAL_HOME
	rm -rf out$a
	bin/spatial $a --synth --out=out$a
	cd out$a
	make aws-F1-afi
done
