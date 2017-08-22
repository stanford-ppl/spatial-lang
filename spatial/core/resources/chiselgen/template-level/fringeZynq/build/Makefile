
all: accel.bit.bin

accel.bit.bin: accel.bit
	bash convert_bitstream accel.bit accel.bit.bin

accel.bit: bd
	export XILINXD_LICENSE_FILE=7193@cadlic0.stanford.edu
	time vivado -mode batch -source vivado.tcl 2>&1 | tee vivado_synthesis.log

reports:
	export XILINXD_LICENSE_FILE=7193@cadlic0.stanford.edu
	time vivado -mode batch -source getReports.tcl 2>&1 | tee vivado_reports.log

bd: design_1.v
	sed -i 's/design_1_Top_0_0/Top/' design_1.v
	sed -i 's/(\*/\/\/ (\*/' design_1.v

design_1.v:
	export XILINXD_LICENSE_FILE=7193@cadlic0.stanford.edu
	time vivado -mode batch -source bdproject.tcl 2>&1 | tee vivado_bdproject.log

clean:
	rm -rf BOOT.* accel.bit accel.bit.bin bd_project project_1 *.jou *.log *.rpt *.bin design_1.v design_1_wrapper.v
