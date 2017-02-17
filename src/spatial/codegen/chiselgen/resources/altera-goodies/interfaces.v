/******************************************************************************
 *                                                                            *
 * This module decodes video input streams on the DE boards.                  *
 *                                                                            *
 ******************************************************************************/

module altera_up_avalon_video_decoder (
	// Inputs
	clk,
	reset,

`ifdef USE_ONBOARD_VIDEO
	TD_CLK27,
	TD_DATA,
	TD_HS,
	TD_VS,
	clk27_reset,
`else
	PIXEL_CLK,
	LINE_VALID,
	FRAME_VALID,
	PIXEL_DATA,
	pixel_clk_reset,
`endif

	stream_out_ready,

	// Bidirectional

	// Outputs
`ifdef USE_ONBOARD_VIDEO
	TD_RESET,
`endif
	overflow_flag,

	stream_out_data,
	stream_out_startofpacket,
	stream_out_endofpacket,
	stream_out_empty,
	stream_out_valid
);


/******************************************************************************
 *                                                                            
 * DMA Controller:
 * This module store and retrieves video frames to and from memory.           
 *                                                                            
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


/******************************************************************************
 *                                                                            
 * DMA Control Slave:
 *                                                                            
 ******************************************************************************/
module altera_up_video_dma_control_slave (
	// Inputs
	clk,
	reset,

	address,
	byteenable,
	read,
	write,
	writedata,

	swap_addresses_enable,

	// Bi-Directional

	// Outputs
	readdata,

	current_start_address,
	dma_enabled
);


/******************************************************************************
 *                                                                            
 * DMA to memory:
 *
 * This module streams a frame to memory
 *                                                                            
 ******************************************************************************/
module altera_up_video_dma_to_memory (
	// Inputs
	clk,
	reset,

	stream_data,
	stream_startofpacket,
	stream_endofpacket,
	stream_empty,
	stream_valid,

	master_waitrequest,
	
	// Bidirectional

	// Outputs
	stream_ready,

	master_write,
	master_writedata,

	inc_address,
	reset_address
);


/******************************************************************************
 *                                                                            
 * DMA to stream:
 *
 * This module streams a frame from memory to stream
 *                                                                            
 ******************************************************************************/
module altera_up_video_dma_to_stream (
	// Inputs
	clk,
	reset,

	stream_ready,

	master_readdata,
	master_readdatavalid,
	master_waitrequest,
	
	reading_first_pixel_in_frame,
	reading_last_pixel_in_frame,

	// Bidirectional

	// Outputs
	stream_data,
	stream_startofpacket,
	stream_endofpacket,
	stream_empty,
	stream_valid,

	master_arbiterlock,
	master_read,

	inc_address,
	reset_address
);


/******************************************************************************
 *                                                                            *
 * VGA controller: 
 *
 * This module controls VGA output 
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
