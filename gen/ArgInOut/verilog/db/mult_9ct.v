//lpm_mult CBX_DECLARE_ALL_CONNECTED_PORTS="OFF" DEDICATED_MULTIPLIER_CIRCUITRY="YES" DEVICE_FAMILY="Cyclone V" DSP_BLOCK_BALANCING="Auto" LPM_PIPELINE=5 LPM_REPRESENTATION="UNSIGNED" LPM_WIDTHA=24 LPM_WIDTHB=24 LPM_WIDTHP=48 LPM_WIDTHS=1 aclr clken clock dataa datab result CARRY_CHAIN="MANUAL" CARRY_CHAIN_LENGTH=48
//VERSION_BEGIN 16.1 cbx_cycloneii 2016:10:19:21:26:20:SJ cbx_lpm_add_sub 2016:10:19:21:26:20:SJ cbx_lpm_mult 2016:10:19:21:26:20:SJ cbx_mgl 2016:10:19:22:10:30:SJ cbx_nadder 2016:10:19:21:26:20:SJ cbx_padd 2016:10:19:21:26:20:SJ cbx_stratix 2016:10:19:21:26:20:SJ cbx_stratixii 2016:10:19:21:26:20:SJ cbx_util_mgl 2016:10:19:21:26:20:SJ  VERSION_END
// synthesis VERILOG_INPUT_VERSION VERILOG_2001
// altera message_off 10463



// Copyright (C) 2016  Intel Corporation. All rights reserved.
//  Your use of Intel Corporation's design tools, logic functions 
//  and other software and tools, and its AMPP partner logic 
//  functions, and any output files from any of the foregoing 
//  (including device programming or simulation files), and any 
//  associated documentation or information are expressly subject 
//  to the terms and conditions of the Intel Program License 
//  Subscription Agreement, the Intel Quartus Prime License Agreement,
//  the Intel MegaCore Function License Agreement, or other 
//  applicable license agreement, including, without limitation, 
//  that your use is for the sole purpose of programming logic 
//  devices manufactured by Intel and sold by Intel or its 
//  authorized distributors.  Please refer to the applicable 
//  agreement for further details.



//synthesis_resources = 
//synopsys translate_off
`timescale 1 ps / 1 ps
//synopsys translate_on
module  mult_9ct
	( 
	aclr,
	clken,
	clock,
	dataa,
	datab,
	result) /* synthesis synthesis_clearbox=1 */;
	input   aclr;
	input   clken;
	input   clock;
	input   [23:0]  dataa;
	input   [23:0]  datab;
	output   [47:0]  result;
`ifndef ALTERA_RESERVED_QIS
// synopsys translate_off
`endif
	tri0   aclr;
	tri1   clken;
	tri0   clock;
`ifndef ALTERA_RESERVED_QIS
// synopsys translate_on
`endif

	reg  [23:0]  dataa_input_reg;
	reg  [23:0]  datab_input_reg;
	reg  [47:0]  result_output_reg;
	reg  [47:0]  result_extra0_reg;
	reg  [47:0]  result_extra1_reg;
	reg  [47:0]  result_extra2_reg;
	wire [23:0]    dataa_wire;
	wire [23:0]    datab_wire;
	wire [47:0]    result_wire;


	// synopsys translate_off
	initial
		dataa_input_reg = 0;
	// synopsys translate_on
	always @(posedge clock or posedge aclr)
		if (aclr == 1'b1)
			dataa_input_reg <= 24'b0;
		else
			if (clken == 1'b1)
				dataa_input_reg <= dataa;
	// synopsys translate_off
	initial
		datab_input_reg = 0;
	// synopsys translate_on
	always @(posedge clock or posedge aclr)
		if (aclr == 1'b1)
			datab_input_reg <= 24'b0;
		else
			if (clken == 1'b1)
				datab_input_reg <= datab;
	// synopsys translate_off
	initial
		result_output_reg = 0;
	// synopsys translate_on
	always @(posedge clock or posedge aclr)
		if (aclr == 1'b1)
			result_output_reg <= 48'b0;
		else
			if (clken == 1'b1)
				result_output_reg <= result_extra2_reg;
	// synopsys translate_off
	initial
		result_extra0_reg = 0;
	// synopsys translate_on
	always @(posedge clock or posedge aclr)
		if (aclr == 1'b1)
			result_extra0_reg <= 48'b0;
		else
			if (clken == 1'b1)
				result_extra0_reg <= result_wire[47:0];
	// synopsys translate_off
	initial
		result_extra1_reg = 0;
	// synopsys translate_on
	always @(posedge clock or posedge aclr)
		if (aclr == 1'b1)
			result_extra1_reg <= 48'b0;
		else
			if (clken == 1'b1)
				result_extra1_reg <= result_extra0_reg;
	// synopsys translate_off
	initial
		result_extra2_reg = 0;
	// synopsys translate_on
	always @(posedge clock or posedge aclr)
		if (aclr == 1'b1)
			result_extra2_reg <= 48'b0;
		else
			if (clken == 1'b1)
				result_extra2_reg <= result_extra1_reg;

	assign dataa_wire = dataa_input_reg;
	assign datab_wire = datab_input_reg;
	assign result_wire = dataa_wire * datab_wire;
	assign result = ({result_output_reg});

endmodule //mult_9ct
//VALID FILE
