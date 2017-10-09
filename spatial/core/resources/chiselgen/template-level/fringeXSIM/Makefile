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


all: dram sim

sim:
	xelab work.Top -prj accel.prj -dll -s accel -debug wave
	g++ -I/opt/Xilinx/Vivado/2017.1/bin/../data/xsim/include -O3 -c -o xsi_loader.o xsi_loader.cpp
	# export LM_LICENSE_FILE=27000@cadlic0.stanford.edu
	# xsim ${XSIM_OPTS} -cpp ${CC} ${CC_OPTS} -o accel.bit.bin SRAMVerilogSim.v ${TOP}.v ${TOP}-harness.sv sim.cpp

dram:
	make -j8 -C DRAMSim2 libdramsim.so
	ln -sf DRAMSim2/libdramsim.so .
#	make -C dramShim
#	ln -sf dramShim/dram .
clean:
	rm -rf *.o *.csrc *.daidir ${TOP} simv ucli.key *.cmd *.in *.out *.vcd *.vpd Sim
