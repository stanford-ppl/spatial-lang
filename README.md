# Regression buttons (Currently inaccurate!!)

Templates: [![Build Status](https://travis-ci.org/mattfel1/UnitTracker.svg?branch=chisel)](https://github.com/stanford-ppl/spatial/wiki/chiselBranch-chiselTest-Regression-Tests-Status)

Unit: [![Build Status](https://travis-ci.org/mattfel1/UnitTracker.svg?branch=chisel)](https://github.com/stanford-ppl/spatial/wiki/chiselBranch-chiselTest-Regression-Tests-Status)

Dense: [![Build Status](https://travis-ci.org/mattfel1/DenseTracker.svg?branch=chisel)](https://github.com/stanford-ppl/spatial/wiki/chiselBranch-chiselTest-Regression-Tests-Status)

Sparse: [![Build Status](https://travis-ci.org/mattfel1/SparseTracker.svg?branch=chisel)](https://github.com/stanford-ppl/spatial/wiki/chiselBranch-chiselTest-Regression-Tests-Status) 

Characterization: [![Build Status](https://travis-ci.org/mattfel1/CharacterizationTracker.svg?branch=chisel)](https://github.com/stanford-ppl/spatial/wiki/chiselBranch-chiselTest-Regression-Tests-Status)

# Spatial
Spatial is an Argon DSL for programming reconfigurable hardware from a parameterized, high level abstraction.

#Prerequisites
- [Scala SBT](http://www.scala-sbt.org)
- [Java JDK](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
- [Argon](https://github.com/stanford-ppl/argon)

#Installation
```bash
git clone https://github.com/stanford-ppl/spatial-lang.git
```

Next, make sure the following environment variables are set. 
You may need to customize some of them. 
You can either set them in your bashrc or in your current shell.
```bash
export JAVA_HOME = ### Directory Java is installed, usually /usr/bin
export ARGON_HOME = ### Top directory of Argon
export SPATIAL_HOME = ### Top directory of Spatial
```
Compile Spatial using `sbt compile` in the `SPATIAL_HOME` directory.
