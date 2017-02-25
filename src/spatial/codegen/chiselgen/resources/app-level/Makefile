VERILATOR_SRC=cpp/verilator

all: help

help:
	@echo "------- SUPPORTED MAKE TARGETS -------"
	@echo "make sim 				: Verilator SW + HW build"
	@echo "make sim-hw 			: Build Chisel for Verilator"
	@echo "make aws-sim 		: AWS simulation SW + HW build"
	@echo "make aws-sim-hw 	: Build Chisel for AWS simulation"
	@echo "make sim-clean 	: Verilator simulation clean up"
	@echo "------- END HELP -------"

sim: sim-hw
	make -C ${VERILATOR_SRC}
	ln -sf ${VERILATOR_SRC}/Top .

sim-hw:
	rm -rf ${VERILATOR_SRC}
	sbt "run-main example.TopTest --backend-name verilator --target-dir ${VERILATOR_SRC} --testArgs"
	cp verilator.mk ${VERILATOR_SRC}/Makefile
	mv ${VERILATOR_SRC}/example* ${VERILATOR_SRC}/verilator_srcs_tmp
	mkdir ${VERILATOR_SRC}/verilator_srcs
	mv ${VERILATOR_SRC}/verilator_srcs_tmp/VTop* ${VERILATOR_SRC}/verilator_srcs
	rm -rf ${VERILATOR_SRC}/verilator_srcs_tmp
	mv ${VERILATOR_SRC}/verilator_srcs/*.mk ${VERILATOR_SRC}

aws-sim: aws-sim-hw
	@echo "aws-sim software build NOT YET SUPPORTED!"

aws-sim-hw:
	rm -rf ${VERILATOR_SRC}
	sbt "run-main example.TopTest --backend-name verilator --target-dir ${VERILATOR_SRC} --testArgs aws"
	cp verilator.mk ${VERILATOR_SRC}/Makefile
	mv ${VERILATOR_SRC}/example* ${VERILATOR_SRC}/verilator_srcs_tmp
	mkdir ${VERILATOR_SRC}/verilator_srcs
	mv ${VERILATOR_SRC}/verilator_srcs_tmp/VTop* ${VERILATOR_SRC}/verilator_srcs
	rm -rf ${VERILATOR_SRC}/verilator_srcs_tmp
	mv ${VERILATOR_SRC}/verilator_srcs/*.mk ${VERILATOR_SRC}

sim-clean:
	make -C ${VERILATOR_SRC} clean
