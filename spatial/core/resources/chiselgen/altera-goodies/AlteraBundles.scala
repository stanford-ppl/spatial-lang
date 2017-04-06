
// Parameters
// io for streaming into memory
val DW					=  15; // Frame's datawidth
val EW					=   0; // Frame's empty width
val WIDTH				= 640; // Frame's width in pixels
val HEIGHT				= 480; // Frame's height in lines

val AW					=  17; // Frame's address width
val WW					=   8; // Frame width's address width
val HW					=   7; // Frame height's address width

val MDW					=  15; // Avalon master's datawidth

val DEFAULT_BUFFER_ADDRESS		= 32'h00000000;
val DEFAULT_BACK_BUF_ADDRESS	        = 32'h00000000;

val ADDRESSING_BITS			= 16'h0809;
val COLOR_BITS				= 4'h7;
val COLOR_PLANES			= 2'h2;

val DEFAULT_DMA_ENABLED			= 1'b1; // 0: OFF or 1: ON

val DMA_io = new Bundle {
  val reset = UInt(INPUT, 1)
  
  val stream_data = UInt(INPUT, DW + 1)
  val stream_startofpacket = UInt(INPUT, 1)
  val stream_endofpacket = UInt(INPUT, 1)
  val stream_empty = UInt(INPUT, EW + 1)
  val stream_valid = UInt(INPUT, 1)
  
  val master_waitrequest = UInt(INPUT, 1)
  
  val slave_address = UInt(INPUT, 2)
  val slave_byteenable = UInt(INPUT, 4)
  val slave_read = UInt(INPUT, 1)
  val slave_write = UInt(INPUT, 1)
  val slave_writedata = UInt(INPUT, 32)
  
  // Bidirectional
  
  // Outputs
  val stream_ready = UInt(OUTPUT, 1)
  val master_address = UInt(OUTPUT, 32)
  val master_write = UInt(OUTPUT, 1)
  val master_writedata = UInt(OUTPUT, MDW + 1)
  val slave_readdata = UInt(OUTPUT, 32)
}

// io for streaming from memory
val DMA_io = new Bundle {
  val reset = UInt(INPUT, 1)
  val stream_ready = UInt(INPUT, 1)
  val master_readdata = UInt(INPUT, MDW + 1)
  val master_readdatavalid = UInt(INPUT, MDW + 1)
  val master_waitrequest = UInt(INPUT, 1)
	
  val slave_address = UInt(INPUT, 2)
  val slave_byteenable = UInt(INPUT, 4)
  val slave_read = UInt(INPUT, 1)
  val slave_write = UInt(INPUT, 1)
  val slave_writedata = UInt(INPUT, 32)

  // output
  val stream_data = UInt(OUTPUT, DW + 1)
  val stream_startofpacket = UInt(OUTPUT, 1)
  val stream_endofpacket = UInt(OUTPUT, 1)
  val stream_empty = UInt(OUTPUT, EW + 1)
  val stream_valid = UInt(OUTPUT, 1)
  val master_address = UInt(OUTPUT, 32)
  val master_arbiterlock = UInt(OUTPUT, 1)
  val master_read = UInt(OUTPUT, 1)

  val slave_readdata = UInt(OUTPUT, 32)
}

// Conduit interface, video decoder
// Assuming that we are going to use the onboard video-in port
val IW		= 9; 
val OW		= 15;
val FW		= 17;
val PIXELS	= 1280;

val Video_Decoder_io = new Bundle {
  val reset = UInt(INPUT, 1)
  val TD_CLK27 = UInt(INPUT, 1)
  val TD_DATA = UInt(INPUT, 8)
  val TD_HS = UInt(INPUT, 1)
  val TD_VS = UInt(INPUT, 1)
  val clk27_reset = UInt(INPUT, 1)

  val stream_out_ready = UInt(INPUT, 1)

  // output
  val TD_RESET = UInt(OUTPUT, 1)
  val overflow_flag = UInt(OUTPUT, 1)

  val stream_out_data = UInt(OUTPUT, OW + 1)
  val stream_out_startofpacket = UInt(OUTPUT, 1)
  val stream_out_endofpacket = UInt(OUTPUT, 1)
  val stream_out_empty = UInt(OUTPOUT, 1)
  val stream_out_valid = UInt(OUTPUT, 1)
}