## Project constants
set CLOCK_FREQ_MHZ 100
set CLOCK_FREQ_HZ  [expr $CLOCK_FREQ_MHZ * 1000000]

# Create project to make processing system IP from bd
create_project ps_project ./ps_project -part xc7z045ffg900-2
set_property board_part xilinx.com:zc706:part0:1.4 [current_project]

## Create processing system using bd
create_bd_design "design_1"
update_compile_order -fileset sources_1
create_bd_cell -type ip -vlnv xilinx.com:ip:processing_system7:5.5 processing_system7_0
apply_bd_automation -rule xilinx.com:bd_rule:processing_system7 -config {make_external "FIXED_IO, DDR" apply_board_preset "1" Master "Disable" Slave "Disable" }  [get_bd_cells processing_system7_0]

set_property -dict [list CONFIG.PCW_USE_S_AXI_HP0 {1}] [get_bd_cells processing_system7_0]
set_property -dict [list CONFIG.PCW_FPGA0_PERIPHERAL_FREQMHZ $CLOCK_FREQ_MHZ] [get_bd_cells processing_system7_0]
# validate_bd_design
save_bd_design
close_project

## Create a second project to build the design
create_project project_1 ./project_1 -part xc7z045ffg900-2
set_property board_part xilinx.com:zc706:part0:1.4 [current_project]

add_files -norecurse [glob *.v]

## Import PS, reset, AXI protocol conversion and word width conversion IP
import_ip -files [glob *.xci]
import_ip -files {./ps_project/ps_project.srcs/sources_1/bd/design_1/ip/design_1_processing_system7_0_0/design_1_processing_system7_0_0.xci}

set_property -dict [list CONFIG.CLK.FREQ_HZ $CLOCK_FREQ_HZ] [ get_ips design_1_auto_pc_0]
set_property -dict [list CONFIG.CLK.FREQ_HZ $CLOCK_FREQ_HZ] [ get_ips design_1_auto_pc_1]
set_property -dict [list CONFIG.MI_CLK.FREQ_HZ $CLOCK_FREQ_HZ] [ get_ips design_1_auto_ds_0]
set_property -dict [list CONFIG.SI_CLK.FREQ_HZ $CLOCK_FREQ_HZ] [ get_ips design_1_auto_ds_0]


# create_ip -name processing_system7 -vendor xilinx.com -library ip -version 5.5 -module_name processing_system7_0
#set_property -dict [list CONFIG.PCW_USE_S_AXI_HP0 {1}] [get_ips processing_system7_0]
#set_property -dict [list CONFIG.PCW_FPGA0_PERIPHERAL_FREQMHZ $CLOCK_FREQ_MHZ] [get_ips processing_system7_0]

#create_ip -name proc_sys_reset -vendor xilinx.com -library ip -version 5.0 -module_name proc_sys_reset_0
#set_property -dict [list CONFIG.CLOCK.FREQ_HZ $CLOCK_FREQ_HZ] [get_ips proc_sys_reset_0]


#import_ip -files {/home/raghu/work/research/spatial/spatial-lang/gen/InOutArg_xilinx_forum/divide_project/project_1/project_1.srcs/sources_1/bd/design_1/ip/design_1_auto_pc_0/design_1_auto_pc_0.xci}
#import_ip -files {/home/raghu/work/research/spatial/spatial-lang/gen/InOutArg_xilinx_forum/divide_project/project_1/project_1.srcs/sources_1/bd/design_1/ip/design_1_auto_pc_1/design_1_auto_pc_1.xci}
#import_ip -files {/home/raghu/work/research/spatial/spatial-lang/gen/InOutArg_xilinx_forum/divide_project/project_1/project_1.srcs/sources_1/bd/design_1/ip/design_1_auto_ds_0/design_1_auto_ds_0.xci}


# Create Custom IP
#create_ip -name div_gen -vendor xilinx.com -library ip -version 5.1 -module_name div32
#set_property -dict [list CONFIG.dividend_and_quotient_width {32} CONFIG.divisor_width {32} CONFIG.remainder_type {Fractional} CONFIG.clocks_per_division {4} CONFIG.fractional_width {32} CONFIG.latency {69}] [get_ips div32]
#set_property -dict [list CONFIG.ACLK_INTF.FREQ_HZ $CLOCK_FREQ_HZ] [get_ips div32]

update_compile_order -fileset sources_1
set_property top design_1_wrapper [current_fileset]

set_property STEPS.SYNTH_DESIGN.ARGS.RETIMING true [get_runs synth_1]
set_property STEPS.SYNTH_DESIGN.ARGS.KEEP_EQUIVALENT_REGISTERS true [get_runs synth_1]

launch_runs synth_1
wait_on_run synth_1

launch_runs impl_1
wait_on_run impl_1
launch_runs impl_1 -to_step write_bitstream
wait_on_run impl_1

# Reports
open_run -name implDesign impl_1
report_timing_summary -file ./par_timing_summary.rpt
report_utilization -packthru -file ./par_utilization.rpt
report_ram_utilization -file ./par_ram_utilization.rpt

#Export bitstream
file copy -force ./project_1/project_1.runs/impl_1/design_1_wrapper.bit ./accel.bit
