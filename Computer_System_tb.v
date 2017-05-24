module Computer_System_tb;
  reg [31:0] expansion_jp1_export;
  reg [31:0] expansion_jp2_export;
  reg        system_pll_ref_clk_clk;
  reg        system_pll_ref_reset_reset;
  reg [15:0] tb_video_in_subsystem_top_avalon_slave_address;
  reg        tb_video_in_subsystem_top_avalon_slave_write;
  wire [31:0] tb_video_in_subsystem_top_avalon_slave_readdata;
  reg [31:0] tb_video_in_subsystem_top_avalon_slave_writedata;
  reg        tb_video_in_subsystem_top_avalon_slave_chipselect;

  Computer_System cs0 (
		.system_pll_ref_clk_clk                                   (system_pll_ref_clk_clk),
		.system_pll_ref_reset_reset                               (system_pll_ref_reset_reset),
		.expansion_jp1_export                                     (expansion_jp1_export),
		.expansion_jp2_export                                     (expansion_jp2_export),
		.tb_video_in_subsystem_top_avalon_slave_address           (tb_video_in_subsystem_top_avalon_slave_address),
		.tb_video_in_subsystem_top_avalon_slave_write             (tb_video_in_subsystem_top_avalon_slave_write),
		.tb_video_in_subsystem_top_avalon_slave_readdata          (tb_video_in_subsystem_top_avalon_slave_readdata),
		.tb_video_in_subsystem_top_avalon_slave_writedata         (tb_video_in_subsystem_top_avalon_slave_writedata),
		.tb_video_in_subsystem_top_avalon_slave_chipselect        (tb_video_in_subsystem_top_avalon_slave_chipselect)
  );

  initial
  begin
    $dumpfile("test.vcd"); 
    $dumpvar(0, cs0);
    system_pll_ref_clk_clk = 0;
    system_pll_ref_reset_reset = 0;
    tb_video_in_subsystem_top_avalon_slave_chipselect = 1; 
    tb_video_in_subsystem_top_avalon_slave_writedata = 1;
    tb_video_in_subsystem_top_avalon_slave_write = 1;
    tb_video_in_subsystem_top_avalon_slave_address = 0;

    // reset
    system_pll_ref_reset_reset = 1;
    #10
    system_pll_ref_reset_reset = 0;

    #20
    // run the design, write a 1 to the command register
    tb_video_in_subsystem_top_avalon_slave_write = 0;
    #10
    tb_video_in_subsystem_top_avalon_slave_write = 1;
    
    #1000
    $finish;
  end
  always
    #5 system_pll_ref_clk_clk = ~system_pll_ref_clk_clk;


endmodule
