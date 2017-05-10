#!/bin/bash
if [[ "$USE_IDEAL_DRAM" = "0" || "$USE_IDEAL_DRAM" = "1" ]]; then
	ideal=$USE_IDEAL_DRAM
else
	ideal=0
fi
export USE_IDEAL_DRAM=$ideal
export DRAM_DEBUG=0
export DRAM_NUM_OUTSTANDING_BURSTS=-1  # -1 == infinite number of outstanding bursts
export DRAMSIM_HOME=`pwd`/verilog/DRAMSim2
export LD_LIBRARY_PATH=${DRAMSIM_HOME}:$LD_LIBRARY_PATH
./Top $@ 2>&1 | tee sim.log
if [[ "$USE_IDEAL_DRAM" = "1" ]]; then
	echo "Ideal DRAM Simulation"
elif [[ "$USE_IDEAL_DRAM" = "0" ]]; then
	echo "Realistic DRAM Simulation"
else
	echo "UNKNOWN DRAM SIMULATION!"
fi
