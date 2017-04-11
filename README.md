# Spatial
Spatial is an Argon DSL for programming reconfigurable hardware from a parameterized, high level abstraction.  Our user forum is here: 

## [Forum](https://groups.google.com/forum/#!forum/spatial-lang-users)

## [Getting Started](http://spatial-lang.readthedocs.io/en/latest/tutorial/starting.html)

## [API](http://spatial-lang.readthedocs.io/en/latest/)

# Lab 1
In this lab, you will learn about designing apps in Spatial and interacting with hardware peripherals on DE1SoC board.

First, you will need to set up dependencies on your computer. "Getting Started" link gives a quick tutorial on how to install the denpendencies. You will also need to have the lab files on your computer. Run the following command to clone this repository and switch to the ee109 lab branch:

```bash
// To get spatial-lang
git clone https://github.com/stanford-ppl/spatial-lang.git 
git checkout lab1_release

// To get argon
cd argon
git submodule update --init 
git checkout lab1_release
```

In spatial-lang directory, there are two directories: lab1_part1 and lab1_part2. READMEs in these two directories provides instructions on how to complete the lab.

If you have any questions, please contact Tian Zhao at tianzhao@stanford.edu.
