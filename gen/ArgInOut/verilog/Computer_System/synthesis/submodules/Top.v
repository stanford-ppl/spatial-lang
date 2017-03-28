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
  reg [31:0] _GEN_5;
  reg  _T_45;
  reg [31:0] _GEN_6;
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
  reg  _GEN_3;
  reg [31:0] _GEN_7;
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
  assign FF_io_input_reset = _GEN_3;
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
  _GEN_5 = {1{$random}};
  _T_42 = _GEN_5[0:0];
  `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_6 = {1{$random}};
  _T_45 = _GEN_6[0:0];
  `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_7 = {1{$random}};
  _GEN_3 = _GEN_7[0:0];
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
module Innerpipe(
  input         clock,
  input         reset,
  input         io_input_enable,
  input         io_input_ctr_done,
  input  [31:0] io_input_ctr_maxIn_0,
  input         io_input_forever,
  input  [31:0] io_input_nextState,
  input  [31:0] io_input_initState,
  input         io_input_doneCondition,
  output        io_output_done,
  output        io_output_ctr_en,
  output        io_output_ctr_inc,
  output        io_output_rst_en,
  output [31:0] io_output_ctr_maxOut_0,
  output [31:0] io_output_state
);
  reg [2:0] _T_36;
  reg [31:0] _GEN_19;
  reg [31:0] _T_39;
  reg [31:0] _GEN_20;
  wire  SingleCounter_clock;
  wire  SingleCounter_reset;
  wire [31:0] SingleCounter_io_input_start;
  wire [31:0] SingleCounter_io_input_max;
  wire [31:0] SingleCounter_io_input_stride;
  wire [31:0] SingleCounter_io_input_gap;
  wire  SingleCounter_io_input_reset;
  wire  SingleCounter_io_input_enable;
  wire  SingleCounter_io_input_saturate;
  wire [31:0] SingleCounter_io_output_count_0;
  wire [31:0] SingleCounter_io_output_countWithoutWrap_0;
  wire  SingleCounter_io_output_done;
  wire  SingleCounter_io_output_extendedDone;
  wire  SingleCounter_io_output_saturated;
  wire  _T_41;
  wire  _T_43;
  wire  _T_48;
  wire [31:0] _GEN_1;
  wire [2:0] _GEN_2;
  wire  _T_57;
  wire  _T_58;
  wire [1:0] _T_62;
  wire [1:0] _T_66;
  wire  _GEN_3;
  wire [1:0] _GEN_4;
  wire  _GEN_5;
  wire [2:0] _GEN_7;
  wire  _T_68;
  wire  _T_72;
  wire  _T_73;
  wire  _T_74;
  wire  _GEN_8;
  wire [31:0] _GEN_9;
  wire [2:0] _GEN_10;
  wire  _T_81;
  wire [2:0] _GEN_11;
  wire  _GEN_13;
  wire [31:0] _GEN_14;
  wire [2:0] _GEN_15;
  wire  _T_84;
  wire  _T_91;
  wire  _T_92;
  wire  _T_93;
  wire  _T_96;
  wire  _GEN_16;
  wire [2:0] _GEN_17;
  wire  _T_99;
  wire  _T_109;
  wire  _T_110;
  wire  _T_111;
  wire [2:0] _GEN_18;
  wire [31:0] _GEN_23;
  wire [2:0] _GEN_24;
  wire  _T_114;
  wire  _GEN_26;
  wire  _GEN_27;
  wire  _GEN_28;
  wire  _GEN_29;
  wire [2:0] _GEN_30;
  reg [31:0] _GEN_0;
  reg [31:0] _GEN_21;
  reg [31:0] _GEN_6;
  reg [31:0] _GEN_22;
  reg [31:0] _GEN_12;
  reg [31:0] _GEN_25;
  SingleCounter SingleCounter (
    .clock(SingleCounter_clock),
    .reset(SingleCounter_reset),
    .io_input_start(SingleCounter_io_input_start),
    .io_input_max(SingleCounter_io_input_max),
    .io_input_stride(SingleCounter_io_input_stride),
    .io_input_gap(SingleCounter_io_input_gap),
    .io_input_reset(SingleCounter_io_input_reset),
    .io_input_enable(SingleCounter_io_input_enable),
    .io_input_saturate(SingleCounter_io_input_saturate),
    .io_output_count_0(SingleCounter_io_output_count_0),
    .io_output_countWithoutWrap_0(SingleCounter_io_output_countWithoutWrap_0),
    .io_output_done(SingleCounter_io_output_done),
    .io_output_extendedDone(SingleCounter_io_output_extendedDone),
    .io_output_saturated(SingleCounter_io_output_saturated)
  );
  assign io_output_done = _GEN_26;
  assign io_output_ctr_en = _GEN_27;
  assign io_output_ctr_inc = _GEN_28;
  assign io_output_rst_en = _GEN_29;
  assign io_output_ctr_maxOut_0 = _T_39;
  assign io_output_state = _GEN_0;
  assign SingleCounter_clock = clock;
  assign SingleCounter_reset = reset;
  assign SingleCounter_io_input_start = _GEN_6;
  assign SingleCounter_io_input_max = 32'ha;
  assign SingleCounter_io_input_stride = 32'h1;
  assign SingleCounter_io_input_gap = _GEN_12;
  assign SingleCounter_io_input_reset = _T_43;
  assign SingleCounter_io_input_enable = _T_41;
  assign SingleCounter_io_input_saturate = 1'h1;
  assign _T_41 = _T_36 == 3'h1;
  assign _T_43 = _T_36 != 3'h1;
  assign _T_48 = _T_36 == 3'h0;
  assign _GEN_1 = _T_48 ? io_input_ctr_maxIn_0 : _T_39;
  assign _GEN_2 = _T_48 ? 3'h1 : _T_36;
  assign _T_57 = _T_48 == 1'h0;
  assign _T_58 = _T_57 & _T_41;
  assign _T_62 = io_input_ctr_done ? 2'h3 : 2'h1;
  assign _T_66 = io_input_ctr_done ? 2'h3 : 2'h2;
  assign _GEN_3 = SingleCounter_io_output_done ? 1'h0 : 1'h1;
  assign _GEN_4 = SingleCounter_io_output_done ? _T_66 : _T_62;
  assign _GEN_5 = _T_58 ? _GEN_3 : 1'h0;
  assign _GEN_7 = _T_58 ? {{1'd0}, _GEN_4} : _GEN_2;
  assign _T_68 = _T_36 == 3'h2;
  assign _T_72 = _T_41 == 1'h0;
  assign _T_73 = _T_57 & _T_72;
  assign _T_74 = _T_73 & _T_68;
  assign _GEN_8 = io_input_ctr_done ? 1'h0 : 1'h1;
  assign _GEN_9 = io_input_ctr_done ? 32'h0 : _GEN_1;
  assign _GEN_10 = io_input_ctr_done ? 3'h3 : _GEN_7;
  assign _T_81 = io_input_ctr_done == 1'h0;
  assign _GEN_11 = _T_81 ? 3'h2 : _GEN_10;
  assign _GEN_13 = _T_74 ? _GEN_8 : 1'h0;
  assign _GEN_14 = _T_74 ? _GEN_9 : _GEN_1;
  assign _GEN_15 = _T_74 ? _GEN_11 : _GEN_7;
  assign _T_84 = _T_36 == 3'h3;
  assign _T_91 = _T_68 == 1'h0;
  assign _T_92 = _T_73 & _T_91;
  assign _T_93 = _T_92 & _T_84;
  assign _T_96 = io_input_forever ? 1'h0 : 1'h1;
  assign _GEN_16 = _T_93 ? _T_96 : 1'h0;
  assign _GEN_17 = _T_93 ? 3'h1 : _GEN_15;
  assign _T_99 = _T_36 == 3'h4;
  assign _T_109 = _T_84 == 1'h0;
  assign _T_110 = _T_92 & _T_109;
  assign _T_111 = _T_110 & _T_99;
  assign _GEN_18 = _T_111 ? 3'h4 : _GEN_17;
  assign _GEN_23 = io_input_enable ? _GEN_14 : _T_39;
  assign _GEN_24 = io_input_enable ? _GEN_18 : _T_36;
  assign _T_114 = io_input_enable == 1'h0;
  assign _GEN_26 = _T_114 ? io_input_ctr_done : _GEN_16;
  assign _GEN_27 = _T_114 ? 1'h0 : _T_74;
  assign _GEN_28 = _T_114 ? 1'h0 : _GEN_13;
  assign _GEN_29 = _T_114 ? 1'h0 : _GEN_5;
  assign _GEN_30 = _T_114 ? 3'h0 : _GEN_24;
`ifdef RANDOMIZE
  integer initvar;
  initial begin
    `ifndef verilator
      #0.002 begin end
    `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_19 = {1{$random}};
  _T_36 = _GEN_19[2:0];
  `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_20 = {1{$random}};
  _T_39 = _GEN_20[31:0];
  `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_21 = {1{$random}};
  _GEN_0 = _GEN_21[31:0];
  `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_22 = {1{$random}};
  _GEN_6 = _GEN_22[31:0];
  `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_25 = {1{$random}};
  _GEN_12 = _GEN_25[31:0];
  `endif
  end
`endif
  always @(posedge clock) begin
    if (reset) begin
      _T_36 <= 3'h0;
    end else begin
      if (_T_114) begin
        _T_36 <= 3'h0;
      end else begin
        if (io_input_enable) begin
          if (_T_111) begin
            _T_36 <= 3'h4;
          end else begin
            if (_T_93) begin
              _T_36 <= 3'h1;
            end else begin
              if (_T_74) begin
                if (_T_81) begin
                  _T_36 <= 3'h2;
                end else begin
                  if (io_input_ctr_done) begin
                    _T_36 <= 3'h3;
                  end else begin
                    if (_T_58) begin
                      _T_36 <= {{1'd0}, _GEN_4};
                    end else begin
                      if (_T_48) begin
                        _T_36 <= 3'h1;
                      end
                    end
                  end
                end
              end else begin
                if (_T_58) begin
                  _T_36 <= {{1'd0}, _GEN_4};
                end else begin
                  if (_T_48) begin
                    _T_36 <= 3'h1;
                  end
                end
              end
            end
          end
        end
      end
    end
    if (reset) begin
      _T_39 <= 32'h0;
    end else begin
      if (io_input_enable) begin
        if (_T_74) begin
          if (io_input_ctr_done) begin
            _T_39 <= 32'h0;
          end else begin
            if (_T_48) begin
              _T_39 <= io_input_ctr_maxIn_0;
            end
          end
        end else begin
          if (_T_48) begin
            _T_39 <= io_input_ctr_maxIn_0;
          end
        end
      end
    end
  end
endmodule
module SRFF(
  input   clock,
  input   reset,
  input   io_input_set,
  input   io_input_reset,
  input   io_input_asyn_reset,
  output  io_output_data
);
  reg  _T_14;
  reg [31:0] _GEN_0;
  wire  _T_18;
  wire  _T_19;
  wire  _T_20;
  wire  _T_22;
  assign io_output_data = _T_22;
  assign _T_18 = io_input_reset ? 1'h0 : _T_14;
  assign _T_19 = io_input_set ? 1'h1 : _T_18;
  assign _T_20 = io_input_asyn_reset ? 1'h0 : _T_19;
  assign _T_22 = io_input_asyn_reset ? 1'h0 : _T_14;
`ifdef RANDOMIZE
  integer initvar;
  initial begin
    `ifndef verilator
      #0.002 begin end
    `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_0 = {1{$random}};
  _T_14 = _GEN_0[0:0];
  `endif
  end
`endif
  always @(posedge clock) begin
    if (reset) begin
      _T_14 <= 1'h0;
    end else begin
      if (io_input_asyn_reset) begin
        _T_14 <= 1'h0;
      end else begin
        if (io_input_set) begin
          _T_14 <= 1'h1;
        end else begin
          if (io_input_reset) begin
            _T_14 <= 1'h0;
          end
        end
      end
    end
  end
endmodule
module AccelTop(
  input         clock,
  input         reset,
  input         io_enable,
  output        io_done,
  input  [31:0] io_argIns_0,
  input         io_argOuts_0_ready,
  output        io_argOuts_0_valid,
  output [31:0] io_argOuts_0_bits
);
  wire  AccelController_done;
  wire [31:0] x153_readx150_number;
  wire  x153_readx150_debug_overflow;
  wire  _T_724;
  wire  AccelController_en;
  wire  AccelController_sm_clock;
  wire  AccelController_sm_reset;
  wire  AccelController_sm_io_input_enable;
  wire  AccelController_sm_io_input_ctr_done;
  wire [31:0] AccelController_sm_io_input_ctr_maxIn_0;
  wire  AccelController_sm_io_input_forever;
  wire [31:0] AccelController_sm_io_input_nextState;
  wire [31:0] AccelController_sm_io_input_initState;
  wire  AccelController_sm_io_input_doneCondition;
  wire  AccelController_sm_io_output_done;
  wire  AccelController_sm_io_output_ctr_en;
  wire  AccelController_sm_io_output_ctr_inc;
  wire  AccelController_sm_io_output_rst_en;
  wire [31:0] AccelController_sm_io_output_ctr_maxOut_0;
  wire [31:0] AccelController_sm_io_output_state;
  reg  _T_727;
  reg [31:0] _GEN_8;
  wire  _T_730;
  wire  done_latch_clock;
  wire  done_latch_reset;
  wire  done_latch_io_input_set;
  wire  done_latch_io_input_reset;
  wire  done_latch_io_input_asyn_reset;
  wire  done_latch_io_output_data;
  wire [32:0] _T_738_number;
  wire  _T_738_debug_overflow;
  wire [32:0] _T_741;
  wire [31:0] _T_742;
  wire [31:0] x154_sumx153_unk_number;
  wire  x154_sumx153_unk_debug_overflow;
  wire [31:0] _T_749;
  wire  _T_751;
  wire [31:0] _T_752;
  reg [31:0] x151_argout;
  reg [31:0] _GEN_9;
  wire [31:0] _T_759;
  reg  _GEN_0;
  reg [31:0] _GEN_10;
  reg [31:0] _GEN_1;
  reg [31:0] _GEN_11;
  reg [31:0] _GEN_2;
  reg [31:0] _GEN_12;
  reg [31:0] _GEN_3;
  reg [31:0] _GEN_13;
  reg  _GEN_4;
  reg [31:0] _GEN_14;
  reg  _GEN_5;
  reg [31:0] _GEN_15;
  reg  _GEN_6;
  reg [31:0] _GEN_16;
  reg [31:0] _GEN_7;
  reg [31:0] _GEN_17;
  Innerpipe AccelController_sm (
    .clock(AccelController_sm_clock),
    .reset(AccelController_sm_reset),
    .io_input_enable(AccelController_sm_io_input_enable),
    .io_input_ctr_done(AccelController_sm_io_input_ctr_done),
    .io_input_ctr_maxIn_0(AccelController_sm_io_input_ctr_maxIn_0),
    .io_input_forever(AccelController_sm_io_input_forever),
    .io_input_nextState(AccelController_sm_io_input_nextState),
    .io_input_initState(AccelController_sm_io_input_initState),
    .io_input_doneCondition(AccelController_sm_io_input_doneCondition),
    .io_output_done(AccelController_sm_io_output_done),
    .io_output_ctr_en(AccelController_sm_io_output_ctr_en),
    .io_output_ctr_inc(AccelController_sm_io_output_ctr_inc),
    .io_output_rst_en(AccelController_sm_io_output_rst_en),
    .io_output_ctr_maxOut_0(AccelController_sm_io_output_ctr_maxOut_0),
    .io_output_state(AccelController_sm_io_output_state)
  );
  SRFF done_latch (
    .clock(done_latch_clock),
    .reset(done_latch_reset),
    .io_input_set(done_latch_io_input_set),
    .io_input_reset(done_latch_io_input_reset),
    .io_input_asyn_reset(done_latch_io_input_asyn_reset),
    .io_output_data(done_latch_io_output_data)
  );
  assign io_done = done_latch_io_output_data;
  assign io_argOuts_0_valid = AccelController_en;
  assign io_argOuts_0_bits = x151_argout;
  assign AccelController_done = AccelController_sm_io_output_done;
  assign x153_readx150_number = io_argIns_0;
  assign x153_readx150_debug_overflow = _GEN_0;
  assign _T_724 = io_done == 1'h0;
  assign AccelController_en = io_enable & _T_724;
  assign AccelController_sm_clock = clock;
  assign AccelController_sm_reset = reset;
  assign AccelController_sm_io_input_enable = AccelController_en;
  assign AccelController_sm_io_input_ctr_done = _T_727;
  assign AccelController_sm_io_input_ctr_maxIn_0 = _GEN_1;
  assign AccelController_sm_io_input_forever = 1'h0;
  assign AccelController_sm_io_input_nextState = _GEN_2;
  assign AccelController_sm_io_input_initState = _GEN_3;
  assign AccelController_sm_io_input_doneCondition = _GEN_4;
  assign _T_730 = AccelController_sm_io_output_ctr_en;
  assign done_latch_clock = clock;
  assign done_latch_reset = reset;
  assign done_latch_io_input_set = AccelController_sm_io_output_done;
  assign done_latch_io_input_reset = 1'h0;
  assign done_latch_io_input_asyn_reset = _GEN_5;
  assign _T_738_number = {{1'd0}, _T_742};
  assign _T_738_debug_overflow = _GEN_6;
  assign _T_741 = x153_readx150_number + 32'h4;
  assign _T_742 = _T_741[31:0];
  assign x154_sumx153_unk_number = _T_752;
  assign x154_sumx153_unk_debug_overflow = _T_751;
  assign _T_749 = _GEN_7;
  assign _T_751 = _T_738_number[32];
  assign _T_752 = _T_738_number[31:0];
  assign _T_759 = AccelController_en ? x154_sumx153_unk_number : x151_argout;
`ifdef RANDOMIZE
  integer initvar;
  initial begin
    `ifndef verilator
      #0.002 begin end
    `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_8 = {1{$random}};
  _T_727 = _GEN_8[0:0];
  `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_9 = {1{$random}};
  x151_argout = _GEN_9[31:0];
  `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_10 = {1{$random}};
  _GEN_0 = _GEN_10[0:0];
  `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_11 = {1{$random}};
  _GEN_1 = _GEN_11[31:0];
  `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_12 = {1{$random}};
  _GEN_2 = _GEN_12[31:0];
  `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_13 = {1{$random}};
  _GEN_3 = _GEN_13[31:0];
  `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_14 = {1{$random}};
  _GEN_4 = _GEN_14[0:0];
  `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_15 = {1{$random}};
  _GEN_5 = _GEN_15[0:0];
  `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_16 = {1{$random}};
  _GEN_6 = _GEN_16[0:0];
  `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_17 = {1{$random}};
  _GEN_7 = _GEN_17[31:0];
  `endif
  end
`endif
  always @(posedge clock) begin
    if (reset) begin
      _T_727 <= 1'h0;
    end else begin
      _T_727 <= _T_730;
    end
    if (reset) begin
      x151_argout <= 32'h0;
    end else begin
      if (AccelController_en) begin
        x151_argout <= x154_sumx153_unk_number;
      end
    end
  end
endmodule
module FF_1(
  input         clock,
  input         reset,
  input  [31:0] io_in,
  input  [31:0] io_init,
  output [31:0] io_out,
  input         io_enable
);
  wire [31:0] d;
  reg [31:0] ff;
  reg [31:0] _GEN_0;
  wire  _T_13;
  wire [31:0] _GEN_1;
  assign io_out = ff;
  assign d = _GEN_1;
  assign _T_13 = io_enable == 1'h0;
  assign _GEN_1 = _T_13 ? ff : io_in;
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
      ff <= io_init;
    end else begin
      ff <= d;
    end
  end
endmodule
module MuxN(
  input         clock,
  input         reset,
  input  [31:0] io_ins_0,
  input  [31:0] io_ins_1,
  input  [31:0] io_ins_2,
  input  [31:0] io_ins_3,
  input  [1:0]  io_sel,
  output [31:0] io_out
);
  wire [31:0] _GEN_0;
  wire [31:0] _GEN_1;
  wire [31:0] _GEN_2;
  wire [31:0] _GEN_3;
  assign io_out = _GEN_0;
  assign _GEN_0 = _GEN_3;
  assign _GEN_1 = 2'h1 == io_sel ? io_ins_1 : io_ins_0;
  assign _GEN_2 = 2'h2 == io_sel ? io_ins_2 : _GEN_1;
  assign _GEN_3 = 2'h3 == io_sel ? io_ins_3 : _GEN_2;
endmodule
module RegFile(
  input         clock,
  input         reset,
  input  [1:0]  io_raddr,
  input         io_wen,
  input  [1:0]  io_waddr,
  input  [31:0] io_wdata,
  output [31:0] io_rdata,
  output [31:0] io_argIns_0,
  output [31:0] io_argIns_1,
  output [31:0] io_argIns_2,
  output        io_argOuts_0_ready,
  input         io_argOuts_0_valid,
  input  [31:0] io_argOuts_0_bits,
  output        io_argOuts_1_ready,
  input         io_argOuts_1_valid,
  input  [31:0] io_argOuts_1_bits
);
  wire  regs_0_clock;
  wire  regs_0_reset;
  wire [31:0] regs_0_io_in;
  wire [31:0] regs_0_io_init;
  wire [31:0] regs_0_io_out;
  wire  regs_0_io_enable;
  wire  _T_57;
  wire  _T_58;
  wire  regs_1_clock;
  wire  regs_1_reset;
  wire [31:0] regs_1_io_in;
  wire [31:0] regs_1_io_init;
  wire [31:0] regs_1_io_out;
  wire  regs_1_io_enable;
  wire [31:0] _T_59;
  wire  _T_61;
  wire  _T_62;
  wire  _T_63;
  wire  regs_2_clock;
  wire  regs_2_reset;
  wire [31:0] regs_2_io_in;
  wire [31:0] regs_2_io_init;
  wire [31:0] regs_2_io_out;
  wire  regs_2_io_enable;
  wire  _T_65;
  wire  _T_66;
  wire  regs_3_clock;
  wire  regs_3_reset;
  wire [31:0] regs_3_io_in;
  wire [31:0] regs_3_io_init;
  wire [31:0] regs_3_io_out;
  wire  regs_3_io_enable;
  wire [31:0] _T_67;
  wire  _T_69;
  wire  _T_70;
  wire  _T_71;
  wire  rport_clock;
  wire  rport_reset;
  wire [31:0] rport_io_ins_0;
  wire [31:0] rport_io_ins_1;
  wire [31:0] rport_io_ins_2;
  wire [31:0] rport_io_ins_3;
  wire [1:0] rport_io_sel;
  wire [31:0] rport_io_out;
  wire [31:0] regOuts_0;
  wire [31:0] regOuts_1;
  wire [31:0] regOuts_2;
  wire [31:0] regOuts_3;
  wire [31:0] _T_82_0;
  wire [31:0] _T_82_1;
  wire [31:0] _T_82_2;
  reg  _GEN_0;
  reg [31:0] _GEN_6;
  reg  _GEN_1;
  reg [31:0] _GEN_7;
  reg [31:0] _GEN_2;
  reg [31:0] _GEN_8;
  reg [31:0] _GEN_3;
  reg [31:0] _GEN_9;
  reg [31:0] _GEN_4;
  reg [31:0] _GEN_10;
  reg [31:0] _GEN_5;
  reg [31:0] _GEN_11;
  FF_1 regs_0 (
    .clock(regs_0_clock),
    .reset(regs_0_reset),
    .io_in(regs_0_io_in),
    .io_init(regs_0_io_init),
    .io_out(regs_0_io_out),
    .io_enable(regs_0_io_enable)
  );
  FF_1 regs_1 (
    .clock(regs_1_clock),
    .reset(regs_1_reset),
    .io_in(regs_1_io_in),
    .io_init(regs_1_io_init),
    .io_out(regs_1_io_out),
    .io_enable(regs_1_io_enable)
  );
  FF_1 regs_2 (
    .clock(regs_2_clock),
    .reset(regs_2_reset),
    .io_in(regs_2_io_in),
    .io_init(regs_2_io_init),
    .io_out(regs_2_io_out),
    .io_enable(regs_2_io_enable)
  );
  FF_1 regs_3 (
    .clock(regs_3_clock),
    .reset(regs_3_reset),
    .io_in(regs_3_io_in),
    .io_init(regs_3_io_init),
    .io_out(regs_3_io_out),
    .io_enable(regs_3_io_enable)
  );
  MuxN rport (
    .clock(rport_clock),
    .reset(rport_reset),
    .io_ins_0(rport_io_ins_0),
    .io_ins_1(rport_io_ins_1),
    .io_ins_2(rport_io_ins_2),
    .io_ins_3(rport_io_ins_3),
    .io_sel(rport_io_sel),
    .io_out(rport_io_out)
  );
  assign io_rdata = rport_io_out;
  assign io_argIns_0 = _T_82_0;
  assign io_argIns_1 = _T_82_1;
  assign io_argIns_2 = _T_82_2;
  assign io_argOuts_0_ready = _GEN_0;
  assign io_argOuts_1_ready = _GEN_1;
  assign regs_0_clock = clock;
  assign regs_0_reset = reset;
  assign regs_0_io_in = io_wdata;
  assign regs_0_io_init = _GEN_2;
  assign regs_0_io_enable = _T_58;
  assign _T_57 = io_waddr == 2'h0;
  assign _T_58 = io_wen & _T_57;
  assign regs_1_clock = clock;
  assign regs_1_reset = reset;
  assign regs_1_io_in = _T_59;
  assign regs_1_io_init = _GEN_3;
  assign regs_1_io_enable = _T_63;
  assign _T_59 = io_argOuts_0_valid ? io_argOuts_0_bits : io_wdata;
  assign _T_61 = io_waddr == 2'h1;
  assign _T_62 = io_wen & _T_61;
  assign _T_63 = io_argOuts_0_valid | _T_62;
  assign regs_2_clock = clock;
  assign regs_2_reset = reset;
  assign regs_2_io_in = io_wdata;
  assign regs_2_io_init = _GEN_4;
  assign regs_2_io_enable = _T_66;
  assign _T_65 = io_waddr == 2'h2;
  assign _T_66 = io_wen & _T_65;
  assign regs_3_clock = clock;
  assign regs_3_reset = reset;
  assign regs_3_io_in = _T_67;
  assign regs_3_io_init = _GEN_5;
  assign regs_3_io_enable = _T_71;
  assign _T_67 = io_argOuts_1_valid ? io_argOuts_1_bits : io_wdata;
  assign _T_69 = io_waddr == 2'h3;
  assign _T_70 = io_wen & _T_69;
  assign _T_71 = io_argOuts_1_valid | _T_70;
  assign rport_clock = clock;
  assign rport_reset = reset;
  assign rport_io_ins_0 = regOuts_0;
  assign rport_io_ins_1 = regOuts_1;
  assign rport_io_ins_2 = regOuts_2;
  assign rport_io_ins_3 = regOuts_3;
  assign rport_io_sel = io_raddr;
  assign regOuts_0 = regs_0_io_out;
  assign regOuts_1 = regs_1_io_out;
  assign regOuts_2 = regs_2_io_out;
  assign regOuts_3 = regs_3_io_out;
  assign _T_82_0 = regOuts_0;
  assign _T_82_1 = regOuts_1;
  assign _T_82_2 = regOuts_2;
`ifdef RANDOMIZE
  integer initvar;
  initial begin
    `ifndef verilator
      #0.002 begin end
    `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_6 = {1{$random}};
  _GEN_0 = _GEN_6[0:0];
  `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_7 = {1{$random}};
  _GEN_1 = _GEN_7[0:0];
  `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_8 = {1{$random}};
  _GEN_2 = _GEN_8[31:0];
  `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_9 = {1{$random}};
  _GEN_3 = _GEN_9[31:0];
  `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_10 = {1{$random}};
  _GEN_4 = _GEN_10[31:0];
  `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_11 = {1{$random}};
  _GEN_5 = _GEN_11[31:0];
  `endif
  end
`endif
endmodule
module FF_5(
  input   clock,
  input   reset,
  input   io_in,
  input   io_init,
  output  io_out,
  input   io_enable
);
  wire  d;
  reg  ff;
  reg [31:0] _GEN_0;
  wire  _T_13;
  wire  _GEN_1;
  assign io_out = ff;
  assign d = _GEN_1;
  assign _T_13 = io_enable == 1'h0;
  assign _GEN_1 = _T_13 ? ff : io_in;
`ifdef RANDOMIZE
  integer initvar;
  initial begin
    `ifndef verilator
      #0.002 begin end
    `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_0 = {1{$random}};
  ff = _GEN_0[0:0];
  `endif
  end
`endif
  always @(posedge clock) begin
    if (reset) begin
      ff <= io_init;
    end else begin
      ff <= d;
    end
  end
endmodule
module Depulser(
  input   clock,
  input   reset,
  input   io_in,
  input   io_rst,
  output  io_out
);
  wire  r_clock;
  wire  r_reset;
  wire  r_io_in;
  wire  r_io_init;
  wire  r_io_out;
  wire  r_io_enable;
  wire  _T_9;
  wire  _T_11;
  FF_5 r (
    .clock(r_clock),
    .reset(r_reset),
    .io_in(r_io_in),
    .io_init(r_io_init),
    .io_out(r_io_out),
    .io_enable(r_io_enable)
  );
  assign io_out = r_io_out;
  assign r_clock = clock;
  assign r_reset = reset;
  assign r_io_in = _T_9;
  assign r_io_init = 1'h0;
  assign r_io_enable = _T_11;
  assign _T_9 = io_rst ? 1'h0 : io_in;
  assign _T_11 = io_in | io_rst;
endmodule
module FIFOArbiter(
  input         clock,
  input         reset,
  output [31:0] io_deq_0,
  output [31:0] io_deq_1,
  output [31:0] io_deq_2,
  output [31:0] io_deq_3,
  output [31:0] io_deq_4,
  output [31:0] io_deq_5,
  output [31:0] io_deq_6,
  output [31:0] io_deq_7,
  output [31:0] io_deq_8,
  output [31:0] io_deq_9,
  output [31:0] io_deq_10,
  output [31:0] io_deq_11,
  output [31:0] io_deq_12,
  output [31:0] io_deq_13,
  output [31:0] io_deq_14,
  output [31:0] io_deq_15,
  input         io_deqVld,
  output        io_empty,
  output        io_forceTag_ready,
  input         io_forceTag_valid,
  input         io_forceTag_bits,
  output        io_tag,
  input         io_config_chainWrite,
  input         io_config_chainRead
);
  wire  tagFF_clock;
  wire  tagFF_reset;
  wire  tagFF_io_in;
  wire  tagFF_io_init;
  wire  tagFF_io_out;
  wire  tagFF_io_enable;
  wire [31:0] _T_162_0;
  wire [31:0] _T_162_1;
  wire [31:0] _T_162_2;
  wire [31:0] _T_162_3;
  wire [31:0] _T_162_4;
  wire [31:0] _T_162_5;
  wire [31:0] _T_162_6;
  wire [31:0] _T_162_7;
  wire [31:0] _T_162_8;
  wire [31:0] _T_162_9;
  wire [31:0] _T_162_10;
  wire [31:0] _T_162_11;
  wire [31:0] _T_162_12;
  wire [31:0] _T_162_13;
  wire [31:0] _T_162_14;
  wire [31:0] _T_162_15;
  reg  _GEN_0;
  reg [31:0] _GEN_3;
  reg  _GEN_1;
  reg [31:0] _GEN_4;
  reg  _GEN_2;
  reg [31:0] _GEN_5;
  FF_5 tagFF (
    .clock(tagFF_clock),
    .reset(tagFF_reset),
    .io_in(tagFF_io_in),
    .io_init(tagFF_io_init),
    .io_out(tagFF_io_out),
    .io_enable(tagFF_io_enable)
  );
  assign io_deq_0 = _T_162_0;
  assign io_deq_1 = _T_162_1;
  assign io_deq_2 = _T_162_2;
  assign io_deq_3 = _T_162_3;
  assign io_deq_4 = _T_162_4;
  assign io_deq_5 = _T_162_5;
  assign io_deq_6 = _T_162_6;
  assign io_deq_7 = _T_162_7;
  assign io_deq_8 = _T_162_8;
  assign io_deq_9 = _T_162_9;
  assign io_deq_10 = _T_162_10;
  assign io_deq_11 = _T_162_11;
  assign io_deq_12 = _T_162_12;
  assign io_deq_13 = _T_162_13;
  assign io_deq_14 = _T_162_14;
  assign io_deq_15 = _T_162_15;
  assign io_empty = 1'h1;
  assign io_forceTag_ready = _GEN_0;
  assign io_tag = 1'h0;
  assign tagFF_clock = clock;
  assign tagFF_reset = reset;
  assign tagFF_io_in = _GEN_1;
  assign tagFF_io_init = 1'h0;
  assign tagFF_io_enable = _GEN_2;
  assign _T_162_0 = 32'h0;
  assign _T_162_1 = 32'h0;
  assign _T_162_2 = 32'h0;
  assign _T_162_3 = 32'h0;
  assign _T_162_4 = 32'h0;
  assign _T_162_5 = 32'h0;
  assign _T_162_6 = 32'h0;
  assign _T_162_7 = 32'h0;
  assign _T_162_8 = 32'h0;
  assign _T_162_9 = 32'h0;
  assign _T_162_10 = 32'h0;
  assign _T_162_11 = 32'h0;
  assign _T_162_12 = 32'h0;
  assign _T_162_13 = 32'h0;
  assign _T_162_14 = 32'h0;
  assign _T_162_15 = 32'h0;
`ifdef RANDOMIZE
  integer initvar;
  initial begin
    `ifndef verilator
      #0.002 begin end
    `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_3 = {1{$random}};
  _GEN_0 = _GEN_3[0:0];
  `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_4 = {1{$random}};
  _GEN_1 = _GEN_4[0:0];
  `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_5 = {1{$random}};
  _GEN_2 = _GEN_5[0:0];
  `endif
  end
`endif
endmodule
module FIFOArbiter_1(
  input   clock,
  input   reset,
  output  io_deq_0,
  output  io_deq_1,
  output  io_deq_2,
  output  io_deq_3,
  output  io_deq_4,
  output  io_deq_5,
  output  io_deq_6,
  output  io_deq_7,
  output  io_deq_8,
  output  io_deq_9,
  output  io_deq_10,
  output  io_deq_11,
  output  io_deq_12,
  output  io_deq_13,
  output  io_deq_14,
  output  io_deq_15,
  input   io_deqVld,
  output  io_empty,
  output  io_forceTag_ready,
  input   io_forceTag_valid,
  input   io_forceTag_bits,
  output  io_tag,
  input   io_config_chainWrite,
  input   io_config_chainRead
);
  wire  tagFF_clock;
  wire  tagFF_reset;
  wire  tagFF_io_in;
  wire  tagFF_io_init;
  wire  tagFF_io_out;
  wire  tagFF_io_enable;
  wire  _T_162_0;
  wire  _T_162_1;
  wire  _T_162_2;
  wire  _T_162_3;
  wire  _T_162_4;
  wire  _T_162_5;
  wire  _T_162_6;
  wire  _T_162_7;
  wire  _T_162_8;
  wire  _T_162_9;
  wire  _T_162_10;
  wire  _T_162_11;
  wire  _T_162_12;
  wire  _T_162_13;
  wire  _T_162_14;
  wire  _T_162_15;
  reg  _GEN_0;
  reg [31:0] _GEN_3;
  reg  _GEN_1;
  reg [31:0] _GEN_4;
  reg  _GEN_2;
  reg [31:0] _GEN_5;
  FF_5 tagFF (
    .clock(tagFF_clock),
    .reset(tagFF_reset),
    .io_in(tagFF_io_in),
    .io_init(tagFF_io_init),
    .io_out(tagFF_io_out),
    .io_enable(tagFF_io_enable)
  );
  assign io_deq_0 = _T_162_0;
  assign io_deq_1 = _T_162_1;
  assign io_deq_2 = _T_162_2;
  assign io_deq_3 = _T_162_3;
  assign io_deq_4 = _T_162_4;
  assign io_deq_5 = _T_162_5;
  assign io_deq_6 = _T_162_6;
  assign io_deq_7 = _T_162_7;
  assign io_deq_8 = _T_162_8;
  assign io_deq_9 = _T_162_9;
  assign io_deq_10 = _T_162_10;
  assign io_deq_11 = _T_162_11;
  assign io_deq_12 = _T_162_12;
  assign io_deq_13 = _T_162_13;
  assign io_deq_14 = _T_162_14;
  assign io_deq_15 = _T_162_15;
  assign io_empty = 1'h1;
  assign io_forceTag_ready = _GEN_0;
  assign io_tag = 1'h0;
  assign tagFF_clock = clock;
  assign tagFF_reset = reset;
  assign tagFF_io_in = _GEN_1;
  assign tagFF_io_init = 1'h0;
  assign tagFF_io_enable = _GEN_2;
  assign _T_162_0 = 1'h0;
  assign _T_162_1 = 1'h0;
  assign _T_162_2 = 1'h0;
  assign _T_162_3 = 1'h0;
  assign _T_162_4 = 1'h0;
  assign _T_162_5 = 1'h0;
  assign _T_162_6 = 1'h0;
  assign _T_162_7 = 1'h0;
  assign _T_162_8 = 1'h0;
  assign _T_162_9 = 1'h0;
  assign _T_162_10 = 1'h0;
  assign _T_162_11 = 1'h0;
  assign _T_162_12 = 1'h0;
  assign _T_162_13 = 1'h0;
  assign _T_162_14 = 1'h0;
  assign _T_162_15 = 1'h0;
`ifdef RANDOMIZE
  integer initvar;
  initial begin
    `ifndef verilator
      #0.002 begin end
    `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_3 = {1{$random}};
  _GEN_0 = _GEN_3[0:0];
  `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_4 = {1{$random}};
  _GEN_1 = _GEN_4[0:0];
  `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_5 = {1{$random}};
  _GEN_2 = _GEN_5[0:0];
  `endif
  end
`endif
endmodule
module Counter(
  input         clock,
  input         reset,
  input  [31:0] io_max,
  input  [31:0] io_stride,
  output [31:0] io_out,
  output [31:0] io_next,
  input         io_reset,
  input         io_enable,
  input         io_saturate,
  output        io_done
);
  wire  reg$_clock;
  wire  reg$_reset;
  wire [31:0] reg$_io_in;
  wire [31:0] reg$_io_init;
  wire [31:0] reg$_io_out;
  wire  reg$_io_enable;
  wire  _T_18;
  wire [32:0] count;
  wire [32:0] _GEN_2;
  wire [33:0] _T_20;
  wire [32:0] newval;
  wire [32:0] _GEN_3;
  wire  isMax;
  wire [32:0] _T_21;
  wire [32:0] next;
  wire  _T_23;
  wire [32:0] _GEN_1;
  wire  _T_24;
  FF_1 reg$ (
    .clock(reg$_clock),
    .reset(reg$_reset),
    .io_in(reg$_io_in),
    .io_init(reg$_io_init),
    .io_out(reg$_io_out),
    .io_enable(reg$_io_enable)
  );
  assign io_out = count[31:0];
  assign io_next = next[31:0];
  assign io_done = _T_24;
  assign reg$_clock = clock;
  assign reg$_reset = reset;
  assign reg$_io_in = _GEN_1[31:0];
  assign reg$_io_init = 32'h0;
  assign reg$_io_enable = _T_18;
  assign _T_18 = io_reset | io_enable;
  assign count = {1'h0,reg$_io_out};
  assign _GEN_2 = {{1'd0}, io_stride};
  assign _T_20 = count + _GEN_2;
  assign newval = _T_20[32:0];
  assign _GEN_3 = {{1'd0}, io_max};
  assign isMax = newval >= _GEN_3;
  assign _T_21 = io_saturate ? count : 33'h0;
  assign next = isMax ? _T_21 : newval;
  assign _T_23 = io_reset == 1'h0;
  assign _GEN_1 = _T_23 ? next : 33'h0;
  assign _T_24 = io_enable & isMax;
endmodule
module FF_11(
  input         clock,
  input         reset,
  input  [10:0] io_in,
  input  [10:0] io_init,
  output [10:0] io_out,
  input         io_enable
);
  wire [10:0] d;
  reg [10:0] ff;
  reg [31:0] _GEN_0;
  wire  _T_13;
  wire [10:0] _GEN_1;
  assign io_out = ff;
  assign d = _GEN_1;
  assign _T_13 = io_enable == 1'h0;
  assign _GEN_1 = _T_13 ? ff : io_in;
`ifdef RANDOMIZE
  integer initvar;
  initial begin
    `ifndef verilator
      #0.002 begin end
    `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_0 = {1{$random}};
  ff = _GEN_0[10:0];
  `endif
  end
`endif
  always @(posedge clock) begin
    if (reset) begin
      ff <= io_init;
    end else begin
      ff <= d;
    end
  end
endmodule
module Counter_1(
  input         clock,
  input         reset,
  input  [10:0] io_max,
  input  [10:0] io_stride,
  output [10:0] io_out,
  output [10:0] io_next,
  input         io_reset,
  input         io_enable,
  input         io_saturate,
  output        io_done
);
  wire  reg$_clock;
  wire  reg$_reset;
  wire [10:0] reg$_io_in;
  wire [10:0] reg$_io_init;
  wire [10:0] reg$_io_out;
  wire  reg$_io_enable;
  wire  _T_18;
  wire [11:0] count;
  wire [11:0] _GEN_2;
  wire [12:0] _T_20;
  wire [11:0] newval;
  wire [11:0] _GEN_3;
  wire  isMax;
  wire [11:0] _T_21;
  wire [11:0] next;
  wire  _T_23;
  wire [11:0] _GEN_1;
  wire  _T_24;
  FF_11 reg$ (
    .clock(reg$_clock),
    .reset(reg$_reset),
    .io_in(reg$_io_in),
    .io_init(reg$_io_init),
    .io_out(reg$_io_out),
    .io_enable(reg$_io_enable)
  );
  assign io_out = count[10:0];
  assign io_next = next[10:0];
  assign io_done = _T_24;
  assign reg$_clock = clock;
  assign reg$_reset = reset;
  assign reg$_io_in = _GEN_1[10:0];
  assign reg$_io_init = 11'h0;
  assign reg$_io_enable = _T_18;
  assign _T_18 = io_reset | io_enable;
  assign count = {1'h0,reg$_io_out};
  assign _GEN_2 = {{1'd0}, io_stride};
  assign _T_20 = count + _GEN_2;
  assign newval = _T_20[11:0];
  assign _GEN_3 = {{1'd0}, io_max};
  assign isMax = newval >= _GEN_3;
  assign _T_21 = io_saturate ? count : 12'h0;
  assign next = isMax ? _T_21 : newval;
  assign _T_23 = io_reset == 1'h0;
  assign _GEN_1 = _T_23 ? next : 12'h0;
  assign _T_24 = io_enable & isMax;
endmodule
module MAGCore(
  input         clock,
  input         reset,
  input         io_dram_cmd_ready,
  output        io_dram_cmd_valid,
  output [31:0] io_dram_cmd_bits_addr,
  output        io_dram_cmd_bits_isWr,
  output [31:0] io_dram_cmd_bits_tag,
  output [31:0] io_dram_cmd_bits_streamId,
  output [31:0] io_dram_cmd_bits_wdata_0,
  output [31:0] io_dram_cmd_bits_wdata_1,
  output [31:0] io_dram_cmd_bits_wdata_2,
  output [31:0] io_dram_cmd_bits_wdata_3,
  output [31:0] io_dram_cmd_bits_wdata_4,
  output [31:0] io_dram_cmd_bits_wdata_5,
  output [31:0] io_dram_cmd_bits_wdata_6,
  output [31:0] io_dram_cmd_bits_wdata_7,
  output [31:0] io_dram_cmd_bits_wdata_8,
  output [31:0] io_dram_cmd_bits_wdata_9,
  output [31:0] io_dram_cmd_bits_wdata_10,
  output [31:0] io_dram_cmd_bits_wdata_11,
  output [31:0] io_dram_cmd_bits_wdata_12,
  output [31:0] io_dram_cmd_bits_wdata_13,
  output [31:0] io_dram_cmd_bits_wdata_14,
  output [31:0] io_dram_cmd_bits_wdata_15,
  output        io_dram_resp_ready,
  input         io_dram_resp_valid,
  input  [31:0] io_dram_resp_bits_rdata_0,
  input  [31:0] io_dram_resp_bits_rdata_1,
  input  [31:0] io_dram_resp_bits_rdata_2,
  input  [31:0] io_dram_resp_bits_rdata_3,
  input  [31:0] io_dram_resp_bits_rdata_4,
  input  [31:0] io_dram_resp_bits_rdata_5,
  input  [31:0] io_dram_resp_bits_rdata_6,
  input  [31:0] io_dram_resp_bits_rdata_7,
  input  [31:0] io_dram_resp_bits_rdata_8,
  input  [31:0] io_dram_resp_bits_rdata_9,
  input  [31:0] io_dram_resp_bits_rdata_10,
  input  [31:0] io_dram_resp_bits_rdata_11,
  input  [31:0] io_dram_resp_bits_rdata_12,
  input  [31:0] io_dram_resp_bits_rdata_13,
  input  [31:0] io_dram_resp_bits_rdata_14,
  input  [31:0] io_dram_resp_bits_rdata_15,
  input  [31:0] io_dram_resp_bits_tag,
  input  [31:0] io_dram_resp_bits_streamId,
  input         io_config_scatterGather
);
  wire  addrFifo_clock;
  wire  addrFifo_reset;
  wire [31:0] addrFifo_io_deq_0;
  wire [31:0] addrFifo_io_deq_1;
  wire [31:0] addrFifo_io_deq_2;
  wire [31:0] addrFifo_io_deq_3;
  wire [31:0] addrFifo_io_deq_4;
  wire [31:0] addrFifo_io_deq_5;
  wire [31:0] addrFifo_io_deq_6;
  wire [31:0] addrFifo_io_deq_7;
  wire [31:0] addrFifo_io_deq_8;
  wire [31:0] addrFifo_io_deq_9;
  wire [31:0] addrFifo_io_deq_10;
  wire [31:0] addrFifo_io_deq_11;
  wire [31:0] addrFifo_io_deq_12;
  wire [31:0] addrFifo_io_deq_13;
  wire [31:0] addrFifo_io_deq_14;
  wire [31:0] addrFifo_io_deq_15;
  wire  addrFifo_io_deqVld;
  wire  addrFifo_io_empty;
  wire  addrFifo_io_forceTag_ready;
  wire  addrFifo_io_forceTag_valid;
  wire  addrFifo_io_forceTag_bits;
  wire  addrFifo_io_tag;
  wire  addrFifo_io_config_chainWrite;
  wire  addrFifo_io_config_chainRead;
  wire  addrFifoConfig_chainWrite;
  wire  addrFifoConfig_chainRead;
  wire  _T_536;
  wire [25:0] burstAddrs_0;
  wire  isWrFifo_clock;
  wire  isWrFifo_reset;
  wire  isWrFifo_io_deq_0;
  wire  isWrFifo_io_deq_1;
  wire  isWrFifo_io_deq_2;
  wire  isWrFifo_io_deq_3;
  wire  isWrFifo_io_deq_4;
  wire  isWrFifo_io_deq_5;
  wire  isWrFifo_io_deq_6;
  wire  isWrFifo_io_deq_7;
  wire  isWrFifo_io_deq_8;
  wire  isWrFifo_io_deq_9;
  wire  isWrFifo_io_deq_10;
  wire  isWrFifo_io_deq_11;
  wire  isWrFifo_io_deq_12;
  wire  isWrFifo_io_deq_13;
  wire  isWrFifo_io_deq_14;
  wire  isWrFifo_io_deq_15;
  wire  isWrFifo_io_deqVld;
  wire  isWrFifo_io_empty;
  wire  isWrFifo_io_forceTag_ready;
  wire  isWrFifo_io_forceTag_valid;
  wire  isWrFifo_io_forceTag_bits;
  wire  isWrFifo_io_tag;
  wire  isWrFifo_io_config_chainWrite;
  wire  isWrFifo_io_config_chainRead;
  wire  isWrFifoConfig_chainWrite;
  wire  isWrFifoConfig_chainRead;
  wire  sizeFifo_clock;
  wire  sizeFifo_reset;
  wire [31:0] sizeFifo_io_deq_0;
  wire [31:0] sizeFifo_io_deq_1;
  wire [31:0] sizeFifo_io_deq_2;
  wire [31:0] sizeFifo_io_deq_3;
  wire [31:0] sizeFifo_io_deq_4;
  wire [31:0] sizeFifo_io_deq_5;
  wire [31:0] sizeFifo_io_deq_6;
  wire [31:0] sizeFifo_io_deq_7;
  wire [31:0] sizeFifo_io_deq_8;
  wire [31:0] sizeFifo_io_deq_9;
  wire [31:0] sizeFifo_io_deq_10;
  wire [31:0] sizeFifo_io_deq_11;
  wire [31:0] sizeFifo_io_deq_12;
  wire [31:0] sizeFifo_io_deq_13;
  wire [31:0] sizeFifo_io_deq_14;
  wire [31:0] sizeFifo_io_deq_15;
  wire  sizeFifo_io_deqVld;
  wire  sizeFifo_io_empty;
  wire  sizeFifo_io_forceTag_ready;
  wire  sizeFifo_io_forceTag_valid;
  wire  sizeFifo_io_forceTag_bits;
  wire  sizeFifo_io_tag;
  wire  sizeFifo_io_config_chainWrite;
  wire  sizeFifo_io_config_chainRead;
  wire  sizeFifoConfig_chainWrite;
  wire  sizeFifoConfig_chainRead;
  wire [25:0] _T_554;
  wire [5:0] _T_555;
  wire  _T_557;
  wire [25:0] _GEN_0;
  wire [26:0] _T_558;
  wire [25:0] sizeInBursts;
  wire  dataFifo_clock;
  wire  dataFifo_reset;
  wire [31:0] dataFifo_io_deq_0;
  wire [31:0] dataFifo_io_deq_1;
  wire [31:0] dataFifo_io_deq_2;
  wire [31:0] dataFifo_io_deq_3;
  wire [31:0] dataFifo_io_deq_4;
  wire [31:0] dataFifo_io_deq_5;
  wire [31:0] dataFifo_io_deq_6;
  wire [31:0] dataFifo_io_deq_7;
  wire [31:0] dataFifo_io_deq_8;
  wire [31:0] dataFifo_io_deq_9;
  wire [31:0] dataFifo_io_deq_10;
  wire [31:0] dataFifo_io_deq_11;
  wire [31:0] dataFifo_io_deq_12;
  wire [31:0] dataFifo_io_deq_13;
  wire [31:0] dataFifo_io_deq_14;
  wire [31:0] dataFifo_io_deq_15;
  wire  dataFifo_io_deqVld;
  wire  dataFifo_io_empty;
  wire  dataFifo_io_forceTag_ready;
  wire  dataFifo_io_forceTag_valid;
  wire  dataFifo_io_forceTag_bits;
  wire  dataFifo_io_tag;
  wire  dataFifo_io_config_chainWrite;
  wire  dataFifo_io_config_chainRead;
  wire  dataFifoConfig_chainWrite;
  wire  dataFifoConfig_chainRead;
  wire  burstCounter_clock;
  wire  burstCounter_reset;
  wire [31:0] burstCounter_io_max;
  wire [31:0] burstCounter_io_stride;
  wire [31:0] burstCounter_io_out;
  wire [31:0] burstCounter_io_next;
  wire  burstCounter_io_reset;
  wire  burstCounter_io_enable;
  wire  burstCounter_io_saturate;
  wire  burstCounter_io_done;
  wire  wrPhase_clock;
  wire  wrPhase_reset;
  wire  wrPhase_io_input_set;
  wire  wrPhase_io_input_reset;
  wire  wrPhase_io_input_asyn_reset;
  wire  wrPhase_io_output_data;
  wire  _T_565;
  wire  _T_566;
  wire  _T_567;
  wire  _T_568;
  wire  _T_570;
  wire  burstVld;
  wire [25:0] _T_573;
  wire  _T_576;
  wire  _T_577;
  wire  _T_578;
  wire  burstTagCounter_clock;
  wire  burstTagCounter_reset;
  wire [10:0] burstTagCounter_io_max;
  wire [10:0] burstTagCounter_io_stride;
  wire [10:0] burstTagCounter_io_out;
  wire [10:0] burstTagCounter_io_next;
  wire  burstTagCounter_io_reset;
  wire  burstTagCounter_io_enable;
  wire  burstTagCounter_io_saturate;
  wire  burstTagCounter_io_done;
  wire  _T_587;
  wire  _T_588;
  wire  tagOut_streamTag;
  wire [30:0] tagOut_burstTag;
  wire [25:0] _T_594;
  wire [31:0] _GEN_1;
  wire [32:0] _T_595;
  wire [31:0] _T_596;
  wire [37:0] _T_598;
  wire [31:0] _T_599;
  reg  _T_602;
  reg [31:0] _GEN_8;
  reg  _GEN_2;
  reg [31:0] _GEN_9;
  reg  _GEN_3;
  reg [31:0] _GEN_10;
  reg  _GEN_4;
  reg [31:0] _GEN_11;
  reg  _GEN_5;
  reg [31:0] _GEN_12;
  reg  _GEN_6;
  reg [31:0] _GEN_13;
  reg  _GEN_7;
  reg [31:0] _GEN_14;
  FIFOArbiter addrFifo (
    .clock(addrFifo_clock),
    .reset(addrFifo_reset),
    .io_deq_0(addrFifo_io_deq_0),
    .io_deq_1(addrFifo_io_deq_1),
    .io_deq_2(addrFifo_io_deq_2),
    .io_deq_3(addrFifo_io_deq_3),
    .io_deq_4(addrFifo_io_deq_4),
    .io_deq_5(addrFifo_io_deq_5),
    .io_deq_6(addrFifo_io_deq_6),
    .io_deq_7(addrFifo_io_deq_7),
    .io_deq_8(addrFifo_io_deq_8),
    .io_deq_9(addrFifo_io_deq_9),
    .io_deq_10(addrFifo_io_deq_10),
    .io_deq_11(addrFifo_io_deq_11),
    .io_deq_12(addrFifo_io_deq_12),
    .io_deq_13(addrFifo_io_deq_13),
    .io_deq_14(addrFifo_io_deq_14),
    .io_deq_15(addrFifo_io_deq_15),
    .io_deqVld(addrFifo_io_deqVld),
    .io_empty(addrFifo_io_empty),
    .io_forceTag_ready(addrFifo_io_forceTag_ready),
    .io_forceTag_valid(addrFifo_io_forceTag_valid),
    .io_forceTag_bits(addrFifo_io_forceTag_bits),
    .io_tag(addrFifo_io_tag),
    .io_config_chainWrite(addrFifo_io_config_chainWrite),
    .io_config_chainRead(addrFifo_io_config_chainRead)
  );
  FIFOArbiter_1 isWrFifo (
    .clock(isWrFifo_clock),
    .reset(isWrFifo_reset),
    .io_deq_0(isWrFifo_io_deq_0),
    .io_deq_1(isWrFifo_io_deq_1),
    .io_deq_2(isWrFifo_io_deq_2),
    .io_deq_3(isWrFifo_io_deq_3),
    .io_deq_4(isWrFifo_io_deq_4),
    .io_deq_5(isWrFifo_io_deq_5),
    .io_deq_6(isWrFifo_io_deq_6),
    .io_deq_7(isWrFifo_io_deq_7),
    .io_deq_8(isWrFifo_io_deq_8),
    .io_deq_9(isWrFifo_io_deq_9),
    .io_deq_10(isWrFifo_io_deq_10),
    .io_deq_11(isWrFifo_io_deq_11),
    .io_deq_12(isWrFifo_io_deq_12),
    .io_deq_13(isWrFifo_io_deq_13),
    .io_deq_14(isWrFifo_io_deq_14),
    .io_deq_15(isWrFifo_io_deq_15),
    .io_deqVld(isWrFifo_io_deqVld),
    .io_empty(isWrFifo_io_empty),
    .io_forceTag_ready(isWrFifo_io_forceTag_ready),
    .io_forceTag_valid(isWrFifo_io_forceTag_valid),
    .io_forceTag_bits(isWrFifo_io_forceTag_bits),
    .io_tag(isWrFifo_io_tag),
    .io_config_chainWrite(isWrFifo_io_config_chainWrite),
    .io_config_chainRead(isWrFifo_io_config_chainRead)
  );
  FIFOArbiter sizeFifo (
    .clock(sizeFifo_clock),
    .reset(sizeFifo_reset),
    .io_deq_0(sizeFifo_io_deq_0),
    .io_deq_1(sizeFifo_io_deq_1),
    .io_deq_2(sizeFifo_io_deq_2),
    .io_deq_3(sizeFifo_io_deq_3),
    .io_deq_4(sizeFifo_io_deq_4),
    .io_deq_5(sizeFifo_io_deq_5),
    .io_deq_6(sizeFifo_io_deq_6),
    .io_deq_7(sizeFifo_io_deq_7),
    .io_deq_8(sizeFifo_io_deq_8),
    .io_deq_9(sizeFifo_io_deq_9),
    .io_deq_10(sizeFifo_io_deq_10),
    .io_deq_11(sizeFifo_io_deq_11),
    .io_deq_12(sizeFifo_io_deq_12),
    .io_deq_13(sizeFifo_io_deq_13),
    .io_deq_14(sizeFifo_io_deq_14),
    .io_deq_15(sizeFifo_io_deq_15),
    .io_deqVld(sizeFifo_io_deqVld),
    .io_empty(sizeFifo_io_empty),
    .io_forceTag_ready(sizeFifo_io_forceTag_ready),
    .io_forceTag_valid(sizeFifo_io_forceTag_valid),
    .io_forceTag_bits(sizeFifo_io_forceTag_bits),
    .io_tag(sizeFifo_io_tag),
    .io_config_chainWrite(sizeFifo_io_config_chainWrite),
    .io_config_chainRead(sizeFifo_io_config_chainRead)
  );
  FIFOArbiter dataFifo (
    .clock(dataFifo_clock),
    .reset(dataFifo_reset),
    .io_deq_0(dataFifo_io_deq_0),
    .io_deq_1(dataFifo_io_deq_1),
    .io_deq_2(dataFifo_io_deq_2),
    .io_deq_3(dataFifo_io_deq_3),
    .io_deq_4(dataFifo_io_deq_4),
    .io_deq_5(dataFifo_io_deq_5),
    .io_deq_6(dataFifo_io_deq_6),
    .io_deq_7(dataFifo_io_deq_7),
    .io_deq_8(dataFifo_io_deq_8),
    .io_deq_9(dataFifo_io_deq_9),
    .io_deq_10(dataFifo_io_deq_10),
    .io_deq_11(dataFifo_io_deq_11),
    .io_deq_12(dataFifo_io_deq_12),
    .io_deq_13(dataFifo_io_deq_13),
    .io_deq_14(dataFifo_io_deq_14),
    .io_deq_15(dataFifo_io_deq_15),
    .io_deqVld(dataFifo_io_deqVld),
    .io_empty(dataFifo_io_empty),
    .io_forceTag_ready(dataFifo_io_forceTag_ready),
    .io_forceTag_valid(dataFifo_io_forceTag_valid),
    .io_forceTag_bits(dataFifo_io_forceTag_bits),
    .io_tag(dataFifo_io_tag),
    .io_config_chainWrite(dataFifo_io_config_chainWrite),
    .io_config_chainRead(dataFifo_io_config_chainRead)
  );
  Counter burstCounter (
    .clock(burstCounter_clock),
    .reset(burstCounter_reset),
    .io_max(burstCounter_io_max),
    .io_stride(burstCounter_io_stride),
    .io_out(burstCounter_io_out),
    .io_next(burstCounter_io_next),
    .io_reset(burstCounter_io_reset),
    .io_enable(burstCounter_io_enable),
    .io_saturate(burstCounter_io_saturate),
    .io_done(burstCounter_io_done)
  );
  SRFF wrPhase (
    .clock(wrPhase_clock),
    .reset(wrPhase_reset),
    .io_input_set(wrPhase_io_input_set),
    .io_input_reset(wrPhase_io_input_reset),
    .io_input_asyn_reset(wrPhase_io_input_asyn_reset),
    .io_output_data(wrPhase_io_output_data)
  );
  Counter_1 burstTagCounter (
    .clock(burstTagCounter_clock),
    .reset(burstTagCounter_reset),
    .io_max(burstTagCounter_io_max),
    .io_stride(burstTagCounter_io_stride),
    .io_out(burstTagCounter_io_out),
    .io_next(burstTagCounter_io_next),
    .io_reset(burstTagCounter_io_reset),
    .io_enable(burstTagCounter_io_enable),
    .io_saturate(burstTagCounter_io_saturate),
    .io_done(burstTagCounter_io_done)
  );
  assign io_dram_cmd_valid = burstVld;
  assign io_dram_cmd_bits_addr = _T_598[31:0];
  assign io_dram_cmd_bits_isWr = isWrFifo_io_deq_0;
  assign io_dram_cmd_bits_tag = _T_599;
  assign io_dram_cmd_bits_streamId = {{31'd0}, tagOut_streamTag};
  assign io_dram_cmd_bits_wdata_0 = dataFifo_io_deq_0;
  assign io_dram_cmd_bits_wdata_1 = dataFifo_io_deq_1;
  assign io_dram_cmd_bits_wdata_2 = dataFifo_io_deq_2;
  assign io_dram_cmd_bits_wdata_3 = dataFifo_io_deq_3;
  assign io_dram_cmd_bits_wdata_4 = dataFifo_io_deq_4;
  assign io_dram_cmd_bits_wdata_5 = dataFifo_io_deq_5;
  assign io_dram_cmd_bits_wdata_6 = dataFifo_io_deq_6;
  assign io_dram_cmd_bits_wdata_7 = dataFifo_io_deq_7;
  assign io_dram_cmd_bits_wdata_8 = dataFifo_io_deq_8;
  assign io_dram_cmd_bits_wdata_9 = dataFifo_io_deq_9;
  assign io_dram_cmd_bits_wdata_10 = dataFifo_io_deq_10;
  assign io_dram_cmd_bits_wdata_11 = dataFifo_io_deq_11;
  assign io_dram_cmd_bits_wdata_12 = dataFifo_io_deq_12;
  assign io_dram_cmd_bits_wdata_13 = dataFifo_io_deq_13;
  assign io_dram_cmd_bits_wdata_14 = dataFifo_io_deq_14;
  assign io_dram_cmd_bits_wdata_15 = dataFifo_io_deq_15;
  assign io_dram_resp_ready = _GEN_2;
  assign addrFifo_clock = clock;
  assign addrFifo_reset = reset;
  assign addrFifo_io_deqVld = burstCounter_io_done;
  assign addrFifo_io_forceTag_valid = 1'h0;
  assign addrFifo_io_forceTag_bits = _GEN_3;
  assign addrFifo_io_config_chainWrite = addrFifoConfig_chainWrite;
  assign addrFifo_io_config_chainRead = addrFifoConfig_chainRead;
  assign addrFifoConfig_chainWrite = _T_536;
  assign addrFifoConfig_chainRead = 1'h1;
  assign _T_536 = ~ io_config_scatterGather;
  assign burstAddrs_0 = addrFifo_io_deq_0[31:6];
  assign isWrFifo_clock = clock;
  assign isWrFifo_reset = reset;
  assign isWrFifo_io_deqVld = burstCounter_io_done;
  assign isWrFifo_io_forceTag_valid = 1'h0;
  assign isWrFifo_io_forceTag_bits = _GEN_4;
  assign isWrFifo_io_config_chainWrite = isWrFifoConfig_chainWrite;
  assign isWrFifo_io_config_chainRead = isWrFifoConfig_chainRead;
  assign isWrFifoConfig_chainWrite = 1'h1;
  assign isWrFifoConfig_chainRead = 1'h1;
  assign sizeFifo_clock = clock;
  assign sizeFifo_reset = reset;
  assign sizeFifo_io_deqVld = burstCounter_io_done;
  assign sizeFifo_io_forceTag_valid = 1'h0;
  assign sizeFifo_io_forceTag_bits = _GEN_5;
  assign sizeFifo_io_config_chainWrite = sizeFifoConfig_chainWrite;
  assign sizeFifo_io_config_chainRead = sizeFifoConfig_chainRead;
  assign sizeFifoConfig_chainWrite = 1'h1;
  assign sizeFifoConfig_chainRead = 1'h1;
  assign _T_554 = sizeFifo_io_deq_0[31:6];
  assign _T_555 = sizeFifo_io_deq_0[5:0];
  assign _T_557 = _T_555 != 6'h0;
  assign _GEN_0 = {{25'd0}, _T_557};
  assign _T_558 = _T_554 + _GEN_0;
  assign sizeInBursts = _T_558[25:0];
  assign dataFifo_clock = clock;
  assign dataFifo_reset = reset;
  assign dataFifo_io_deqVld = _T_588;
  assign dataFifo_io_forceTag_valid = 1'h1;
  assign dataFifo_io_forceTag_bits = addrFifo_io_tag;
  assign dataFifo_io_config_chainWrite = dataFifoConfig_chainWrite;
  assign dataFifo_io_config_chainRead = dataFifoConfig_chainRead;
  assign dataFifoConfig_chainWrite = io_config_scatterGather;
  assign dataFifoConfig_chainRead = 1'h0;
  assign burstCounter_clock = clock;
  assign burstCounter_reset = reset;
  assign burstCounter_io_max = {{6'd0}, _T_573};
  assign burstCounter_io_stride = 32'h1;
  assign burstCounter_io_reset = 1'h0;
  assign burstCounter_io_enable = _T_578;
  assign burstCounter_io_saturate = 1'h0;
  assign wrPhase_clock = clock;
  assign wrPhase_reset = reset;
  assign wrPhase_io_input_set = isWrFifo_io_deq_0;
  assign wrPhase_io_input_reset = _T_602;
  assign wrPhase_io_input_asyn_reset = _GEN_6;
  assign _T_565 = ~ sizeFifo_io_empty;
  assign _T_566 = isWrFifo_io_deq_0;
  assign _T_567 = wrPhase_io_output_data | _T_566;
  assign _T_568 = ~ dataFifo_io_empty;
  assign _T_570 = _T_567 ? _T_568 : 1'h1;
  assign burstVld = _T_565 & _T_570;
  assign _T_573 = io_config_scatterGather ? 26'h1 : sizeInBursts;
  assign _T_576 = ~ addrFifo_io_empty;
  assign _T_577 = io_config_scatterGather ? _T_576 : burstVld;
  assign _T_578 = _T_577 & io_dram_cmd_ready;
  assign burstTagCounter_clock = clock;
  assign burstTagCounter_reset = reset;
  assign burstTagCounter_io_max = 11'h400;
  assign burstTagCounter_io_stride = 11'h1;
  assign burstTagCounter_io_reset = 1'h0;
  assign burstTagCounter_io_enable = _T_578;
  assign burstTagCounter_io_saturate = _GEN_7;
  assign _T_587 = burstVld & isWrFifo_io_deq_0;
  assign _T_588 = _T_587 & io_dram_cmd_ready;
  assign tagOut_streamTag = addrFifo_io_tag;
  assign tagOut_burstTag = {{5'd0}, _T_594};
  assign _T_594 = io_config_scatterGather ? burstAddrs_0 : {{15'd0}, burstTagCounter_io_out};
  assign _GEN_1 = {{6'd0}, burstAddrs_0};
  assign _T_595 = _GEN_1 + burstCounter_io_out;
  assign _T_596 = _T_595[31:0];
  assign _T_598 = {_T_596,6'h0};
  assign _T_599 = {tagOut_streamTag,tagOut_burstTag};
`ifdef RANDOMIZE
  integer initvar;
  initial begin
    `ifndef verilator
      #0.002 begin end
    `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_8 = {1{$random}};
  _T_602 = _GEN_8[0:0];
  `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_9 = {1{$random}};
  _GEN_2 = _GEN_9[0:0];
  `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_10 = {1{$random}};
  _GEN_3 = _GEN_10[0:0];
  `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_11 = {1{$random}};
  _GEN_4 = _GEN_11[0:0];
  `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_12 = {1{$random}};
  _GEN_5 = _GEN_12[0:0];
  `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_13 = {1{$random}};
  _GEN_6 = _GEN_13[0:0];
  `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_14 = {1{$random}};
  _GEN_7 = _GEN_14[0:0];
  `endif
  end
`endif
  always @(posedge clock) begin
    if (reset) begin
      _T_602 <= 1'h0;
    end else begin
      _T_602 <= burstVld;
    end
  end
endmodule
module Fringe(
  input         clock,
  input         reset,
  input  [1:0]  io_raddr,
  input         io_wen,
  input  [1:0]  io_waddr,
  input  [31:0] io_wdata,
  output [31:0] io_rdata,
  output        io_enable,
  input         io_done,
  output [31:0] io_argIns_0,
  output        io_argOuts_0_ready,
  input         io_argOuts_0_valid,
  input  [31:0] io_argOuts_0_bits,
  input         io_dram_cmd_ready,
  output        io_dram_cmd_valid,
  output [31:0] io_dram_cmd_bits_addr,
  output        io_dram_cmd_bits_isWr,
  output [31:0] io_dram_cmd_bits_tag,
  output [31:0] io_dram_cmd_bits_streamId,
  output [31:0] io_dram_cmd_bits_wdata_0,
  output [31:0] io_dram_cmd_bits_wdata_1,
  output [31:0] io_dram_cmd_bits_wdata_2,
  output [31:0] io_dram_cmd_bits_wdata_3,
  output [31:0] io_dram_cmd_bits_wdata_4,
  output [31:0] io_dram_cmd_bits_wdata_5,
  output [31:0] io_dram_cmd_bits_wdata_6,
  output [31:0] io_dram_cmd_bits_wdata_7,
  output [31:0] io_dram_cmd_bits_wdata_8,
  output [31:0] io_dram_cmd_bits_wdata_9,
  output [31:0] io_dram_cmd_bits_wdata_10,
  output [31:0] io_dram_cmd_bits_wdata_11,
  output [31:0] io_dram_cmd_bits_wdata_12,
  output [31:0] io_dram_cmd_bits_wdata_13,
  output [31:0] io_dram_cmd_bits_wdata_14,
  output [31:0] io_dram_cmd_bits_wdata_15,
  output        io_dram_resp_ready,
  input         io_dram_resp_valid,
  input  [31:0] io_dram_resp_bits_rdata_0,
  input  [31:0] io_dram_resp_bits_rdata_1,
  input  [31:0] io_dram_resp_bits_rdata_2,
  input  [31:0] io_dram_resp_bits_rdata_3,
  input  [31:0] io_dram_resp_bits_rdata_4,
  input  [31:0] io_dram_resp_bits_rdata_5,
  input  [31:0] io_dram_resp_bits_rdata_6,
  input  [31:0] io_dram_resp_bits_rdata_7,
  input  [31:0] io_dram_resp_bits_rdata_8,
  input  [31:0] io_dram_resp_bits_rdata_9,
  input  [31:0] io_dram_resp_bits_rdata_10,
  input  [31:0] io_dram_resp_bits_rdata_11,
  input  [31:0] io_dram_resp_bits_rdata_12,
  input  [31:0] io_dram_resp_bits_rdata_13,
  input  [31:0] io_dram_resp_bits_rdata_14,
  input  [31:0] io_dram_resp_bits_rdata_15,
  input  [31:0] io_dram_resp_bits_tag,
  input  [31:0] io_dram_resp_bits_streamId
);
  wire  regs_clock;
  wire  regs_reset;
  wire [1:0] regs_io_raddr;
  wire  regs_io_wen;
  wire [1:0] regs_io_waddr;
  wire [31:0] regs_io_wdata;
  wire [31:0] regs_io_rdata;
  wire [31:0] regs_io_argIns_0;
  wire [31:0] regs_io_argIns_1;
  wire [31:0] regs_io_argIns_2;
  wire  regs_io_argOuts_0_ready;
  wire  regs_io_argOuts_0_valid;
  wire [31:0] regs_io_argOuts_0_bits;
  wire  regs_io_argOuts_1_ready;
  wire  regs_io_argOuts_1_valid;
  wire [31:0] regs_io_argOuts_1_bits;
  wire  _T_577;
  wire  _T_578;
  wire  _T_579;
  wire  _T_580;
  wire  depulser_clock;
  wire  depulser_reset;
  wire  depulser_io_in;
  wire  depulser_io_rst;
  wire  depulser_io_out;
  wire  status_ready;
  wire  status_valid;
  wire [31:0] status_bits;
  wire [31:0] _GEN_0;
  wire [31:0] _T_595;
  wire  mag_clock;
  wire  mag_reset;
  wire  mag_io_dram_cmd_ready;
  wire  mag_io_dram_cmd_valid;
  wire [31:0] mag_io_dram_cmd_bits_addr;
  wire  mag_io_dram_cmd_bits_isWr;
  wire [31:0] mag_io_dram_cmd_bits_tag;
  wire [31:0] mag_io_dram_cmd_bits_streamId;
  wire [31:0] mag_io_dram_cmd_bits_wdata_0;
  wire [31:0] mag_io_dram_cmd_bits_wdata_1;
  wire [31:0] mag_io_dram_cmd_bits_wdata_2;
  wire [31:0] mag_io_dram_cmd_bits_wdata_3;
  wire [31:0] mag_io_dram_cmd_bits_wdata_4;
  wire [31:0] mag_io_dram_cmd_bits_wdata_5;
  wire [31:0] mag_io_dram_cmd_bits_wdata_6;
  wire [31:0] mag_io_dram_cmd_bits_wdata_7;
  wire [31:0] mag_io_dram_cmd_bits_wdata_8;
  wire [31:0] mag_io_dram_cmd_bits_wdata_9;
  wire [31:0] mag_io_dram_cmd_bits_wdata_10;
  wire [31:0] mag_io_dram_cmd_bits_wdata_11;
  wire [31:0] mag_io_dram_cmd_bits_wdata_12;
  wire [31:0] mag_io_dram_cmd_bits_wdata_13;
  wire [31:0] mag_io_dram_cmd_bits_wdata_14;
  wire [31:0] mag_io_dram_cmd_bits_wdata_15;
  wire  mag_io_dram_resp_ready;
  wire  mag_io_dram_resp_valid;
  wire [31:0] mag_io_dram_resp_bits_rdata_0;
  wire [31:0] mag_io_dram_resp_bits_rdata_1;
  wire [31:0] mag_io_dram_resp_bits_rdata_2;
  wire [31:0] mag_io_dram_resp_bits_rdata_3;
  wire [31:0] mag_io_dram_resp_bits_rdata_4;
  wire [31:0] mag_io_dram_resp_bits_rdata_5;
  wire [31:0] mag_io_dram_resp_bits_rdata_6;
  wire [31:0] mag_io_dram_resp_bits_rdata_7;
  wire [31:0] mag_io_dram_resp_bits_rdata_8;
  wire [31:0] mag_io_dram_resp_bits_rdata_9;
  wire [31:0] mag_io_dram_resp_bits_rdata_10;
  wire [31:0] mag_io_dram_resp_bits_rdata_11;
  wire [31:0] mag_io_dram_resp_bits_rdata_12;
  wire [31:0] mag_io_dram_resp_bits_rdata_13;
  wire [31:0] mag_io_dram_resp_bits_rdata_14;
  wire [31:0] mag_io_dram_resp_bits_rdata_15;
  wire [31:0] mag_io_dram_resp_bits_tag;
  wire [31:0] mag_io_dram_resp_bits_streamId;
  wire  mag_io_config_scatterGather;
  wire  magConfig_scatterGather;
  reg  _GEN_1;
  reg [31:0] _GEN_4;
  reg  _GEN_2;
  reg [31:0] _GEN_5;
  reg  _GEN_3;
  reg [31:0] _GEN_6;
  RegFile regs (
    .clock(regs_clock),
    .reset(regs_reset),
    .io_raddr(regs_io_raddr),
    .io_wen(regs_io_wen),
    .io_waddr(regs_io_waddr),
    .io_wdata(regs_io_wdata),
    .io_rdata(regs_io_rdata),
    .io_argIns_0(regs_io_argIns_0),
    .io_argIns_1(regs_io_argIns_1),
    .io_argIns_2(regs_io_argIns_2),
    .io_argOuts_0_ready(regs_io_argOuts_0_ready),
    .io_argOuts_0_valid(regs_io_argOuts_0_valid),
    .io_argOuts_0_bits(regs_io_argOuts_0_bits),
    .io_argOuts_1_ready(regs_io_argOuts_1_ready),
    .io_argOuts_1_valid(regs_io_argOuts_1_valid),
    .io_argOuts_1_bits(regs_io_argOuts_1_bits)
  );
  Depulser depulser (
    .clock(depulser_clock),
    .reset(depulser_reset),
    .io_in(depulser_io_in),
    .io_rst(depulser_io_rst),
    .io_out(depulser_io_out)
  );
  MAGCore mag (
    .clock(mag_clock),
    .reset(mag_reset),
    .io_dram_cmd_ready(mag_io_dram_cmd_ready),
    .io_dram_cmd_valid(mag_io_dram_cmd_valid),
    .io_dram_cmd_bits_addr(mag_io_dram_cmd_bits_addr),
    .io_dram_cmd_bits_isWr(mag_io_dram_cmd_bits_isWr),
    .io_dram_cmd_bits_tag(mag_io_dram_cmd_bits_tag),
    .io_dram_cmd_bits_streamId(mag_io_dram_cmd_bits_streamId),
    .io_dram_cmd_bits_wdata_0(mag_io_dram_cmd_bits_wdata_0),
    .io_dram_cmd_bits_wdata_1(mag_io_dram_cmd_bits_wdata_1),
    .io_dram_cmd_bits_wdata_2(mag_io_dram_cmd_bits_wdata_2),
    .io_dram_cmd_bits_wdata_3(mag_io_dram_cmd_bits_wdata_3),
    .io_dram_cmd_bits_wdata_4(mag_io_dram_cmd_bits_wdata_4),
    .io_dram_cmd_bits_wdata_5(mag_io_dram_cmd_bits_wdata_5),
    .io_dram_cmd_bits_wdata_6(mag_io_dram_cmd_bits_wdata_6),
    .io_dram_cmd_bits_wdata_7(mag_io_dram_cmd_bits_wdata_7),
    .io_dram_cmd_bits_wdata_8(mag_io_dram_cmd_bits_wdata_8),
    .io_dram_cmd_bits_wdata_9(mag_io_dram_cmd_bits_wdata_9),
    .io_dram_cmd_bits_wdata_10(mag_io_dram_cmd_bits_wdata_10),
    .io_dram_cmd_bits_wdata_11(mag_io_dram_cmd_bits_wdata_11),
    .io_dram_cmd_bits_wdata_12(mag_io_dram_cmd_bits_wdata_12),
    .io_dram_cmd_bits_wdata_13(mag_io_dram_cmd_bits_wdata_13),
    .io_dram_cmd_bits_wdata_14(mag_io_dram_cmd_bits_wdata_14),
    .io_dram_cmd_bits_wdata_15(mag_io_dram_cmd_bits_wdata_15),
    .io_dram_resp_ready(mag_io_dram_resp_ready),
    .io_dram_resp_valid(mag_io_dram_resp_valid),
    .io_dram_resp_bits_rdata_0(mag_io_dram_resp_bits_rdata_0),
    .io_dram_resp_bits_rdata_1(mag_io_dram_resp_bits_rdata_1),
    .io_dram_resp_bits_rdata_2(mag_io_dram_resp_bits_rdata_2),
    .io_dram_resp_bits_rdata_3(mag_io_dram_resp_bits_rdata_3),
    .io_dram_resp_bits_rdata_4(mag_io_dram_resp_bits_rdata_4),
    .io_dram_resp_bits_rdata_5(mag_io_dram_resp_bits_rdata_5),
    .io_dram_resp_bits_rdata_6(mag_io_dram_resp_bits_rdata_6),
    .io_dram_resp_bits_rdata_7(mag_io_dram_resp_bits_rdata_7),
    .io_dram_resp_bits_rdata_8(mag_io_dram_resp_bits_rdata_8),
    .io_dram_resp_bits_rdata_9(mag_io_dram_resp_bits_rdata_9),
    .io_dram_resp_bits_rdata_10(mag_io_dram_resp_bits_rdata_10),
    .io_dram_resp_bits_rdata_11(mag_io_dram_resp_bits_rdata_11),
    .io_dram_resp_bits_rdata_12(mag_io_dram_resp_bits_rdata_12),
    .io_dram_resp_bits_rdata_13(mag_io_dram_resp_bits_rdata_13),
    .io_dram_resp_bits_rdata_14(mag_io_dram_resp_bits_rdata_14),
    .io_dram_resp_bits_rdata_15(mag_io_dram_resp_bits_rdata_15),
    .io_dram_resp_bits_tag(mag_io_dram_resp_bits_tag),
    .io_dram_resp_bits_streamId(mag_io_dram_resp_bits_streamId),
    .io_config_scatterGather(mag_io_config_scatterGather)
  );
  assign io_rdata = regs_io_rdata;
  assign io_enable = _T_580;
  assign io_argIns_0 = regs_io_argIns_2;
  assign io_argOuts_0_ready = _GEN_1;
  assign io_dram_cmd_valid = mag_io_dram_cmd_valid;
  assign io_dram_cmd_bits_addr = mag_io_dram_cmd_bits_addr;
  assign io_dram_cmd_bits_isWr = mag_io_dram_cmd_bits_isWr;
  assign io_dram_cmd_bits_tag = mag_io_dram_cmd_bits_tag;
  assign io_dram_cmd_bits_streamId = mag_io_dram_cmd_bits_streamId;
  assign io_dram_cmd_bits_wdata_0 = mag_io_dram_cmd_bits_wdata_0;
  assign io_dram_cmd_bits_wdata_1 = mag_io_dram_cmd_bits_wdata_1;
  assign io_dram_cmd_bits_wdata_2 = mag_io_dram_cmd_bits_wdata_2;
  assign io_dram_cmd_bits_wdata_3 = mag_io_dram_cmd_bits_wdata_3;
  assign io_dram_cmd_bits_wdata_4 = mag_io_dram_cmd_bits_wdata_4;
  assign io_dram_cmd_bits_wdata_5 = mag_io_dram_cmd_bits_wdata_5;
  assign io_dram_cmd_bits_wdata_6 = mag_io_dram_cmd_bits_wdata_6;
  assign io_dram_cmd_bits_wdata_7 = mag_io_dram_cmd_bits_wdata_7;
  assign io_dram_cmd_bits_wdata_8 = mag_io_dram_cmd_bits_wdata_8;
  assign io_dram_cmd_bits_wdata_9 = mag_io_dram_cmd_bits_wdata_9;
  assign io_dram_cmd_bits_wdata_10 = mag_io_dram_cmd_bits_wdata_10;
  assign io_dram_cmd_bits_wdata_11 = mag_io_dram_cmd_bits_wdata_11;
  assign io_dram_cmd_bits_wdata_12 = mag_io_dram_cmd_bits_wdata_12;
  assign io_dram_cmd_bits_wdata_13 = mag_io_dram_cmd_bits_wdata_13;
  assign io_dram_cmd_bits_wdata_14 = mag_io_dram_cmd_bits_wdata_14;
  assign io_dram_cmd_bits_wdata_15 = mag_io_dram_cmd_bits_wdata_15;
  assign io_dram_resp_ready = mag_io_dram_cmd_ready;
  assign regs_clock = clock;
  assign regs_reset = reset;
  assign regs_io_raddr = io_raddr;
  assign regs_io_wen = io_wen;
  assign regs_io_waddr = io_waddr;
  assign regs_io_wdata = io_wdata;
  assign regs_io_argOuts_0_valid = status_valid;
  assign regs_io_argOuts_0_bits = status_bits;
  assign regs_io_argOuts_1_valid = io_argOuts_0_valid;
  assign regs_io_argOuts_1_bits = io_argOuts_0_bits;
  assign _T_577 = regs_io_argIns_0[0];
  assign _T_578 = regs_io_argIns_1[0];
  assign _T_579 = ~ _T_578;
  assign _T_580 = _T_577 & _T_579;
  assign depulser_clock = clock;
  assign depulser_reset = reset;
  assign depulser_io_in = io_done;
  assign depulser_io_rst = _GEN_2;
  assign status_ready = _GEN_3;
  assign status_valid = depulser_io_out;
  assign status_bits = _T_595;
  assign _GEN_0 = {{31'd0}, depulser_io_out};
  assign _T_595 = regs_io_argIns_0 & _GEN_0;
  assign mag_clock = clock;
  assign mag_reset = reset;
  assign mag_io_dram_cmd_ready = io_dram_cmd_ready;
  assign mag_io_dram_resp_valid = io_dram_resp_valid;
  assign mag_io_dram_resp_bits_rdata_0 = io_dram_resp_bits_rdata_0;
  assign mag_io_dram_resp_bits_rdata_1 = io_dram_resp_bits_rdata_1;
  assign mag_io_dram_resp_bits_rdata_2 = io_dram_resp_bits_rdata_2;
  assign mag_io_dram_resp_bits_rdata_3 = io_dram_resp_bits_rdata_3;
  assign mag_io_dram_resp_bits_rdata_4 = io_dram_resp_bits_rdata_4;
  assign mag_io_dram_resp_bits_rdata_5 = io_dram_resp_bits_rdata_5;
  assign mag_io_dram_resp_bits_rdata_6 = io_dram_resp_bits_rdata_6;
  assign mag_io_dram_resp_bits_rdata_7 = io_dram_resp_bits_rdata_7;
  assign mag_io_dram_resp_bits_rdata_8 = io_dram_resp_bits_rdata_8;
  assign mag_io_dram_resp_bits_rdata_9 = io_dram_resp_bits_rdata_9;
  assign mag_io_dram_resp_bits_rdata_10 = io_dram_resp_bits_rdata_10;
  assign mag_io_dram_resp_bits_rdata_11 = io_dram_resp_bits_rdata_11;
  assign mag_io_dram_resp_bits_rdata_12 = io_dram_resp_bits_rdata_12;
  assign mag_io_dram_resp_bits_rdata_13 = io_dram_resp_bits_rdata_13;
  assign mag_io_dram_resp_bits_rdata_14 = io_dram_resp_bits_rdata_14;
  assign mag_io_dram_resp_bits_rdata_15 = io_dram_resp_bits_rdata_15;
  assign mag_io_dram_resp_bits_tag = io_dram_resp_bits_tag;
  assign mag_io_dram_resp_bits_streamId = io_dram_resp_bits_streamId;
  assign mag_io_config_scatterGather = magConfig_scatterGather;
  assign magConfig_scatterGather = 1'h0;
`ifdef RANDOMIZE
  integer initvar;
  initial begin
    `ifndef verilator
      #0.002 begin end
    `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_4 = {1{$random}};
  _GEN_1 = _GEN_4[0:0];
  `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_5 = {1{$random}};
  _GEN_2 = _GEN_5[0:0];
  `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_6 = {1{$random}};
  _GEN_3 = _GEN_6[0:0];
  `endif
  end
`endif
endmodule
module AXI4LiteToRFBridge(
  input         clock,
  input         reset,
  input  [31:0] io_S_AXI_AWADDR,
  input  [2:0]  io_S_AXI_AWPROT,
  input         io_S_AXI_AWVALID,
  output        io_S_AXI_AWREADY,
  input  [31:0] io_S_AXI_ARADDR,
  input  [2:0]  io_S_AXI_ARPROT,
  input         io_S_AXI_ARVALID,
  output        io_S_AXI_ARREADY,
  input  [31:0] io_S_AXI_WDATA,
  input  [3:0]  io_S_AXI_WSTRB,
  input         io_S_AXI_WVALID,
  output        io_S_AXI_WREADY,
  output [31:0] io_S_AXI_RDATA,
  output [1:0]  io_S_AXI_RRESP,
  output        io_S_AXI_RVALID,
  input         io_S_AXI_RREADY,
  output [1:0]  io_S_AXI_BRESP,
  output        io_S_AXI_BVALID,
  input         io_S_AXI_BREADY,
  output [31:0] io_raddr,
  output        io_wen,
  output [31:0] io_waddr,
  output [31:0] io_wdata,
  input  [31:0] io_rdata
);
  wire [31:0] d_rf_rdata;
  wire [31:0] d_rf_wdata;
  wire [31:0] d_rf_waddr;
  wire  d_rf_wen;
  wire [31:0] d_rf_raddr;
  wire  d_S_AXI_ARESETN;
  wire  d_S_AXI_ACLK;
  wire [31:0] d_S_AXI_AWADDR;
  wire [2:0] d_S_AXI_AWPROT;
  wire  d_S_AXI_AWVALID;
  wire  d_S_AXI_AWREADY;
  wire [31:0] d_S_AXI_ARADDR;
  wire [2:0] d_S_AXI_ARPROT;
  wire  d_S_AXI_ARVALID;
  wire  d_S_AXI_ARREADY;
  wire [31:0] d_S_AXI_WDATA;
  wire [3:0] d_S_AXI_WSTRB;
  wire  d_S_AXI_WVALID;
  wire  d_S_AXI_WREADY;
  wire [31:0] d_S_AXI_RDATA;
  wire [1:0] d_S_AXI_RRESP;
  wire  d_S_AXI_RVALID;
  wire  d_S_AXI_RREADY;
  wire [1:0] d_S_AXI_BRESP;
  wire  d_S_AXI_BVALID;
  wire  d_S_AXI_BREADY;
  AXI4LiteToRFBridgeVerilog d (
    .rf_rdata(d_rf_rdata),
    .rf_wdata(d_rf_wdata),
    .rf_waddr(d_rf_waddr),
    .rf_wen(d_rf_wen),
    .rf_raddr(d_rf_raddr),
    .S_AXI_ARESETN(d_S_AXI_ARESETN),
    .S_AXI_ACLK(d_S_AXI_ACLK),
    .S_AXI_AWADDR(d_S_AXI_AWADDR),
    .S_AXI_AWPROT(d_S_AXI_AWPROT),
    .S_AXI_AWVALID(d_S_AXI_AWVALID),
    .S_AXI_AWREADY(d_S_AXI_AWREADY),
    .S_AXI_ARADDR(d_S_AXI_ARADDR),
    .S_AXI_ARPROT(d_S_AXI_ARPROT),
    .S_AXI_ARVALID(d_S_AXI_ARVALID),
    .S_AXI_ARREADY(d_S_AXI_ARREADY),
    .S_AXI_WDATA(d_S_AXI_WDATA),
    .S_AXI_WSTRB(d_S_AXI_WSTRB),
    .S_AXI_WVALID(d_S_AXI_WVALID),
    .S_AXI_WREADY(d_S_AXI_WREADY),
    .S_AXI_RDATA(d_S_AXI_RDATA),
    .S_AXI_RRESP(d_S_AXI_RRESP),
    .S_AXI_RVALID(d_S_AXI_RVALID),
    .S_AXI_RREADY(d_S_AXI_RREADY),
    .S_AXI_BRESP(d_S_AXI_BRESP),
    .S_AXI_BVALID(d_S_AXI_BVALID),
    .S_AXI_BREADY(d_S_AXI_BREADY)
  );
  assign io_S_AXI_AWREADY = d_S_AXI_AWREADY;
  assign io_S_AXI_ARREADY = d_S_AXI_ARREADY;
  assign io_S_AXI_WREADY = d_S_AXI_WREADY;
  assign io_S_AXI_RDATA = d_S_AXI_RDATA;
  assign io_S_AXI_RRESP = d_S_AXI_RRESP;
  assign io_S_AXI_RVALID = d_S_AXI_RVALID;
  assign io_S_AXI_BRESP = d_S_AXI_BRESP;
  assign io_S_AXI_BVALID = d_S_AXI_BVALID;
  assign io_raddr = d_rf_raddr;
  assign io_wen = d_rf_wen;
  assign io_waddr = d_rf_waddr;
  assign io_wdata = d_rf_wdata;
  assign d_rf_rdata = io_rdata;
  assign d_S_AXI_ARESETN = reset;
  assign d_S_AXI_ACLK = clock;
  assign d_S_AXI_AWADDR = io_S_AXI_AWADDR;
  assign d_S_AXI_AWPROT = io_S_AXI_AWPROT;
  assign d_S_AXI_AWVALID = io_S_AXI_AWVALID;
  assign d_S_AXI_ARADDR = io_S_AXI_ARADDR;
  assign d_S_AXI_ARPROT = io_S_AXI_ARPROT;
  assign d_S_AXI_ARVALID = io_S_AXI_ARVALID;
  assign d_S_AXI_WDATA = io_S_AXI_WDATA;
  assign d_S_AXI_WSTRB = io_S_AXI_WSTRB;
  assign d_S_AXI_WVALID = io_S_AXI_WVALID;
  assign d_S_AXI_RREADY = io_S_AXI_RREADY;
  assign d_S_AXI_BREADY = io_S_AXI_BREADY;
endmodule
module FringeDE1SoC(
  input         clock,
  input         reset,
  input  [31:0] io_S_AXI_AWADDR,
  input  [2:0]  io_S_AXI_AWPROT,
  input         io_S_AXI_AWVALID,
  output        io_S_AXI_AWREADY,
  input  [31:0] io_S_AXI_ARADDR,
  input  [2:0]  io_S_AXI_ARPROT,
  input         io_S_AXI_ARVALID,
  output        io_S_AXI_ARREADY,
  input  [31:0] io_S_AXI_WDATA,
  input  [3:0]  io_S_AXI_WSTRB,
  input         io_S_AXI_WVALID,
  output        io_S_AXI_WREADY,
  output [31:0] io_S_AXI_RDATA,
  output [1:0]  io_S_AXI_RRESP,
  output        io_S_AXI_RVALID,
  input         io_S_AXI_RREADY,
  output [1:0]  io_S_AXI_BRESP,
  output        io_S_AXI_BVALID,
  input         io_S_AXI_BREADY,
  output        io_enable,
  input         io_done,
  output [31:0] io_argIns_0,
  output        io_argOuts_0_ready,
  input         io_argOuts_0_valid,
  input  [31:0] io_argOuts_0_bits
);
  wire  fringeCommon_clock;
  wire  fringeCommon_reset;
  wire [1:0] fringeCommon_io_raddr;
  wire  fringeCommon_io_wen;
  wire [1:0] fringeCommon_io_waddr;
  wire [31:0] fringeCommon_io_wdata;
  wire [31:0] fringeCommon_io_rdata;
  wire  fringeCommon_io_enable;
  wire  fringeCommon_io_done;
  wire [31:0] fringeCommon_io_argIns_0;
  wire  fringeCommon_io_argOuts_0_ready;
  wire  fringeCommon_io_argOuts_0_valid;
  wire [31:0] fringeCommon_io_argOuts_0_bits;
  wire  fringeCommon_io_dram_cmd_ready;
  wire  fringeCommon_io_dram_cmd_valid;
  wire [31:0] fringeCommon_io_dram_cmd_bits_addr;
  wire  fringeCommon_io_dram_cmd_bits_isWr;
  wire [31:0] fringeCommon_io_dram_cmd_bits_tag;
  wire [31:0] fringeCommon_io_dram_cmd_bits_streamId;
  wire [31:0] fringeCommon_io_dram_cmd_bits_wdata_0;
  wire [31:0] fringeCommon_io_dram_cmd_bits_wdata_1;
  wire [31:0] fringeCommon_io_dram_cmd_bits_wdata_2;
  wire [31:0] fringeCommon_io_dram_cmd_bits_wdata_3;
  wire [31:0] fringeCommon_io_dram_cmd_bits_wdata_4;
  wire [31:0] fringeCommon_io_dram_cmd_bits_wdata_5;
  wire [31:0] fringeCommon_io_dram_cmd_bits_wdata_6;
  wire [31:0] fringeCommon_io_dram_cmd_bits_wdata_7;
  wire [31:0] fringeCommon_io_dram_cmd_bits_wdata_8;
  wire [31:0] fringeCommon_io_dram_cmd_bits_wdata_9;
  wire [31:0] fringeCommon_io_dram_cmd_bits_wdata_10;
  wire [31:0] fringeCommon_io_dram_cmd_bits_wdata_11;
  wire [31:0] fringeCommon_io_dram_cmd_bits_wdata_12;
  wire [31:0] fringeCommon_io_dram_cmd_bits_wdata_13;
  wire [31:0] fringeCommon_io_dram_cmd_bits_wdata_14;
  wire [31:0] fringeCommon_io_dram_cmd_bits_wdata_15;
  wire  fringeCommon_io_dram_resp_ready;
  wire  fringeCommon_io_dram_resp_valid;
  wire [31:0] fringeCommon_io_dram_resp_bits_rdata_0;
  wire [31:0] fringeCommon_io_dram_resp_bits_rdata_1;
  wire [31:0] fringeCommon_io_dram_resp_bits_rdata_2;
  wire [31:0] fringeCommon_io_dram_resp_bits_rdata_3;
  wire [31:0] fringeCommon_io_dram_resp_bits_rdata_4;
  wire [31:0] fringeCommon_io_dram_resp_bits_rdata_5;
  wire [31:0] fringeCommon_io_dram_resp_bits_rdata_6;
  wire [31:0] fringeCommon_io_dram_resp_bits_rdata_7;
  wire [31:0] fringeCommon_io_dram_resp_bits_rdata_8;
  wire [31:0] fringeCommon_io_dram_resp_bits_rdata_9;
  wire [31:0] fringeCommon_io_dram_resp_bits_rdata_10;
  wire [31:0] fringeCommon_io_dram_resp_bits_rdata_11;
  wire [31:0] fringeCommon_io_dram_resp_bits_rdata_12;
  wire [31:0] fringeCommon_io_dram_resp_bits_rdata_13;
  wire [31:0] fringeCommon_io_dram_resp_bits_rdata_14;
  wire [31:0] fringeCommon_io_dram_resp_bits_rdata_15;
  wire [31:0] fringeCommon_io_dram_resp_bits_tag;
  wire [31:0] fringeCommon_io_dram_resp_bits_streamId;
  wire  axiLiteBridge_clock;
  wire  axiLiteBridge_reset;
  wire [31:0] axiLiteBridge_io_S_AXI_AWADDR;
  wire [2:0] axiLiteBridge_io_S_AXI_AWPROT;
  wire  axiLiteBridge_io_S_AXI_AWVALID;
  wire  axiLiteBridge_io_S_AXI_AWREADY;
  wire [31:0] axiLiteBridge_io_S_AXI_ARADDR;
  wire [2:0] axiLiteBridge_io_S_AXI_ARPROT;
  wire  axiLiteBridge_io_S_AXI_ARVALID;
  wire  axiLiteBridge_io_S_AXI_ARREADY;
  wire [31:0] axiLiteBridge_io_S_AXI_WDATA;
  wire [3:0] axiLiteBridge_io_S_AXI_WSTRB;
  wire  axiLiteBridge_io_S_AXI_WVALID;
  wire  axiLiteBridge_io_S_AXI_WREADY;
  wire [31:0] axiLiteBridge_io_S_AXI_RDATA;
  wire [1:0] axiLiteBridge_io_S_AXI_RRESP;
  wire  axiLiteBridge_io_S_AXI_RVALID;
  wire  axiLiteBridge_io_S_AXI_RREADY;
  wire [1:0] axiLiteBridge_io_S_AXI_BRESP;
  wire  axiLiteBridge_io_S_AXI_BVALID;
  wire  axiLiteBridge_io_S_AXI_BREADY;
  wire [31:0] axiLiteBridge_io_raddr;
  wire  axiLiteBridge_io_wen;
  wire [31:0] axiLiteBridge_io_waddr;
  wire [31:0] axiLiteBridge_io_wdata;
  wire [31:0] axiLiteBridge_io_rdata;
  wire  _T_120;
  reg  _GEN_0;
  reg [31:0] _GEN_20;
  reg  _GEN_1;
  reg [31:0] _GEN_21;
  reg [31:0] _GEN_2;
  reg [31:0] _GEN_22;
  reg [31:0] _GEN_3;
  reg [31:0] _GEN_23;
  reg [31:0] _GEN_4;
  reg [31:0] _GEN_24;
  reg [31:0] _GEN_5;
  reg [31:0] _GEN_25;
  reg [31:0] _GEN_6;
  reg [31:0] _GEN_26;
  reg [31:0] _GEN_7;
  reg [31:0] _GEN_27;
  reg [31:0] _GEN_8;
  reg [31:0] _GEN_28;
  reg [31:0] _GEN_9;
  reg [31:0] _GEN_29;
  reg [31:0] _GEN_10;
  reg [31:0] _GEN_30;
  reg [31:0] _GEN_11;
  reg [31:0] _GEN_31;
  reg [31:0] _GEN_12;
  reg [31:0] _GEN_32;
  reg [31:0] _GEN_13;
  reg [31:0] _GEN_33;
  reg [31:0] _GEN_14;
  reg [31:0] _GEN_34;
  reg [31:0] _GEN_15;
  reg [31:0] _GEN_35;
  reg [31:0] _GEN_16;
  reg [31:0] _GEN_36;
  reg [31:0] _GEN_17;
  reg [31:0] _GEN_37;
  reg [31:0] _GEN_18;
  reg [31:0] _GEN_38;
  reg [31:0] _GEN_19;
  reg [31:0] _GEN_39;
  Fringe fringeCommon (
    .clock(fringeCommon_clock),
    .reset(fringeCommon_reset),
    .io_raddr(fringeCommon_io_raddr),
    .io_wen(fringeCommon_io_wen),
    .io_waddr(fringeCommon_io_waddr),
    .io_wdata(fringeCommon_io_wdata),
    .io_rdata(fringeCommon_io_rdata),
    .io_enable(fringeCommon_io_enable),
    .io_done(fringeCommon_io_done),
    .io_argIns_0(fringeCommon_io_argIns_0),
    .io_argOuts_0_ready(fringeCommon_io_argOuts_0_ready),
    .io_argOuts_0_valid(fringeCommon_io_argOuts_0_valid),
    .io_argOuts_0_bits(fringeCommon_io_argOuts_0_bits),
    .io_dram_cmd_ready(fringeCommon_io_dram_cmd_ready),
    .io_dram_cmd_valid(fringeCommon_io_dram_cmd_valid),
    .io_dram_cmd_bits_addr(fringeCommon_io_dram_cmd_bits_addr),
    .io_dram_cmd_bits_isWr(fringeCommon_io_dram_cmd_bits_isWr),
    .io_dram_cmd_bits_tag(fringeCommon_io_dram_cmd_bits_tag),
    .io_dram_cmd_bits_streamId(fringeCommon_io_dram_cmd_bits_streamId),
    .io_dram_cmd_bits_wdata_0(fringeCommon_io_dram_cmd_bits_wdata_0),
    .io_dram_cmd_bits_wdata_1(fringeCommon_io_dram_cmd_bits_wdata_1),
    .io_dram_cmd_bits_wdata_2(fringeCommon_io_dram_cmd_bits_wdata_2),
    .io_dram_cmd_bits_wdata_3(fringeCommon_io_dram_cmd_bits_wdata_3),
    .io_dram_cmd_bits_wdata_4(fringeCommon_io_dram_cmd_bits_wdata_4),
    .io_dram_cmd_bits_wdata_5(fringeCommon_io_dram_cmd_bits_wdata_5),
    .io_dram_cmd_bits_wdata_6(fringeCommon_io_dram_cmd_bits_wdata_6),
    .io_dram_cmd_bits_wdata_7(fringeCommon_io_dram_cmd_bits_wdata_7),
    .io_dram_cmd_bits_wdata_8(fringeCommon_io_dram_cmd_bits_wdata_8),
    .io_dram_cmd_bits_wdata_9(fringeCommon_io_dram_cmd_bits_wdata_9),
    .io_dram_cmd_bits_wdata_10(fringeCommon_io_dram_cmd_bits_wdata_10),
    .io_dram_cmd_bits_wdata_11(fringeCommon_io_dram_cmd_bits_wdata_11),
    .io_dram_cmd_bits_wdata_12(fringeCommon_io_dram_cmd_bits_wdata_12),
    .io_dram_cmd_bits_wdata_13(fringeCommon_io_dram_cmd_bits_wdata_13),
    .io_dram_cmd_bits_wdata_14(fringeCommon_io_dram_cmd_bits_wdata_14),
    .io_dram_cmd_bits_wdata_15(fringeCommon_io_dram_cmd_bits_wdata_15),
    .io_dram_resp_ready(fringeCommon_io_dram_resp_ready),
    .io_dram_resp_valid(fringeCommon_io_dram_resp_valid),
    .io_dram_resp_bits_rdata_0(fringeCommon_io_dram_resp_bits_rdata_0),
    .io_dram_resp_bits_rdata_1(fringeCommon_io_dram_resp_bits_rdata_1),
    .io_dram_resp_bits_rdata_2(fringeCommon_io_dram_resp_bits_rdata_2),
    .io_dram_resp_bits_rdata_3(fringeCommon_io_dram_resp_bits_rdata_3),
    .io_dram_resp_bits_rdata_4(fringeCommon_io_dram_resp_bits_rdata_4),
    .io_dram_resp_bits_rdata_5(fringeCommon_io_dram_resp_bits_rdata_5),
    .io_dram_resp_bits_rdata_6(fringeCommon_io_dram_resp_bits_rdata_6),
    .io_dram_resp_bits_rdata_7(fringeCommon_io_dram_resp_bits_rdata_7),
    .io_dram_resp_bits_rdata_8(fringeCommon_io_dram_resp_bits_rdata_8),
    .io_dram_resp_bits_rdata_9(fringeCommon_io_dram_resp_bits_rdata_9),
    .io_dram_resp_bits_rdata_10(fringeCommon_io_dram_resp_bits_rdata_10),
    .io_dram_resp_bits_rdata_11(fringeCommon_io_dram_resp_bits_rdata_11),
    .io_dram_resp_bits_rdata_12(fringeCommon_io_dram_resp_bits_rdata_12),
    .io_dram_resp_bits_rdata_13(fringeCommon_io_dram_resp_bits_rdata_13),
    .io_dram_resp_bits_rdata_14(fringeCommon_io_dram_resp_bits_rdata_14),
    .io_dram_resp_bits_rdata_15(fringeCommon_io_dram_resp_bits_rdata_15),
    .io_dram_resp_bits_tag(fringeCommon_io_dram_resp_bits_tag),
    .io_dram_resp_bits_streamId(fringeCommon_io_dram_resp_bits_streamId)
  );
  AXI4LiteToRFBridge axiLiteBridge (
    .clock(axiLiteBridge_clock),
    .reset(axiLiteBridge_reset),
    .io_S_AXI_AWADDR(axiLiteBridge_io_S_AXI_AWADDR),
    .io_S_AXI_AWPROT(axiLiteBridge_io_S_AXI_AWPROT),
    .io_S_AXI_AWVALID(axiLiteBridge_io_S_AXI_AWVALID),
    .io_S_AXI_AWREADY(axiLiteBridge_io_S_AXI_AWREADY),
    .io_S_AXI_ARADDR(axiLiteBridge_io_S_AXI_ARADDR),
    .io_S_AXI_ARPROT(axiLiteBridge_io_S_AXI_ARPROT),
    .io_S_AXI_ARVALID(axiLiteBridge_io_S_AXI_ARVALID),
    .io_S_AXI_ARREADY(axiLiteBridge_io_S_AXI_ARREADY),
    .io_S_AXI_WDATA(axiLiteBridge_io_S_AXI_WDATA),
    .io_S_AXI_WSTRB(axiLiteBridge_io_S_AXI_WSTRB),
    .io_S_AXI_WVALID(axiLiteBridge_io_S_AXI_WVALID),
    .io_S_AXI_WREADY(axiLiteBridge_io_S_AXI_WREADY),
    .io_S_AXI_RDATA(axiLiteBridge_io_S_AXI_RDATA),
    .io_S_AXI_RRESP(axiLiteBridge_io_S_AXI_RRESP),
    .io_S_AXI_RVALID(axiLiteBridge_io_S_AXI_RVALID),
    .io_S_AXI_RREADY(axiLiteBridge_io_S_AXI_RREADY),
    .io_S_AXI_BRESP(axiLiteBridge_io_S_AXI_BRESP),
    .io_S_AXI_BVALID(axiLiteBridge_io_S_AXI_BVALID),
    .io_S_AXI_BREADY(axiLiteBridge_io_S_AXI_BREADY),
    .io_raddr(axiLiteBridge_io_raddr),
    .io_wen(axiLiteBridge_io_wen),
    .io_waddr(axiLiteBridge_io_waddr),
    .io_wdata(axiLiteBridge_io_wdata),
    .io_rdata(axiLiteBridge_io_rdata)
  );
  assign io_S_AXI_AWREADY = axiLiteBridge_io_S_AXI_AWREADY;
  assign io_S_AXI_ARREADY = axiLiteBridge_io_S_AXI_ARREADY;
  assign io_S_AXI_WREADY = axiLiteBridge_io_S_AXI_WREADY;
  assign io_S_AXI_RDATA = axiLiteBridge_io_S_AXI_RDATA;
  assign io_S_AXI_RRESP = axiLiteBridge_io_S_AXI_RRESP;
  assign io_S_AXI_RVALID = axiLiteBridge_io_S_AXI_RVALID;
  assign io_S_AXI_BRESP = axiLiteBridge_io_S_AXI_BRESP;
  assign io_S_AXI_BVALID = axiLiteBridge_io_S_AXI_BVALID;
  assign io_enable = fringeCommon_io_enable;
  assign io_argIns_0 = fringeCommon_io_argIns_0;
  assign io_argOuts_0_ready = fringeCommon_io_argOuts_0_ready;
  assign fringeCommon_clock = clock;
  assign fringeCommon_reset = _T_120;
  assign fringeCommon_io_raddr = axiLiteBridge_io_raddr[1:0];
  assign fringeCommon_io_wen = axiLiteBridge_io_wen;
  assign fringeCommon_io_waddr = axiLiteBridge_io_waddr[1:0];
  assign fringeCommon_io_wdata = axiLiteBridge_io_wdata;
  assign fringeCommon_io_done = io_done;
  assign fringeCommon_io_argOuts_0_valid = io_argOuts_0_valid;
  assign fringeCommon_io_argOuts_0_bits = io_argOuts_0_bits;
  assign fringeCommon_io_dram_cmd_ready = _GEN_0;
  assign fringeCommon_io_dram_resp_valid = _GEN_1;
  assign fringeCommon_io_dram_resp_bits_rdata_0 = _GEN_2;
  assign fringeCommon_io_dram_resp_bits_rdata_1 = _GEN_3;
  assign fringeCommon_io_dram_resp_bits_rdata_2 = _GEN_4;
  assign fringeCommon_io_dram_resp_bits_rdata_3 = _GEN_5;
  assign fringeCommon_io_dram_resp_bits_rdata_4 = _GEN_6;
  assign fringeCommon_io_dram_resp_bits_rdata_5 = _GEN_7;
  assign fringeCommon_io_dram_resp_bits_rdata_6 = _GEN_8;
  assign fringeCommon_io_dram_resp_bits_rdata_7 = _GEN_9;
  assign fringeCommon_io_dram_resp_bits_rdata_8 = _GEN_10;
  assign fringeCommon_io_dram_resp_bits_rdata_9 = _GEN_11;
  assign fringeCommon_io_dram_resp_bits_rdata_10 = _GEN_12;
  assign fringeCommon_io_dram_resp_bits_rdata_11 = _GEN_13;
  assign fringeCommon_io_dram_resp_bits_rdata_12 = _GEN_14;
  assign fringeCommon_io_dram_resp_bits_rdata_13 = _GEN_15;
  assign fringeCommon_io_dram_resp_bits_rdata_14 = _GEN_16;
  assign fringeCommon_io_dram_resp_bits_rdata_15 = _GEN_17;
  assign fringeCommon_io_dram_resp_bits_tag = _GEN_18;
  assign fringeCommon_io_dram_resp_bits_streamId = _GEN_19;
  assign axiLiteBridge_clock = clock;
  assign axiLiteBridge_reset = reset;
  assign axiLiteBridge_io_S_AXI_AWADDR = io_S_AXI_AWADDR;
  assign axiLiteBridge_io_S_AXI_AWPROT = io_S_AXI_AWPROT;
  assign axiLiteBridge_io_S_AXI_AWVALID = io_S_AXI_AWVALID;
  assign axiLiteBridge_io_S_AXI_ARADDR = io_S_AXI_ARADDR;
  assign axiLiteBridge_io_S_AXI_ARPROT = io_S_AXI_ARPROT;
  assign axiLiteBridge_io_S_AXI_ARVALID = io_S_AXI_ARVALID;
  assign axiLiteBridge_io_S_AXI_WDATA = io_S_AXI_WDATA;
  assign axiLiteBridge_io_S_AXI_WSTRB = io_S_AXI_WSTRB;
  assign axiLiteBridge_io_S_AXI_WVALID = io_S_AXI_WVALID;
  assign axiLiteBridge_io_S_AXI_RREADY = io_S_AXI_RREADY;
  assign axiLiteBridge_io_S_AXI_BREADY = io_S_AXI_BREADY;
  assign axiLiteBridge_io_rdata = fringeCommon_io_rdata;
  assign _T_120 = ~ reset;
`ifdef RANDOMIZE
  integer initvar;
  initial begin
    `ifndef verilator
      #0.002 begin end
    `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_20 = {1{$random}};
  _GEN_0 = _GEN_20[0:0];
  `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_21 = {1{$random}};
  _GEN_1 = _GEN_21[0:0];
  `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_22 = {1{$random}};
  _GEN_2 = _GEN_22[31:0];
  `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_23 = {1{$random}};
  _GEN_3 = _GEN_23[31:0];
  `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_24 = {1{$random}};
  _GEN_4 = _GEN_24[31:0];
  `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_25 = {1{$random}};
  _GEN_5 = _GEN_25[31:0];
  `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_26 = {1{$random}};
  _GEN_6 = _GEN_26[31:0];
  `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_27 = {1{$random}};
  _GEN_7 = _GEN_27[31:0];
  `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_28 = {1{$random}};
  _GEN_8 = _GEN_28[31:0];
  `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_29 = {1{$random}};
  _GEN_9 = _GEN_29[31:0];
  `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_30 = {1{$random}};
  _GEN_10 = _GEN_30[31:0];
  `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_31 = {1{$random}};
  _GEN_11 = _GEN_31[31:0];
  `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_32 = {1{$random}};
  _GEN_12 = _GEN_32[31:0];
  `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_33 = {1{$random}};
  _GEN_13 = _GEN_33[31:0];
  `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_34 = {1{$random}};
  _GEN_14 = _GEN_34[31:0];
  `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_35 = {1{$random}};
  _GEN_15 = _GEN_35[31:0];
  `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_36 = {1{$random}};
  _GEN_16 = _GEN_36[31:0];
  `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_37 = {1{$random}};
  _GEN_17 = _GEN_37[31:0];
  `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_38 = {1{$random}};
  _GEN_18 = _GEN_38[31:0];
  `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_39 = {1{$random}};
  _GEN_19 = _GEN_39[31:0];
  `endif
  end
`endif
endmodule
module Top(
  input         clock,
  input         reset,
  input         io_raddr,
  input         io_wen,
  input         io_waddr,
  input         io_wdata,
  output        io_rdata,
  input  [15:0] io_S_AXI_AWADDR,
  input  [2:0]  io_S_AXI_AWPROT,
  input         io_S_AXI_AWVALID,
  output        io_S_AXI_AWREADY,
  input  [15:0] io_S_AXI_ARADDR,
  input  [2:0]  io_S_AXI_ARPROT,
  input         io_S_AXI_ARVALID,
  output        io_S_AXI_ARREADY,
  input  [31:0] io_S_AXI_WDATA,
  input  [3:0]  io_S_AXI_WSTRB,
  input         io_S_AXI_WVALID,
  output        io_S_AXI_WREADY,
  output [31:0] io_S_AXI_RDATA,
  output [1:0]  io_S_AXI_RRESP,
  output        io_S_AXI_RVALID,
  input         io_S_AXI_RREADY,
  output [1:0]  io_S_AXI_BRESP,
  output        io_S_AXI_BVALID,
  input         io_S_AXI_BREADY
);
  wire  accel_clock;
  wire  accel_reset;
  wire  accel_io_enable;
  wire  accel_io_done;
  wire [31:0] accel_io_argIns_0;
  wire  accel_io_argOuts_0_ready;
  wire  accel_io_argOuts_0_valid;
  wire [31:0] accel_io_argOuts_0_bits;
  wire  FringeDE1SoC_clock;
  wire  FringeDE1SoC_reset;
  wire [31:0] FringeDE1SoC_io_S_AXI_AWADDR;
  wire [2:0] FringeDE1SoC_io_S_AXI_AWPROT;
  wire  FringeDE1SoC_io_S_AXI_AWVALID;
  wire  FringeDE1SoC_io_S_AXI_AWREADY;
  wire [31:0] FringeDE1SoC_io_S_AXI_ARADDR;
  wire [2:0] FringeDE1SoC_io_S_AXI_ARPROT;
  wire  FringeDE1SoC_io_S_AXI_ARVALID;
  wire  FringeDE1SoC_io_S_AXI_ARREADY;
  wire [31:0] FringeDE1SoC_io_S_AXI_WDATA;
  wire [3:0] FringeDE1SoC_io_S_AXI_WSTRB;
  wire  FringeDE1SoC_io_S_AXI_WVALID;
  wire  FringeDE1SoC_io_S_AXI_WREADY;
  wire [31:0] FringeDE1SoC_io_S_AXI_RDATA;
  wire [1:0] FringeDE1SoC_io_S_AXI_RRESP;
  wire  FringeDE1SoC_io_S_AXI_RVALID;
  wire  FringeDE1SoC_io_S_AXI_RREADY;
  wire [1:0] FringeDE1SoC_io_S_AXI_BRESP;
  wire  FringeDE1SoC_io_S_AXI_BVALID;
  wire  FringeDE1SoC_io_S_AXI_BREADY;
  wire  FringeDE1SoC_io_enable;
  wire  FringeDE1SoC_io_done;
  wire [31:0] FringeDE1SoC_io_argIns_0;
  wire  FringeDE1SoC_io_argOuts_0_ready;
  wire  FringeDE1SoC_io_argOuts_0_valid;
  wire [31:0] FringeDE1SoC_io_argOuts_0_bits;
  wire  _T_105;
  reg  _GEN_0;
  reg [31:0] _GEN_2;
  reg  _GEN_1;
  reg [31:0] _GEN_3;
  AccelTop accel (
    .clock(accel_clock),
    .reset(accel_reset),
    .io_enable(accel_io_enable),
    .io_done(accel_io_done),
    .io_argIns_0(accel_io_argIns_0),
    .io_argOuts_0_ready(accel_io_argOuts_0_ready),
    .io_argOuts_0_valid(accel_io_argOuts_0_valid),
    .io_argOuts_0_bits(accel_io_argOuts_0_bits)
  );
  FringeDE1SoC FringeDE1SoC (
    .clock(FringeDE1SoC_clock),
    .reset(FringeDE1SoC_reset),
    .io_S_AXI_AWADDR(FringeDE1SoC_io_S_AXI_AWADDR),
    .io_S_AXI_AWPROT(FringeDE1SoC_io_S_AXI_AWPROT),
    .io_S_AXI_AWVALID(FringeDE1SoC_io_S_AXI_AWVALID),
    .io_S_AXI_AWREADY(FringeDE1SoC_io_S_AXI_AWREADY),
    .io_S_AXI_ARADDR(FringeDE1SoC_io_S_AXI_ARADDR),
    .io_S_AXI_ARPROT(FringeDE1SoC_io_S_AXI_ARPROT),
    .io_S_AXI_ARVALID(FringeDE1SoC_io_S_AXI_ARVALID),
    .io_S_AXI_ARREADY(FringeDE1SoC_io_S_AXI_ARREADY),
    .io_S_AXI_WDATA(FringeDE1SoC_io_S_AXI_WDATA),
    .io_S_AXI_WSTRB(FringeDE1SoC_io_S_AXI_WSTRB),
    .io_S_AXI_WVALID(FringeDE1SoC_io_S_AXI_WVALID),
    .io_S_AXI_WREADY(FringeDE1SoC_io_S_AXI_WREADY),
    .io_S_AXI_RDATA(FringeDE1SoC_io_S_AXI_RDATA),
    .io_S_AXI_RRESP(FringeDE1SoC_io_S_AXI_RRESP),
    .io_S_AXI_RVALID(FringeDE1SoC_io_S_AXI_RVALID),
    .io_S_AXI_RREADY(FringeDE1SoC_io_S_AXI_RREADY),
    .io_S_AXI_BRESP(FringeDE1SoC_io_S_AXI_BRESP),
    .io_S_AXI_BVALID(FringeDE1SoC_io_S_AXI_BVALID),
    .io_S_AXI_BREADY(FringeDE1SoC_io_S_AXI_BREADY),
    .io_enable(FringeDE1SoC_io_enable),
    .io_done(FringeDE1SoC_io_done),
    .io_argIns_0(FringeDE1SoC_io_argIns_0),
    .io_argOuts_0_ready(FringeDE1SoC_io_argOuts_0_ready),
    .io_argOuts_0_valid(FringeDE1SoC_io_argOuts_0_valid),
    .io_argOuts_0_bits(FringeDE1SoC_io_argOuts_0_bits)
  );
  assign io_rdata = _GEN_0;
  assign io_S_AXI_AWREADY = FringeDE1SoC_io_S_AXI_AWREADY;
  assign io_S_AXI_ARREADY = FringeDE1SoC_io_S_AXI_ARREADY;
  assign io_S_AXI_WREADY = FringeDE1SoC_io_S_AXI_WREADY;
  assign io_S_AXI_RDATA = FringeDE1SoC_io_S_AXI_RDATA;
  assign io_S_AXI_RRESP = FringeDE1SoC_io_S_AXI_RRESP;
  assign io_S_AXI_RVALID = FringeDE1SoC_io_S_AXI_RVALID;
  assign io_S_AXI_BRESP = FringeDE1SoC_io_S_AXI_BRESP;
  assign io_S_AXI_BVALID = FringeDE1SoC_io_S_AXI_BVALID;
  assign accel_clock = clock;
  assign accel_reset = _T_105;
  assign accel_io_enable = FringeDE1SoC_io_enable;
  assign accel_io_argIns_0 = FringeDE1SoC_io_argIns_0;
  assign accel_io_argOuts_0_ready = _GEN_1;
  assign FringeDE1SoC_clock = clock;
  assign FringeDE1SoC_reset = reset;
  assign FringeDE1SoC_io_S_AXI_AWADDR = {{16'd0}, io_S_AXI_AWADDR};
  assign FringeDE1SoC_io_S_AXI_AWPROT = io_S_AXI_AWPROT;
  assign FringeDE1SoC_io_S_AXI_AWVALID = io_S_AXI_AWVALID;
  assign FringeDE1SoC_io_S_AXI_ARADDR = {{16'd0}, io_S_AXI_ARADDR};
  assign FringeDE1SoC_io_S_AXI_ARPROT = io_S_AXI_ARPROT;
  assign FringeDE1SoC_io_S_AXI_ARVALID = io_S_AXI_ARVALID;
  assign FringeDE1SoC_io_S_AXI_WDATA = io_S_AXI_WDATA;
  assign FringeDE1SoC_io_S_AXI_WSTRB = io_S_AXI_WSTRB;
  assign FringeDE1SoC_io_S_AXI_WVALID = io_S_AXI_WVALID;
  assign FringeDE1SoC_io_S_AXI_RREADY = io_S_AXI_RREADY;
  assign FringeDE1SoC_io_S_AXI_BREADY = io_S_AXI_BREADY;
  assign FringeDE1SoC_io_done = accel_io_done;
  assign FringeDE1SoC_io_argOuts_0_valid = 1'h1;
  assign FringeDE1SoC_io_argOuts_0_bits = accel_io_argOuts_0_bits;
  assign _T_105 = ~ reset;
`ifdef RANDOMIZE
  integer initvar;
  initial begin
    `ifndef verilator
      #0.002 begin end
    `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_2 = {1{$random}};
  _GEN_0 = _GEN_2[0:0];
  `endif
  `ifdef RANDOMIZE_REG_INIT
  _GEN_3 = {1{$random}};
  _GEN_1 = _GEN_3[0:0];
  `endif
  end
`endif
endmodule
