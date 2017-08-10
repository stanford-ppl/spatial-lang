source settings.tcl

## Create a second project to build the design
create_project project_1 ./project_1 -part $PART
set_property board_part $BOARD [current_project]

## Import Verilog generated from Chisel and static Verilog files
add_files -norecurse [glob *.v]

## Import PS, reset, AXI protocol conversion and word width conversion IP
#import_ip -files [glob *.xci]
import_ip -files [list \
  ./bd_project/bd_project.srcs/sources_1/bd/design_1/ip/design_1_processing_system7_0_0/design_1_processing_system7_0_0.xci \
  ./bd_project/bd_project.srcs/sources_1/bd/design_1/ip/design_1_rst_ps7_0_${CLOCK_FREQ_MHZ}M_0/design_1_rst_ps7_0_${CLOCK_FREQ_MHZ}M_0.xci                        \
  ./bd_project/bd_project.srcs/sources_1/bd/design_1/ip/design_1_auto_ds_0/design_1_auto_ds_0.xci                                      \
  ./bd_project/bd_project.srcs/sources_1/bd/design_1/ip/design_1_auto_pc_0/design_1_auto_pc_0.xci                                      \
  ./bd_project/bd_project.srcs/sources_1/bd/design_1/ip/design_1_auto_pc_1/design_1_auto_pc_1.xci
]

## Create application-specific IP
source bigIP.tcl

#set_property -dict [list CONFIG.CLK.FREQ_HZ $CLOCK_FREQ_HZ] [ get_ips design_1_auto_pc_0]
#set_property -dict [list CONFIG.CLK.FREQ_HZ $CLOCK_FREQ_HZ] [ get_ips design_1_auto_pc_1]
#set_property -dict [list CONFIG.MI_CLK.FREQ_HZ $CLOCK_FREQ_HZ] [ get_ips design_1_auto_ds_0]
#set_property -dict [list CONFIG.SI_CLK.FREQ_HZ $CLOCK_FREQ_HZ] [ get_ips design_1_auto_ds_0]

update_compile_order -fileset sources_1
set_property top design_1_wrapper [current_fileset]

#set_property STEPS.SYNTH_DESIGN.ARGS.RETIMING true [get_runs synth_1]
set_property STEPS.SYNTH_DESIGN.ARGS.KEEP_EQUIVALENT_REGISTERS true [get_runs synth_1]

launch_runs synth_1
wait_on_run synth_1

open_run -name implDesign synth_1
report_timing_summary -file ./synth_timing_summary.rpt
report_utilization -packthru -file ./synth_utilization.rpt
report_utilization -packthru -hierarchical -hierarchical_depth 20 -hierarchical_percentages -file ./synth_utilization_hierarchical.rpt
report_ram_utilization -detail -file ./synth_ram_utilization.rpt

launch_runs impl_1
wait_on_run impl_1
launch_runs impl_1 -to_step write_bitstream
wait_on_run impl_1

# Reports
open_run -name implDesign impl_1
report_timing_summary -file ./par_timing_summary.rpt
report_utilization -packthru -file  ./par_utilization.rpt
report_utilization -packthru -hierarchical -hierarchical_depth 20 -hierarchical_percentages -file  ./par_utilization_hierarchical.rpt
report_ram_utilization -detail -file ./par_ram_utilization.rpt
report_high_fanout_nets -ascending -timing -load_types -file ./par_high_fanout_nets.rpt

#Export bitstream
file copy -force ./project_1/project_1.runs/impl_1/design_1_wrapper.bit ./accel.bit
