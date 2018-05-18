module designware_mult (clock, reset, in0, in1, out);

parameter IN0_BIT_WIDTH = 16;
parameter IN1_BIT_WIDTH = 16;
parameter OUT_BIT_WIDTH = 16;
parameter FRAC_BIT_WIDTH = 0; //denotes how many bits are in the deicmal part of the number format (used for fixed point computations)
                              //assumes in0, in1, and out all use the same number of bits in the decimal part of the number
parameter SIGNED = 0;         //0 means unsigned, 1 means two's complement signed numbers; if this is not a 0 or 1, strange things could happen
parameter LATENCY = 0;	      //number of pipeline stages is latency + 1

input clock, reset;           //note reset is active low
input [IN0_BIT_WIDTH-1:0] in0;
input [IN1_BIT_WIDTH-1:0] in1;
output [OUT_BIT_WIDTH-1:0] out;

wire [IN0_BIT_WIDTH + IN1_BIT_WIDTH - 1:0] product;

assign out[OUT_BIT_WIDTH - 1:0] = product[OUT_BIT_WIDTH + FRAC_BIT_WIDTH - 1:FRAC_BIT_WIDTH + 0];

generate

  if (LATENCY == 0) begin
    DW02_mult #(
      .A_width(IN0_BIT_WIDTH),
      .B_width(IN1_BIT_WIDTH)
    ) mult_comb_inst (
        .A(in0),
        .B(in1),
        .TC(SIGNED[0]),
        .PRODUCT(product)
    );
  end else begin
    DW_mult_pipe #( //assumes asynchronous reset
      .a_width(IN0_BIT_WIDTH),
      .b_width(IN1_BIT_WIDTH),
      .num_stages(LATENCY + 1),
      .stall_mode(1'b0),
      .rst_mode(1)
    ) mult_pipe_inst (
        .clk(clock),
        .rst_n(!reset),
        .en(1'b1),
        .tc(SIGNED[0]),
        .a(in0),
        .b(in1),
        .product(product)
    );
  end

endgenerate

endmodule
