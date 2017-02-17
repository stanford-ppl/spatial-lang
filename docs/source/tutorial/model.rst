2. The Spatial Programming Model
================================
An application in Spatial always has two components: the portion of code that runs on the CPU or host machine and the
portion of code that runs on the FPGA or other dataflow accelerator.
In general, algorithms have computation-heavy sections that a user will want to offload to an accelerator, and then other
computations that can be done on a CPU.

There are a number of hardware templates available in Spatial for specifying algorithms on hardware:
* :doc:`Memory Templates <memories>` allow communicating results, both between logic and to and between the accelerator and the host CPU.
* :doc:`Controllers <controllers>` provide the core control structure of all Spatial applications
* :doc:`Main Memory Transfers <transfers>` are specialized controllers which provide communication between the accelerator and off-chip memory
* Primitives are basically everything else - including memory reads and writes and all of the core compute logic

Additionally, there is some protocol that you, the designer, may want for passing data between the two.
The language allows you to express any algorithm and divvy up the workload appropriately between devices.
It provides many abstractions and optimizations that allow you to work with your algorithm at a relatively high level and
automatically generates efficient Verilog for that design.  This means you are free to make high level changes in a few
lines of Spatial code that may correspond to changing hundreds or thousands of lines of Verilog if you were to write your
own hand-optimized version.  Here we will discuss the components of the language in detail, as well as important features
of the language, such as counters/parallelization.  Please refer to the rest of the docs for detailed API information for these components.



Let's first :doc:`look at the memory templates available in Spatial <memories>`


