
all: accel.bit.bin

accel.bit.bin: accel.bit
	bash convert_bitstream accel.bit accel.bit.bin

accel.bit:
	export XILINXD_LICENSE_FILE=7193@cadlic0.stanford.edu
	time vivado -mode batch -source vivado.tcl 2>&1 | tee vivado_synthesis.log

reports:
	export XILINXD_LICENSE_FILE=7193@cadlic0.stanford.edu
	time vivado -mode batch -source getReports.tcl 2>&1 | tee vivado_reports.log

clean:
	rm -rf BOOT.* accel.bit accel.bit.bin ps_project project_1 *.jou *.log *.rpt *.bin
