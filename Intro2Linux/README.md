# Introduction to Linux
## Writing to LED registers

In this part, we will write a program that increments the LED register every time we run it. On DE1-SoC, the red LEDs will turn on based on the value stored in the LED register. For example, if the LED register stores 2, only the 2nd-to-the-right LED will turn on to indicate a binary 10. 

Upon startup, DE1SoC loads a bitstream into /dev/fpga that maps a set of peripheral registers to the ARM2FPGA bridge. The ARM2FPGA bridge starts at address 0xff200000, and the register controlling LEDs locates at offset 0x0. Your task is to write 1 to this register whenever the program runs. 

To complete the task, first you need to navigate to the project directory:
```bash
cd $SPATIAL_HOME
cd Intro2Linux
cd increment_leds
```

In increment_leds, you will find three files. Please complete led.c by following the comments.

After you complete it, please upload the increment_leds folder to the board you are assigned to. For example, if you are assigned to ee109-03, here are the instructions to upload, compile, and run the project:

```bash
cd ../
scp -r increment_leds ee109@ee109-03:~/
ssh ee109@ee109-03
cd increment_leds
make && sudo ./led
```
After you observe that the red LEDs behave as expected, you can quit your session by running: 
```bash
exit
```

# Board Setup
In general, to transfer files from your local computer to DE1SoC, run: 
```cplusplus
scp -r YOUR_SRC_DIR ee109@ee109-GROUP_NUMBER:YOUR_TARGET_DIR
```
To sign in to your FPGA, first make sure that you are connected to the Stanford network. Then run: 
```bash
ssh ee109@ee109-GROUP_NUMBER
```
