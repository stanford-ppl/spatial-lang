module Computer_System_Expansion_JP1_tb;
	reg clk, reset_n, chipselect, write_n;
  reg   [ 1:0]  address;
  reg   [31:0]  writedata;
  wire  [31:0]  bidir_port;
  wire          irq;
  wire  [31:0]  readdata;

  Computer_System_Expansion_JP1 jp1 (
      // inputs:
      .address                (address),
      .chipselect             (chipselect),
      .clk                    (clk),
      .reset_n                (reset_n),
      .write_n                (write_n),
      .writedata              (writedata),

      // outputs:
      .bidir_port             (bidir_port),
      .irq                    (irq),
      .readdata               (readdata)
  );

	initial
	begin
		$dumpfile("test.vcd");
		$dumpvars(0, jp1);
		clk = 0;
		// reset. write is flipped... 
		chipselect = 1;
    write_n = 0;
		reset_n = 1;
		#10
		reset_n = 0;
    #10
    reset_n = 1;

    #50
    address = 1;
    writedata = 32'hffffffff; 
    
    #10
    address = 0;
    writedata = 32'd1023;

    #30
    address = 0;
    writedata = 32'd1123;

    #30
    address = 0;
    writedata = 32'd1120;

    #200



		$finish;
	end
	always
		#5 clk = !clk;

endmodule
