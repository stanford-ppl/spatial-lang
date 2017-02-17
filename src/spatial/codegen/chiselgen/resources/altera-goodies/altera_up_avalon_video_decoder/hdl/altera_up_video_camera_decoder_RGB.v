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
 * This module decodes video streams from the Terasic CCD cameras and         * 
 *  outputs the video in RGB format.                                          *
 *                                                                            *
 ******************************************************************************/

module altera_up_video_camera_decoder_RGB (
	// Inputs
	clk,
	reset,

	PIXEL_DATA,
	LINE_VALID,
	FRAME_VALID,

	ready,

	// Bidirectional

	// Outputs
	data,
	startofpacket,
	endofpacket,
	valid
);

/*****************************************************************************
 *                           Parameter Declarations                          *
 *****************************************************************************/

parameter IDW			= 9;
parameter ODW			= 29;

parameter WIDTH		= 1280;

/*****************************************************************************
 *                             Port Declarations                             *
 *****************************************************************************/

// Inputs
input						clk;
input						reset;

input			[IDW:0]	PIXEL_DATA;
input						LINE_VALID;
input						FRAME_VALID;

input						ready;

// Bidirectional

// Outputs
output reg	[ODW:0]	data;
output reg				startofpacket;
output reg			 	endofpacket;
output reg			 	valid;

/*****************************************************************************
 *                           Constant Declarations                           *
 *****************************************************************************/

/*****************************************************************************
 *                 Internal Wires and Registers Declarations                 *
 *****************************************************************************/
// Internal Wires
wire						read_temps;

wire			[IDW:0]	shift_reg_data;
wire			[IDW:0]	new_green;

// Internal Registers
reg			[IDW:0]	io_pixel_data;
reg						io_line_valid;
reg						io_frame_valid;

reg						last_io_line_valid;
reg			[IDW:0]	last_pixel_data;
reg			[IDW:0]	last_shift_reg_data;

reg						even_line;
reg						even_pixel;
reg						frame_sync;

reg			[ODW:0]	temp_data;
reg						temp_start;
reg						temp_end;
reg						temp_valid;

// State Machine Registers

/*****************************************************************************
 *                         Finite State Machine(s)                           *
 *****************************************************************************/


/*****************************************************************************
 *                             Sequential Logic                              *
 *****************************************************************************/
// Input Registers
always @ (posedge clk)
begin
	io_pixel_data				<= PIXEL_DATA;
	io_line_valid				<= LINE_VALID;
	io_frame_valid				<= FRAME_VALID;
end

// Output Registers
always @ (posedge clk)
begin
	if (reset)
	begin
		data						<= 'h0;
		startofpacket			<= 1'b0;
		endofpacket				<= 1'b0;
		valid						<= 1'b0;
	end
	else if (read_temps)
	begin
		data						<= temp_data;
		startofpacket			<= temp_start;
		endofpacket				<= temp_end;
		valid						<= temp_valid;
	end
	else if (ready)
		valid						<= 1'b0;
end

// Internal Registers
always @ (posedge clk)
begin
	if (reset)
	begin
		last_io_line_valid	<= 1'b0;
		last_pixel_data		<= 'h0;
		last_shift_reg_data	<= 'h0;
	end
	else if (~io_frame_valid | ~io_line_valid)
	begin
		last_io_line_valid	<= io_line_valid;
		last_pixel_data		<= 'h0;
		last_shift_reg_data	<= 'h0;
	end
	else
	begin
		last_io_line_valid	<= io_line_valid;
		last_pixel_data		<= io_pixel_data;
		last_shift_reg_data	<= shift_reg_data;
	end
end

always @ (posedge clk)
begin
	if (reset)
	begin
		even_line 				<= 1'b0;
		even_pixel 				<= 1'b0;
	end
	else if (~io_frame_valid)
	begin
		even_line 				<= 1'b0;
		even_pixel 				<= 1'b0;
	end
	else if (io_line_valid)
	begin
		even_pixel				<= even_pixel ^ 1'b1;
	end
	else
	begin
		even_pixel				<= 1'b0;
		if (last_io_line_valid)
			even_line 			<= even_line ^ 1'b1;
	end
end

always @ (posedge clk)
begin
	if (reset)
		frame_sync 				<= 1'b0;
	else if (~io_frame_valid)
		frame_sync 				<= 1'b1;
	else if (read_temps)
		frame_sync 				<= 1'b0;
end

always @ (posedge clk)
begin
	if (reset)
	begin
		temp_data 				<= 'h0;
		temp_start				<= 1'b0;
		temp_end					<= 1'b0;
		temp_valid				<= 1'b0;
	end
	else if (read_temps)
	begin
//		temp_data[29:20] 		<= shift_reg_data;
//		temp_data[19:10] 		<= last_shift_reg_data[9:1] + io_pixel_data[9:1];
//		temp_data[ 9: 0] 		<= last_pixel_data;
		temp_data 				<= {shift_reg_data, new_green, last_pixel_data};
		temp_start				<= frame_sync;
		temp_end					<= ~io_frame_valid;
		temp_valid				<= even_pixel & even_line;
	end
	else if (~io_frame_valid)
	begin
		temp_end					<= ~io_frame_valid;
	end
end

/*****************************************************************************
 *                            Combinational Logic                            *
 *****************************************************************************/
// Output Assignments

// Internal Assignments
assign read_temps = (ready | ~valid) & 
	((even_pixel & even_line) | ((temp_start | temp_end) & temp_valid));

assign new_green	=  
	{1'b0, last_shift_reg_data[IDW:1]} + {1'b0, io_pixel_data[IDW:1]};

/*****************************************************************************
 *                              Internal Modules                             *
 *****************************************************************************/

altshift_taps bayern_pattern_shift_reg (
	// Inputs
	.clock		(clk),

	.clken		(io_line_valid & io_frame_valid),
	.shiftin		(io_pixel_data),
	
	.aclr 		(),

	// Outputs
	.shiftout	(shift_reg_data),

	// synopsys translate_off
	.taps			()
	
	// synopsys translate_on
);
defparam
	bayern_pattern_shift_reg.lpm_hint			= "RAM_BLOCK_TYPE=M4K",
	bayern_pattern_shift_reg.lpm_type			= "altshift_taps",
	bayern_pattern_shift_reg.number_of_taps	= 1,
	bayern_pattern_shift_reg.tap_distance		= WIDTH,
	bayern_pattern_shift_reg.width				= (IDW + 1);

endmodule

