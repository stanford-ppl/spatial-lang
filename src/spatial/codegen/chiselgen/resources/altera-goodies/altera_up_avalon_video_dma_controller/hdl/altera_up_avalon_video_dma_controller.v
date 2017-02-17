// (C) 2001-2016 Intel Corporation. All rights reserved.
// Your use of Intel Corporation's design tools, logic functions and other 
// software and tools, and its AMPP partner logic functions, and any output 
// files any of the foregoing (including device programming or simulation 
// files), and any associated documentation or information are expressly subject 
// to the terms and conditions of the Intel Program License Subscription 
// Agreement, Intel MegaCore Function License Agreement, or other applicable 
// license agreement, including, without limitation, that your use is for the 
// sole purpose of programming logic devices manufactured by Intel and sold by 
// Intel or its authorized distributors.  Please refer to the applicable 
// agreement for further details.


// THIS FILE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
// THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
// FROM, OUT OF OR IN CONNECTION WITH THIS FILE OR THE USE OR OTHER DEALINGS
// IN THIS FILE.

/******************************************************************************
 *                                                                            *
 * This module store and retrieves video frames to and from memory.           *
 *                                                                            *
 ******************************************************************************/

`undef USE_TO_MEMORY
`define USE_32BIT_MASTER

module altera_up_avalon_video_dma_controller (
	// Inputs
	clk,
	reset,

`ifdef USE_TO_MEMORY
	stream_data,
	stream_startofpacket,
	stream_endofpacket,
	stream_empty,
	stream_valid,
`else
	stream_ready,
`endif

`ifndef USE_TO_MEMORY
	master_readdata,
	master_readdatavalid,
`endif
	master_waitrequest,
	
	slave_address,
	slave_byteenable,
	slave_read,
	slave_write,
	slave_writedata,

	// Bidirectional

	// Outputs
`ifdef USE_TO_MEMORY
	stream_ready,
`else
	stream_data,
	stream_startofpacket,
	stream_endofpacket,
	stream_empty,
	stream_valid,
`endif

	master_address,
`ifdef USE_TO_MEMORY
	master_write,
	master_writedata,
`else
	master_arbiterlock,
	master_read,
`endif

	slave_readdata
);

/*****************************************************************************
 *                           Parameter Declarations                          *
 *****************************************************************************/

parameter DW								=  15; // Frame's datawidth
parameter EW								=   0; // Frame's empty width
parameter WIDTH							= 640; // Frame's width in pixels
parameter HEIGHT							= 480; // Frame's height in lines

parameter AW								=  17; // Frame's address width
parameter WW								=   8; // Frame width's address width
parameter HW								=   7; // Frame height's address width

parameter MDW								=  15; // Avalon master's datawidth

parameter DEFAULT_BUFFER_ADDRESS		= 32'h00000000;
parameter DEFAULT_BACK_BUF_ADDRESS	= 32'h00000000;

parameter ADDRESSING_BITS				= 16'h0809;
parameter COLOR_BITS						= 4'h7;
parameter COLOR_PLANES					= 2'h2;

parameter DEFAULT_DMA_ENABLED			= 1'b1; // 0: OFF or 1: ON

/*****************************************************************************
 *                             Port Declarations                             *
 *****************************************************************************/

// Inputs
input						clk;
input						reset;

`ifdef USE_TO_MEMORY
input			[DW: 0]	stream_data;
input						stream_startofpacket;
input						stream_endofpacket;
input			[EW: 0]	stream_empty;
input						stream_valid;
`else
input						stream_ready;
`endif

`ifndef USE_TO_MEMORY
input			[MDW:0]	master_readdata;
input						master_readdatavalid;
`endif
input						master_waitrequest;
	
input			[ 1: 0]	slave_address;
input			[ 3: 0]	slave_byteenable;
input						slave_read;
input						slave_write;
input			[31: 0]	slave_writedata;

// Bidirectional

// Outputs
`ifdef USE_TO_MEMORY
output					stream_ready;
`else
output		[DW: 0]	stream_data;
output					stream_startofpacket;
output					stream_endofpacket;
output		[EW: 0]	stream_empty;
output					stream_valid;
`endif

output		[31: 0]	master_address;
`ifdef USE_TO_MEMORY
output					master_write;
output		[MDW:0]	master_writedata;
`else
output					master_arbiterlock;
output					master_read;
`endif

output		[31: 0]	slave_readdata;

/*****************************************************************************
 *                           Constant Declarations                           *
 *****************************************************************************/


/*****************************************************************************
 *                 Internal Wires and Registers Declarations                 *
 *****************************************************************************/

// Internal Wires
wire						inc_address;
wire						reset_address;

wire			[31: 0]	buffer_start_address;
wire						dma_enabled;

// Internal Registers
`ifdef USE_CONSECUTIVE_ADDRESSING
reg			[AW: 0]	pixel_address;
`else
reg			[WW: 0]	w_address;		// Frame's width address
reg			[HW: 0]	h_address;		// Frame's height address
`endif

// State Machine Registers

// Integers

/*****************************************************************************
 *                         Finite State Machine(s)                           *
 *****************************************************************************/


/*****************************************************************************
 *                             Sequential Logic                              *
 *****************************************************************************/

// Output Registers

// Internal Registers
`ifdef USE_CONSECUTIVE_ADDRESSING
always @(posedge clk)
begin
	if (reset | ~dma_enabled)
		pixel_address 	<= 'h0;
	else if (reset_address)
		pixel_address 	<= 'h0;
	else if (inc_address)
		pixel_address 	<= pixel_address + 1;
end
`else
always @(posedge clk)
begin
	if (reset)
	begin
		w_address 	<= 'h0;
		h_address 	<= 'h0;
	end
	else if (reset_address)
	begin
		w_address 	<= 'h0;
		h_address 	<= 'h0;
	end
	else if (inc_address)
	begin
		if (w_address == (WIDTH - 1))
		begin
			w_address 	<= 'h0;
			h_address	<= h_address + 1;
		end
		else
			w_address 	<= w_address + 1;
	end
end
`endif

/*****************************************************************************
 *                            Combinational Logic                            *
 *****************************************************************************/

// Output Assignments
assign master_address		= buffer_start_address +
`ifdef USE_CONSECUTIVE_ADDRESSING
`ifdef USE_8BIT_MASTER
								pixel_address;
`elsif USE_16BIT_MASTER
						 		{pixel_address, 1'b0};
`elsif USE_32BIT_MASTER
								{pixel_address, 2'h0};
`else
								{pixel_address, 3'h0};
`endif
`else
`ifdef USE_8BIT_MASTER
								{h_address, w_address};
`elsif USE_16BIT_MASTER
								{h_address, w_address, 1'b0};
`elsif USE_32BIT_MASTER
								{h_address, w_address, 2'h0};
`else
								{h_address, w_address, 3'h0};
`endif
`endif

// Internal Assignments

/*****************************************************************************
 *                              Internal Modules                             *
 *****************************************************************************/

altera_up_video_dma_control_slave DMA_Control_Slave (
	// Inputs
	.clk									(clk),
	.reset								(reset),

	.address								(slave_address),
	.byteenable							(slave_byteenable),
	.read									(slave_read),
	.write								(slave_write),
	.writedata							(slave_writedata),

	.swap_addresses_enable			(reset_address),

	// Bi-Directional

	// Outputs
	.readdata							(slave_readdata),

	.current_start_address			(buffer_start_address),
	.dma_enabled						(dma_enabled)
);
defparam
	DMA_Control_Slave.DEFAULT_BUFFER_ADDRESS		= DEFAULT_BUFFER_ADDRESS,
	DMA_Control_Slave.DEFAULT_BACK_BUF_ADDRESS	= DEFAULT_BACK_BUF_ADDRESS,

	DMA_Control_Slave.WIDTH								= WIDTH,
	DMA_Control_Slave.HEIGHT							= HEIGHT,

	DMA_Control_Slave.ADDRESSING_BITS				= ADDRESSING_BITS,
	DMA_Control_Slave.COLOR_BITS						= COLOR_BITS,
	DMA_Control_Slave.COLOR_PLANES					= COLOR_PLANES,
`ifdef USE_CONSECUTIVE_ADDRESSING
	DMA_Control_Slave.ADDRESSING_MODE				= 1'b1,
`else
	DMA_Control_Slave.ADDRESSING_MODE				= 1'b0,
`endif

	DMA_Control_Slave.DEFAULT_DMA_ENABLED			= DEFAULT_DMA_ENABLED;

`ifdef USE_TO_MEMORY
altera_up_video_dma_to_memory From_Stream_to_Memory (
	// Inputs
	.clk									(clk),
	.reset								(reset | ~dma_enabled),

	.stream_data						(stream_data),
	.stream_startofpacket			(stream_startofpacket),
	.stream_endofpacket				(stream_endofpacket),
	.stream_empty						(stream_empty),
	.stream_valid						(stream_valid),

	.master_waitrequest				(master_waitrequest),
	
	// Bidirectional

	// Outputs
	.stream_ready						(stream_ready),

	.master_write						(master_write),
	.master_writedata					(master_writedata),

	.inc_address						(inc_address),
	.reset_address						(reset_address)
);
defparam
	From_Stream_to_Memory.DW	= DW,
	From_Stream_to_Memory.EW	= EW,
	From_Stream_to_Memory.MDW	= MDW;
`else
altera_up_video_dma_to_stream From_Memory_to_Stream (
	// Inputs
	.clk									(clk),
	.reset								(reset | ~dma_enabled),

	.stream_ready						(stream_ready),

	.master_readdata					(master_readdata),
	.master_readdatavalid			(master_readdatavalid),
	.master_waitrequest				(master_waitrequest),
	
`ifdef USE_CONSECUTIVE_ADDRESSING
	.reading_first_pixel_in_frame	(pixel_address == 0),
	.reading_last_pixel_in_frame	(pixel_address == ((WIDTH * HEIGHT)  - 1)),
`else
	.reading_first_pixel_in_frame	((w_address == 0) && (h_address == 0)),
	.reading_last_pixel_in_frame	((w_address == (WIDTH - 1)) && (h_address == (HEIGHT - 1))),
`endif

	// Bidirectional

	// Outputs
	.stream_data						(stream_data),
	.stream_startofpacket			(stream_startofpacket),
	.stream_endofpacket				(stream_endofpacket),
	.stream_empty						(stream_empty),
	.stream_valid						(stream_valid),

	.master_arbiterlock				(master_arbiterlock),
	.master_read						(master_read),

	.inc_address						(inc_address),
	.reset_address						(reset_address)
);
defparam
	From_Memory_to_Stream.DW	= DW,
	From_Memory_to_Stream.EW	= EW,
	From_Memory_to_Stream.MDW	= MDW;
`endif

endmodule

