# Spatial
Spatial is an Argon DSL for programming reconfigurable hardware from a parameterized, high level abstraction.  Our user forum is here: 

## [Forum](https://groups.google.com/forum/#!forum/spatial-lang-users)

## [Getting Started](http://spatial-lang.readthedocs.io/en/latest/tutorial/starting.html)

## [API](http://spatial-lang.readthedocs.io/en/latest/)

# Branch Statuses

##[Zynq Synthesis Regression](https://docs.google.com/spreadsheets/d/1jZxVO8VFODR8_nEGBHfcmfeIJ3vo__LCPdjt4osb3aE/edit#gid=0)

##[AWS Synthesis Regression](https://docs.google.com/spreadsheets/d/19G95ZMMoruIsi1iMHYJ8Th9VUSX87SGTpo6yHsSCdvU/edit#gid=0)

!["what's running" is unavailable](https://github.com/mattfel1/Window/blob/master/window.png?raw=true "whatsrunning")

## Release Flow

|            | Last Update | Templates + SBT | Scala Backend | Chisel Backend |
|------------|-----------|------------|------------|-------------|
| **Master** | ![timestamp unavailable](https://github.com/mattfel1/Trackers/blob/timestamps/timestamp_master.png?raw=true "timestamp")  | [![Build Status](https://travis-ci.org/stanford-ppl/spatial-lang.svg?branch=master)](https://travis-ci.org/stanford-ppl/spatial-lang) | [![Build Status](https://travis-ci.org/mattfel1/Trackers.svg?branch=ClassCombined-Branchmaster-Backendscala-Tracker)](https://github.com/stanford-ppl/spatial-lang/wiki/Brnch:master-Trgt:scala) | [![Build Status](https://travis-ci.org/mattfel1/Trackers.svg?branch=ClassCombined-Branchmaster-Backendchisel-Tracker)](https://github.com/stanford-ppl/spatial-lang/wiki/Brnch:master-Trgt:chisel) |
| **Pre-master** | ![timestamp unavailable](https://github.com/mattfel1/Trackers/blob/timestamps/timestamp_pre-master.png?raw=true "timestamp")  | [![Build Status](https://travis-ci.org/stanford-ppl/spatial-lang.svg?branch=pre-master)](https://travis-ci.org/stanford-ppl/spatial-lang) | [![Build Status](https://travis-ci.org/mattfel1/Trackers.svg?branch=ClassCombined-Branchpre-master-Backendscala-Tracker)](https://github.com/stanford-ppl/spatial-lang/wiki/Brnch:pre-master-Trgt:scala) | [![Build Status](https://travis-ci.org/mattfel1/Trackers.svg?branch=ClassCombined-Branchpre-master-Backendchisel-Tracker)](https://github.com/stanford-ppl/spatial-lang/wiki/Brnch:pre-master-Trgt:chisel) |
| **SyncMem** | ![timestamp unavailable](https://github.com/mattfel1/Trackers/blob/timestamps/timestamp_syncMem.png?raw=true "timestamp")  | [![Build Status](https://travis-ci.org/stanford-ppl/spatial-lang.svg?branch=syncMem)](https://travis-ci.org/stanford-ppl/spatial-lang) | [![Build Status](https://travis-ci.org/mattfel1/Trackers.svg?branch=ClassCombined-BranchsyncMem-Backendscala-Tracker)](https://github.com/stanford-ppl/spatial-lang/wiki/Brnch:syncMem-Trgt:scala) | [![Build Status](https://travis-ci.org/mattfel1/Trackers.svg?branch=ClassCombined-BranchsyncMem-Backendchisel-Tracker)](https://github.com/stanford-ppl/spatial-lang/wiki/Brnch:syncMem-Trgt:chisel) |
| **Retime** | ![timestamp unavailable](https://github.com/mattfel1/Trackers/blob/timestamps/timestamp_retime.png?raw=true "timestamp")  | [![Build Status](https://travis-ci.org/stanford-ppl/spatial-lang.svg?branch=retime)](https://travis-ci.org/stanford-ppl/spatial-lang) | [![Build Status](https://travis-ci.org/mattfel1/Trackers.svg?branch=ClassCombined-Branchretime-Backendscala-Tracker)](https://github.com/stanford-ppl/spatial-lang/wiki/Brnch:retime-Trgt:scala) | [![Build Status](https://travis-ci.org/mattfel1/Trackers.svg?branch=ClassCombined-Branchretime-Backendchisel-Tracker)](https://github.com/stanford-ppl/spatial-lang/wiki/Brnch:retime-Trgt:chisel) |

## Work Branches

|            | Last Update | Templates + SBT | Scala Backend | Chisel Backend |
|------------|-----------|------------|------------|-------------|
| **Develop** | ![timestamp unavailable](https://github.com/mattfel1/Trackers/blob/timestamps/timestamp_develop.png?raw=true "timestamp")  | [![Build Status](https://travis-ci.org/stanford-ppl/spatial-lang.svg?branch=develop)](https://travis-ci.org/stanford-ppl/spatial-lang) | [![Build Status](https://travis-ci.org/mattfel1/Trackers.svg?branch=ClassCombined-Branchdevelop-Backendscala-Tracker)](https://github.com/stanford-ppl/spatial-lang/wiki/Brnch:develop-Trgt:scala) | [![Build Status](https://travis-ci.org/mattfel1/Trackers.svg?branch=ClassCombined-Branchdevelop-Backendchisel-Tracker)](https://github.com/stanford-ppl/spatial-lang/wiki/Brnch:develop-Trgt:chisel) |
| **FPGA** | ![timestamp unavailable](https://github.com/mattfel1/Trackers/blob/timestamps/timestamp_fpga.png?raw=true "timestamp")  | [![Build Status](https://travis-ci.org/stanford-ppl/spatial-lang.svg?branch=fpga)](https://travis-ci.org/stanford-ppl/spatial-lang) | [![Build Status](https://travis-ci.org/mattfel1/Trackers.svg?branch=ClassCombined-Branchfpga-Backendscala-Tracker)](https://github.com/stanford-ppl/spatial-lang/wiki/Brnch:fpga-Trgt:scala) | [![Build Status](https://travis-ci.org/mattfel1/Trackers.svg?branch=ClassCombined-Branchfpga-Backendchisel-Tracker)](https://github.com/stanford-ppl/spatial-lang/wiki/Brnch:fpga-Trgt:chisel) |
| **Compile** | ![timestamp unavailable](https://github.com/mattfel1/Trackers/blob/timestamps/timestamp_compile.png?raw=true "timestamp")  | [![Build Status](https://travis-ci.org/stanford-ppl/spatial-lang.svg?branch=compile)](https://travis-ci.org/stanford-ppl/spatial-lang) | [![Build Status](https://travis-ci.org/mattfel1/Trackers.svg?branch=ClassCombined-Branchcompile-Backendscala-Tracker)](https://github.com/stanford-ppl/spatial-lang/wiki/Brnch:compile-Trgt:scala) | [![Build Status](https://travis-ci.org/mattfel1/Trackers.svg?branch=ClassCombined-Branchcompile-Backendchisel-Tracker)](https://github.com/stanford-ppl/spatial-lang/wiki/Brnch:compile-Trgt:chisel) |
