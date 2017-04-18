|        | Templates | Unit Tests | FSM Tests  | Dense Apps | Sparse Apps | Characterization Tests |
|--------|-----------|------------|------------|------------|-------------|------------------------|
| Chisel | [![Build Status](https://travis-ci.org/stanford-ppl/spatial-lang.svg?branch=fpga)](https://travis-ci.org/stanford-ppl/spatial-lang)         | [![Build Status](https://travis-ci.org/mattfel1/Trackers.svg?branch=ClassUnit-Branchfpga-Backendchisel-Tracker)](https://github.com/stanford-ppl/spatial-lang/wiki/Brnch:fpga-Trgt:chisel)           | [![Build Status](https://travis-ci.org/mattfel1/Trackers.svg?branch=ClassFSM-Branchfpga-Backendchisel-Tracker)](https://github.com/stanford-ppl/spatial-lang/wiki/Brnch:fpga-Trgt:chisel)           | [![Build Status](https://travis-ci.org/mattfel1/Trackers.svg?branch=ClassDense-Branchfpga-Backendchisel-Tracker)](https://github.com/stanford-ppl/spatial-lang/wiki/Brnch:fpga-Trgt:chisel)           | [![Build Status](https://travis-ci.org/mattfel1/Trackers.svg?branch=ClassSparse-Branchfpga-Backendchisel-Tracker)](https://github.com/stanford-ppl/spatial-lang/wiki/Brnch:fpga-Trgt:chisel)             | [![Build Status](https://travis-ci.org/mattfel1/Trackers.svg?branch=ClassCharacterization-Branchfpga-Backendchisel-Tracker)](https://github.com/stanford-ppl/spatial-lang/wiki/Brnch:fpga-Trgt:chisel)                       |
| Scala  | N/A       |  [![Build Status](https://travis-ci.org/mattfel1/Trackers.svg?branch=ClassUnit-Branchfpga-Backendscala-Tracker)](https://github.com/stanford-ppl/spatial-lang/wiki/Brnch:fpga-Trgt:scala)           | [![Build Status](https://travis-ci.org/mattfel1/Trackers.svg?branch=ClassFSM-Branchfpga-Backendscala-Tracker)](https://github.com/stanford-ppl/spatial-lang/wiki/Brnch:fpga-Trgt:scala)           | [![Build Status](https://travis-ci.org/mattfel1/Trackers.svg?branch=ClassDense-Branchfpga-Backendscala-Tracker)](https://github.com/stanford-ppl/spatial-lang/wiki/Brnch:fpga-Trgt:scala)           | [![Build Status](https://travis-ci.org/mattfel1/Trackers.svg?branch=ClassSparse-Branchfpga-Backendscala-Tracker)](https://github.com/stanford-ppl/spatial-lang/wiki/Brnch:fpga-Trgt:scala)             | [![Build Status](https://travis-ci.org/mattfel1/Trackers.svg?branch=ClassCharacterization-Branchfpga-Backendscala-Tracker)](https://github.com/stanford-ppl/spatial-lang/wiki/Brnch:fpga-Trgt:scala)                   |


# Spatial
Spatial is an Argon DSL for programming reconfigurable hardware from a parameterized, high level abstraction.  Our user forum is here: 

## [Forum](https://groups.google.com/forum/#!forum/spatial-lang-users)

## [Getting Started](http://spatial-lang.readthedocs.io/en/latest/tutorial/starting.html)

## [API](http://spatial-lang.readthedocs.io/en/latest/)

# Lab 1
In this lab, you will learn about designing apps in Spatial and writing program in Linux to control peripherals on DE1SoC board.

First, you will need to set up spatial on your computer:

```bash
# To get spatial-lang
git clone https://github.com/stanford-ppl/spatial-lang.git 
cd spatial-lang
git checkout lab1_release
git submodule update --init 
cd ./argon
git checkout lab1_release

# To set up your environment variables
cd ../
source init-env.sh
```

You should verify your environment variables by running:
```bash
echo $SPATIAL_HOME
echo $JAVA_HOME
```
Please verify that you are using Java 8.

To finish the lab, please read [Introduction to Spatial](https://github.com/stanford-ppl/spatial-lang/tree/lab1_release/Intro2Spatial) and [Introduction to Linux](https://github.com/stanford-ppl/spatial-lang/tree/lab1_release/Intro2Linux) instructions.
