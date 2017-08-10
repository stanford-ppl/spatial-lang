source settings.tcl

# Create project to make processing system IP from bd
create_project bd_project ./bd_project -part $PART
set_property board_part $BOARD [current_project]

add_files -norecurse [glob *.v]
update_compile_order -fileset sources_1
update_compile_order -fileset sim_1

## Create processing system using bd
create_bd_design "design_1"
update_compile_order -fileset sources_1
create_bd_cell -type ip -vlnv xilinx.com:ip:processing_system7:5.5 processing_system7_0
apply_bd_automation -rule xilinx.com:bd_rule:processing_system7 -config {make_external "FIXED_IO, DDR" apply_board_preset "1" Master "Disable" Slave "Disable" }  [get_bd_cells processing_system7_0]
create_bd_cell -type module -reference Top Top_0

set_property -dict [list CONFIG.PCW_FPGA0_PERIPHERAL_FREQMHZ $CLOCK_FREQ_MHZ] [get_bd_cells processing_system7_0]
apply_bd_automation -rule xilinx.com:bd_rule:axi4 -config {Master "/processing_system7_0/M_AXI_GP0" Clk "/processing_system7_0/FCLK_CLK0 ($CLOCK_FREQ_MHZ MHz)" }  [get_bd_intf_pins Top_0/io_S_AXI]

set_property -dict [list CONFIG.PCW_USE_S_AXI_HP0 {1}] [get_bd_cells processing_system7_0]
apply_bd_automation -rule xilinx.com:bd_rule:axi4 -config {Master "/Top_0/io_M_AXI" Clk "/processing_system7_0/FCLK_CLK0 ($CLOCK_FREQ_MHZ MHz)" }  [get_bd_intf_pins processing_system7_0/S_AXI_HP0]

validate_bd_design
save_bd_design

make_wrapper -files [get_files ./bd_project/bd_project.srcs/sources_1/bd/design_1/design_1.bd] -top
add_files -norecurse ./bd_project/bd_project.srcs/sources_1/bd/design_1/hdl/design_1_wrapper.v
update_compile_order -fileset sources_1

set_property top design_1_wrapper [current_fileset]
update_compile_order -fileset sources_1

# Copy required files here
file copy -force ./bd_project/bd_project.srcs/sources_1/bd/design_1/hdl/design_1.v ./design_1.v
file copy -force ./bd_project/bd_project.srcs/sources_1/bd/design_1/hdl/design_1_wrapper.v ./design_1_wrapper.v

close_project

