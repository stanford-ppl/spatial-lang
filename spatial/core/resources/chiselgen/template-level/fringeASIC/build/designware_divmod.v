module designware_divmod(clock, reset, dividend, divisor, quot_out, rem_out);

parameter DIVIDEND_BIT_WIDTH = 16;
parameter DIVISOR_BIT_WIDTH = 16;
parameter SIGNED = 0;               // 0 means unsigned, 1 means two's complement signed numbers
parameter LATENCY = 1;              //number of pipeline stages is latency + 1

input clock, reset;                 //note reset is active low
input [DIVIDEND_BIT_WIDTH-1:0] dividend;
input [DIVISOR_BIT_WIDTH-1:0] divisor;
output [DIVIDEND_BIT_WIDTH-1:0] quot_out;
output [DIVISOR_BIT_WIDTH-1:0] rem_out;

wire divide_by_0;                   //left floating intentionally (assumes we don't need this signal)

generate

  if (LATENCY == 0) begin
    DW_div #(
      .a_width(DIVIDEND_BIT_WIDTH),
      .b_width(DIVISOR_BIT_WIDTH),
      .tc_mode(SIGNED),
      .rem_mode(1'b1)
    ) div_comb_inst (
        .a(dividend),
        .b(divisor),
        .quotient(quot_out),
        .remainder(rem_out),
        .divide_by_0(divide_by_0)
    );
  end else begin
    DW_div_pipe #(                  //computes dividend / divisor, assumes asynchronous reset
      .a_width(DIVIDEND_BIT_WIDTH),
      .b_width(DIVISOR_BIT_WIDTH),
      .tc_mode(SIGNED),
      .rem_mode(1'b1),              //remainder port outputs the remainder (not modulus)
      .num_stages(LATENCY + 1),
      .stall_mode(1'b0),
      .rst_mode(1)
    ) div_pipe_inst (
        .clk(clock),
        .rst_n(!reset),
        .en(1'b1),
        .a(dividend),
        .b(divisor),
        .quotient(quot_out),
        .remainder(rem_out),
        .divide_by_0(divide_by_0)
    );
  end

endgenerate

endmodule
