if { $argc != 1 } {
  puts $argc
  puts [llength $argv]
  foreach i $argv {puts $i}
  puts "The second arg is [lindex $argv 1]"; #indexes start at 0
  puts "Usage: settings.tcl <clockFreqMHz>"
  exit -1
}

set CLOCK_FREQ_MHZ [lindex $argv 0]
set CLOCK_FREQ_HZ  [expr $CLOCK_FREQ_MHZ * 1000000]

source settings.tcl

# Create project to make processing system IP from bd
create_project bd_project ./bd_project -part $PART
set_property board_part $BOARD [current_project]

add_files -norecurse [glob *.v]
add_files -norecurse [glob *.sv]
update_compile_order -fileset sources_1
update_compile_order -fileset sim_1

## Create processing system using bd
create_bd_design "design_1"
update_compile_order -fileset sources_1
switch $TARGET {
  "ZCU102" {
    set RST_FREQ [expr $CLOCK_FREQ_MHZ - 1]

    # Drop in ps-pl and Top
    create_bd_cell -type ip -vlnv xilinx.com:ip:zynq_ultra_ps_e:3.0 zynq_ultra_ps_e_0
    apply_bd_automation -rule xilinx.com:bd_rule:zynq_ultra_ps_e -config {apply_board_preset "1" }  [get_bd_cells zynq_ultra_ps_e_0]
    create_bd_cell -type module -reference Top Top_0    
    ## Set freqs
    set_property -dict [list CONFIG.PSU__FPGA_PL1_ENABLE {1} CONFIG.PSU__CRL_APB__PL1_REF_CTRL__SRCSEL {IOPLL} CONFIG.PSU__CRL_APB__PL1_REF_CTRL__FREQMHZ {250}] [get_bd_cells zynq_ultra_ps_e_0]
    set_property -dict [list CONFIG.PSU__CRL_APB__PL0_REF_CTRL__FREQMHZ $CLOCK_FREQ_MHZ] [get_bd_cells zynq_ultra_ps_e_0]
    apply_bd_automation -rule xilinx.com:bd_rule:axi4 -config {Master "/zynq_ultra_ps_e_0/M_AXI_HPM0_FPD" Clk "/zynq_ultra_ps_e_0/pl_clk0 ($CLOCK_FREQ_MHZ MHz)" }  [get_bd_intf_pins Top_0/io_S_AXI]

    connect_bd_net [get_bd_pins zynq_ultra_ps_e_0/pl_clk0] [get_bd_pins zynq_ultra_ps_e_0/maxihpm1_fpd_aclk]
    # Disable HP1
    set_property -dict [list CONFIG.PSU__USE__M_AXI_GP1 {0}] [get_bd_cells zynq_ultra_ps_e_0]

    # # Make AXI4 to AXI3 protocol converters
    # create_bd_cell -type ip -vlnv xilinx.com:ip:axi_protocol_converter:2.1 axi_protocol_converter_0
    # create_bd_cell -type ip -vlnv xilinx.com:ip:axi_protocol_converter:2.1 axi_protocol_converter_1
    # create_bd_cell -type ip -vlnv xilinx.com:ip:axi_protocol_converter:2.1 axi_protocol_converter_2
    # create_bd_cell -type ip -vlnv xilinx.com:ip:axi_protocol_converter:2.1 axi_protocol_converter_3

    # Disable unused interfaces
    set_property -dict [list CONFIG.PSU__PCIE__PERIPHERAL__ENABLE {0} CONFIG.PSU__DISPLAYPORT__PERIPHERAL__ENABLE {0}] [get_bd_cells zynq_ultra_ps_e_0]

    # Do HP# stuff
    set_property -dict [list CONFIG.PSU__USE__S_AXI_GP2 {1} CONFIG.PSU__USE__S_AXI_GP3 {1} CONFIG.PSU__USE__S_AXI_GP4 {1} CONFIG.PSU__USE__S_AXI_GP5 {1}] [get_bd_cells zynq_ultra_ps_e_0]
    # Connect HP# to faster clocks
    connect_bd_net [get_bd_pins zynq_ultra_ps_e_0/saxihp0_fpd_aclk] [get_bd_pins zynq_ultra_ps_e_0/pl_clk1] 
    connect_bd_net [get_bd_pins zynq_ultra_ps_e_0/saxihp1_fpd_aclk] [get_bd_pins zynq_ultra_ps_e_0/pl_clk1] 
    connect_bd_net [get_bd_pins zynq_ultra_ps_e_0/saxihp2_fpd_aclk] [get_bd_pins zynq_ultra_ps_e_0/pl_clk1] 
    connect_bd_net [get_bd_pins zynq_ultra_ps_e_0/saxihp3_fpd_aclk] [get_bd_pins zynq_ultra_ps_e_0/pl_clk1] 
    # 512-to-64 data width converters
    create_bd_cell -type ip -vlnv xilinx.com:ip:axi_dwidth_converter:2.1 axi_dwidth_converter_0
    create_bd_cell -type ip -vlnv xilinx.com:ip:axi_dwidth_converter:2.1 axi_dwidth_converter_1
    create_bd_cell -type ip -vlnv xilinx.com:ip:axi_dwidth_converter:2.1 axi_dwidth_converter_2
    create_bd_cell -type ip -vlnv xilinx.com:ip:axi_dwidth_converter:2.1 axi_dwidth_converter_3
    set_property -dict [list CONFIG.SI_ID_WIDTH.VALUE_SRC USER CONFIG.SI_DATA_WIDTH.VALUE_SRC USER CONFIG.MI_DATA_WIDTH.VALUE_SRC USER CONFIG.READ_WRITE_MODE.VALUE_SRC USER CONFIG.PROTOCOL.VALUE_SRC USER CONFIG.ADDR_WIDTH.VALUE_SRC USER] [get_bd_cells axi_dwidth_converter_0]
    set_property -dict [list CONFIG.SI_ID_WIDTH.VALUE_SRC USER CONFIG.SI_DATA_WIDTH.VALUE_SRC USER CONFIG.MI_DATA_WIDTH.VALUE_SRC USER CONFIG.READ_WRITE_MODE.VALUE_SRC USER CONFIG.PROTOCOL.VALUE_SRC USER CONFIG.ADDR_WIDTH.VALUE_SRC USER] [get_bd_cells axi_dwidth_converter_1]
    set_property -dict [list CONFIG.SI_ID_WIDTH.VALUE_SRC USER CONFIG.SI_DATA_WIDTH.VALUE_SRC USER CONFIG.MI_DATA_WIDTH.VALUE_SRC USER CONFIG.READ_WRITE_MODE.VALUE_SRC USER CONFIG.PROTOCOL.VALUE_SRC USER CONFIG.ADDR_WIDTH.VALUE_SRC USER] [get_bd_cells axi_dwidth_converter_2]
    set_property -dict [list CONFIG.SI_ID_WIDTH.VALUE_SRC USER CONFIG.SI_DATA_WIDTH.VALUE_SRC USER CONFIG.MI_DATA_WIDTH.VALUE_SRC USER CONFIG.READ_WRITE_MODE.VALUE_SRC USER CONFIG.PROTOCOL.VALUE_SRC USER CONFIG.ADDR_WIDTH.VALUE_SRC USER] [get_bd_cells axi_dwidth_converter_3]
    set_property -dict [list CONFIG.SI_DATA_WIDTH {512} CONFIG.SI_ID_WIDTH {6} CONFIG.MI_DATA_WIDTH {128}] [get_bd_cells axi_dwidth_converter_0]
    set_property -dict [list CONFIG.SI_DATA_WIDTH {512} CONFIG.SI_ID_WIDTH {6} CONFIG.MI_DATA_WIDTH {128}] [get_bd_cells axi_dwidth_converter_1]
    set_property -dict [list CONFIG.SI_DATA_WIDTH {512} CONFIG.SI_ID_WIDTH {6} CONFIG.MI_DATA_WIDTH {128}] [get_bd_cells axi_dwidth_converter_2]
    set_property -dict [list CONFIG.SI_DATA_WIDTH {512} CONFIG.SI_ID_WIDTH {6} CONFIG.MI_DATA_WIDTH {128}] [get_bd_cells axi_dwidth_converter_3]
    connect_bd_net [get_bd_pins zynq_ultra_ps_e_0/pl_clk0] [get_bd_pins axi_dwidth_converter_0/s_axi_aclk]
    connect_bd_net [get_bd_pins zynq_ultra_ps_e_0/pl_clk0] [get_bd_pins axi_dwidth_converter_1/s_axi_aclk]
    connect_bd_net [get_bd_pins zynq_ultra_ps_e_0/pl_clk0] [get_bd_pins axi_dwidth_converter_2/s_axi_aclk]
    connect_bd_net [get_bd_pins zynq_ultra_ps_e_0/pl_clk0] [get_bd_pins axi_dwidth_converter_3/s_axi_aclk]
    connect_bd_net [get_bd_pins rst_ps8_0_${RST_FREQ}M/peripheral_aresetn] [get_bd_pins axi_dwidth_converter_0/s_axi_aresetn]
    connect_bd_net [get_bd_pins rst_ps8_0_${RST_FREQ}M/peripheral_aresetn] [get_bd_pins axi_dwidth_converter_1/s_axi_aresetn]
    connect_bd_net [get_bd_pins rst_ps8_0_${RST_FREQ}M/peripheral_aresetn] [get_bd_pins axi_dwidth_converter_2/s_axi_aresetn]
    connect_bd_net [get_bd_pins rst_ps8_0_${RST_FREQ}M/peripheral_aresetn] [get_bd_pins axi_dwidth_converter_3/s_axi_aresetn]

    # # AXI4 to AXI3 protocol converter
    # set_property -dict [list CONFIG.MI_PROTOCOL.VALUE_SRC USER CONFIG.ID_WIDTH.VALUE_SRC USER CONFIG.DATA_WIDTH.VALUE_SRC USER CONFIG.SI_PROTOCOL.VALUE_SRC USER] [get_bd_cells axi_protocol_converter_0]
    # set_property -dict [list CONFIG.MI_PROTOCOL {AXI3} CONFIG.DATA_WIDTH {64} CONFIG.ID_WIDTH {6} CONFIG.TRANSLATION_MODE {2}] [get_bd_cells axi_protocol_converter_0]
    # connect_bd_net [get_bd_pins proc_sys_reset_fclk1/slowest_sync_clk] [get_bd_pins zynq_ultra_ps_e_0/pl_clk0]
    # connect_bd_net [get_bd_pins axi_protocol_converter_0/aclk] [get_bd_pins zynq_ultra_ps_e_0/pl_clk0]
    # connect_bd_net [get_bd_pins axi_protocol_converter_1/aclk] [get_bd_pins zynq_ultra_ps_e_0/pl_clk0]
    # connect_bd_net [get_bd_pins axi_protocol_converter_2/aclk] [get_bd_pins zynq_ultra_ps_e_0/pl_clk0]
    # connect_bd_net [get_bd_pins axi_protocol_converter_3/aclk] [get_bd_pins zynq_ultra_ps_e_0/pl_clk0]
    # connect_bd_net [get_bd_pins axi_protocol_converter_0/aresetn] [get_bd_pins rst_ps8_0_${RST_FREQ}M/peripheral_aresetn]
    # connect_bd_net [get_bd_pins axi_protocol_converter_1/aresetn] [get_bd_pins rst_ps8_0_${RST_FREQ}M/peripheral_aresetn]
    # connect_bd_net [get_bd_pins axi_protocol_converter_2/aresetn] [get_bd_pins rst_ps8_0_${RST_FREQ}M/peripheral_aresetn]
    # connect_bd_net [get_bd_pins axi_protocol_converter_3/aresetn] [get_bd_pins rst_ps8_0_${RST_FREQ}M/peripheral_aresetn]
    # # Clock converters from FCLKCLK0 <-> FCLKCLK1
    create_bd_cell -type ip -vlnv xilinx.com:ip:axi_clock_converter:2.1 axi_clock_converter_0
    create_bd_cell -type ip -vlnv xilinx.com:ip:axi_clock_converter:2.1 axi_clock_converter_1
    create_bd_cell -type ip -vlnv xilinx.com:ip:axi_clock_converter:2.1 axi_clock_converter_2
    create_bd_cell -type ip -vlnv xilinx.com:ip:axi_clock_converter:2.1 axi_clock_converter_3
    set_property -dict [list CONFIG.ID_WIDTH.VALUE_SRC USER CONFIG.DATA_WIDTH.VALUE_SRC USER CONFIG.READ_WRITE_MODE.VALUE_SRC USER CONFIG.ADDR_WIDTH.VALUE_SRC USER CONFIG.PROTOCOL.VALUE_SRC USER] [get_bd_cells axi_clock_converter_0]
    set_property -dict [list CONFIG.ID_WIDTH.VALUE_SRC USER CONFIG.DATA_WIDTH.VALUE_SRC USER CONFIG.READ_WRITE_MODE.VALUE_SRC USER CONFIG.ADDR_WIDTH.VALUE_SRC USER CONFIG.PROTOCOL.VALUE_SRC USER] [get_bd_cells axi_clock_converter_1]
    set_property -dict [list CONFIG.ID_WIDTH.VALUE_SRC USER CONFIG.DATA_WIDTH.VALUE_SRC USER CONFIG.READ_WRITE_MODE.VALUE_SRC USER CONFIG.ADDR_WIDTH.VALUE_SRC USER CONFIG.PROTOCOL.VALUE_SRC USER] [get_bd_cells axi_clock_converter_2]
    set_property -dict [list CONFIG.ID_WIDTH.VALUE_SRC USER CONFIG.DATA_WIDTH.VALUE_SRC USER CONFIG.READ_WRITE_MODE.VALUE_SRC USER CONFIG.ADDR_WIDTH.VALUE_SRC USER CONFIG.PROTOCOL.VALUE_SRC USER] [get_bd_cells axi_clock_converter_3]
    set_property -dict [list CONFIG.PROTOCOL {AXI4} CONFIG.DATA_WIDTH {128} CONFIG.ID_WIDTH {6}] [get_bd_cells axi_clock_converter_0]
    set_property -dict [list CONFIG.PROTOCOL {AXI4} CONFIG.DATA_WIDTH {128} CONFIG.ID_WIDTH {6}] [get_bd_cells axi_clock_converter_1]
    set_property -dict [list CONFIG.PROTOCOL {AXI4} CONFIG.DATA_WIDTH {128} CONFIG.ID_WIDTH {6}] [get_bd_cells axi_clock_converter_2]
    set_property -dict [list CONFIG.PROTOCOL {AXI4} CONFIG.DATA_WIDTH {128} CONFIG.ID_WIDTH {6}] [get_bd_cells axi_clock_converter_3]
    connect_bd_intf_net [get_bd_intf_pins Top_0/io_M_AXI_0] [get_bd_intf_pins axi_dwidth_converter_0/S_AXI]
    connect_bd_intf_net [get_bd_intf_pins Top_0/io_M_AXI_1] [get_bd_intf_pins axi_dwidth_converter_1/S_AXI]
    connect_bd_intf_net [get_bd_intf_pins Top_0/io_M_AXI_2] [get_bd_intf_pins axi_dwidth_converter_2/S_AXI]
    connect_bd_intf_net [get_bd_intf_pins Top_0/io_M_AXI_3] [get_bd_intf_pins axi_dwidth_converter_3/S_AXI]
    # # data width converter -> protocol converter
    # connect_bd_intf_net [get_bd_intf_pins axi_dwidth_converter_0/M_AXI] [get_bd_intf_pins axi_protocol_converter_0/S_AXI]
    # connect_bd_intf_net [get_bd_intf_pins axi_dwidth_converter_1/M_AXI] [get_bd_intf_pins axi_protocol_converter_1/S_AXI]
    # connect_bd_intf_net [get_bd_intf_pins axi_dwidth_converter_2/M_AXI] [get_bd_intf_pins axi_protocol_converter_2/S_AXI]
    # connect_bd_intf_net [get_bd_intf_pins axi_dwidth_converter_3/M_AXI] [get_bd_intf_pins axi_protocol_converter_3/S_AXI]
    # # protocol converter -> Clock converter
    # connect_bd_intf_net [get_bd_intf_pins axi_protocol_converter_0/M_AXI] [get_bd_intf_pins axi_clock_converter_0/S_AXI]
    # connect_bd_intf_net [get_bd_intf_pins axi_protocol_converter_1/M_AXI] [get_bd_intf_pins axi_clock_converter_1/S_AXI]
    # connect_bd_intf_net [get_bd_intf_pins axi_protocol_converter_2/M_AXI] [get_bd_intf_pins axi_clock_converter_2/S_AXI]
    # connect_bd_intf_net [get_bd_intf_pins axi_protocol_converter_3/M_AXI] [get_bd_intf_pins axi_clock_converter_3/S_AXI]
    # data width converter -> protocol converter
    connect_bd_intf_net [get_bd_intf_pins axi_dwidth_converter_0/M_AXI] [get_bd_intf_pins axi_clock_converter_0/S_AXI]
    connect_bd_intf_net [get_bd_intf_pins axi_dwidth_converter_1/M_AXI] [get_bd_intf_pins axi_clock_converter_1/S_AXI]
    connect_bd_intf_net [get_bd_intf_pins axi_dwidth_converter_2/M_AXI] [get_bd_intf_pins axi_clock_converter_2/S_AXI]
    connect_bd_intf_net [get_bd_intf_pins axi_dwidth_converter_3/M_AXI] [get_bd_intf_pins axi_clock_converter_3/S_AXI]
    # clock converter -> Top
    connect_bd_intf_net [get_bd_intf_pins axi_clock_converter_0/M_AXI] [get_bd_intf_pins zynq_ultra_ps_e_0/S_AXI_HP0_FPD]
    connect_bd_intf_net [get_bd_intf_pins axi_clock_converter_1/M_AXI] [get_bd_intf_pins zynq_ultra_ps_e_0/S_AXI_HP1_FPD]
    connect_bd_intf_net [get_bd_intf_pins axi_clock_converter_2/M_AXI] [get_bd_intf_pins zynq_ultra_ps_e_0/S_AXI_HP2_FPD]
    connect_bd_intf_net [get_bd_intf_pins axi_clock_converter_3/M_AXI] [get_bd_intf_pins zynq_ultra_ps_e_0/S_AXI_HP3_FPD]


    # Wire up the resets
    connect_bd_net [get_bd_pins axi_clock_converter_0/s_axi_aresetn] [get_bd_pins axi_clock_converter_0/m_axi_aresetn]
    connect_bd_net [get_bd_pins axi_clock_converter_0/m_axi_aresetn] [get_bd_pins axi_clock_converter_1/s_axi_aresetn]
    connect_bd_net [get_bd_pins axi_clock_converter_1/s_axi_aresetn] [get_bd_pins axi_clock_converter_1/m_axi_aresetn]
    connect_bd_net [get_bd_pins axi_clock_converter_1/m_axi_aresetn] [get_bd_pins axi_clock_converter_2/s_axi_aresetn]
    connect_bd_net [get_bd_pins axi_clock_converter_2/s_axi_aresetn] [get_bd_pins axi_clock_converter_2/m_axi_aresetn]
    connect_bd_net [get_bd_pins axi_clock_converter_2/m_axi_aresetn] [get_bd_pins axi_clock_converter_3/s_axi_aresetn]
    connect_bd_net [get_bd_pins axi_clock_converter_3/s_axi_aresetn] [get_bd_pins axi_clock_converter_3/m_axi_aresetn]
    connect_bd_net [get_bd_pins axi_clock_converter_0/m_axi_aresetn] [get_bd_pins rst_ps8_0_${RST_FREQ}M/peripheral_aresetn]
    
    connect_bd_net [get_bd_pins zynq_ultra_ps_e_0/pl_clk0] [get_bd_pins axi_clock_converter_0/s_axi_aclk]
    connect_bd_net [get_bd_pins zynq_ultra_ps_e_0/pl_clk0] [get_bd_pins axi_clock_converter_1/s_axi_aclk]
    connect_bd_net [get_bd_pins zynq_ultra_ps_e_0/pl_clk0] [get_bd_pins axi_clock_converter_2/s_axi_aclk]
    connect_bd_net [get_bd_pins zynq_ultra_ps_e_0/pl_clk0] [get_bd_pins axi_clock_converter_3/s_axi_aclk]
    connect_bd_net [get_bd_pins zynq_ultra_ps_e_0/pl_clk1] [get_bd_pins axi_clock_converter_0/m_axi_aclk]
    connect_bd_net [get_bd_pins zynq_ultra_ps_e_0/pl_clk1] [get_bd_pins axi_clock_converter_1/m_axi_aclk]
    connect_bd_net [get_bd_pins zynq_ultra_ps_e_0/pl_clk1] [get_bd_pins axi_clock_converter_2/m_axi_aclk]
    connect_bd_net [get_bd_pins zynq_ultra_ps_e_0/pl_clk1] [get_bd_pins axi_clock_converter_3/m_axi_aclk]
    # what to do about Clock converter -> HP0?
    # what to do about Address assignment to HP0?

  }
  default {
    create_bd_cell -type ip -vlnv xilinx.com:ip:processing_system7:5.5 processing_system7_0
    apply_bd_automation -rule xilinx.com:bd_rule:processing_system7 -config {make_external "FIXED_IO, DDR" apply_board_preset "1" Master "Disable" Slave "Disable" }  [get_bd_cells processing_system7_0]
    create_bd_cell -type module -reference Top Top_0
    set_property -dict [list CONFIG.PCW_FPGA0_PERIPHERAL_FREQMHZ $CLOCK_FREQ_MHZ] [get_bd_cells processing_system7_0]
    set_property -dict [list CONFIG.PCW_FPGA1_PERIPHERAL_FREQMHZ {250} CONFIG.PCW_EN_CLK1_PORT {1}] [get_bd_cells processing_system7_0]
    apply_bd_automation -rule xilinx.com:bd_rule:axi4 -config {Master "/processing_system7_0/M_AXI_GP0" Clk "/processing_system7_0/FCLK_CLK0 ($CLOCK_FREQ_MHZ MHz)" }  [get_bd_intf_pins Top_0/io_S_AXI]
    # Faster clock (200 MHz) for memory interface
    create_bd_cell -type ip -vlnv xilinx.com:ip:proc_sys_reset:5.0 proc_sys_reset_fclk1
    connect_bd_net [get_bd_pins processing_system7_0/FCLK_CLK1] [get_bd_pins proc_sys_reset_fclk1/slowest_sync_clk]
    connect_bd_net [get_bd_pins processing_system7_0/FCLK_RESET0_N] [get_bd_pins proc_sys_reset_fclk1/ext_reset_in]
    ### HP0 Begin {
      # Enable HP0, connect faster clock
      set_property -dict [list CONFIG.PCW_USE_S_AXI_HP0 {1}] [get_bd_cells processing_system7_0]
      connect_bd_net [get_bd_pins processing_system7_0/FCLK_CLK1] [get_bd_pins processing_system7_0/S_AXI_HP0_ACLK]
      # Create axi slice for timing
      create_bd_cell -type ip -vlnv xilinx.com:ip:axi_register_slice:2.1 axi_register_slice_0
      connect_bd_net [get_bd_pins processing_system7_0/FCLK_CLK0] [get_bd_pins axi_register_slice_0/aclk]
      connect_bd_net [get_bd_pins rst_ps7_0_${CLOCK_FREQ_MHZ}M/peripheral_aresetn] [get_bd_pins axi_register_slice_0/aresetn]
      # 512-to-64 data width converter
      create_bd_cell -type ip -vlnv xilinx.com:ip:axi_dwidth_converter:2.1 axi_dwidth_converter_0
      set_property -dict [list CONFIG.SI_ID_WIDTH.VALUE_SRC USER CONFIG.SI_DATA_WIDTH.VALUE_SRC USER CONFIG.MI_DATA_WIDTH.VALUE_SRC USER CONFIG.READ_WRITE_MODE.VALUE_SRC USER CONFIG.PROTOCOL.VALUE_SRC USER CONFIG.ADDR_WIDTH.VALUE_SRC USER] [get_bd_cells axi_dwidth_converter_0]
      set_property -dict [list CONFIG.SI_DATA_WIDTH {512} CONFIG.SI_ID_WIDTH {6} CONFIG.MI_DATA_WIDTH {64}] [get_bd_cells axi_dwidth_converter_0]
      connect_bd_net [get_bd_pins processing_system7_0/FCLK_CLK0] [get_bd_pins axi_dwidth_converter_0/s_axi_aclk]
      connect_bd_net [get_bd_pins rst_ps7_0_${CLOCK_FREQ_MHZ}M/peripheral_aresetn] [get_bd_pins axi_dwidth_converter_0/s_axi_aresetn]
      # AXI4 to AXI3 protocol converter
      create_bd_cell -type ip -vlnv xilinx.com:ip:axi_protocol_converter:2.1 axi_protocol_converter_0
      set_property -dict [list CONFIG.MI_PROTOCOL.VALUE_SRC USER CONFIG.ID_WIDTH.VALUE_SRC USER CONFIG.DATA_WIDTH.VALUE_SRC USER CONFIG.SI_PROTOCOL.VALUE_SRC USER] [get_bd_cells axi_protocol_converter_0]
      set_property -dict [list CONFIG.MI_PROTOCOL {AXI3} CONFIG.DATA_WIDTH {64} CONFIG.ID_WIDTH {6} CONFIG.TRANSLATION_MODE {2}] [get_bd_cells axi_protocol_converter_0]
      connect_bd_net [get_bd_pins rst_ps7_0_${CLOCK_FREQ_MHZ}M/peripheral_aresetn] [get_bd_pins axi_protocol_converter_0/aresetn]
      connect_bd_net [get_bd_pins processing_system7_0/FCLK_CLK0] [get_bd_pins axi_protocol_converter_0/aclk]
      # Clock converter from FCLKCLK0 <-> FCLKCLK1
      create_bd_cell -type ip -vlnv xilinx.com:ip:axi_clock_converter:2.1 axi_clock_converter_0
      set_property -dict [list CONFIG.ID_WIDTH.VALUE_SRC USER CONFIG.DATA_WIDTH.VALUE_SRC USER CONFIG.READ_WRITE_MODE.VALUE_SRC USER CONFIG.ADDR_WIDTH.VALUE_SRC USER CONFIG.PROTOCOL.VALUE_SRC USER] [get_bd_cells axi_clock_converter_0]
      set_property -dict [list CONFIG.PROTOCOL {AXI3} CONFIG.DATA_WIDTH {64} CONFIG.ID_WIDTH {6}] [get_bd_cells axi_clock_converter_0]
      connect_bd_net [get_bd_pins rst_ps7_0_${CLOCK_FREQ_MHZ}M/peripheral_aresetn] [get_bd_pins axi_clock_converter_0/s_axi_aresetn]
      connect_bd_net [get_bd_pins proc_sys_reset_fclk1/peripheral_aresetn] [get_bd_pins axi_clock_converter_0/m_axi_aresetn]
      connect_bd_net [get_bd_pins processing_system7_0/FCLK_CLK0] [get_bd_pins axi_clock_converter_0/s_axi_aclk]
      connect_bd_net [get_bd_pins processing_system7_0/FCLK_CLK1] [get_bd_pins axi_clock_converter_0/m_axi_aclk]
      # Top -> axi slicer
      connect_bd_intf_net [get_bd_intf_pins Top_0/io_M_AXI_0] [get_bd_intf_pins axi_register_slice_0/S_AXI]
      # axi slicer -> data width converter
      connect_bd_intf_net [get_bd_intf_pins axi_register_slice_0/M_AXI] [get_bd_intf_pins axi_dwidth_converter_0/S_AXI]
      # data width converter -> protocol converter
      connect_bd_intf_net [get_bd_intf_pins axi_dwidth_converter_0/M_AXI] [get_bd_intf_pins axi_protocol_converter_0/S_AXI]
      # protocol converter -> Clock converter
      connect_bd_intf_net [get_bd_intf_pins axi_protocol_converter_0/M_AXI] [get_bd_intf_pins axi_clock_converter_0/S_AXI]

      # Clock converter -> HP0
      connect_bd_intf_net [get_bd_intf_pins axi_clock_converter_0/M_AXI] [get_bd_intf_pins processing_system7_0/S_AXI_HP0]

      # Address assignment to HP0
      assign_bd_address [get_bd_addr_segs processing_system7_0/S_AXI_HP0/HP0_DDR_LOWOCM] -target_address_space /Top_0/io_M_AXI_0
    ### } HP0 end

    ### HP1 Begin {
      # Enable HP1, connect faster clock
      set_property -dict [list CONFIG.PCW_USE_S_AXI_HP1 {1}] [get_bd_cells processing_system7_0]
      connect_bd_net [get_bd_pins processing_system7_0/FCLK_CLK1] [get_bd_pins processing_system7_0/S_AXI_HP1_ACLK]

      # Create axi slice for timing
      create_bd_cell -type ip -vlnv xilinx.com:ip:axi_register_slice:2.1 axi_register_slice_1
      connect_bd_net [get_bd_pins processing_system7_0/FCLK_CLK0] [get_bd_pins axi_register_slice_1/aclk]
      connect_bd_net [get_bd_pins rst_ps7_0_${CLOCK_FREQ_MHZ}M/peripheral_aresetn] [get_bd_pins axi_register_slice_1/aresetn]

      # 512-to-64 data width converter
      create_bd_cell -type ip -vlnv xilinx.com:ip:axi_dwidth_converter:2.1 axi_dwidth_converter_1
      set_property -dict [list CONFIG.SI_ID_WIDTH.VALUE_SRC USER CONFIG.SI_DATA_WIDTH.VALUE_SRC USER CONFIG.MI_DATA_WIDTH.VALUE_SRC USER CONFIG.READ_WRITE_MODE.VALUE_SRC USER CONFIG.PROTOCOL.VALUE_SRC USER CONFIG.ADDR_WIDTH.VALUE_SRC USER] [get_bd_cells axi_dwidth_converter_1]
      set_property -dict [list CONFIG.SI_DATA_WIDTH {512} CONFIG.SI_ID_WIDTH {6} CONFIG.MI_DATA_WIDTH {64}] [get_bd_cells axi_dwidth_converter_1]
      connect_bd_net [get_bd_pins processing_system7_0/FCLK_CLK0] [get_bd_pins axi_dwidth_converter_1/s_axi_aclk]
      connect_bd_net [get_bd_pins rst_ps7_0_${CLOCK_FREQ_MHZ}M/peripheral_aresetn] [get_bd_pins axi_dwidth_converter_1/s_axi_aresetn]

      # AXI4 to AXI3 protocol converter
      create_bd_cell -type ip -vlnv xilinx.com:ip:axi_protocol_converter:2.1 axi_protocol_converter_1
      set_property -dict [list CONFIG.MI_PROTOCOL.VALUE_SRC USER CONFIG.ID_WIDTH.VALUE_SRC USER CONFIG.DATA_WIDTH.VALUE_SRC USER CONFIG.SI_PROTOCOL.VALUE_SRC USER] [get_bd_cells axi_protocol_converter_1]
      set_property -dict [list CONFIG.MI_PROTOCOL {AXI3} CONFIG.DATA_WIDTH {64} CONFIG.ID_WIDTH {6} CONFIG.TRANSLATION_MODE {2}] [get_bd_cells axi_protocol_converter_1]
      connect_bd_net [get_bd_pins rst_ps7_0_${CLOCK_FREQ_MHZ}M/peripheral_aresetn] [get_bd_pins axi_protocol_converter_1/aresetn]
      connect_bd_net [get_bd_pins processing_system7_0/FCLK_CLK0] [get_bd_pins axi_protocol_converter_1/aclk]

      # Clock converter from FCLKCLK0 <-> FCLKCLK1
      create_bd_cell -type ip -vlnv xilinx.com:ip:axi_clock_converter:2.1 axi_clock_converter_1
      set_property -dict [list CONFIG.ID_WIDTH.VALUE_SRC USER CONFIG.DATA_WIDTH.VALUE_SRC USER CONFIG.READ_WRITE_MODE.VALUE_SRC USER CONFIG.ADDR_WIDTH.VALUE_SRC USER CONFIG.PROTOCOL.VALUE_SRC USER] [get_bd_cells axi_clock_converter_1]
      set_property -dict [list CONFIG.PROTOCOL {AXI3} CONFIG.DATA_WIDTH {64} CONFIG.ID_WIDTH {6}] [get_bd_cells axi_clock_converter_1]
      connect_bd_net [get_bd_pins rst_ps7_0_${CLOCK_FREQ_MHZ}M/peripheral_aresetn] [get_bd_pins axi_clock_converter_1/s_axi_aresetn]
      connect_bd_net [get_bd_pins proc_sys_reset_fclk1/peripheral_aresetn] [get_bd_pins axi_clock_converter_1/m_axi_aresetn]
      connect_bd_net [get_bd_pins processing_system7_0/FCLK_CLK0] [get_bd_pins axi_clock_converter_1/s_axi_aclk]
      connect_bd_net [get_bd_pins processing_system7_0/FCLK_CLK1] [get_bd_pins axi_clock_converter_1/m_axi_aclk]

      # Top -> axi slicer
      connect_bd_intf_net [get_bd_intf_pins Top_0/io_M_AXI_1] [get_bd_intf_pins axi_register_slice_1/S_AXI]
      # axi slicer -> data width converter
      connect_bd_intf_net [get_bd_intf_pins axi_register_slice_1/M_AXI] [get_bd_intf_pins axi_dwidth_converter_1/S_AXI]
      # data width converter -> protocol converter
      connect_bd_intf_net [get_bd_intf_pins axi_dwidth_converter_1/M_AXI] [get_bd_intf_pins axi_protocol_converter_1/S_AXI]
      # protocol converter -> Clock converter
      connect_bd_intf_net [get_bd_intf_pins axi_protocol_converter_1/M_AXI] [get_bd_intf_pins axi_clock_converter_1/S_AXI]
      # Clock converter -> HP1
      connect_bd_intf_net [get_bd_intf_pins axi_clock_converter_1/M_AXI] [get_bd_intf_pins processing_system7_0/S_AXI_HP1]

      # Address assignment to HP1
      assign_bd_address [get_bd_addr_segs processing_system7_0/S_AXI_HP1/HP1_DDR_LOWOCM] -target_address_space /Top_0/io_M_AXI_1
    ### } HP1 end

    ### HP2 Begin {
      # Enable HP2, connect faster clock
      set_property -dict [list CONFIG.PCW_USE_S_AXI_HP2 {1}] [get_bd_cells processing_system7_0]
      connect_bd_net [get_bd_pins processing_system7_0/FCLK_CLK1] [get_bd_pins processing_system7_0/S_AXI_HP2_ACLK]

      # Create axi slice for timing
      create_bd_cell -type ip -vlnv xilinx.com:ip:axi_register_slice:2.1 axi_register_slice_2
      connect_bd_net [get_bd_pins processing_system7_0/FCLK_CLK0] [get_bd_pins axi_register_slice_2/aclk]
      connect_bd_net [get_bd_pins rst_ps7_0_${CLOCK_FREQ_MHZ}M/peripheral_aresetn] [get_bd_pins axi_register_slice_2/aresetn]

      # 512-to-64 data width converter
      create_bd_cell -type ip -vlnv xilinx.com:ip:axi_dwidth_converter:2.1 axi_dwidth_converter_2
      set_property -dict [list CONFIG.SI_ID_WIDTH.VALUE_SRC USER CONFIG.SI_DATA_WIDTH.VALUE_SRC USER CONFIG.MI_DATA_WIDTH.VALUE_SRC USER CONFIG.READ_WRITE_MODE.VALUE_SRC USER CONFIG.PROTOCOL.VALUE_SRC USER CONFIG.ADDR_WIDTH.VALUE_SRC USER] [get_bd_cells axi_dwidth_converter_2]
      set_property -dict [list CONFIG.SI_DATA_WIDTH {512} CONFIG.SI_ID_WIDTH {6} CONFIG.MI_DATA_WIDTH {64}] [get_bd_cells axi_dwidth_converter_2]
      connect_bd_net [get_bd_pins processing_system7_0/FCLK_CLK0] [get_bd_pins axi_dwidth_converter_2/s_axi_aclk]
      connect_bd_net [get_bd_pins rst_ps7_0_${CLOCK_FREQ_MHZ}M/peripheral_aresetn] [get_bd_pins axi_dwidth_converter_2/s_axi_aresetn]

      # AXI4 to AXI3 protocol converter
      create_bd_cell -type ip -vlnv xilinx.com:ip:axi_protocol_converter:2.1 axi_protocol_converter_2
      set_property -dict [list CONFIG.MI_PROTOCOL.VALUE_SRC USER CONFIG.ID_WIDTH.VALUE_SRC USER CONFIG.DATA_WIDTH.VALUE_SRC USER CONFIG.SI_PROTOCOL.VALUE_SRC USER] [get_bd_cells axi_protocol_converter_2]
      set_property -dict [list CONFIG.MI_PROTOCOL {AXI3} CONFIG.DATA_WIDTH {64} CONFIG.ID_WIDTH {6} CONFIG.TRANSLATION_MODE {2}] [get_bd_cells axi_protocol_converter_2]
      connect_bd_net [get_bd_pins rst_ps7_0_${CLOCK_FREQ_MHZ}M/peripheral_aresetn] [get_bd_pins axi_protocol_converter_2/aresetn]
      connect_bd_net [get_bd_pins processing_system7_0/FCLK_CLK0] [get_bd_pins axi_protocol_converter_2/aclk]

      # Clock converter from FCLKCLK0 <-> FCLKCLK1
      create_bd_cell -type ip -vlnv xilinx.com:ip:axi_clock_converter:2.1 axi_clock_converter_2
      set_property -dict [list CONFIG.ID_WIDTH.VALUE_SRC USER CONFIG.DATA_WIDTH.VALUE_SRC USER CONFIG.READ_WRITE_MODE.VALUE_SRC USER CONFIG.ADDR_WIDTH.VALUE_SRC USER CONFIG.PROTOCOL.VALUE_SRC USER] [get_bd_cells axi_clock_converter_2]
      set_property -dict [list CONFIG.PROTOCOL {AXI3} CONFIG.DATA_WIDTH {64} CONFIG.ID_WIDTH {6}] [get_bd_cells axi_clock_converter_2]
      connect_bd_net [get_bd_pins rst_ps7_0_${CLOCK_FREQ_MHZ}M/peripheral_aresetn] [get_bd_pins axi_clock_converter_2/s_axi_aresetn]
      connect_bd_net [get_bd_pins proc_sys_reset_fclk1/peripheral_aresetn] [get_bd_pins axi_clock_converter_2/m_axi_aresetn]
      connect_bd_net [get_bd_pins processing_system7_0/FCLK_CLK0] [get_bd_pins axi_clock_converter_2/s_axi_aclk]
      connect_bd_net [get_bd_pins processing_system7_0/FCLK_CLK1] [get_bd_pins axi_clock_converter_2/m_axi_aclk]

      # Top -> axi slicer
      connect_bd_intf_net [get_bd_intf_pins Top_0/io_M_AXI_2] [get_bd_intf_pins axi_register_slice_2/S_AXI]
      # axi slicer -> data width converter
      connect_bd_intf_net [get_bd_intf_pins axi_register_slice_2/M_AXI] [get_bd_intf_pins axi_dwidth_converter_2/S_AXI]
      # data width converter -> protocol converter
      connect_bd_intf_net [get_bd_intf_pins axi_dwidth_converter_2/M_AXI] [get_bd_intf_pins axi_protocol_converter_2/S_AXI]
      # protocol converter -> Clock converter
      connect_bd_intf_net [get_bd_intf_pins axi_protocol_converter_2/M_AXI] [get_bd_intf_pins axi_clock_converter_2/S_AXI]
      # Clock converter -> HP2
      connect_bd_intf_net [get_bd_intf_pins axi_clock_converter_2/M_AXI] [get_bd_intf_pins processing_system7_0/S_AXI_HP2]
      # Address assignment to HP2
      assign_bd_address [get_bd_addr_segs processing_system7_0/S_AXI_HP2/HP2_DDR_LOWOCM] -target_address_space /Top_0/io_M_AXI_2
    ### } HP2 end

    ### HP3 Begin {
      # Enable HP3, connect faster clock
      set_property -dict [list CONFIG.PCW_USE_S_AXI_HP3 {1}] [get_bd_cells processing_system7_0]
      connect_bd_net [get_bd_pins processing_system7_0/FCLK_CLK1] [get_bd_pins processing_system7_0/S_AXI_HP3_ACLK]

      # Create axi slice for timing
      create_bd_cell -type ip -vlnv xilinx.com:ip:axi_register_slice:2.1 axi_register_slice_3
      connect_bd_net [get_bd_pins processing_system7_0/FCLK_CLK0] [get_bd_pins axi_register_slice_3/aclk]
      connect_bd_net [get_bd_pins rst_ps7_0_${CLOCK_FREQ_MHZ}M/peripheral_aresetn] [get_bd_pins axi_register_slice_3/aresetn]

      # 512-to-64 data width converter
      create_bd_cell -type ip -vlnv xilinx.com:ip:axi_dwidth_converter:2.1 axi_dwidth_converter_3
      set_property -dict [list CONFIG.SI_ID_WIDTH.VALUE_SRC USER CONFIG.SI_DATA_WIDTH.VALUE_SRC USER CONFIG.MI_DATA_WIDTH.VALUE_SRC USER CONFIG.READ_WRITE_MODE.VALUE_SRC USER CONFIG.PROTOCOL.VALUE_SRC USER CONFIG.ADDR_WIDTH.VALUE_SRC USER] [get_bd_cells axi_dwidth_converter_3]
      set_property -dict [list CONFIG.SI_DATA_WIDTH {512} CONFIG.SI_ID_WIDTH {6} CONFIG.MI_DATA_WIDTH {64}] [get_bd_cells axi_dwidth_converter_3]
      connect_bd_net [get_bd_pins processing_system7_0/FCLK_CLK0] [get_bd_pins axi_dwidth_converter_3/s_axi_aclk]
      connect_bd_net [get_bd_pins rst_ps7_0_${CLOCK_FREQ_MHZ}M/peripheral_aresetn] [get_bd_pins axi_dwidth_converter_3/s_axi_aresetn]

      # AXI4 to AXI3 protocol converter
      create_bd_cell -type ip -vlnv xilinx.com:ip:axi_protocol_converter:2.1 axi_protocol_converter_3
      set_property -dict [list CONFIG.MI_PROTOCOL.VALUE_SRC USER CONFIG.ID_WIDTH.VALUE_SRC USER CONFIG.DATA_WIDTH.VALUE_SRC USER CONFIG.SI_PROTOCOL.VALUE_SRC USER] [get_bd_cells axi_protocol_converter_3]
      set_property -dict [list CONFIG.MI_PROTOCOL {AXI3} CONFIG.DATA_WIDTH {64} CONFIG.ID_WIDTH {6} CONFIG.TRANSLATION_MODE {2}] [get_bd_cells axi_protocol_converter_3]
      connect_bd_net [get_bd_pins rst_ps7_0_${CLOCK_FREQ_MHZ}M/peripheral_aresetn] [get_bd_pins axi_protocol_converter_3/aresetn]
      connect_bd_net [get_bd_pins processing_system7_0/FCLK_CLK0] [get_bd_pins axi_protocol_converter_3/aclk]

      # Clock converter from FCLKCLK0 <-> FCLKCLK1
      create_bd_cell -type ip -vlnv xilinx.com:ip:axi_clock_converter:2.1 axi_clock_converter_3
      set_property -dict [list CONFIG.ID_WIDTH.VALUE_SRC USER CONFIG.DATA_WIDTH.VALUE_SRC USER CONFIG.READ_WRITE_MODE.VALUE_SRC USER CONFIG.ADDR_WIDTH.VALUE_SRC USER CONFIG.PROTOCOL.VALUE_SRC USER] [get_bd_cells axi_clock_converter_3]
      set_property -dict [list CONFIG.PROTOCOL {AXI3} CONFIG.DATA_WIDTH {64} CONFIG.ID_WIDTH {6}] [get_bd_cells axi_clock_converter_3]
      connect_bd_net [get_bd_pins rst_ps7_0_${CLOCK_FREQ_MHZ}M/peripheral_aresetn] [get_bd_pins axi_clock_converter_3/s_axi_aresetn]
      connect_bd_net [get_bd_pins proc_sys_reset_fclk1/peripheral_aresetn] [get_bd_pins axi_clock_converter_3/m_axi_aresetn]
      connect_bd_net [get_bd_pins processing_system7_0/FCLK_CLK0] [get_bd_pins axi_clock_converter_3/s_axi_aclk]
      connect_bd_net [get_bd_pins processing_system7_0/FCLK_CLK1] [get_bd_pins axi_clock_converter_3/m_axi_aclk]

      # Top -> axi slicer
      connect_bd_intf_net [get_bd_intf_pins Top_0/io_M_AXI_3] [get_bd_intf_pins axi_register_slice_3/S_AXI]
      # axi slicer -> data width converter
      connect_bd_intf_net [get_bd_intf_pins axi_register_slice_3/M_AXI] [get_bd_intf_pins axi_dwidth_converter_3/S_AXI]
      # data width converter -> protocol converter
      connect_bd_intf_net [get_bd_intf_pins axi_dwidth_converter_3/M_AXI] [get_bd_intf_pins axi_protocol_converter_3/S_AXI]
      # protocol converter -> Clock converter
      connect_bd_intf_net [get_bd_intf_pins axi_protocol_converter_3/M_AXI] [get_bd_intf_pins axi_clock_converter_3/S_AXI]
      # Clock converter -> HP3
      connect_bd_intf_net [get_bd_intf_pins axi_clock_converter_3/M_AXI] [get_bd_intf_pins processing_system7_0/S_AXI_HP3]
      # Address assignment to HP3
      assign_bd_address [get_bd_addr_segs processing_system7_0/S_AXI_HP3/HP3_DDR_LOWOCM] -target_address_space /Top_0/io_M_AXI_3
    ### } HP3 end



  }
}

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
