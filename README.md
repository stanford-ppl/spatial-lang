# Spatial
Spatial is an Argon DSL for programming reconfigurable hardware from a parameterized, high level abstraction.

#Prerequisites
- [Scala SBT](http://www.scala-sbt.org)
- [Java JDK](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
- [Argon](https://github.com/dkoeplin/argon)

#Installation
```bash
git clone https://github.com/dkoeplin/spatial.git
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
