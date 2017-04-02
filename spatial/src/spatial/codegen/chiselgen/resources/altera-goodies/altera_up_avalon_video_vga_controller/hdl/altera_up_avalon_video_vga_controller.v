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
 * This module controls VGA output for Altera's DE1 and DE2 Boards.           *
 *                                                                            *
 ******************************************************************************/

module altera_up_avalon_video_vga_controller (
	// Inputs
	clk,
	reset,

	data,
	startofpacket,
	endofpacket,
	empty,
	valid,

	// Bidirectionals

	// Outputs
	ready,

`ifdef USE_UNDERFLOW_FLAG
	underflow_flag,
`endif

	VGA_CLK,
	VGA_BLANK,
	VGA_SYNC,
	VGA_HS,
	VGA_VS,
`ifdef USE_TRDB_LTM
	VGA_DATA_EN,
`elsif USE_TPAD
	VGA_DATA_EN,
`elsif USE_VEEK_MT
	VGA_DATA_EN,
`endif
`ifdef USE_TRDB_LCM
	VGA_COLOR
`else
	VGA_R,
	VGA_G,
	VGA_B
`endif
);

/*****************************************************************************
 *                           Parameter Declarations                          *
 *****************************************************************************/

parameter CW								= 9;
parameter DW								= 29;

parameter R_UI								= 29;
parameter R_LI								= 20;
parameter G_UI								= 19;
parameter G_LI								= 10;
parameter B_UI								= 9;
parameter B_LI								= 0;

`ifdef USE_TRDB_LCM
/* Number of pixels */
parameter H_ACTIVE 						= 1280;
parameter H_FRONT_PORCH					=   40;
parameter H_SYNC							=    1;
parameter H_BACK_PORCH 					=  239;
parameter H_TOTAL 						= 1560;

/* Number of lines */
parameter V_ACTIVE 						= 240;
parameter V_FRONT_PORCH					=   1;
parameter V_SYNC							=   1;
parameter V_BACK_PORCH 					=  20;
parameter V_TOTAL							= 262;

parameter LW								= 9;		// Number of bits for lines
parameter LINE_COUNTER_INCREMENT		= 9'h001;

parameter PW								= 11;		// Number of bits for pixels
parameter PIXEL_COUNTER_INCREMENT	= 11'h001;

`elsif USE_TRDB_LTM
/* Number of pixels */
parameter H_ACTIVE 						=  800;
parameter H_FRONT_PORCH					=   40;
parameter H_SYNC							=    1;
parameter H_BACK_PORCH 					=  215;
parameter H_TOTAL 						= 1056;

/* Number of lines */
parameter V_ACTIVE 						= 480;
parameter V_FRONT_PORCH					=  10;
parameter V_SYNC							=   1;
parameter V_BACK_PORCH 					=  34;
parameter V_TOTAL							= 525;

parameter LW								= 10;
parameter LINE_COUNTER_INCREMENT		= 10'h001;

parameter PW								= 11;
parameter PIXEL_COUNTER_INCREMENT	= 11'h001;

`elsif USE_TPAD
/* Number of pixels */
parameter H_ACTIVE 						=  800;
parameter H_FRONT_PORCH					=   40;
parameter H_SYNC							=  128;
parameter H_BACK_PORCH 					=   88;
parameter H_TOTAL 						= 1056;

/* Number of lines */
parameter V_ACTIVE 						= 600;
parameter V_FRONT_PORCH					=   1;
parameter V_SYNC							=   4;
parameter V_BACK_PORCH 					=  23;
parameter V_TOTAL							= 628;

parameter LW								= 10;
parameter LINE_COUNTER_INCREMENT		= 10'h001;

parameter PW								= 11;
parameter PIXEL_COUNTER_INCREMENT	= 11'h001;

`elsif USE_VEEK_MT
/* Number of pixels */
parameter H_ACTIVE 						=  800;
parameter H_FRONT_PORCH					=  210;
parameter H_SYNC							=   30;
parameter H_BACK_PORCH 					=   16;
parameter H_TOTAL 						= 1056;

/* Number of lines */
parameter V_ACTIVE 						= 480;
parameter V_FRONT_PORCH					=  22;
parameter V_SYNC							=  13;
parameter V_BACK_PORCH 					=  10;
parameter V_TOTAL							= 525;

parameter LW								= 10;
parameter LINE_COUNTER_INCREMENT		= 10'h001;

parameter PW								= 11;
parameter PIXEL_COUNTER_INCREMENT	= 11'h001;

`elsif USE_VGA
/* Number of pixels */
parameter H_ACTIVE 						= 640;
parameter H_FRONT_PORCH					=  16;
parameter H_SYNC							=  96;
parameter H_BACK_PORCH 					=  48;
parameter H_TOTAL 						= 800;

/* Number of lines */
parameter V_ACTIVE 						= 480;
parameter V_FRONT_PORCH					=  10;
parameter V_SYNC							=   2;
parameter V_BACK_PORCH 					=  33;
parameter V_TOTAL							= 525;

parameter LW								= 10;
parameter LINE_COUNTER_INCREMENT		= 10'h001;

parameter PW								= 10;
parameter PIXEL_COUNTER_INCREMENT	= 10'h001;

`elsif USE_SVGA
/* Number of pixels */
parameter H_ACTIVE 						=  800;
parameter H_FRONT_PORCH					=   40;
parameter H_SYNC							=  128;
parameter H_BACK_PORCH 					=   88;
parameter H_TOTAL 						= 1056;

/* Number of lines */
parameter V_ACTIVE 						= 600;
parameter V_FRONT_PORCH					=   2;
parameter V_SYNC							=   4;
parameter V_BACK_PORCH 					=  22;
parameter V_TOTAL							= 628;

parameter LW								= 10;
parameter LINE_COUNTER_INCREMENT		= 10'h001;

parameter PW								= 11;
parameter PIXEL_COUNTER_INCREMENT	= 11'h001;

`elsif USE_XGA
/* Number of pixels */
parameter H_ACTIVE 						= 1024;
parameter H_FRONT_PORCH					=   24;
parameter H_SYNC							=  136;
parameter H_BACK_PORCH 					=  160;
parameter H_TOTAL 						= 1344;

/* Number of lines */
parameter V_ACTIVE 						= 768;
parameter V_FRONT_PORCH					=   3;
parameter V_SYNC							=   6;
parameter V_BACK_PORCH 					=  29;
parameter V_TOTAL							= 806;

parameter LW								= 10;
parameter LINE_COUNTER_INCREMENT		= 10'h001;

parameter PW								= 11;
parameter PIXEL_COUNTER_INCREMENT	= 11'h001;

`elsif USE_WXGA
/* Number of pixels */
parameter H_ACTIVE 						= 1280;
parameter H_FRONT_PORCH					=   64;
parameter H_SYNC							=  136;
parameter H_BACK_PORCH 					=  200;
parameter H_TOTAL 						= 1680;

/* Number of lines */
parameter V_ACTIVE 						= 800;
parameter V_FRONT_PORCH					=   2;
parameter V_SYNC							=   3;
parameter V_BACK_PORCH 					=  23;
parameter V_TOTAL							= 828;

parameter LW								= 10;
parameter LINE_COUNTER_INCREMENT		= 10'h001;

parameter PW								= 11;
parameter PIXEL_COUNTER_INCREMENT	= 11'h001;

`elsif USE_SXGA
/* Number of pixels */
parameter H_ACTIVE 						= 1280;
parameter H_FRONT_PORCH					=   48;
parameter H_SYNC							=  112;
parameter H_BACK_PORCH 					=  248;
parameter H_TOTAL 						= 1688;

/* Number of lines */
parameter V_ACTIVE 						= 1024;
parameter V_FRONT_PORCH					=    2;
parameter V_SYNC							=    3;
parameter V_BACK_PORCH 					=   37;
parameter V_TOTAL							= 1066;

parameter LW								= 11;
parameter LINE_COUNTER_INCREMENT		= 11'h001;

parameter PW								= 11;
parameter PIXEL_COUNTER_INCREMENT	= 11'h001;

`elsif USE_WSXGA
/* Number of pixels */
parameter H_ACTIVE 						= 1680;
parameter H_FRONT_PORCH					=  104;
parameter H_SYNC							=  184;
parameter H_BACK_PORCH 					=  288;
parameter H_TOTAL 						= 2256;

/* Number of lines */
parameter V_ACTIVE 						= 1050;
parameter V_FRONT_PORCH					=    2;
parameter V_SYNC							=    3;
parameter V_BACK_PORCH 					=   32;
parameter V_TOTAL							= 1087;

parameter LW								= 11;
parameter LINE_COUNTER_INCREMENT		= 11'h001;

parameter PW								= 12;
parameter PIXEL_COUNTER_INCREMENT	= 12'h001;

`elsif USE_SDTV
/* Number of pixels */
parameter H_ACTIVE 						= 720;
parameter H_FRONT_PORCH					=  24;
parameter H_SYNC							=  40;
parameter H_BACK_PORCH 					=  96;
parameter H_TOTAL 						= 880;

/* Number of lines */
parameter V_ACTIVE 						= 480;
parameter V_FRONT_PORCH					=  10;
parameter V_SYNC							=   3;
parameter V_BACK_PORCH 					=  32;
parameter V_TOTAL							= 525;

parameter LW								= 10;
parameter LINE_COUNTER_INCREMENT		= 10'h001;

parameter PW								= 10;
parameter PIXEL_COUNTER_INCREMENT	= 10'h001;

`elsif USE_HDTV
/* Number of pixels */
parameter H_ACTIVE 						= 1280;
parameter H_FRONT_PORCH					=   72;
parameter H_SYNC							=   80;
parameter H_BACK_PORCH 					=  216;
parameter H_TOTAL 						= 1648;

/* Number of lines */
parameter V_ACTIVE 						= 720;
parameter V_FRONT_PORCH					=   3;
parameter V_SYNC							=   5;
parameter V_BACK_PORCH 					=  22;
parameter V_TOTAL							= 750;

parameter LW								= 10;
parameter LINE_COUNTER_INCREMENT		= 10'h001;

parameter PW								= 11;
parameter PIXEL_COUNTER_INCREMENT	= 11'h001;

`else
/* Default is the same as USE_VGA */
/* Number of pixels */
parameter H_ACTIVE 						= 640;
parameter H_FRONT_PORCH					=  16;
parameter H_SYNC							=  96;
parameter H_BACK_PORCH 					=  48;
parameter H_TOTAL 						= 800;

/* Number of lines */
parameter V_ACTIVE 						= 480;
parameter V_FRONT_PORCH					=  10;
parameter V_SYNC							=   2;
parameter V_BACK_PORCH 					=  33;
parameter V_TOTAL							= 525;

parameter LW								= 10;
parameter LINE_COUNTER_INCREMENT		= 10'h001;

parameter PW								= 10;
parameter PIXEL_COUNTER_INCREMENT	= 10'h001;
`endif
/*****************************************************************************
 *                             Port Declarations                             *
 *****************************************************************************/
// Inputs
input						clk;
input						reset;

input			[DW: 0]	data;
input						startofpacket;
input						endofpacket;
input			[ 1: 0]	empty;
input						valid;

// Bidirectionals

// Outputs
output					ready;

`ifdef USE_UNDERFLOW_FLAG
output reg				underflow_flag;
`endif

output					VGA_CLK;
output reg				VGA_BLANK;
output reg				VGA_SYNC;
output reg				VGA_HS;
output reg				VGA_VS;
`ifdef USE_TRDB_LTM
output reg				VGA_DATA_EN;
`elsif USE_TPAD
output reg				VGA_DATA_EN;
`elsif USE_VEEK_MT
output reg				VGA_DATA_EN;
`endif
`ifdef USE_TRDB_LCM
output reg	[CW: 0]	VGA_COLOR;
`else
output reg	[CW: 0]	VGA_R;
output reg	[CW: 0]	VGA_G;
output reg	[CW: 0]	VGA_B;
`endif

/*****************************************************************************
 *                           Constant Declarations                           *
 *****************************************************************************/

// States
localparam	STATE_0_SYNC_FRAME	= 1'b0,
				STATE_1_DISPLAY		= 1'b1;

/*****************************************************************************
 *                 Internal Wires and Registers Declarations                 *
 *****************************************************************************/
// Internal Wires
wire						read_enable;
wire						end_of_active_frame;

wire						vga_blank_sync;
wire						vga_c_sync;
wire						vga_h_sync;
wire						vga_v_sync;
wire						vga_data_enable;
wire			[CW: 0]	vga_red;
wire			[CW: 0]	vga_green;
wire			[CW: 0]	vga_blue;
wire			[CW: 0]	vga_color_data;


// Internal Registers
reg			[ 3: 0]	color_select; // Use for the TRDB_LCM

// State Machine Registers
reg						ns_mode;
reg						s_mode;

/*****************************************************************************
 *                         Finite State Machine(s)                           *
 *****************************************************************************/

always @(posedge clk)	// sync reset
begin
	if (reset == 1'b1)
		s_mode <= STATE_0_SYNC_FRAME;
	else
		s_mode <= ns_mode;
end

always @(*)
begin
	// Defaults
	ns_mode = STATE_0_SYNC_FRAME;

   case (s_mode)
	STATE_0_SYNC_FRAME:
	begin
		if (valid & startofpacket)
			ns_mode = STATE_1_DISPLAY;
		else
			ns_mode = STATE_0_SYNC_FRAME;
	end
	STATE_1_DISPLAY:
	begin
		if (end_of_active_frame)
			ns_mode = STATE_0_SYNC_FRAME;
		else
			ns_mode = STATE_1_DISPLAY;
	end
	default:
	begin
		ns_mode = STATE_0_SYNC_FRAME;
	end
	endcase
end

/*****************************************************************************
 *                             Sequential Logic                              *
 *****************************************************************************/

// Output Registers
`ifdef USE_UNDERFLOW_FLAG
always @(posedge clk)
begin
	if (reset)
		underflow_flag <= 1'b0;
	else if ((ns_mode == STATE_1_DISPLAY) && (read_enable & ~valid))
		underflow_flag <= 1'b1;
end
`endif

always @(posedge clk)
begin
	VGA_BLANK	<= vga_blank_sync;
	VGA_SYNC		<= 1'b0;
	VGA_HS		<= vga_h_sync;
	VGA_VS		<= vga_v_sync;
`ifdef USE_TRDB_LTM
	VGA_DATA_EN	<= vga_data_enable;
`elsif USE_TPAD
	VGA_DATA_EN	<= vga_data_enable;
`elsif USE_VEEK_MT
	VGA_DATA_EN	<= vga_data_enable;
`endif
`ifdef USE_TRDB_LCM
	VGA_COLOR	<= vga_color_data;
`else
	VGA_R			<= vga_red;
	VGA_G			<= vga_green;
	VGA_B			<= vga_blue;
`endif
end


// Internal Registers
always @(posedge clk)
begin
	if (reset)
		color_select <= 4'h1;
	else if (s_mode == STATE_0_SYNC_FRAME)
		color_select <= 4'h1;
	else if (~read_enable)
		color_select <= {color_select[2:0], color_select[3]};
end


/*****************************************************************************
 *                            Combinational Logic                            *
 *****************************************************************************/
// Output Assignments
assign ready = 
	(s_mode == STATE_0_SYNC_FRAME) ? 
		valid & ~startofpacket : 
`ifdef USE_TRDB_LCM
		read_enable & color_select[3];
`else
		read_enable;
`endif

assign VGA_CLK = ~clk;

/*****************************************************************************
 *                              Internal Modules                             *
 *****************************************************************************/

altera_up_avalon_video_vga_timing VGA_Timing (
	// Inputs
	.clk							(clk),
	.reset						(reset),

	.red_to_vga_display		(data[R_UI:R_LI]),
	.green_to_vga_display	(data[G_UI:G_LI]),
	.blue_to_vga_display		(data[B_UI:B_LI]),
	.color_select				(color_select),

//	.data_valid					(1'b1),

	// Bidirectionals

	// Outputs
	.read_enable				(read_enable),

	.end_of_active_frame		(end_of_active_frame),
	.end_of_frame				(), // (end_of_frame),

	// dac pins
	.vga_blank					(vga_blank_sync),
	.vga_c_sync					(vga_c_sync),
	.vga_h_sync					(vga_h_sync),
	.vga_v_sync					(vga_v_sync),
	.vga_data_enable			(vga_data_enable),
	.vga_red						(vga_red),
	.vga_green					(vga_green),
	.vga_blue					(vga_blue),
	.vga_color_data			(vga_color_data)
);
defparam
	VGA_Timing.CW 									= CW,

	VGA_Timing.H_ACTIVE 							= H_ACTIVE,
	VGA_Timing.H_FRONT_PORCH					= H_FRONT_PORCH,
	VGA_Timing.H_SYNC								= H_SYNC,
	VGA_Timing.H_BACK_PORCH 					= H_BACK_PORCH,
	VGA_Timing.H_TOTAL 							= H_TOTAL,

	VGA_Timing.V_ACTIVE 							= V_ACTIVE,
	VGA_Timing.V_FRONT_PORCH					= V_FRONT_PORCH,
	VGA_Timing.V_SYNC								= V_SYNC,
	VGA_Timing.V_BACK_PORCH		 				= V_BACK_PORCH,
	VGA_Timing.V_TOTAL							= V_TOTAL,

	VGA_Timing.LW									= LW,
	VGA_Timing.LINE_COUNTER_INCREMENT		= LINE_COUNTER_INCREMENT,

	VGA_Timing.PW									= PW,
	VGA_Timing.PIXEL_COUNTER_INCREMENT		= PIXEL_COUNTER_INCREMENT;

endmodule

