TOP=Top
EXE=accel.bit.bin

CC=g++
VCS_OPTS=-full64 -quiet -timescale=1ns/1ps -sverilog -debug_pp -Mdir=${TOP}.csrc +v2k +vcs+lic+wait +vcs+initreg+random +define+CLOCK_PERIOD=1 +lint=TFIPC-L
CC_OPTS=-LDFLAGS -lstdc++ -CFLAGS "-O0 -g -I${VCS_HOME}/include -I../../cpp/fringeVCS/ -fPIC -std=c++11"

sim:
	export LM_LICENSE_FILE=27000@cadlic0.stanford.edu
	bash vcs ${VCS_OPTS} -cpp ${CC} ${CC_OPTS} -o accel.bit.bin ${TOP}.v ${TOP}-harness.sv sim.cpp

clean:
	rm -rf *.o *.csrc *.daidir ${TOP} simv ucli.key *.cmd *.in *.out *.vcd *.vpd Sim
