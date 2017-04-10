VERILATOR_SRC=cpp/verilator
AWS_SRC=cpp/aws

all: help

help:
	@echo "------- SUPPORTED MAKE TARGETS -------"
	@echo "make sim 				: Verilator SW + HW build"
	@echo "make sim-sw 				: Verilator SW build"
	@echo "make sim-hw 			: Build Chisel for Verilator"
	@echo "make aws-sim 		: AWS simulation SW + HW build"
	@echo "make aws-sim-hw 	: Build Chisel for AWS simulation"
	@echo "make zynq 				: Zynq SW + HW build"
	@echo "make zynq-hw    	: Build Chisel for Zynq"
	@echo "make sim-clean 	: Verilator simulation clean up"
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
	sbt "run-main top.Instantiator --backend-name verilator --target-dir ${VERILATOR_SRC} --testArgs"
	cp verilator.mk ${VERILATOR_SRC}/Makefile
	mv ${VERILATOR_SRC}/top* ${VERILATOR_SRC}/verilator_srcs_tmp
	mkdir ${VERILATOR_SRC}/verilator_srcs
	mv ${VERILATOR_SRC}/verilator_srcs_tmp/VTop* ${VERILATOR_SRC}/verilator_srcs
	mv ${VERILATOR_SRC}/verilator_srcs_tmp/*.v ${VERILATOR_SRC}/verilator_srcs
	rm -rf ${VERILATOR_SRC}/verilator_srcs_tmp
	mv ${VERILATOR_SRC}/verilator_srcs/*.mk ${VERILATOR_SRC}

# Run simulation using Vivado XSIM
aws-sim: aws-sim-hw
	# Build the DPI .so library
	cd $(AWS_HOME)/hdk/cl/examples/spatial_design/verif/scripts
	make C_TEST=test_spatial_main compile
	cd -
	# Create the binary
	sed 's:{{{INSERT_DESIGN_DIR}}}:'"${AWS_HOME}"'/hdk/cl/examples/spatial_design/verif/scripts:g' cpp/fringeAWS/Top_template > ./Top
	chmod 700 Top

# Set up simulation directory
# Requires sim-hw to have been run in order to get verilog and cpp
aws-sim-hw:
	# First build chisel, with backend verilator to create the verilog
	sbt "run-main top.Instantiator --backend-name verilator --target-dir ${AWS_SRC} --testArgs aws"
	mv ${AWS_SRC}/example* ${AWS_SRC}/aws_srcs_tmp
	mv ${AWS_SRC}/aws_srcs_tmp/*.v ${AWS_SRC}/
	rm -rf ${AWS_SRC}/aws_srcs_tmp
	# Make a copy of the template directory
	rm -rf $(AWS_HOME)/hdk/cl/examples/spatial_design
	cp -r $(AWS_HOME)/hdk/cl/examples/cl_simple $(AWS_HOME)/hdk/cl/examples/spatial_design
	# Add all the static design files
	cp -f cpp/fringeAWS/design/* $(AWS_HOME)/hdk/cl/examples/spatial_design/design/
	cp -f cpp/aws/Top.v $(AWS_HOME)/hdk/cl/examples/spatial_design/design/
	# Add all the static software files
	cp -f cpp/TopHost.cpp $(AWS_HOME)/hdk/cl/examples/spatial_design/software/src/
	cp -f cpp/fringeAWS/headers/* $(AWS_HOME)/hdk/cl/examples/spatial_design/software/include/
	cp -rf cpp/datastructures $(AWS_HOME)/hdk/cl/examples/spatial_design/software/src/
	# Add all the simulation Makefiles
	cp -f cpp/fringeAWS/sim/* $(AWS_HOME)/hdk/cl/examples/spatial_design/verif/scripts/
	# Run a script to put everything together
	python cpp/fringeAWS/gen_aws_dir.py $(AWS_HOME)/hdk/cl/examples/spatial_design

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

de1soc-sw: 
	cp de1soc.mk cpp/Makefile
	make -C cpp

de1soc-hw:
	sbt "run-main top.Instantiator --verilog --testArgs de1soc"
	sed -i 's/SRFF/SRFF_sp/g' verilog-de1soc/Top.v
	cp -r chisel/fringeDE1SoC/simulation verilog-de1soc/
	cp -r chisel/fringeDE1SoC/* verilog-de1soc/
	cp verilog-de1soc/Top.v verilog-de1soc/Computer_System/synthesis/submodules
	cd verilog-de1soc && ./compile.sh
	echo "sp.rbf generated. Please copy it to your working dir on FPGA ARM"

de1soc-clean:
	rm -rf verilog

vcs: vcs-hw vcs-sw
	tar -czf TopVCS.tar.gz -C verilog-vcs accel.bit.bin -C ../cpp Top

vcs-sw:
	cp vcs.mk cpp/Makefile
	cp cpp/cpptypes.h cpp/datastructures
	cp cpp/Structs.h cpp/datastructures 2>/dev/null || :
	cp cpp/cppDeliteArrayStructs.h cpp/datastructures 2>/dev/null || :
	make -C cpp
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
	rm -f verilog TopVCS.tar.gz Top *.log *.vcd
	rm -rf target

sim-clean:
	make -C ${VERILATOR_SRC} clean
	rm -f Top *.vcd
