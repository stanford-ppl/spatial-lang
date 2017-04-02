#!/bin/bash
export USE_IDEAL_DRAM=1
export DRAMSIM_HOME=`pwd`/verilog/DRAMSim2
export LD_LIBRARY_PATH=${DRAMSIM_HOME}:$LD_LIBRARY_PATH
./Top $@
