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

module FF(
  input         clock,
  input         reset,
  input  [31:0] io_input_data,
  input  [31:0] io_input_init,
  input         io_input_enable,
  input         io_input_reset,
  output [31:0] io_output_data
);
  reg [31:0] ff;
  reg [31:0] _GEN_0;
  wire [31:0] _T_17;
  wire [31:0] _T_18;
  wire [31:0] _T_19;
  assign io_output_data = _T_19;
  assign _T_17 = io_input_enable ? io_input_data : ff;
  assign _T_18 = io_input_reset ? io_input_init : _T_17;
  assign _T_19 = io_input_reset ? io_input_init : ff;
`ifdef RANDOMIZE
  integer initvar;
  initial begin
    `ifndef verilator
      #0.002 begin end
    `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_0 = {1{$random}};
  ff = _GEN_0[31:0];
  `endif
  end
`endif
  always @(posedge clock) begin
    if (reset) begin
      ff <= io_input_init;
    end else begin
      if (io_input_reset) begin
        ff <= io_input_init;
      end else begin
        if (io_input_enable) begin
          ff <= io_input_data;
        end
      end
    end
  end
endmodule
module SingleCounter(
  input         clock,
  input         reset,
  input  [31:0] io_input_start,
  input  [31:0] io_input_max,
  input  [31:0] io_input_stride,
  input  [31:0] io_input_gap,
  input         io_input_reset,
  input         io_input_enable,
  input         io_input_saturate,
  output [31:0] io_output_count_0,
  output [31:0] io_output_countWithoutWrap_0,
  output        io_output_done,
  output        io_output_extendedDone,
  output        io_output_saturated
);
  wire  FF_clock;
  wire  FF_reset;
  wire [31:0] FF_io_input_data;
  wire [31:0] FF_io_input_init;
  wire  FF_io_input_enable;
  wire  FF_io_input_reset;
  wire [31:0] FF_io_output_data;
  wire  _T_32;
  wire [32:0] _T_34;
  wire [32:0] _GEN_0;
  wire [33:0] _T_35;
  wire [32:0] _T_36;
  wire [32:0] _GEN_1;
  wire [33:0] _T_37;
  wire [32:0] _T_38;
  wire [32:0] _GEN_2;
  wire  _T_39;
  reg  _T_42;
  reg [31:0] _GEN_3;
  reg  _T_45;
  reg [31:0] _GEN_5;
  wire [31:0] _T_46;
  wire [32:0] _T_47;
  wire [32:0] _T_48;
  wire [32:0] _T_50;
  wire [33:0] _T_51;
  wire [32:0] _T_52;
  wire  _T_54;
  wire [31:0] _T_55;
  wire [32:0] _GEN_4;
  wire [33:0] _T_58;
  wire [32:0] _T_59;
  wire  _T_60;
  wire  _T_61;
  wire  _T_62;
  wire  _T_63;
  wire  _T_64;
  FF FF (
    .clock(FF_clock),
    .reset(FF_reset),
    .io_input_data(FF_io_input_data),
    .io_input_init(FF_io_input_init),
    .io_input_enable(FF_io_input_enable),
    .io_input_reset(FF_io_input_reset),
    .io_output_data(FF_io_output_data)
  );
  assign io_output_count_0 = _T_52[31:0];
  assign io_output_countWithoutWrap_0 = _T_59[31:0];
  assign io_output_done = _T_60;
  assign io_output_extendedDone = _T_64;
  assign io_output_saturated = _T_61;
  assign FF_clock = clock;
  assign FF_reset = reset;
  assign FF_io_input_data = _T_48[31:0];
  assign FF_io_input_init = io_input_start;
  assign FF_io_input_enable = _T_32;
  assign FF_io_input_reset = io_input_reset;
  assign _T_32 = io_input_reset | io_input_enable;
  assign _T_34 = io_input_stride * 32'h1;
  assign _GEN_0 = {{1'd0}, FF_io_output_data};
  assign _T_35 = _GEN_0 + _T_34;
  assign _T_36 = _T_35[32:0];
  assign _GEN_1 = {{1'd0}, io_input_gap};
  assign _T_37 = _T_36 + _GEN_1;
  assign _T_38 = _T_37[32:0];
  assign _GEN_2 = {{1'd0}, io_input_max};
  assign _T_39 = _T_38 >= _GEN_2;
  assign _T_46 = io_input_saturate ? FF_io_output_data : io_input_start;
  assign _T_47 = _T_39 ? {{1'd0}, _T_46} : _T_38;
  assign _T_48 = io_input_reset ? {{1'd0}, io_input_start} : _T_47;
  assign _T_50 = 32'h0 * io_input_stride;
  assign _T_51 = _GEN_0 + _T_50;
  assign _T_52 = _T_51[32:0];
  assign _T_54 = FF_io_output_data == 32'h0;
  assign _T_55 = _T_54 ? io_input_max : FF_io_output_data;
  assign _GEN_4 = {{1'd0}, _T_55};
  assign _T_58 = _GEN_4 + _T_50;
  assign _T_59 = _T_58[32:0];
  assign _T_60 = io_input_enable & _T_39;
  assign _T_61 = io_input_saturate & _T_39;
  assign _T_62 = io_input_enable | _T_45;
  assign _T_63 = _T_39 | _T_42;
  assign _T_64 = _T_62 & _T_63;
`ifdef RANDOMIZE
  integer initvar;
  initial begin
    `ifndef verilator
      #0.002 begin end
    `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_3 = {1{$random}};
  _T_42 = _GEN_3[0:0];
  `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_5 = {1{$random}};
  _T_45 = _GEN_5[0:0];
  `endif
  end
