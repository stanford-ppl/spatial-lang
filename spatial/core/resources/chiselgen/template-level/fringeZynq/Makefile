
all: accel.bit.bin

accel.bit.bin: accel.bit
	bash convert_bitstream accel.bit accel.bit.bin

accel.bit:
	export XILINXD_LICENSE_FILE=7193@cadlic0.stanford.edu
	vivado -mode batch -source vivado.tcl

clean:
	rm -rf accel.bit accel.bit.bin project_1 *.jou *.log
