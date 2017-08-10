VERILATOR_SRC=cpp/verilator
AWS_V_DIR=verilog-aws
AWS_V_SIM_DIR=verilog-aws-sim
ZYNQ_V_DIR=verilog-zynq
APPNAME=$(shell basename $(shell pwd))
BIGIP_SCRIPT=bigIP.tcl
timestamp := $(shell /bin/date "+%Y-%m-%d---%H-%M-%S")

all: help

help:
	@echo "------- SUPPORTED MAKE TARGETS -------"
	@echo "make sim         : Verilator SW + HW build"
	@echo "make sim-sw      : Verilator SW build"
	@echo "make sim-hw      : Build Chisel for Verilator"
	@echo "make aws-sim     : AWS simulation SW + HW build"
	@echo "make aws-sim-hw  : Build Chisel for AWS simulation"
	@echo "make aws-F1      : AWS F1 SW + HW build"
	@echo "make aws-F1-hw   : Build Chisel for AWS F1"
	@echo "make aws-F1-afi  : Build Bitstream for AWS F1"
	@echo "make aws-F1-sw   : Build Host for AWS F1"
	@echo "make zynq        : Zynq SW + HW build"
	@echo "make zynq-hw     : Build Chisel for Zynq"
	@echo "make vcs         : VCS SW + HW build"
	@echo "make vcs-hw      : Build Chisel for VCS"
	@echo "make sim-clean   : Verilator simulation clean up"
	@echo "make de1soc	: Build Chisel for DE1-SoC"
	@echo "------- END HELP -------"

sim: sim-hw
	make -C ${VERILATOR_SRC}
	ln -sf ${VERILATOR_SRC}/Top .

sim-sw:
	cp cpp/cpptypes.h cpp/datastructures
	cp cpp/Structs.h cpp/datastructures 2>/dev/null || :
	cp cpp/cppDeliteArrayStructs.h cpp/datastructures 2>/dev/null || :
	make -C ${VERILATOR_SRC}
	ln -sf ${VERILATOR_SRC}/Top .

