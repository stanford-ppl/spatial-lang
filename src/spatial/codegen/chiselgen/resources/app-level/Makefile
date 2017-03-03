VERILATOR_SRC=cpp/verilator

all: help

help:
	@echo "------- SUPPORTED MAKE TARGETS -------"
	@echo "make sim 				: Verilator SW + HW build"
	@echo "make sim-hw 			: Build Chisel for Verilator"
	@echo "make aws-sim 		: AWS simulation SW + HW build"
	@echo "make aws-sim-hw 	: Build Chisel for AWS simulation"
	@echo "make zynq 				: Zynq SW + HW build"
	@echo "make zynq-hw    	: Build Chisel for Zynq"
	@echo "make sim-clean 	: Verilator simulation clean up"
	@echo "------- END HELP -------"

sim: sim-hw
	make -C ${VERILATOR_SRC}
	ln -sf ${VERILATOR_SRC}/Top .

sim-hw:
	rm -rf ${VERILATOR_SRC}
	sbt "run-main top.Instantiator --backend-name verilator --target-dir ${VERILATOR_SRC} --testArgs"
	cp verilator.mk ${VERILATOR_SRC}/Makefile
	mv ${VERILATOR_SRC}/top* ${VERILATOR_SRC}/verilator_srcs_tmp
	mkdir ${VERILATOR_SRC}/verilator_srcs
	mv ${VERILATOR_SRC}/verilator_srcs_tmp/VTop* ${VERILATOR_SRC}/verilator_srcs
	mv ${VERILATOR_SRC}/verilator_srcs_tmp/*.v ${VERILATOR_SRC}/verilator_srcs
	rm -rf ${VERILATOR_SRC}/verilator_srcs_tmp
	mv ${VERILATOR_SRC}/verilator_srcs/*.mk ${VERILATOR_SRC}

aws-sim: aws-sim-hw
	@echo "aws-sim software build NOT YET SUPPORTED!"

aws-sim-hw:
	rm -rf ${VERILATOR_SRC}
	sbt "run-main top.Instantiator --backend-name verilator --target-dir ${VERILATOR_SRC} --testArgs aws"
	cp verilator.mk ${VERILATOR_SRC}/Makefile
	mv ${VERILATOR_SRC}/top* ${VERILATOR_SRC}/verilator_srcs_tmp
	mkdir ${VERILATOR_SRC}/verilator_srcs
	mv ${VERILATOR_SRC}/verilator_srcs_tmp/VTop* ${VERILATOR_SRC}/verilator_srcs
	mv ${VERILATOR_SRC}/verilator_srcs_tmp/*.v ${VERILATOR_SRC}/verilator_srcs
	rm -rf ${VERILATOR_SRC}/verilator_srcs_tmp
	mv ${VERILATOR_SRC}/verilator_srcs/*.mk ${VERILATOR_SRC}

zynq: zynq-hw zynq-sw

zynq-sw:
	cp zynq.mk cpp/Makefile
	make -C cpp
	tar -czf TopZynq.tar.gz -C verilog accel.bit.bin -C ../cpp Top -C fringeZynq/utils set_perms run.sh

zynq-hw:
	sbt "run-main top.Instantiator --verilog --testArgs zynq"
	cp chisel/fringeZynq/* verilog
	mv verilog/fsbl.elf._ verilog/fsbl.elf
	mv verilog/u-boot.elf._ verilog/u-boot.elf
	make -C verilog

zynq-clean:
	make -C verilog clean
	make -C cpp clean
	rm -rf verilog
	rm -f TopZynq.tar.gz

sim-clean:
	make -C ${VERILATOR_SRC} clean
