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
cd ../
git checkout 3ce8d5b470b0350b8476f14f564d452d9d60d80d
```

If you are using Ubuntu, to set up your environment variables, run: 
```
source init-env.sh
```

You should verify your environment variables by running:
```bash
echo $SPATIAL_HOME
echo $JAVA_HOME
```
Please make sure that you are using Java 8. 

If you are using a Mac, you will not need to use the init-env script to set up the dependencies, but you will need to take some extra steps to ensure that you have the correct Java version.
First, please make sure that your Java version is 1.8 by running: 
```bash
java -version
```
If it is Java 1.8, then you can proceed to the next step. Otherwise, you will have to install Java 1.8 on your machine. 
Here is a link to Oracle's Java 8 download page: 
http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html

After installation, please check again to make sure that you are using Java 8.

After all the dependencies are installed, you can now make Spatial and the apps by running: 
```bash
make 
make apps
```

To finish the lab, please read [Introduction to Spatial](https://github.com/stanford-ppl/spatial-lang/tree/lab1_release/Intro2Spatial) and [Introduction to Linux](https://github.com/stanford-ppl/spatial-lang/tree/lab1_release/Intro2Linux) instructions.