`endif
  always @(posedge clock) begin
    if (reset) begin
      _T_42 <= 1'h0;
    end else begin
      _T_42 <= _T_39;
    end
    if (reset) begin
      _T_45 <= 1'h0;
    end else begin
      _T_45 <= io_input_enable;
    end
  end
endmodule
module Counter(
  input         clock,
  input         reset,
  input  [31:0] io_input_starts_0,
  input  [31:0] io_input_starts_1,
  input  [31:0] io_input_starts_2,
  input  [31:0] io_input_maxes_0,
  input  [31:0] io_input_maxes_1,
  input  [31:0] io_input_maxes_2,
  input  [31:0] io_input_strides_0,
  input  [31:0] io_input_strides_1,
  input  [31:0] io_input_strides_2,
  input  [31:0] io_input_gaps_0,
  input  [31:0] io_input_gaps_1,
  input  [31:0] io_input_gaps_2,
  input         io_input_reset,
  input         io_input_enable,
  input         io_input_saturate,
  input         io_input_isStream,
  output [31:0] io_output_counts_0,
  output [31:0] io_output_counts_1,
  output [31:0] io_output_counts_2,
  output        io_output_done,
  output        io_output_extendedDone,
  output        io_output_saturated
);
  wire  ctrs_0_clock;
  wire  ctrs_0_reset;
  wire [31:0] ctrs_0_io_input_start;
  wire [31:0] ctrs_0_io_input_max;
  wire [31:0] ctrs_0_io_input_stride;
  wire [31:0] ctrs_0_io_input_gap;
  wire  ctrs_0_io_input_reset;
  wire  ctrs_0_io_input_enable;
  wire  ctrs_0_io_input_saturate;
  wire [31:0] ctrs_0_io_output_count_0;
  wire [31:0] ctrs_0_io_output_countWithoutWrap_0;
  wire  ctrs_0_io_output_done;
  wire  ctrs_0_io_output_extendedDone;
  wire  ctrs_0_io_output_saturated;
  wire  ctrs_1_clock;
  wire  ctrs_1_reset;
  wire [31:0] ctrs_1_io_input_start;
  wire [31:0] ctrs_1_io_input_max;
  wire [31:0] ctrs_1_io_input_stride;
  wire [31:0] ctrs_1_io_input_gap;
  wire  ctrs_1_io_input_reset;
  wire  ctrs_1_io_input_enable;
  wire  ctrs_1_io_input_saturate;
  wire [31:0] ctrs_1_io_output_count_0;
  wire [31:0] ctrs_1_io_output_countWithoutWrap_0;
  wire  ctrs_1_io_output_done;
  wire  ctrs_1_io_output_extendedDone;
  wire  ctrs_1_io_output_saturated;
  wire  ctrs_2_clock;
  wire  ctrs_2_reset;
  wire [31:0] ctrs_2_io_input_start;
  wire [31:0] ctrs_2_io_input_max;
  wire [31:0] ctrs_2_io_input_stride;
  wire [31:0] ctrs_2_io_input_gap;
  wire  ctrs_2_io_input_reset;
  wire  ctrs_2_io_input_enable;
  wire  ctrs_2_io_input_saturate;
  wire [31:0] ctrs_2_io_output_count_0;
  wire [31:0] ctrs_2_io_output_countWithoutWrap_0;
  wire  ctrs_2_io_output_done;
  wire  ctrs_2_io_output_extendedDone;
  wire  ctrs_2_io_output_saturated;
  wire  _T_41;
  wire  _T_42;
  wire  _T_43;
  wire  _T_44;
  wire  _T_45;
  wire  _T_46;
  wire  isDone;
  reg  wasDone;
  reg [31:0] _GEN_0;
  wire  isSaturated;
  reg  wasWasDone;
  reg [31:0] _GEN_1;
  wire  _T_53;
  wire  _T_54;
  wire  _T_55;
  wire  _T_56;
  wire  _T_57;
  wire  _T_58;
  wire  _T_59;
  wire  _T_60;
  SingleCounter ctrs_0 (
    .clock(ctrs_0_clock),
    .reset(ctrs_0_reset),
    .io_input_start(ctrs_0_io_input_start),
    .io_input_max(ctrs_0_io_input_max),
    .io_input_stride(ctrs_0_io_input_stride),
    .io_input_gap(ctrs_0_io_input_gap),
    .io_input_reset(ctrs_0_io_input_reset),
    .io_input_enable(ctrs_0_io_input_enable),
    .io_input_saturate(ctrs_0_io_input_saturate),
    .io_output_count_0(ctrs_0_io_output_count_0),
    .io_output_countWithoutWrap_0(ctrs_0_io_output_countWithoutWrap_0),
    .io_output_done(ctrs_0_io_output_done),
    .io_output_extendedDone(ctrs_0_io_output_extendedDone),
    .io_output_saturated(ctrs_0_io_output_saturated)
  );
  SingleCounter ctrs_1 (
    .clock(ctrs_1_clock),
    .reset(ctrs_1_reset),
    .io_input_start(ctrs_1_io_input_start),
    .io_input_max(ctrs_1_io_input_max),
    .io_input_stride(ctrs_1_io_input_stride),
    .io_input_gap(ctrs_1_io_input_gap),
    .io_input_reset(ctrs_1_io_input_reset),
    .io_input_enable(ctrs_1_io_input_enable),
    .io_input_saturate(ctrs_1_io_input_saturate),
    .io_output_count_0(ctrs_1_io_output_count_0),
    .io_output_countWithoutWrap_0(ctrs_1_io_output_countWithoutWrap_0),
    .io_output_done(ctrs_1_io_output_done),
    .io_output_extendedDone(ctrs_1_io_output_extendedDone),
    .io_output_saturated(ctrs_1_io_output_saturated)
  );
  SingleCounter ctrs_2 (
    .clock(ctrs_2_clock),
    .reset(ctrs_2_reset),
    .io_input_start(ctrs_2_io_input_start),
    .io_input_max(ctrs_2_io_input_max),
    .io_input_stride(ctrs_2_io_input_stride),
    .io_input_gap(ctrs_2_io_input_gap),
    .io_input_reset(ctrs_2_io_input_reset),
    .io_input_enable(ctrs_2_io_input_enable),
    .io_input_saturate(ctrs_2_io_input_saturate),
    .io_output_count_0(ctrs_2_io_output_count_0),
    .io_output_countWithoutWrap_0(ctrs_2_io_output_countWithoutWrap_0),
    .io_output_done(ctrs_2_io_output_done),
    .io_output_extendedDone(ctrs_2_io_output_extendedDone),
    .io_output_saturated(ctrs_2_io_output_saturated)
  );
  assign io_output_counts_0 = ctrs_0_io_output_count_0;
  assign io_output_counts_1 = ctrs_1_io_output_count_0;
  assign io_output_counts_2 = ctrs_2_io_output_count_0;
  assign io_output_done = _T_56;
  assign io_output_extendedDone = _T_59;
  assign io_output_saturated = _T_60;
  assign ctrs_0_clock = clock;
  assign ctrs_0_reset = reset;
  assign ctrs_0_io_input_start = io_input_starts_0;
  assign ctrs_0_io_input_max = io_input_maxes_0;
  assign ctrs_0_io_input_stride = io_input_strides_0;
  assign ctrs_0_io_input_gap = 32'h0;
  assign ctrs_0_io_input_reset = io_input_reset;
  assign ctrs_0_io_input_enable = _T_41;
  assign ctrs_0_io_input_saturate = io_input_saturate;
  assign ctrs_1_clock = clock;
  assign ctrs_1_reset = reset;
  assign ctrs_1_io_input_start = io_input_starts_1;
  assign ctrs_1_io_input_max = io_input_maxes_1;
  assign ctrs_1_io_input_stride = io_input_strides_1;
  assign ctrs_1_io_input_gap = 32'h0;
  assign ctrs_1_io_input_reset = io_input_reset;
  assign ctrs_1_io_input_enable = _T_42;
  assign ctrs_1_io_input_saturate = _T_43;
  assign ctrs_2_clock = clock;
  assign ctrs_2_reset = reset;
  assign ctrs_2_io_input_start = io_input_starts_2;
  assign ctrs_2_io_input_max = io_input_maxes_2;
  assign ctrs_2_io_input_stride = io_input_strides_2;
  assign ctrs_2_io_input_gap = 32'h0;
  assign ctrs_2_io_input_reset = io_input_reset;
  assign ctrs_2_io_input_enable = io_input_enable;
  assign ctrs_2_io_input_saturate = _T_45;
  assign _T_41 = ctrs_1_io_output_done & io_input_enable;
  assign _T_42 = ctrs_2_io_output_done & io_input_enable;
  assign _T_43 = io_input_saturate & ctrs_0_io_output_saturated;
  assign _T_44 = ctrs_0_io_output_saturated & ctrs_1_io_output_saturated;
  assign _T_45 = io_input_saturate & _T_44;
  assign _T_46 = ctrs_0_io_output_done & ctrs_1_io_output_done;
  assign isDone = _T_46 & ctrs_2_io_output_done;
  assign isSaturated = _T_44 & ctrs_2_io_output_saturated;
  assign _T_53 = io_input_isStream ? 1'h1 : io_input_enable;
  assign _T_54 = _T_53 & isDone;
  assign _T_55 = ~ wasDone;
  assign _T_56 = _T_54 & _T_55;
  assign _T_57 = io_input_enable & isDone;
  assign _T_58 = ~ wasWasDone;
  assign _T_59 = _T_57 & _T_58;
  assign _T_60 = io_input_saturate & isSaturated;
`ifdef RANDOMIZE
  integer initvar;
  initial begin
    `ifndef verilator
      #0.002 begin end
    `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_0 = {1{$random}};
  wasDone = _GEN_0[0:0];
  `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_1 = {1{$random}};
  wasWasDone = _GEN_1[0:0];
  `endif
  end
`endif
  always @(posedge clock) begin
    if (reset) begin
      wasDone <= 1'h0;
    end else begin
      wasDone <= isDone;
    end
    if (reset) begin
      wasWasDone <= 1'h0;
    end else begin
      wasWasDone <= wasDone;
    end
  end
endmodule
