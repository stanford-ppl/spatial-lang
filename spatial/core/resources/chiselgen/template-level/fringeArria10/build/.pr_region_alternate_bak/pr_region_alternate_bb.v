module pr_region_alternate (
		input  wire         clk_clk,            //        clk.clk
		output wire [5:0]   io_m_axi_0_awid,    // io_m_axi_0.awid
		output wire [31:0]  io_m_axi_0_awuser,  //           .awuser
		output wire [31:0]  io_m_axi_0_awaddr,  //           .awaddr
		output wire [7:0]   io_m_axi_0_awlen,   //           .awlen
		output wire [2:0]   io_m_axi_0_awsize,  //           .awsize
		output wire [1:0]   io_m_axi_0_awburst, //           .awburst
		output wire         io_m_axi_0_awlock,  //           .awlock
		output wire [3:0]   io_m_axi_0_awcache, //           .awcache
		output wire [2:0]   io_m_axi_0_awprot,  //           .awprot
		output wire [3:0]   io_m_axi_0_awqos,   //           .awqos
		output wire         io_m_axi_0_awvalid, //           .awvalid
		input  wire         io_m_axi_0_awready, //           .awready
		output wire [5:0]   io_m_axi_0_arid,    //           .arid
		output wire [31:0]  io_m_axi_0_aruser,  //           .aruser
		output wire [31:0]  io_m_axi_0_araddr,  //           .araddr
		output wire [7:0]   io_m_axi_0_arlen,   //           .arlen
		output wire [2:0]   io_m_axi_0_arsize,  //           .arsize
		output wire [1:0]   io_m_axi_0_arburst, //           .arburst
		output wire         io_m_axi_0_arlock,  //           .arlock
		output wire [3:0]   io_m_axi_0_arcache, //           .arcache
		output wire [2:0]   io_m_axi_0_arprot,  //           .arprot
		output wire [3:0]   io_m_axi_0_arqos,   //           .arqos
		output wire         io_m_axi_0_arvalid, //           .arvalid
		input  wire         io_m_axi_0_arready, //           .arready
		output wire [511:0] io_m_axi_0_wdata,   //           .wdata
		output wire [63:0]  io_m_axi_0_wstrb,   //           .wstrb
		output wire         io_m_axi_0_wlast,   //           .wlast
		output wire         io_m_axi_0_wvalid,  //           .wvalid
		input  wire         io_m_axi_0_wready,  //           .wready
		input  wire [5:0]   io_m_axi_0_rid,     //           .rid
		input  wire [31:0]  io_m_axi_0_ruser,   //           .ruser
		input  wire [511:0] io_m_axi_0_rdata,   //           .rdata
		input  wire [1:0]   io_m_axi_0_rresp,   //           .rresp
		input  wire         io_m_axi_0_rlast,   //           .rlast
		input  wire         io_m_axi_0_rvalid,  //           .rvalid
		output wire         io_m_axi_0_rready,  //           .rready
		input  wire [5:0]   io_m_axi_0_bid,     //           .bid
		input  wire [31:0]  io_m_axi_0_buser,   //           .buser
		input  wire [1:0]   io_m_axi_0_bresp,   //           .bresp
		input  wire         io_m_axi_0_bvalid,  //           .bvalid
		output wire         io_m_axi_0_bready,  //           .bready
		input  wire         reset_reset,        //      reset.reset
		output wire         s0_waitrequest,     //         s0.waitrequest
		output wire [31:0]  s0_readdata,        //           .readdata
		output wire         s0_readdatavalid,   //           .readdatavalid
		input  wire [0:0]   s0_burstcount,      //           .burstcount
		input  wire [31:0]  s0_writedata,       //           .writedata
		input  wire [9:0]   s0_address,         //           .address
		input  wire         s0_write,           //           .write
		input  wire         s0_read,            //           .read
		input  wire [3:0]   s0_byteenable,      //           .byteenable
		input  wire         s0_debugaccess      //           .debugaccess
	);
endmodule

