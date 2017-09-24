module Top_tb;
	reg           clk; 
  reg           reset_n;
	reg   [15:0]  addr;
	wire  [31:0]  readdata;
	reg   [31:0]  writedata;
	reg           write_n;
	reg           chipselect;
	// for streaming
	reg   [23:0] 	in_data;
	reg 		      in_startofpacket;
	reg 		      in_endofpacket;
	reg   [1:0] 	in_empty;
	reg 		      in_valid;
	reg 		      out_ready;
	wire 		      in_ready;
	wire  [15:0]	out_data;
	wire		      out_startofpacket;
	wire		      out_endofpacket;	
	wire		      out_empty;
	wire		      out_valid;
  wire  [3:0]   out_LEDR_STREAM_address;
  wire          out_LEDR_STREAM_chipselect;
  wire  [31:0]  out_LEDR_STREAM_writedata;
  wire          out_LEDR_STREAM_write_n;
  wire  [31:0]  out_SWITCHES_STREAM_address;
  reg   [31:0]  in_SWITCHES_STREAM_readdata;
  wire          out_SWITCHES_STREAM_read;
  reg           in_BUFFOUT_waitrequest;
  wire  [31:0]  out_BUFFOUT_address;
  wire          out_BUFFOUT_write;
  wire  [15:0]  out_BUFFOUT_writedata;

	Top top0 (
		.clock                  		              (clk),                    	    //   clock_sink.clk
		.reset                  		              (reset_n),			                //   reset_sink.reset
		.io_S_AVALON_address    		              (addr),       			            //   avalon_slave.address
		.io_S_AVALON_readdata   		              (readdata),      		            //  .readdata
		.io_S_AVALON_writedata  		              (writedata),     		            //  .writedata
		.io_S_AVALON_write_n    		              (write_n),       		            //  .write_n
		.io_S_AVALON_chipselect 		              (chipselect),     		          //  .chipselect
		.io_S_STREAM_stream_in_data		            (in_data),		  	              //  input	
		.io_S_STREAM_stream_in_startofpacket   	  (in_startofpacket), 		        //  input	
		.io_S_STREAM_stream_in_endofpacket	      (in_endofpacket),		            //  input
		.io_S_STREAM_stream_in_empty		          (in_empty),			                //  input
		.io_S_STREAM_stream_in_valid		          (in_valid),			                //  input
		.io_S_STREAM_stream_out_ready		          (out_ready),			              //  input
		.io_S_STREAM_stream_in_ready		          (in_ready),			                //  output
		.io_S_STREAM_stream_out_data		          (out_data),			                //  output
		.io_S_STREAM_stream_out_startofpacket	    (out_startofpacket),		        //  output
		.io_S_STREAM_stream_out_endofpacket	      (out_endofpacket),		          //  output
		.io_S_STREAM_stream_out_empty		          (out_empty),			              //  output
		.io_S_STREAM_stream_out_valid		          (out_valid),			              //  output
    .io_LEDR_STREAM_address                   (out_LEDR_STREAM_address),      //  output
    .io_LEDR_STREAM_chipselect                (out_LEDR_STREAM_chipselect),   //  output
    .io_LEDR_STREAM_writedata                 (out_LEDR_STREAM_writedata),    //  output
    .io_LEDR_STREAM_write_n                   (out_LEDR_STREAM_write_n),      //  output
    .io_SWITCHES_STREAM_address               (out_SWITCHES_STREAM_address),  //  input
    .io_SWITCHES_STREAM_readdata              (in_SWITCHES_STREAM_readdata),  //  input
    .io_SWITCHES_STREAM_read                  (out_SWITCHES_STREAM_read),     //  output
    .io_BUFFOUT_waitrequest                   (in_BUFFOUT_waitrequest),       //  input
    .io_BUFFOUT_address                       (out_BUFFOUT_address),          //  output
    .io_BUFFOUT_write                         (out_BUFFOUT_write),            //  output
    .io_BUFFOUT_writedata                     (out_BUFFOUT_writedata)         //  output
	);

	initial
	begin
		$dumpfile("test.vcd");
		$dumpvars(0, top0);
		clk = 0;
		chipselect = 1;
		reset_n = 0;
		write_n = 1;
    in_data = 24'h1;
    in_startofpacket = 0;
    in_endofpacket = 0;
    in_empty = 0;
    in_valid = 0;
    out_ready = 1;
    in_SWITCHES_STREAM_readdata = 32'h100;
    addr = 16'h0;
    in_BUFFOUT_waitrequest = 1'b1;

		#10
		reset_n = 1;
		#10
		reset_n = 0;

		// start a write to the command register
		// prepare data for streaming
		in_data = 24'h1;
		in_startofpacket = 1'b1;
		in_endofpacket = 1'b0;
		in_empty  = 2'b0;
		in_valid = 1'b1;
		out_ready = 1'b1;
	
		addr = 16'h2;
		writedata = 32'd128;
		write_n = 1;
		#10
		write_n = 0;
		#10
		write_n = 1;

		addr = 16'h3;
		writedata = 32'd128;
		write_n = 1;
		#10
		write_n = 0;
		#10
		write_n = 1;

		addr = 16'h0;
		writedata = 32'h1;
		write_n = 1;
		#10
		write_n = 0;
		#10
		write_n = 1;

    #10
    in_startofpacket = 1'b0;

    #8000
    in_endofpacket = 1'b1;
    in_valid = 1'b0;

    #10
    in_endofpacket = 1'b0;

    
    #80000
		$finish;
	end
	always
		#5 clk = !clk;

endmodule
