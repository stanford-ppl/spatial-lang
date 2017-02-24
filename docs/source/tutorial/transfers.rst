5. Transfer Templates
=====================

Data Transfers
--------------

**Load**

Loads some chunk of memory from DRAM and place in SRAM or FIFO.  If the chunk is not burst aligned, then a whole burst is issued but data that was not wanted gets dumped into a null buffer.

**Store**

Stores some SRAM or FIFO to memory.

**Gather**

Given DRAM addresses in some SRAM, it fetches these words from DRAM and places them in an SRAM that corresponds to the address SRAM.

**Scatter**

Given DRAM addresses in one SRAM and data in another SRAM, it scatters the data to the corresponding DRAM addresses


Primitives
----------

**Register Read/Write**

Read and write to a register.  Note that although register reads are non-destructive, they are effectful if done in a Foreach node.  This is because the Spatial language does implicit buffering for your registers when their accesses appear in different stages of a metapipeline and a read is connected to the signals that swap this buffering

**SRAM Read/Write**

Read and write to SRAM.  Same as register accesses except an address must be specified.
####iii) FIFO Push/Pop
Push or pop to a FIFO.

Counters/Parallelization
------------------------

**Parallelized Counters**

An accelerator would not be very good at accelerating anything if it did not allow the user to take advantage of the spatial architecture.  You can parallelize any control node in your algorithm by adding `par $n` to the counter declaration.  If you parallelize a control node that contains other control nodes, Spatial will unroll the inner nodes for you.  If you parallelize a control node that contains only primitives, the counter for that control node will be vectorized.  When you parallelize, all of your memory elements and arithmetic operations will be banked and/or duplicated in order to ensure correctness of your result, assuming there are no dependencies between the counter values.  Parallelized reductions will generate reduction trees, which results in the reduction latency increasing by log2(par).

**Endless Counters**

Endless counters are counters whose maximum value is specified by `*` (i.e. `Foreach(* by 1 par 8){<body>}`.  For an intuitive understanding of what this means, please see [this video](https://www.youtube.com/watch?v=xWcldHxHFpo)
