#!/bin/python

import fnmatch
import os

latencies = []

# dir_modifier = "retime"
dir_modifier = "done_23"
target = "aws" # ONLY USE THIS SCRIPT FOR AWS TARGET "aws" or "zynq"

if ("zynq" in target):
	rootdir = os.environ['SPATIAL_HOME']
elif ("aws" in target): 
	rootdir = os.environ['RPT_HOME']

algs = []
for item in os.listdir(rootdir):
	if ("out" in item):
		check_for = "_" + dir_modifier + "_"
		if (check_for in item): 
			algs.append(item.replace("out",""))
print algs

latencies = [0] * len(algs)
slacks = [0] * len(algs)
timings = [0] * len(algs)
lut_raws = [0] * len(algs)
ff_raws = [0] * len(algs)
dsp_raws = [0] * len(algs)
mem_raws = [0] * len(algs)

# Look for synth results
for alg in algs:
	if ("zynq" in target):
		util_file=os.path.join(rootdir, "out" + alg, "verilog-zynq", "par_utilization.rpt")
	elif ("aws" in target): 
		util_file=os.path.join(rootdir, "out" + alg, "build", "reports", "utilization_route_design.rpt")
	if os.path.isfile(util_file):
		clb_summary = 0
		mem_summary = 0
		dsp_summary = 0
		with open(util_file) as fin:
			for i, line in enumerate(fin):
				line_array = line.split("|")

				# Set table entry flags
				if (len(line_array) == 1) and ("CLB Logic" in line_array[0]):
					clb_summary = 1
				if (len(line_array) == 1 and ("BLOCKRAM" in line_array[0]) ):
					mem_summary = 1
				if (len(line_array) == 1 and ("ARITHMETIC" in line_array[0]) ):
					dsp_summary = 1

				# Collect data based on current table
				if (clb_summary == 1 and len(line_array) == 9 and "CLB LUTs" in line_array[1]):
					this_id = algs.index(alg)
					lut = int(line_array[2].strip())
					lut_raws[this_id] = lut_raws[this_id] + lut
				if (clb_summary == 1 and len(line_array) == 9 and "CLB Registers" in line_array[1]):
					this_id = algs.index(alg)
					ff = int(line_array[2].strip())
					ff_raws[this_id] = ff_raws[this_id] + ff			
					clb_summary = 0
				if (mem_summary == 1 and len(line_array) == 9 and "Block RAM Tile" in line_array[1]):
					this_id = algs.index(alg)
					mem = float(line_array[2].strip())
					mem_raws[this_id] = mem_raws[this_id] + mem			
					mem_summary = 0
				if (dsp_summary == 1 and len(line_array) == 9 and "DSPs" in line_array[1]):
					this_id = algs.index(alg)
					dsp = int(line_array[2].strip())
					dsp_raws[this_id] = dsp_raws[this_id] + dsp			
					dsp_summary = 0

	if ("zynq" in target):
		util_file=os.path.join(rootdir, "out" + alg, "verilog-zynq", "par_timing.rpt")
	elif ("aws" in target): 
		util_file=os.path.join(rootdir, "out" + alg, "build", "reports", "timing_summary_route_design.rpt")
	if os.path.isfile(util_file):
		tmg_pass = 0
		with open(util_file) as fin:
			for i, line in enumerate(fin):
				if ("All user specified timing constraints are met." in line):
					tmg_pass = 1
		this_id = algs.index(alg)
		timings[this_id] = tmg_pass

				# Figure out which section is the relevent clock with slack

# Sort the arrays


print "%18s\t%s\t%s\t%s\t%s\t%s\t%s" % ("ALGORITHM", "SLACK", "TIMING_PASS", "MEM", "DSP", "LUT", "FF")
print "---------------------------------------------------------------------------------------"
for i in range(0, len(algs)):
	print "%18s\t%s\t%s\t%s\t%s\t%s\t%s" % (algs[i], latencies[i], timings[i], mem_raws[i], dsp_raws[i], lut_raws[i], ff_raws[i])
