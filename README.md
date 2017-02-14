|        | Templates | Unit Tests | Dense Apps | Sparse Apps | Characterization Tests |
|--------|-----------|------------|------------|-------------|------------------------|
| Chisel | [![Build Status](https://travis-ci.org/stanford-ppl/spatial-lang.svg?branch=fpga)](https://travis-ci.org/stanford-ppl/spatial-lang)         | [![Build Status](https://travis-ci.org/mattfel1/Trackers.svg?branch=ClassUnit-Branchfpga-Backendchisel-Tracker)](https://github.com/stanford-ppl/spatial-lang/wiki/Branch-fpga-Backend-chisel-Regression)           | [![Build Status](https://travis-ci.org/mattfel1/Trackers.svg?branch=ClassDense-Branchfpga-Backendchisel-Tracker)](https://github.com/stanford-ppl/spatial-lang/wiki/Branch-fpga-Backend-chisel-Regression)           | [![Build Status](https://travis-ci.org/mattfel1/Trackers.svg?branch=ClassSparse-Branchfpga-Backendchisel-Tracker)](https://github.com/stanford-ppl/spatial-lang/wiki/Branch-fpga-Backend-chisel-Regression)             | [![Build Status](https://travis-ci.org/mattfel1/Trackers.svg?branch=ClassCharacterization-Branchfpga-Backendchisel-Tracker)](https://github.com/stanford-ppl/spatial-lang/wiki/Branch-fpga-Backend-chisel-Regression)                       |
| Scala  | N/A       |  [![Build Status](https://travis-ci.org/mattfel1/Trackers.svg?branch=ClassUnit-Branchfpga-Backendscala-Tracker)](https://github.com/stanford-ppl/spatial-lang/wiki/Branch-fpga-Backend-scala-Regression)           | [![Build Status](https://travis-ci.org/mattfel1/Trackers.svg?branch=ClassDense-Branchfpga-Backendscala-Tracker)](https://github.com/stanford-ppl/spatial-lang/wiki/Branch-fpga-Backend-scala-Regression)           | [![Build Status](https://travis-ci.org/mattfel1/Trackers.svg?branch=ClassSparse-Branchfpga-Backendscala-Tracker)](https://github.com/stanford-ppl/spatial-lang/wiki/Branch-fpga-Backend-scala-Regression)             | [![Build Status](https://travis-ci.org/mattfel1/Trackers.svg?branch=ClassCharacterization-Branchfpga-Backendscala-Tracker)](https://github.com/stanford-ppl/spatial-lang/wiki/Branch-fpga-Backend-scala-Regression)                   |




# Spatial
Spatial is an Argon DSL for programming reconfigurable hardware from a parameterized, high level abstraction.

# Prerequisites
- [Scala SBT](http://www.scala-sbt.org)
- [Java JDK](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
- [Argon](https://github.com/stanford-ppl/argon)
- [Verilator](https://www.veripool.org/projects/verilator/wiki/Installing) (Required for fpga simulation only)

# Installation
You will clone three repositories, scala-virtualized, argon, and spatial-lang.  You can place these anywhere as long as you
point your environment variables correctly, but this tutorial will assume you place all three in ${HOME}/spatial.  Run the
following commands:
```bash
mkdir ${HOME}/spatial
cd ${HOME}/spatial
git clone https://github.com/stanford-ppl/spatial-lang.git
git clone https://github.com/stanford-ppl/argon.git
git clone https://github.com/stanford-ppl/scala-virtualized.git
cd scala-virtualized && git fetch && git checkout origin/argon && cd ../ # switch to Argon branch for scala-virtualized
cd spatial-lang
```

Next, make sure the following environment variables are set.  If you are using the recommended
directory structure in this tutorial, then you can simply run the following command:
```bash
cd ${HOME}/spatial/spatial-lang
source ./init-env.sh
```
Otherwise, if you have some other structure, you need to set the following variables manually.
It may be easiest to set them in your bashrc so all future bash sessions have them: 
```bash
export JAVA_HOME = ### Directory Java is installed, usually /usr/bin
export ARGON_HOME = ### Top directory of argon
export SPATIAL_HOME = ### Top directory of spatial-lang
export VIRTUALIZED_HOME = ### Top directory of scala-virtualized
```
Once these are all set, you are ready to compile the language.  Run the following:
```bash
cd ${SPATIAL_HOME}
make full
```


#Introduction to Spatial
Now that you have cloned all of the code and set all of your environment variables, let's look at how to write, build, and run your first Spatial app!

### a) Spatial App Structure
All Spatial programs have a few basic components. The following code example shows each of those components.

```scala
// 0. Imports
import org.virtualized._
import spatial._

// 1. The Scala object which can be compiled and staged
object ArgInOut extends SpatialApp {
  import IR._

  // 3. This method, main, is called on program startup.  Spatial apps are required to use the "@virtualize" macro
  @virtualize
  def main() {

    // 5. Declare command line input arguments
    val N = args(0).to[Int]

    // 6. clare the interface and explicitly communicate between the hardware and software
    val x = ArgIn[Int]
    val y = ArgOut[Int]
    setArg(x, N)

    // 7. Specify algorithm to be done on hardware
    Accel {
      y := x + 4
    }

    // 8. Explicitly pull data off of the hardware
    val result = getArg(y)

    // 9. Specify algorithm to be done in software
    val gold = N + 4
    println("expected: " + gold)
    println("result: " + result)
  }
}
```
 

Because Spatial is a DSL for programming reconfigurable *hardware*, we will begin with the hardware equivalent of "Hello, World."  In this app, the hardware reads some numeric argument from an off-chip source and then echoes it back to an off-chip destination.

You can find the source code (shown in the next section for convenience) for this app in the previous example:

    $SPATIAL_HOME/apps/src/ArgInOutTest.scala


### b) Hello, World!
Spatial apps are always divided into two parts: the portion of code that runs on the host CPU and the portion of code that gets generated as an accelerator.  The entirety of the app exists inside of `Main()`, and the subset of code inside of the `Accel{}` block is the hardware part of the app.

In ArgInOut, we start with three declarations above the `Accel{}` block.  First, we declare x to be an `ArgIn` of type `Int`.  Then, we declare y to be an `ArgOut` of type `Int`.  Finally, we declare `N` to be one of the command-line input arguments at run-time by setting it equal to `args(0)`.  We must also explicitly cast this argument to a Spatial type by appending `.as[Int]`.  In addition to ArgIns and ArgOuts, there is a type, `DRAM`, which represents a 1-D or 2-D array that the accelerator can read from and write to.

Now that we have both a val that represents an ArgIn and another val which reads some value from the command-line at runtime, we must connect the two with `setArg(<HW val>, <SW val>)`.  Similarly, we can connect a DRAM to an array with `setMem(<HW array>, <SW array>)`.

Next, we specify the `Accel{}` block.  In this particular app, we simply want to add the number 4 to whatever input argument is read in.  To do this, we create a `Pipe` that consists of this primitive addition operation and writes the result to an ArgOut register with `:=`.  In later sections, you will learn what other operations and building blocks Spatial exposes to the developer.

After the `Accel{}` block, we return to the host code section of an app that will interact with the result generated by the hardware.  Specifically, we start by assigning the ArgOut register to a SW variable with `getArg(<HW val>)`.  Similarly, we can assign a DRAM to a SW val with `getMem(<HW array>)`.

Finally, we add any debug and validation code to check if the accelerator is performing as expected.  In this example, we compute the result we expect the hardware to give, and then println both this number and the number we actually got.

### c) Compiling, Synthesizing, and Testing

You should edit and place apps inside of your `${SPATIAL_HOME}/apps/src/` directory.  **Any time you change an app, you must remake Spatial with:**

    cd ${SPATIAL_HOME} && make apps

Once you have a complete Spatial app, you will want to compile and run it.  Currently, there are two available targets; Scala and Chisel (for fpga).  

#### i) Compiling to Scala
Targetting Scala is the quickest way to simulate your app and test for accuracy.  It also exposes println to code that exists inside the `Accel` block with string interpolation arguments (s"").  You should use this backend if you are debugging things at the algorithm level.  In order to compile and simulate for the Scala backend, run:

    cd ${SPATIAL_HOME}/
    bin/spatial <app name> --scala # + other options

The "<app name>" refers to the name of the `Object`.  See the "Testing" section below for a guide on how to test the generated app

#### ii) Compiling to Chisel
Targetting Chisel will let you compile your app down into Berkeley's chisel language, which eventually compiles down to Verilog.  It also allows you to debug your app at the clock-cycle resolution. In order to compile with the Chisel backend, run the following:

    cd ${SPATIAL_HOME}
    bin/spatial <app name> --chisel # + other options

#### iii) Testing
After you have used the bin/spatial script to compile the app, navigate to the generated code 
directory to test the app.  By default, this is `${SPATIAL_HOME}/gen/<app name>`.  You will see some
files and directories in this folder that correspond to the code that spatial created for the various
target platforms.  
For the chisel backend specifically, here is a rough breakdown of what the important files are:
	
	chisel/TopTrait.scala # Main trait where all of the controller and dataflow connections are made
	chisel/IOModule.scala # Interface between fpga and CPU
	chisel/BufferControlCxns # Connections for all n-buffered memories in the design
	chisel/resources/*.scala # Files for all of the fundamental building blocks of a spatial app
	cpp/TopHost.scala # Contains the Application method where all CPU code is generated
	controller_tree.html # Helpful diagram for showing the hierarchy of control nodes in your app

In order to finally test this code, you must compile the backend code itself.  In order to do so, run the following:
    
    cd ${SPATIAL_HOME}/gen/<app name>
    make sim
    bash run.sh <arguments>

If using the chisel backend, this will turn any chisel code into verilog, which then gets turned into C++ through verilator.  It also compiles the Spatial-generated C++.  Finally, the run.sh script executes the entire application with communication between the hardware and CPU and returns the result.  If using the scala backend, this will just test the scala code on your machine.

After running a chisel app, you can see the waveforms generated in the `test_run_dir/app.Launcher####` folder, with the `.vcd` extension for further debugging

The "<arguments>" should be a space-separated list, fully enclosed in quotes.  For example, an app that takes arguments 192 96 should be run with

	bash run.sh "192 96"




#The Spatial Programming Model
An application in Spatial always has two components: the portion of code that runs on the CPU or host machine and the portion of code that runs on the fpga or other dataflow accelerator.  In general, algorithms have computation-heavy sections that a user will want to offload to an accelerator, and then other computations that can be done on a CPU.  Additionally, there is some protocol that you, the designer, may want for passing data between the two.  The language allows you to express any algorithm and divvy up the workload appropriately between devices.  It provides many abstractions and optimizations that allow you to work with your algorithm at a relatively high level and automatically generates efficient verilog for that design.  This means you are free to make high level changes in a few lines of Spatial code that may correspond to changing hundreds or thousands of lines of verilog if you were to write your own hand-optimized version.  Here we will discuss the components of the language in detail, as well as important features of the language, such as counters/parallelization.  Please refer to the Scaladoc for detailed API information for these components.

##1) Accel Block
The `accel` block scopes out any code that will run on the dataflow accelerator (fpga, Plasticine, etc.).  This is where you should specify the dataflow-heavy part of the algorithm using a hierarchy of control nodes and primitive operations.

###a) Control Nodes
A control node is essentially a `foreach` (functional) or a `for loop` (imperative).  It scopes out a section of code that runs for some number of iterations.  It is best to think of the `accel` block as a hierarchy of control nodes that contain either a list of other control nodes, or a list of primitive operations.  The control nodes available are:
####i) Sequential.Foreach
The stages inside of a `Sequential.Foreach` are executed one at a time without overlap between stages.  The counter for this node increments only after the last stage of the controller has finished.  It is best to use this node when there are loop-carry dependencies between the stages of the pipeline that cannot be fixed.
####ii) Foreach
The stages inside of a `Foreach` are executed in standard pipelined fashion.  Stage 0 executes with the first value of the counter first.  When stage 0 finishes, the counter for the control node increments and Stage 0 then begins executing again with this new counter value.  Meanwhile, it has passed its old counter value to stage 1 and then stage 1 executes its first iteration.  And so forth.
####iii) Reduce
Many times, an algorithm requires a reduction.  This is an operation that takes many values and reduces them to one value.  A `Reduce` node consists of a map function, which is responsible for producing the values that will be used in the reduction.  For example, the map function in dot product would be reading and element from both vectors and computing their product.  It also specifies a reduction function to describe how the values should be reduced.  In dot product, this would be simple addition, but you can specify operations such as multiplication or minimum/maximum.
####iv) MemReduce
There are other algorithms where you may have an n-dimensional array and want to accumulate it on top of an array of the same dimensions.  For example, gradient descent algorithms compute a small update to a weights vector and add this to the old weights vector.  This control node is exactly like the Reduce node above, except the accumulator is an SRAM and the map function produces an SRAM of matching dimensions rather than a scalar value.
####v) Fold
Similar to Reduce, but you must provide the initial, default value of the reduction.
####vi) Parallel
All children nodes execute in parallel, and this control node does not finish until all of its children have finished.  Can only contain other control nodes.  Will be deprecated soon.
###b) Memories
####i) SRAM
On fpgas, the SRAMs refer to the block RAMs on the device.  You can think of them as scratchpad memories that take one cycle to read and write.
####ii) DRAM
DRAM refers to memory that is not on-board the accelerator.  Data is generally put into these memories by the CPU and then fetched by the accelerator.  The accelerator can fetch memory from DRAM at the burst-granularity (64 bytes = 16 4-byte words)
####iii) Reg
A register is a memory element that holds some bits.  Generally, this would be a float, fixed point, or integer number, but can also be a tuple as is the case for K-Means.  In such a case, all of the bits of the tuple are concatenated and placed in a register, to be later reinterpreted and sliced.
####iv) FIFO
This is an SRAM behind the scenes, but rather than being word-addressable, you can only push and pop elements to it.
###c) Data Transfers
####i) Load
Loads some chunk of memory from DRAM and place in SRAM or FIFO.  If the chunk is not burst aligned, then a whole burst is issued but data that was not wanted gets dumped into a null buffer
####ii) Store
Stores some SRAM or FIFO to memory.  
####iii) Gather
Given DRAM addresses in some SRAM, it fetches these words from DRAM and places them in an SRAM that corresponds to the address SRAM.
####iv) Scatter
Given DRAM addresses in one SRAM and data in another SRAM, it scatters the data to the corresponding DRAM addresses
###d) Primitives
####i) Register Read/Write
Read and write to a register.  Note that although register reads are non-destructive, they are effectful if done in a Foreach node.  This is because the Spatial language does implicit buffering for your registers when their accesses appear in different stages of a metapipeline and a read is connected to the signals that swap this buffering 
####ii) SRAM Read/Write
Read and write to SRAM.  Same as register accesses except an address must be specified.
####iii) FIFO Push/Pop
Push or pop to a FIFO.  
###e) Counters/Parallelization
####i) Parallelized Counters
An accelerator would not be very good at accelerating anything if it did not allow the user to take advantage of the spatial architecture.  You can parallelize any control node in your algorithm by adding `par $n` to the counter declaration.  If you parallelize a control node that contains other control nodes, Spatial will unroll the inner nodes for you.  If you parallelize a control node that contains only primitives, the counter for that control node will be vectorized.  When you parallelize, all of your memory elements and arithmetic operations will be banked and/or duplicated in order to ensure correctness of your result, assuming there are no dependencies between the counter values.  Parallelized reductions will generate reduction trees, which results in the reduction latency increasing by log2(par).
####ii) Endless Counters
Endless counters are counters whose maximum value is specified by `*`.  For an intuitive understanding of what this means, please see [this video](https://www.youtube.com/watch?v=xWcldHxHFpo)

##2) Main Method
This is where all of the CPU code belongs.  In many examples, we simply redo the computation that the accel block does and check if it produced the correct result.  However, many real-world algorithms will do something different on the CPU than they do in the accel.
###a) Parallel Patterns
See [here](https://www.tutorialspoint.com/scala/) for info on programming in Scala.


Design Space Exploration with Spatial
=====================================
Coming Soon!
**David**

