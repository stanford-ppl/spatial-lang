# Spatial
Spatial is an Argon DSL for programming reconfigurable hardware from a parameterized, high level abstraction.  Our user forum is here: 

## [Forum](https://groups.google.com/forum/#!forum/spatial-lang-users)

## [Getting Started](http://spatial-lang.readthedocs.io/en/latest/tutorial/starting.html)

## [API](http://spatial-lang.readthedocs.io/en/latest/)

# Lab 1
In this lab, you will learn about designing apps in Spatial and interacting with hardware peripherals on DE1SoC board.

First, you will need to set up spatial on your computer:

```bash
// To get spatial-lang
git clone https://github.com/stanford-ppl/spatial-lang.git 
cd spatial-lang
git checkout lab1_release
git submodule update --init 

// To set up your environment variables
source init-env.sh
```

You should verify your environment variables by running:
```bash
echo $SPATIAL_HOME
echo $JAVA_HOME
```

To finish the lab, please read [part1](https://github.com/stanford-ppl/spatial-lang/tree/lab1_release/lab1_part1) and [part2](https://github.com/stanford-ppl/spatial-lang/tree/lab1_release/lab1_part2) instructions.
