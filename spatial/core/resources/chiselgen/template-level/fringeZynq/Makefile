
all: accel.bit.bin

accel.bit.bin: accel.bit
	bash convert_bitstream accel.bit accel.bit.bin

accel.bit:
	export XILINXD_LICENSE_FILE=7193@cadlic0.stanford.edu
	time vivado -mode batch -source vivado.tcl 2>&1 | tee vivado_synthesis.log

clean:
	rm -rf accel.bit accel.bit.bin project_1 *.jou *.log
