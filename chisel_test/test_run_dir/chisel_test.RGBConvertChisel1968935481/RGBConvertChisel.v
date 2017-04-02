`ifdef RANDOMIZE_GARBAGE_ASSIGN
`define RANDOMIZE
`endif
`ifdef RANDOMIZE_INVALID_ASSIGN
`define RANDOMIZE
`endif
`ifdef RANDOMIZE_REG_INIT
`define RANDOMIZE
`endif
`ifdef RANDOMIZE_MEM_INIT
`define RANDOMIZE
`endif

module RGBConvertChisel(
  input         clock,
  input         reset,
  input  [23:0] io_stream_in_data,
  input         io_stream_in_startofpacket,
  input         io_stream_in_endofpacket,
  input  [1:0]  io_stream_in_empty,
  input         io_stream_in_valid,
  input         io_stream_out_ready,
  output        io_stream_in_ready,
  output [15:0] io_stream_out_data,
  output        io_stream_out_startofpacket,
  output        io_stream_out_endofpacket,
  output        io_stream_out_empty,
  output        io_stream_out_valid
);
  wire  _T_26;
  wire  _T_27;
  wire [7:0] _T_28;
  wire [1:0] _T_29;
  wire [9:0] r;
  wire [7:0] _T_30;
  wire [1:0] _T_31;
  wire [9:0] g;
  wire [7:0] _T_32;
  wire [1:0] _T_33;
  wire [9:0] b;
  wire [4:0] _T_34;
  wire [5:0] _T_35;
  wire [4:0] _T_36;
  wire [10:0] _T_37;
  wire [15:0] converted_data;
  wire  _T_46;
  wire  _T_47;
  wire [15:0] _GEN_1;
  wire  _GEN_2;
  wire  _GEN_3;
  wire [1:0] _GEN_4;
  wire  _GEN_5;
  assign io_stream_in_ready = _T_27;
  assign io_stream_out_data = _GEN_1;
  assign io_stream_out_startofpacket = _GEN_2;
  assign io_stream_out_endofpacket = _GEN_3;
  assign io_stream_out_empty = _GEN_4[0];
  assign io_stream_out_valid = _GEN_5;
  assign _T_26 = ~ io_stream_out_valid;
  assign _T_27 = io_stream_out_ready | _T_26;
  assign _T_28 = io_stream_in_data[23:16];
  assign _T_29 = io_stream_in_data[23:22];
  assign r = {_T_28,_T_29};
  assign _T_30 = io_stream_in_data[15:8];
  assign _T_31 = io_stream_in_data[15:14];
  assign g = {_T_30,_T_31};
  assign _T_32 = io_stream_in_data[7:0];
  assign _T_33 = io_stream_in_data[7:6];
  assign b = {_T_32,_T_33};
  assign _T_34 = r[9:5];
  assign _T_35 = g[9:4];
  assign _T_36 = b[9:5];
  assign _T_37 = {_T_34,_T_35};
  assign converted_data = {_T_37,_T_36};
  assign _T_46 = reset == 1'h0;
  assign _T_47 = _T_46 & _T_27;
  assign _GEN_1 = _T_47 ? converted_data : 16'h0;
  assign _GEN_2 = _T_47 ? io_stream_in_startofpacket : 1'h0;
  assign _GEN_3 = _T_47 ? io_stream_in_endofpacket : 1'h0;
  assign _GEN_4 = _T_47 ? io_stream_in_empty : 2'h0;
  assign _GEN_5 = _T_47 ? io_stream_in_valid : 1'h0;
endmodule
