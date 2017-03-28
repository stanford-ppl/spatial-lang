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
 * This module loads data into the Audio and Video chips' control             *
 *  registers after system reset.                                             *
 *                                                                            *
 ******************************************************************************/

module altera_up_av_config_auto_init (
	// Inputs
	clk,
	reset,

	clear_error,

	ack,
	transfer_complete,

	rom_data,

	// Bidirectionals

	// Outputs
	data_out,
	transfer_data,

	rom_address,
	
	auto_init_complete,
	auto_init_error,
);

/*****************************************************************************
 *                           Parameter Declarations                          *
 *****************************************************************************/

parameter ROM_SIZE	= 50;

parameter AW			= 5;		// Auto Initialize ROM's address width 
parameter DW			= 23;		// Auto Initialize ROM's datawidth

/*****************************************************************************
 *                             Port Declarations                             *
 *****************************************************************************/
// Inputs
input						clk;
input						reset;

input						clear_error;

input						ack;
input						transfer_complete;

input			[DW: 0]	rom_data;

// Bidirectionals

// Outputs
output reg	[DW: 0]	data_out;
output reg				transfer_data;

output reg	[AW: 0]	rom_address;

output reg				auto_init_complete;
output reg				auto_init_error;

/*****************************************************************************
 *                           Constant Declarations                           *
 *****************************************************************************/

// States

/*****************************************************************************
 *                 Internal Wires and Registers Declarations                 *
 *****************************************************************************/

// Internal Wires
wire						toggle_next_transfer;

// Internal Registers

// State Machine Registers

/*****************************************************************************
 *                         Finite State Machine(s)                           *
 *****************************************************************************/


/*****************************************************************************
 *                             Sequential Logic                              *
 *****************************************************************************/

// Output Registers
always @(posedge clk)
begin
	if (reset)
		data_out <= 'h0;
	else
		data_out <= rom_data;
end

always @(posedge clk)
begin
	if (reset)
		transfer_data <= 1'b0;
	else if (auto_init_complete | transfer_complete)
		transfer_data <= 1'b0;
	else
		transfer_data <= 1'b1;
end

always @(posedge clk)
begin
	if (reset)
		rom_address <= 'h0;
	else if (toggle_next_transfer)
		rom_address <= rom_address + 'h1;
end

always @(posedge clk)
begin
	if (reset)
		auto_init_complete <= 1'b0;
	else if (toggle_next_transfer & (rom_address == (ROM_SIZE - 1)))
		auto_init_complete <= 1'b1;
end

always @(posedge clk)
begin
	if (reset)
		auto_init_error <= 1'b0;
	else if (toggle_next_transfer & ack)
		auto_init_error <= 1'b1;
	else if (clear_error)
		auto_init_error <= 1'b0;
end

// Internal Registers

/*****************************************************************************
 *                            Combinational Logic                            *
 *****************************************************************************/

// Output Assignments

// Internal Assignments
assign toggle_next_transfer = transfer_data & transfer_complete;

/*****************************************************************************
 *                              Internal Modules                             *
 *****************************************************************************/


endmodule

