|        | Templates | Unit Tests | Dense Apps | Sparse Apps | Characterization Tests |
|--------|-----------|------------|------------|-------------|------------------------|
| Chisel | [![Build Status](https://travis-ci.org/stanford-ppl/spatial-lang.svg?branch=master)](https://travis-ci.org/stanford-ppl/spatial-lang)         | Unit: [![Build Status](https://travis-ci.org/mattfel1/UnitTracker.svg?branch=master)](https://github.com/stanford-ppl/spatial-lang/wiki/Branchmaster-Testchisel-Regression)           | [![Build Status](https://travis-ci.org/mattfel1/DenseTracker.svg?branch=master)](https://github.com/stanford-ppl/spatial-lang/wiki/Branchmaster-Testchisel-Regression)           | [![Build Status](https://travis-ci.org/mattfel1/SparseTracker.svg?branch=master)](https://github.com/stanford-ppl/spatial-lang/wiki/Branchmaster-Testchisel-Regression)             | Characterization: [![Build Status](https://travis-ci.org/mattfel1/CharacterizationTracker.svg?branch=master)](https://github.com/stanford-ppl/spatial-lang/wiki/Branchmaster-Testchisel-Regression)                       |
| Scala  | N/A       |            |            |             |                        |




# Spatial
Spatial is an Argon DSL for programming reconfigurable hardware from a parameterized, high level abstraction.

#Prerequisites
- [Scala SBT](http://www.scala-sbt.org)
- [Java JDK](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
- [Argon](https://github.com/stanford-ppl/argon)
- [Verilator](https://www.veripool.org/projects/verilator/wiki/Installing) (Required for fpga simulation only)

#Installation
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


Introduction to Spatial
=======================
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

The "<app name>" refers to the name of the `Object`.  

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




The Spatial Programming Model
=============================
Coming Soon!
**Raghu**


Design Space Exploration with Spatial
=====================================
Coming Soon!
**David**

