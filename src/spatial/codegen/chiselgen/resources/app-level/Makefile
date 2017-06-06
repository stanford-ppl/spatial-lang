VERILATOR_SRC=cpp/verilator
AWS_V_DIR=verilog-aws
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
	@echo "------- END HELP -------"

sim: sim-hw
	make -C ${VERILATOR_SRC}
	ln -sf ${VERILATOR_SRC}/Top .

sim-sw:
	make -C ${VERILATOR_SRC}
	ln -sf ${VERILATOR_SRC}/Top .

sim-hw:
	rm -rf ${VERILATOR_SRC}
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
# aws-sim: aws-hw  aws-sim-tb
# aws-hw:
# aws-sim-tb:
# aws-F1 : aws-F1-afi    aws-F1-sw
# aws-F1-afi: aws-hw
# aws-F1-sw :

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
	rm -rf ${AWS_V_DIR}
	# First use chisel to create the verilog
	sbt "run-main top.Instantiator --verilog --testArgs aws"
	cat chisel/fringeVCS/SRAMVerilog.v >> ${AWS_V_DIR}/Top.v
	# Make a copy of the template directory
	rm -rf $(AWS_HOME)/hdk/cl/examples/${app_name}
	cp -r $(AWS_HOME)/hdk/cl/examples/cl_dram_dma $(AWS_HOME)/hdk/cl/examples/${app_name}
	# Add all the static design files
	cp -f cpp/fringeAWS/design/* $(AWS_HOME)/hdk/cl/examples/${app_name}/design/
	cp -f ${AWS_V_DIR}/Top.v $(AWS_HOME)/hdk/cl/examples/${app_name}/design/
	# Run a script to put design together
	python cpp/fringeAWS/gen_aws_design.py $(AWS_HOME)/hdk/cl/examples/${app_name}
	# ----------------------------------------------------------------------------
	# Step 2: Make the testbench
	# ----------------------------------------------------------------------------
	# Add all the static software files
	cp -f cpp/TopHost.cpp $(AWS_HOME)/hdk/cl/examples/${app_name}/software/src/
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
	cat chisel/fringeVCS/SRAMVerilog.v >> ${AWS_V_DIR}/Top.v
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
	export CL_DIR=$(AWS_HOME)/hdk/cl/examples/${app_name}
	cp -f cpp/fringeAWS/build/encrypt.tcl $(AWS_HOME)/hdk/cl/examples/${app_name}/build/scripts/
	# NOTE: The DEFAULT strategy will be used, see others here:
	#       https://github.com/aws/aws-fpga/tree/master/hdk/common/shell_current/new_cl_template/build
	# NOTE: This may fail (e.g. our of area) -- may need to rerun this manually
	cd $(AWS_HOME)/hdk/cl/examples/${app_name}/build/scripts && bash aws_build_dcp_from_cl.sh
	# ----------------------------------------------------------------------------
	# Step 2: Upload bitstream to S3 and create AFI (currently done manually, TODO: script this)
	# This will eventually be a new Makefile target, which runs only when the final DCP is created (after synthesis/place/route)
	# ----------------------------------------------------------------------------
	echo "# Instructions to upload bitstream to S3 and create AFI"                                                                                                        > create_spatial_AFI_instructions.txt
	echo "export AWS_CONFIG_FILE=/home/centos/src/project_data/aws-fpga/hdk/cl/examples/rootkey.csv"                                                                     >> create_spatial_AFI_instructions.txt
	echo "aws s3 mb s3://${app_name}_$(timestamp)_bucket"  --region us-east-1                                                                                            >> create_spatial_AFI_instructions.txt
	echo "aws s3 mb s3://${app_name}_$(timestamp)_bucket/dcp"                                                                                                            >> create_spatial_AFI_instructions.txt
	echo "aws s3 cp build/checkpoints/to_aws/*.Developer_CL.tar s3://${app_name}_$(timestamp)_bucket/dcp/"                                                               >> create_spatial_AFI_instructions.txt
	echo "aws s3 mb s3://${app_name}_$(timestamp)_bucket/logs"                                                                                                           >> create_spatial_AFI_instructions.txt
	echo "touch LOGS_FILES_GO_HERE.txt"                                                                                                                                  >> create_spatial_AFI_instructions.txt
	echo "aws s3 cp LOGS_FILES_GO_HERE.txt s3://${app_name}_$(timestamp)_bucket/logs/"                                                                                   >> create_spatial_AFI_instructions.txt
	echo "Set the right policy for this bucket in S3, see example here: (TODO: script this step)"                                                                        >> create_spatial_AFI_instructions.txt
	echo "  https://github.com/aws/aws-fpga/tree/prelease/hdk/cl/examples#3-submit-the-design-checkpoint-to-aws-to-register-the-afi"                                     >> create_spatial_AFI_instructions.txt
	echo ""                                                                                                                                                              >> create_spatial_AFI_instructions.txt
	echo "# Verify the script below passes. It might fail, in which case follow the instructions in the error message."                                                  >> create_spatial_AFI_instructions.txt
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
	# We recommend doing this on the F1 instance (e.g. copy the source over and build)
	# ----------------------------------------------------------------------------
	# Add all the static software files
	cp -f cpp/TopHost.cpp $(AWS_HOME)/hdk/cl/examples/${app_name}/software/runtime/
	cp -f cpp/fringeAWS/headers/* $(AWS_HOME)/hdk/cl/examples/${app_name}/software/include/
	cp -rf cpp/datastructures $(AWS_HOME)/hdk/cl/examples/${app_name}/software/runtime/
	# Compile
	cp -f cpp/fringeAWS/build/Makefile $(AWS_HOME)/hdk/cl/examples/${app_name}/software/runtime/
	cd $(AWS_HOME)/hdk/cl/examples/${app_name}/software/runtime && make all
	# Note:
	# - run on the F1 using sudo, e.g. 'sudo ./Top arg1 arg2'

# ------------------------------------------------------------------------------
# END OF AWS TARGETS
# ------------------------------------------------------------------------------


zynq: zynq-hw zynq-sw

zynq-sw:
	cp zynq.mk cpp/Makefile
	make -C cpp
	tar -czf TopZynq.tar.gz -C verilog accel.bit.bin -C ../cpp Top -C fringeZynq/utils set_perms run.sh

zynq-hw:
	sbt "run-main top.Instantiator --verilog --testArgs zynq"
	cp chisel/fringeZynq/* verilog-zynq
	mv verilog/fsbl.elf._ verilog/fsbl.elf
	mv verilog/u-boot.elf._ verilog/u-boot.elf
	make -C verilog

zynq-clean:
	make -C verilog-zynq clean
	make -C cpp clean
	rm -rf verilog-zynq
	rm -f TopZynq.tar.gz

vcs: vcs-hw vcs-sw
	tar -czf TopVCS.tar.gz -C verilog-vcs accel.bit.bin -C ../cpp Top

vcs-sw:
	cp vcs.mk cpp/Makefile
	make -C cpp
	ln -sf cpp/Top .

vcs-hw:
	sbt "run-main top.Instantiator --verilog --testArgs vcs"
	cp -r chisel/fringeVCS/* verilog-vcs
	make -C verilog-vcs
	ln -sf verilog-vcs verilog

vcs-clean:
	make -C verilog-vcs clean
	make -C cpp clean
	rm -rf verilog-vcs
	rm -f verilog TopVCS.tar.gz Top *.log *.vcd
	rm -rf target

sim-clean:
	make -C ${VERILATOR_SRC} clean
	rm -f Top *.vcd
