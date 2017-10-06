TOP=Top
EXE=accel.bit.bin

CC=g++

# Clean the section below, ripped from aws-fpga
XSIM_OPTS=-full64 -quiet -timescale=1ns/1ps -sverilog -debug_pp -Mdir=${TOP}.csrc +v2k +xsim+lic+wait +xsim+initreg+random +define+CLOCK_PERIOD=1 +lint=TFIPC-L
CC_OPTS=-LDFLAGS "-L../ -ldramsim -lstdc++ -Wl,-rpath=../" -CFLAGS "-O0 -g -I${XSIM_HOME}/include -I../../cpp/fringeVCS -I../dramShim -I../DRAMSim2 -I../ -fPIC -std=c++11 -L../ -ldramsim -lstdc++ -Wl,-rpath=../"
SIM_DIR=development
CL_ROOT=$(PWD)
C_SRC_DIR=$(CL_ROOT)/verilog-xsim/src
C_INC_DIR=$(CL_ROOT)/verilog-xsim/include
TEST=test_null
C_TEST=test_null
TEST_NAME=$(CL_ROOT)/verif/$(TEST).sv
C_TEST_NAME=$(C_SRC_DIR)/$(C_TEST).c
SIM_ROOT=$(CL_ROOT)/verif/sim
SIM_DIR=$(SIM_ROOT)/$(TEST)
SCRIPTS_DIR=$(CL_ROOT)/verilog-xsim/scripts
SV_TEST_LIST = test_dram_dma
C_TEST_LIST  = test_peek_poke.c


all: dram compile

sim:
	export LM_LICENSE_FILE=27000@cadlic0.stanford.edu
	xsim ${XSIM_OPTS} -cpp ${CC} ${CC_OPTS} -o accel.bit.bin SRAMVerilogSim.v ${TOP}.v ${TOP}-harness.sv sim.cpp

dram:
	make -j8 -C DRAMSim2 libdramsim.so
	ln -sf DRAMSim2/libdramsim.so .
#	make -C dramShim
#	ln -sf dramShim/dram .
clean:
	rm -rf *.o *.csrc *.daidir ${TOP} simv ucli.key *.cmd *.in *.out *.vcd *.vpd Sim


compile:
	mkdir -p $(SIM_DIR)
	cd $(SIM_DIR) && xsc $(C_TEST_NAME) --additional_option "-I$(C_INC_DIR)" --additional_option "-DVIVADO_SIM"
#	cd $(SIM_DIR) && xvlog --sv -m64 --initfile $(SCRIPTS_DIR)/xsim.ini --work xil_defaultlib --relax -f $(SCRIPTS_DIR)/top.vivado.f
	cd $(SIM_DIR) && xvlog --sv -m64 --initfile $(XILINX_VIVADO)/data/xsim/ip/xsim_ip.ini --work xil_defaultlib --relax -f $(SCRIPTS_DIR)/top.vivado.f
# #	cd $(SIM_DIR) && xelab -m64 --initfile $(SCRIPTS_DIR)/xsim.ini --timescale 1ps/1ps --debug typical --relax --mt 8 -L axi_clock_converter_v2_1_11 -L generic_baseblocks_v2_1_0 -L axi_infrastructure_v1_1_0 -L axi_register_slice_v2_1_12 -L fifo_generator_v13_1_4 -L axi_data_fifo_v2_1_11 -L axi_crossbar_v2_1_13 -L xil_defaultlib -L unisims_ver -L unimacro_ver -L secureip -L xpm -sv_lib dpi --snapshot tb xil_defaultlib.tb xil_defaultlib.glbl xil_defaultlib.$(TEST) 
# 	cd $(SIM_DIR) && xelab -m64 --initfile $(XILINX_VIVADO)/data/xsim/ip/xsim_ip.ini --timescale 1ps/1ps --debug typical --relax --mt 8 -L axi_clock_converter_v2_1_11 -L generic_baseblocks_v2_1_0 -L axi_infrastructure_v1_1_0 -L axi_register_slice_v2_1_12 -L fifo_generator_v13_1_4 -L axi_data_fifo_v2_1_11 -L axi_crossbar_v2_1_13 -L xil_defaultlib -L unisims_ver -L unimacro_ver -L secureip -L xpm -sv_lib dpi --snapshot tb xil_defaultlib.tb xil_defaultlib.glbl xil_defaultlib.$(TEST) 


run:
	cd $(SIM_DIR) && xsim -R -log test.log -tclbatch $(SCRIPTS_DIR)/waves.tcl tb

