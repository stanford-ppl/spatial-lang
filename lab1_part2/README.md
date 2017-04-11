# Set up Working Environment on tucson
We will be using the tucson.stanford.edu server to run simulation and synthesis. 
Once you log in to tucson, add the following lines to your .bashrc: 
```bash
export LM_LICENSE_FILE=7195@cadlic0.stanford.edu
export VCS_HOME=/cad/synopsys/vcs/K-2015.09-SP2-7
export PATH=/usr/bin:$VCS_HOME/amd64/bin:$PATH
export LM_LICENSE_FILE=27000@cadlic0.stanford.edu:$LM_LICENSE_FILE
export PATH=$PATH:/opt/intelFPGA_lite/16.1/quartus/bin

```
This lines include Quartus and VCS onto your $PATH.

# ArgInOut
In class, we went through a "HelloWorld" example. In Part II, we will first go through the process of deploying this example on board. 

### Completing ArgInOut
Take a look at $SPATIAL_HOME/apps/src/ArgInOut.scala, and complete it by following the comments in the file. 

## Simulating Apps
### Functional Simulation
First, we want to make sure that the app is functionally correct. To do so, run: 
```bash
make apps
bin/spatial ArgInOut --scala
```
This will generate the app files under ./gen/ArgInOut. Navigate into ./gen/ArgInOut, and run: 
```bash
./run.sh YOUR_ARG_IN
```
You will observe some debug information that helps you verify the correctness of your implementation.

### Cycle-accurate Simulation
After verifying the basic functions, we want to start generating circuit designs. To do so, in $SPATIAL_HOME, run: 
```bash
bin/spatial ArgInOut --chsel
```

This will generate the hardware description files under ./gen/ArgInOut. Navigate into ./gen/ArgInOut, and you will see the following folders: 
```bash
chisel
cpp
```

The chisel folder contains descriptions for running designs on FPGA. To generate cycle-accurate simulation, we need to first scp the generated project folder onto a server that has vcs installed. In this class, we will use the tucson server to run our simulation. Run: 
```bash
cd $SPATIAL_HOME
scp gen/ArgInOut USER_NAME@tucson.stanford.edu:~/YOUR_TARGET_DIR
```
Please make sure that your design files only reside under your home directory.
On the server side, first navigate into the project folder, then run: 
```
make vcs
./Top ARG_IN
```
This will initiate VCS simulation on tucson.

## Synthesizing and Running Spatial Apps on DE1-SoC
On tucson, under the project folder, run:
```bash
make de1soc
```
The synthesis process will start, and will take roughly 15 min to finish. After the synthesis finishes, you will see two generated files under ./prog in the project directory:
```bash
Top
sp.rbf
program_de1soc.sh
```
Top is the binary that runs on the ARM core, sp.rbf is the bitstream that runs on the FPGA, and program_de1soc.sh is the shell script that sets /dev/fpga with the bitstream. To test them, you will need to copy ./prog to DE1SoC.

To run the app, in the session that connects to your DE1SoC, you will need to enter: 
```bash
sudo ./Top ARG_IN
```
Here is an example of running ArgInOut with ARG_IN set to 4 on DE1SoC. Your result should look quite similar to this one:
```bash
tianzhao@client: sudo ./Top 4
[WARNING]: DELITE_NUM_THREADS undefined, defaulting to 1
Executing with 1 thread(s)
Running cmd ./program_de1soc.sh ./sp.rbf
Disabling fpga2hps bridge...
Disabling hps2fpga bridge...
Disabling lwhps2fpga bridge...
Loading ./sp.rbf into FPGA device...
2+1 records in
2+1 records out
2892552 bytes (2.9 MB) copied, 0.199989 s, 14.5 MB/s
Enabling fpga2hps bridge...
Enabling hps2fpga bridge...
Enabling lwhps2fpga bridge...
Running design..
Design done, ran for 0.001710 secs
Kernel done, test run time = 0 ms
expected: 8
result: 8
```

# Generate Sum Using FIFO, Reduce and Foreach 
In this example, we would like to implement an accelerator that takes in a number x, adds from 1 to up to x (not including x), and then return the sum. To make the testing easier, we are setting the size of fifo to 16. The input number x should be a multiple of 16. Please take a look at apps/src/FifoPushPop.scala and complete the design by following the comments. You will need to simulate and synthesize this design.

# MemReduce
In this example, we are using MemReduce to produce the following matrix A:

A(row, col) = 32 * (row + col), where A is 32 by 32. 

If you observe that synthesizer takes longer than usual to finish, this is expected because we are synthesizing SRAMs. 

# Streaming Video
On DE1SoC, the video decoder sends in a 24-bit RGB value; however the VGA port only accepts 16-bit RGB data. In this streaming example, we are implementing an RGB space converter that converts 24-bit RGB to 16-bit RGB. Please take a look at apps/src/RGBConvert.scala, implement and simulate your design. To deploy it on board, you will need to change the bus names. The detailed instructions can be found in RGBConvert.scala. 


# Streaming Video Using FSM
Coming soon