sim-hw:
	rm -rf ${VERILATOR_SRC}
	cp cpp/cpptypes.h cpp/datastructures
	cp cpp/Structs.h cpp/datastructures 2>/dev/null || :
	cp cpp/cppDeliteArrayStructs.h cpp/datastructures 2>/dev/null || :
	sbt "run-main top.Instantiator --backend-name verilator --target-dir ${VERILATOR_SRC} --testArgs"
	cp verilator.mk ${VERILATOR_SRC}/Makefile
	mv ${VERILATOR_SRC}/top* ${VERILATOR_SRC}/verilator_srcs_tmp
	mkdir ${VERILATOR_SRC}/verilator_srcs
	mv ${VERILATOR_SRC}/verilator_srcs_tmp/VTop* ${VERILATOR_SRC}/verilator_srcs
	mv ${VERILATOR_SRC}/verilator_srcs_tmp/*.v ${VERILATOR_SRC}/verilator_srcs
	rm -rf ${VERILATOR_SRC}/verilator_srcs_tmp
	mv ${VERILATOR_SRC}/verilator_srcs/*.mk ${VERILATOR_SRC}

# ------------------------------------------------------------------------------
# START OF AWS TARGETS
# ------------------------------------------------------------------------------

# TODO: Use the following structure instead:
# aws-hw:
# aws-sim: aws-hw  aws-sim-tb
# aws-sim-tb:
# aws-F1 : aws-F1-sw
# aws-F1-dcp: aws-hw
# aws-F1-afi: aws-F1-dcp
# aws-F1-sw : aws-F1-afi

# Run simulation using Vivado XSIM
aws-sim: aws-sim-hw
	$(eval app_name=$(notdir $(shell pwd)))
	# ----------------------------------------------------------------------------
	# Compile the testbench and make the binary to call xsim run
	# NOTE: Requires hdk_setup.sh to have been already sourced
	# ----------------------------------------------------------------------------
	# Build the DPI .so library
	cd $(AWS_HOME)/hdk/cl/examples/${app_name}/verif/scripts && make C_TEST=test_spatial_main make_sim_dir compile
	# Create the binary
	sed 's:{{{INSERT_DESIGN_DIR}}}:'"${AWS_HOME}"'/hdk/cl/examples/${app_name}/verif/scripts:g' cpp/fringeAWS/Top_template > ./Top
	chmod 700 Top

# Set up simulation directory
# TODO: Refactor this into aws-hw (step 1) and aws-sim-tb (step 2), since aws-F1-hw reuses the aws-hw portion (step 1)
aws-sim-hw:
	$(eval app_name=$(notdir $(shell pwd)))
	# ----------------------------------------------------------------------------
	# Step 1: Make the design
	# ----------------------------------------------------------------------------
	rm -rf ${AWS_V_SIM_DIR}
	# First use chisel to create the verilog
	sbt "run-main top.Instantiator --verilog --testArgs aws-sim"
	cat chisel/template-level/fringeVCS/SRAMVerilogSim.v >> ${AWS_V_SIM_DIR}/Top.v
	# Make a copy of the template directory
	rm -rf $(AWS_HOME)/hdk/cl/examples/${app_name}
	cp -r $(AWS_HOME)/hdk/cl/examples/cl_dram_dma $(AWS_HOME)/hdk/cl/examples/${app_name}
	# Add all the static design files
	cp -f cpp/fringeAWS/design/* $(AWS_HOME)/hdk/cl/examples/${app_name}/design/
	cp -f ${AWS_V_SIM_DIR}/Top.v $(AWS_HOME)/hdk/cl/examples/${app_name}/design/
	# Run a script to put design together
	python cpp/fringeAWS/gen_aws_design.py $(AWS_HOME)/hdk/cl/examples/${app_name}
	# ----------------------------------------------------------------------------
	# Step 2: Make the testbench
	# ----------------------------------------------------------------------------
	# Add all the static software files
	cp -f cpp/TopHost.cpp $(AWS_HOME)/hdk/cl/examples/${app_name}/software/src/
	cp -f cpp/*.h $(AWS_HOME)/hdk/cl/examples/${app_name}/software/include/
	cp -f cpp/fringeAWS/headers/* $(AWS_HOME)/hdk/cl/examples/${app_name}/software/include/
	cp -rf cpp/datastructures $(AWS_HOME)/hdk/cl/examples/${app_name}/software/src/
	# Add all the simulation Makefiles
	cp -f cpp/fringeAWS/sim/Makefile* $(AWS_HOME)/hdk/cl/examples/${app_name}/verif/scripts/
	cp -f cpp/fringeAWS/sim/test_null.sv $(AWS_HOME)/hdk/cl/examples/${app_name}/verif/tests/
	# Run a script to put tb together
	python cpp/fringeAWS/gen_aws_tb.py $(AWS_HOME)/hdk/cl/examples/${app_name}

aws-F1 : aws-F1-afi    aws-F1-sw

# Build the hardware
# This is identical to step 1 of aws-sim-hw, can refactor to merge the two
aws-F1-hw:
	$(eval app_name=$(notdir $(shell pwd)))
	# ----------------------------------------------------------------------------
	# Make the design
	# ----------------------------------------------------------------------------
	rm -rf ${AWS_V_DIR}
	# First use chisel to create the verilog
	sbt "run-main top.Instantiator --verilog --testArgs aws"
	cat chisel/template-level/fringeAWS/SRAMVerilogAWS.v >> ${AWS_V_DIR}/Top.v
	mv ${BIGIP_SCRIPT} ${AWS_V_DIR}/
	# Make a copy of the template directory
	rm -rf $(AWS_HOME)/hdk/cl/examples/${app_name}
	cp -r $(AWS_HOME)/hdk/cl/examples/cl_dram_dma $(AWS_HOME)/hdk/cl/examples/${app_name}
	# Add all the static design files
	cp -f cpp/fringeAWS/design/* $(AWS_HOME)/hdk/cl/examples/${app_name}/design/
	cp -f ${AWS_V_DIR}/Top.v $(AWS_HOME)/hdk/cl/examples/${app_name}/design/
	# Run a script to put design together
	python cpp/fringeAWS/gen_aws_design.py $(AWS_HOME)/hdk/cl/examples/${app_name}

# Build the bitstream
aws-F1-afi:   aws-F1-hw
	$(eval app_name=$(notdir $(shell pwd)))
	# ----------------------------------------------------------------------------
	# Step 1: Run synthesis
	# NOTE: Requires hdk_setup.sh to have been already sourced, and Vivado license
	# ----------------------------------------------------------------------------
	cp -f cpp/fringeAWS/build/encrypt.tcl $(AWS_HOME)/hdk/cl/examples/${app_name}/build/scripts/
	cp -f cpp/fringeAWS/build/clockFreq.tcl $(AWS_HOME)/hdk/cl/examples/${app_name}/build/scripts/
	cp -f cpp/fringeAWS/build/synth_cl_dram_dma.tcl $(AWS_HOME)/hdk/cl/examples/${app_name}/build/scripts/
	cp -f ${AWS_V_DIR}/${BIGIP_SCRIPT} $(AWS_HOME)/hdk/cl/examples/${app_name}/build/scripts/
	# NOTE: The DEFAULT strategy will be used, see others here:
	#       https://github.com/aws/aws-fpga/blob/master/hdk/common/shell_v04151701/new_cl_template/build/README.md
	# NOTE: This may fail (e.g. our of area) -- may need to rerun this manually
	cd $(AWS_HOME)/hdk/cl/examples/${app_name}/build/scripts && CL_DIR=$(AWS_HOME)/hdk/cl/examples/${app_name} bash aws_build_dcp_from_cl.sh -clock_recipe_a A0
	# Use the following line instead for faster builds:
	# cd $(AWS_HOME)/hdk/cl/examples/${app_name}/build/scripts && CL_DIR=$(AWS_HOME)/hdk/cl/examples/${app_name} bash aws_build_dcp_from_cl.sh -strategy BASIC -clock_recipe_a A2 
	# ----------------------------------------------------------------------------
	# Step 2: Upload bitstream to S3 and create AFI (currently done manually, TODO: script this)
	# This will eventually be a new Makefile target, which runs only when the final DCP is created (after synthesis/place/route)
	# ----------------------------------------------------------------------------
	echo "# Instructions to upload bitstream to S3 and create AFI"                                                                                                        > create_spatial_AFI_instructions.txt
	echo "aws s3 mb s3://${app_name}_$(timestamp)_bucket"  --region us-east-1                                                                                            >> create_spatial_AFI_instructions.txt
	echo "aws s3 mb s3://${app_name}_$(timestamp)_bucket/dcp"                                                                                                            >> create_spatial_AFI_instructions.txt
	echo "aws s3 cp build/checkpoints/to_aws/*.Developer_CL.tar s3://${app_name}_$(timestamp)_bucket/dcp/"                                                               >> create_spatial_AFI_instructions.txt
	echo "aws s3 mb s3://${app_name}_$(timestamp)_bucket/logs"                                                                                                           >> create_spatial_AFI_instructions.txt
	echo "touch LOGS_FILES_GO_HERE.txt"                                                                                                                                  >> create_spatial_AFI_instructions.txt
	echo "aws s3 cp LOGS_FILES_GO_HERE.txt s3://${app_name}_$(timestamp)_bucket/logs/"                                                                                   >> create_spatial_AFI_instructions.txt
	echo "Set the right policy for this bucket in S3, see example here: (TODO: script this step)"                                                                        >> create_spatial_AFI_instructions.txt
	echo "  https://github.com/aws/aws-fpga/tree/master/hdk/cl/examples#3-submit-the-design-checkpoint-to-aws-to-register-the-afi"                                       >> create_spatial_AFI_instructions.txt
	echo ""                                                                                                                                                              >> create_spatial_AFI_instructions.txt
	echo "# Verify the script below passes. If it fails, it is likely because of the policy above. In particular, you may need to set up IAM"                            >> create_spatial_AFI_instructions.txt
	echo "# credentials in the EC2 console. Alternatively you can work around the IAM errors by following the steps in the script's error messages."                     >> create_spatial_AFI_instructions.txt
	echo "check_s3_bucket_policy.py \\"                                                                                                                                  >> create_spatial_AFI_instructions.txt
	echo "--dcp-bucket ${app_name}_$(timestamp)_bucket \\"                                                                                                               >> create_spatial_AFI_instructions.txt
	echo "--dcp-key dcp/<tarball-name> \\"                                                                                                                               >> create_spatial_AFI_instructions.txt
	echo "--logs-bucket ${app_name}_$(timestamp)_bucket \\"                                                                                                              >> create_spatial_AFI_instructions.txt
	echo "--logs-key logs"                                                                                                                                               >> create_spatial_AFI_instructions.txt
	echo ""                                                                                                                                                              >> create_spatial_AFI_instructions.txt
	echo "aws ec2 create-fpga-image \\"                                                                                                                                  >> create_spatial_AFI_instructions.txt
	echo "--name ${app_name} \\"                                                                                                                                         >> create_spatial_AFI_instructions.txt
	echo "--input-storage-location Bucket=${app_name}_$(timestamp)_bucket,Key=dcp/<tarball-name> \\"                                                                     >> create_spatial_AFI_instructions.txt
	echo "--logs-storage-location Bucket=${app_name}_$(timestamp)_bucket,Key=logs/"                                                                                      >> create_spatial_AFI_instructions.txt
	echo ""                                                                                                                                                              >> create_spatial_AFI_instructions.txt
	echo "# Keep a record of the afi and agfi IDs returned above."                                                                                                       >> create_spatial_AFI_instructions.txt
	echo "# Now wait for the logs to be created in S3, and the State file indicating that the AFI is available (or failed, if there were errors)"                        >> create_spatial_AFI_instructions.txt
	echo "# Once that is done, see the online Spatial AWS documentation for how to open and run on an F1 instance."                                                      >> create_spatial_AFI_instructions.txt
	echo ""                                                                                                                                                              >> create_spatial_AFI_instructions.txt
	#
	# *** Place and Route is now running. When it completes, follow instructions in create_spatial_AFI_instructions.txt ***
	#

aws-F1-sw:
	$(eval app_name=$(notdir $(shell pwd)))
	# ----------------------------------------------------------------------------
	# Make the host binary
	# NOTE: Requires sdk_setup.sh to have been sourced, and aws-F1-hw to have been run
	# ----------------------------------------------------------------------------
	# Add all the static software files
	cp -f cpp/TopHost.cpp $(AWS_HOME)/hdk/cl/examples/${app_name}/software/runtime/
	cp -f cpp/*.h $(AWS_HOME)/hdk/cl/examples/${app_name}/software/include/
	cp -f cpp/fringeAWS/headers/* $(AWS_HOME)/hdk/cl/examples/${app_name}/software/include/
	cp -rf cpp/datastructures $(AWS_HOME)/hdk/cl/examples/${app_name}/software/runtime/
	# Compile
	cp -f cpp/fringeAWS/build/Makefile $(AWS_HOME)/hdk/cl/examples/${app_name}/software/runtime/
	cp -f cpp/fringeAWS/build/load.sh $(AWS_HOME)/hdk/cl/examples/${app_name}/software/runtime/
	cd $(AWS_HOME)/hdk/cl/examples/${app_name}/software/runtime && make all
	# The 'Top' binary is now in the software/runtime directory mentioned in the previous line
	# Important: run this on the F1 using sudo, e.g. 'sudo ./Top arg1 arg2'
	# If this compilation was not done on the F1, you may need to change permissions on the F1, e.g. using 'chmod 700 Top'

# ------------------------------------------------------------------------------
# END OF AWS TARGETS
# ------------------------------------------------------------------------------


zynq: zynq-hw zynq-sw

zynq-sw:
	cp zynq.mk cpp/Makefile
	make -C cpp -j8
	tar -czf $(APPNAME).tar.gz -C ${ZYNQ_V_DIR} accel.bit.bin -C ../cpp Top -C fringeZynq/utils set_perms run.sh

zynq-hw:
	sbt "run-main top.Instantiator --verilog --testArgs zynq"
	mv ${BIGIP_SCRIPT} ${ZYNQ_V_DIR}/
	cat chisel/template-level/fringeAWS/SRAMVerilogAWS.v >> ${ZYNQ_V_DIR}/Top.v
	cp chisel/template-level/fringeZynq/build/* ${ZYNQ_V_DIR}
	mv ${ZYNQ_V_DIR}/fsbl.elf._ ${ZYNQ_V_DIR}/fsbl.elf
	mv ${ZYNQ_V_DIR}/u-boot.elf._ ${ZYNQ_V_DIR}/u-boot.elf
	make -C ${ZYNQ_V_DIR}

zynq-clean:
	make -C ${ZYNQ_V_DIR} clean
	make -C cpp clean
	rm -rf ${ZYNQ_V_DIR}
	rm -f $(APPNAME).tar.gz ${BIGIP_SCRIPT}
	rm -rf target

de1soc: de1soc-hw de1soc-sw

de1soc-sw: 
	cp de1soc.mk cpp/Makefile
	cp cpp/cpptypes.h cpp/datastructures
	cp cpp/DE1SoC.h cpp/fringeDE1SoC/
	cp cpp/Structs.h cpp/datastructures 2>/dev/null || :
	cp cpp/cppDeliteArrayStructs.h cpp/datastructures 2>/dev/null || :
	make -C cpp
	ln -sf cpp/Top .
	cp verilog-de1soc/program_de1soc.sh ./ && chmod +x program_de1soc.sh
	rm -rf ./prog
	mkdir ./prog
	cd ./prog/ && mkdir verilog
	cp Top program_de1soc.sh ./prog
	cp sp.rbf ./prog/verilog/accel.bit.bin

de1soc-hw:
	sbt "run-main top.Instantiator --verilog --testArgs de1soc"
	sed -i 's/SRFF/SRFF_sp/g' verilog-de1soc/Top.v
	cp -r chisel/template-level/fringeDE1SoC/simulation verilog-de1soc/
	cp -r chisel/template-level/fringeDE1SoC/* verilog-de1soc/
	cp verilog-de1soc/Top.v verilog-de1soc/Computer_System/synthesis/submodules
	cd verilog-de1soc && chmod +x compile.sh && ./compile.sh
	echo "sp.rbf generated. Please copy it to your working dir on FPGA ARM"
	cp verilog-de1soc/sp.rbf ./

de1soc-clean:
	rm -rf verilog-de1soc
	rm -rf ./prog
	cd ./cpp && make clean

vcs: vcs-hw vcs-sw
	tar -czf TopVCS.tar.gz -C verilog-vcs accel.bit.bin -C ../cpp Top

vcs-sw:
	cp vcs.mk cpp/Makefile
	cp cpp/cpptypes.h cpp/datastructures
	cp cpp/Structs.h cpp/datastructures 2>/dev/null || :
	cp cpp/cppDeliteArrayStructs.h cpp/datastructures 2>/dev/null || :
	make -j8 -C cpp
	ln -sf cpp/Top .

vcs-hw:
	sbt "run-main top.Instantiator --verilog --testArgs vcs"
	cp -r chisel/template-level/fringeVCS/* verilog-vcs
	touch in.txt
	make -C verilog-vcs
	ln -sf verilog-vcs verilog

vcs-clean:
	make -C verilog-vcs clean
	make -C cpp clean
	rm -rf verilog-vcs
	rm -f verilog TopVCS.tar.gz Top *.log *.vcd ucli.key ${BIGIP_SCRIPT}
	rm -rf target

sim-clean:
	make -C ${VERILATOR_SRC} clean
	rm -f Top *.vcd
