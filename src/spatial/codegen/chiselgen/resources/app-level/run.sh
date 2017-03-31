#!/bin/bash
export DRAMSIM_HOME=`pwd`/verilog/DRAMSim2
export LD_LIBRARY_PATH=${DRAMSIM_HOME}:$LD_LIBRARY_PATH
./Top $@
