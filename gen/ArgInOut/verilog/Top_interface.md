//Top.v

// This file was auto-generated as a prototype implementation of a module
// created in component editor.  It ties off all outputs to ground and
// ignores all inputs.  It needs to be edited to make it do something
// useful.
// 
// This file will not be automatically regenerated.  You should check it in
// to your version control system if you want to keep it.

`timescale 1 ps / 1 ps
module Top(
		input  wire [15:0] io_S_AXI_ARADDR,  // altera_axi4lite_slave.araddr
		input  wire [2:0]  io_S_AXI_ARPROT,  //                      .arprot
		output wire        io_S_AXI_ARREADY, //                      .arready
		input  wire        io_S_AXI_ARVALID, //                      .arvalid
		input  wire [15:0] io_S_AXI_AWADDR,  //                      .awaddr
		input  wire [2:0]  io_S_AXI_AWPROT,  //                      .awprot
		output wire        io_S_AXI_AWREADY, //                      .awready
		input  wire        io_S_AXI_AWVALID, //                      .awvalid
		input  wire        io_S_AXI_BREADY,  //                      .bready
		output wire [1:0]  io_S_AXI_BRESP,   //                      .bresp
		output wire        io_S_AXI_BVALID,  //                      .bvalid
		output wire [31:0] io_S_AXI_RDATA,   //                      .rdata
		output wire [1:0]  io_S_AXI_RRESP,   //                      .rresp
		input  wire        io_S_AXI_RREADY,  //                      .rready
		output wire        io_S_AXI_RVALID,  //                      .rvalid
		input  wire [31:0] io_S_AXI_WDATA,   //                      .wdata
		output wire        io_S_AXI_WREADY,  //                      .wready
		input  wire [3:0]  io_S_AXI_WSTRB,   //                      .wstrb
		input  wire        io_S_AXI_WVALID,  //                      .wvalid
		input  wire        clock,            //            clock_sink.clk
		input  wire        reset             //            reset_sink.reset
	);

	// TODO: Auto-generated HDL template

	assign io_S_AXI_ARREADY = 1'b0;

	assign io_S_AXI_BRESP = 2'b00;

	assign io_S_AXI_RDATA = 32'b00000000000000000000000000000000;

	assign io_S_AXI_WREADY = 1'b0;

	assign io_S_AXI_AWREADY = 1'b0;

	assign io_S_AXI_RRESP = 2'b00;

	assign io_S_AXI_BVALID = 1'b0;

	assign io_S_AXI_RVALID = 1'b0;

endmodule
