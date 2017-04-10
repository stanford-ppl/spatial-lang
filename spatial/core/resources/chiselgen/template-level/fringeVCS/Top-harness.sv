module test;
  import "DPI" function void sim_init();
  import "DPI" function int tick();
  import "DPI" function void sendDRAMRequest(longint addr, int tag, int isWr, int wdata0, int wdata1, int wdata2, int wdata3, int wdata4, int wdata5, int wdata6, int wdata7, int wdata8, int wdata9, int wdata10, int wdata11, int wdata12, int wdata13, int wdata14, int wdata15);
  import "DPI" function void readOutputStream(int data, int tag, int last);

  // Export functionality to C layer
  export "DPI" function start;
  export "DPI" function rst;
  export "DPI" function writeReg;
  export "DPI" function readRegRaddr;
  export "DPI" function readRegRdataHi32;
  export "DPI" function readRegRdataLo32;
  export "DPI" function pokeDRAMResponse;
  export "DPI" function getDRAMRespReady;
  export "DPI" function writeStream;

  reg clock = 1;
  reg reset = 1;
  reg finish = 0;

  function void start();
    reset = 0;
  endfunction

  function void rst();
    reset = 1;
  endfunction

  always #`CLOCK_PERIOD clock = ~clock;

  reg vcdon = 0;
  reg [1023:0] vcdfile = 0;
  reg [1023:0] vpdfile = 0;

  reg [31:0] io_raddr;
  reg io_wen;
  reg [31:0] io_waddr;
  reg [63:0] io_wdata;
  wire [63:0] io_rdata;
  reg io_dram_cmd_ready;
  wire  io_dram_cmd_valid;
  wire [63:0] io_dram_cmd_bits_addr;
  wire  io_dram_cmd_bits_isWr;
  wire [31:0] io_dram_cmd_bits_streamId;
  wire [31:0] io_dram_cmd_bits_tag;
  wire [31:0] io_dram_cmd_bits_wdata_0;
  wire [31:0] io_dram_cmd_bits_wdata_1;
  wire [31:0] io_dram_cmd_bits_wdata_2;
  wire [31:0] io_dram_cmd_bits_wdata_3;
  wire [31:0] io_dram_cmd_bits_wdata_4;
  wire [31:0] io_dram_cmd_bits_wdata_5;
  wire [31:0] io_dram_cmd_bits_wdata_6;
  wire [31:0] io_dram_cmd_bits_wdata_7;
  wire [31:0] io_dram_cmd_bits_wdata_8;
  wire [31:0] io_dram_cmd_bits_wdata_9;
  wire [31:0] io_dram_cmd_bits_wdata_10;
  wire [31:0] io_dram_cmd_bits_wdata_11;
  wire [31:0] io_dram_cmd_bits_wdata_12;
  wire [31:0] io_dram_cmd_bits_wdata_13;
  wire [31:0] io_dram_cmd_bits_wdata_14;
  wire [31:0] io_dram_cmd_bits_wdata_15;
  wire  io_dram_resp_ready;
  reg io_dram_resp_valid;
  reg [31:0] io_dram_resp_bits_rdata_0;
  reg [31:0] io_dram_resp_bits_rdata_1;
  reg [31:0] io_dram_resp_bits_rdata_2;
  reg [31:0] io_dram_resp_bits_rdata_3;
  reg [31:0] io_dram_resp_bits_rdata_4;
  reg [31:0] io_dram_resp_bits_rdata_5;
  reg [31:0] io_dram_resp_bits_rdata_6;
  reg [31:0] io_dram_resp_bits_rdata_7;
  reg [31:0] io_dram_resp_bits_rdata_8;
  reg [31:0] io_dram_resp_bits_rdata_9;
  reg [31:0] io_dram_resp_bits_rdata_10;
  reg [31:0] io_dram_resp_bits_rdata_11;
  reg [31:0] io_dram_resp_bits_rdata_12;
  reg [31:0] io_dram_resp_bits_rdata_13;
  reg [31:0] io_dram_resp_bits_rdata_14;
  reg [31:0] io_dram_resp_bits_rdata_15;
  reg [31:0] io_dram_resp_bits_tag;
  reg [31:0] io_dram_resp_bits_streamId;

  wire        io_genericStreamIn_ready;
  reg         io_genericStreamIn_valid;
  reg  [31:0] io_genericStreamIn_bits_data;
  reg  [31:0] io_genericStreamIn_bits_tag = 0;

  reg         io_genericStreamIn_bits_last;
  reg         io_genericStreamOut_ready;
  wire        io_genericStreamOut_valid;
  wire [31:0] io_genericStreamOut_bits_data;
  wire [31:0] io_genericStreamOut_bits_tag;
  wire        io_genericStreamOut_bits_last;

  /*** DUT instantiation ***/
  Top Top(
    .clock(clock),
    .reset(reset),
    .io_raddr(io_raddr),
    .io_wen(io_wen),
    .io_waddr(io_waddr),
    .io_wdata(io_wdata),
    .io_rdata(io_rdata),
    .io_dram_cmd_ready(io_dram_cmd_ready),
    .io_dram_cmd_valid(io_dram_cmd_valid),
    .io_dram_cmd_bits_addr(io_dram_cmd_bits_addr),
    .io_dram_cmd_bits_isWr(io_dram_cmd_bits_isWr),
    .io_dram_cmd_bits_tag(io_dram_cmd_bits_tag),
    .io_dram_cmd_bits_streamId(io_dram_cmd_bits_streamId),
    .io_dram_cmd_bits_wdata_0(io_dram_cmd_bits_wdata_0),
    .io_dram_cmd_bits_wdata_1(io_dram_cmd_bits_wdata_1),
    .io_dram_cmd_bits_wdata_2(io_dram_cmd_bits_wdata_2),
    .io_dram_cmd_bits_wdata_3(io_dram_cmd_bits_wdata_3),
    .io_dram_cmd_bits_wdata_4(io_dram_cmd_bits_wdata_4),
    .io_dram_cmd_bits_wdata_5(io_dram_cmd_bits_wdata_5),
    .io_dram_cmd_bits_wdata_6(io_dram_cmd_bits_wdata_6),
    .io_dram_cmd_bits_wdata_7(io_dram_cmd_bits_wdata_7),
    .io_dram_cmd_bits_wdata_8(io_dram_cmd_bits_wdata_8),
    .io_dram_cmd_bits_wdata_9(io_dram_cmd_bits_wdata_9),
    .io_dram_cmd_bits_wdata_10(io_dram_cmd_bits_wdata_10),
    .io_dram_cmd_bits_wdata_11(io_dram_cmd_bits_wdata_11),
    .io_dram_cmd_bits_wdata_12(io_dram_cmd_bits_wdata_12),
    .io_dram_cmd_bits_wdata_13(io_dram_cmd_bits_wdata_13),
    .io_dram_cmd_bits_wdata_14(io_dram_cmd_bits_wdata_14),
    .io_dram_cmd_bits_wdata_15(io_dram_cmd_bits_wdata_15),
    .io_dram_resp_ready(io_dram_resp_ready),
    .io_dram_resp_valid(io_dram_resp_valid),
    .io_dram_resp_bits_rdata_0(io_dram_resp_bits_rdata_0),
    .io_dram_resp_bits_rdata_1(io_dram_resp_bits_rdata_1),
    .io_dram_resp_bits_rdata_2(io_dram_resp_bits_rdata_2),
    .io_dram_resp_bits_rdata_3(io_dram_resp_bits_rdata_3),
    .io_dram_resp_bits_rdata_4(io_dram_resp_bits_rdata_4),
    .io_dram_resp_bits_rdata_5(io_dram_resp_bits_rdata_5),
    .io_dram_resp_bits_rdata_6(io_dram_resp_bits_rdata_6),
    .io_dram_resp_bits_rdata_7(io_dram_resp_bits_rdata_7),
    .io_dram_resp_bits_rdata_8(io_dram_resp_bits_rdata_8),
    .io_dram_resp_bits_rdata_9(io_dram_resp_bits_rdata_9),
    .io_dram_resp_bits_rdata_10(io_dram_resp_bits_rdata_10),
    .io_dram_resp_bits_rdata_11(io_dram_resp_bits_rdata_11),
    .io_dram_resp_bits_rdata_12(io_dram_resp_bits_rdata_12),
    .io_dram_resp_bits_rdata_13(io_dram_resp_bits_rdata_13),
    .io_dram_resp_bits_rdata_14(io_dram_resp_bits_rdata_14),
    .io_dram_resp_bits_rdata_15(io_dram_resp_bits_rdata_15),
    .io_dram_resp_bits_tag(io_dram_resp_bits_tag),
    .io_dram_resp_bits_streamId(io_dram_resp_bits_streamId),
    .io_genericStreamIn_ready(io_genericStreamIn_ready),
    .io_genericStreamIn_valid(io_genericStreamIn_valid),
    .io_genericStreamIn_bits_data(io_genericStreamIn_bits_data),
    .io_genericStreamIn_bits_tag(io_genericStreamIn_bits_tag),
    .io_genericStreamIn_bits_last(io_genericStreamIn_bits_last),
    .io_genericStreamOut_ready(io_genericStreamOut_ready),
    .io_genericStreamOut_valid(io_genericStreamOut_valid),
    .io_genericStreamOut_bits_data(io_genericStreamOut_bits_data),
    .io_genericStreamOut_bits_tag(io_genericStreamOut_bits_tag),
    .io_genericStreamOut_bits_last(io_genericStreamOut_bits_last)
);

  function void readRegRaddr(input int r);
    io_raddr = r;
  endfunction

  function void readRegRdataHi32(output bit [31:0] rdatahi);
    rdatahi = io_rdata[63:32];
  endfunction

  function void readRegRdataLo32(output bit [31:0] rdatalo);
    rdatalo = io_rdata[31:0];
  endfunction

  function void writeReg(input int r, longint wdata);
    io_waddr = r;
    io_wdata = wdata;
    io_wen = 1;
  endfunction

  function void getDRAMRespReady(output bit [31:0] respReady);
    respReady = io_dram_resp_ready;
  endfunction

  function void pokeDRAMResponse(
    input int tag,
    input int rdata0,
    input int rdata1,
    input int rdata2,
    input int rdata3,
    input int rdata4,
    input int rdata5,
    input int rdata6,
    input int rdata7,
    input int rdata8,
    input int rdata9,
    input int rdata10,
    input int rdata11,
    input int rdata12,
    input int rdata13,
    input int rdata14,
    input int rdata15
  );
    io_dram_resp_valid = 1;
    io_dram_resp_bits_tag = tag;
    io_dram_resp_bits_rdata_0 = rdata0;
    io_dram_resp_bits_rdata_1 = rdata1;
    io_dram_resp_bits_rdata_2 = rdata2;
    io_dram_resp_bits_rdata_3 = rdata3;
    io_dram_resp_bits_rdata_4 = rdata4;
    io_dram_resp_bits_rdata_5 = rdata5;
    io_dram_resp_bits_rdata_6 = rdata6;
    io_dram_resp_bits_rdata_7 = rdata7;
    io_dram_resp_bits_rdata_8 = rdata8;
    io_dram_resp_bits_rdata_9 = rdata9;
    io_dram_resp_bits_rdata_10 = rdata10;
    io_dram_resp_bits_rdata_11 = rdata11;
    io_dram_resp_bits_rdata_12 = rdata12;
    io_dram_resp_bits_rdata_13 = rdata13;
    io_dram_resp_bits_rdata_14 = rdata14;
    io_dram_resp_bits_rdata_15 = rdata15;
  endfunction

  function void writeStream(
    input int data,
    input int tag,
    input int last
  );
    io_genericStreamIn_valid = 1;
    io_genericStreamIn_bits_data = data;
    io_genericStreamIn_bits_tag = tag;
    io_genericStreamIn_bits_last = last;
  endfunction

  initial begin
    /*** VCD & VPD dump ***/
      $dumpfile("Top.vcd");
      $dumpvars(0, Top);
      $vcdplusfile("Top.vpd");
      sim_init();
  end

  // 1. If io_dram_cmd_valid, then send send DRAM request to CPP layer
  function void callbacks();
    if (io_dram_cmd_valid & ~reset) begin
      sendDRAMRequest(
        io_dram_cmd_bits_addr,
        io_dram_cmd_bits_tag,
        io_dram_cmd_bits_isWr,
        io_dram_cmd_bits_wdata_0,
        io_dram_cmd_bits_wdata_1,
        io_dram_cmd_bits_wdata_2,
        io_dram_cmd_bits_wdata_3,
        io_dram_cmd_bits_wdata_4,
        io_dram_cmd_bits_wdata_5,
        io_dram_cmd_bits_wdata_6,
        io_dram_cmd_bits_wdata_7,
        io_dram_cmd_bits_wdata_8,
        io_dram_cmd_bits_wdata_9,
        io_dram_cmd_bits_wdata_10,
        io_dram_cmd_bits_wdata_11,
        io_dram_cmd_bits_wdata_12,
        io_dram_cmd_bits_wdata_13,
        io_dram_cmd_bits_wdata_14,
        io_dram_cmd_bits_wdata_15
      );
      io_dram_cmd_ready = 1;
    end

    if (io_genericStreamOut_valid & ~reset) begin
      readOutputStream(
        io_genericStreamOut_bits_data,
        io_genericStreamOut_bits_tag,
       io_genericStreamOut_bits_last
      );
    end

  endfunction

  int numCycles = 0;
  always @(negedge clock) begin
    numCycles = numCycles + 1;
    io_wen = 0;
    io_dram_resp_valid = 0;
    io_dram_cmd_ready = 0;
    io_genericStreamIn_valid = 0;
    io_genericStreamOut_ready = 1;

    if (tick()) begin
      $dumpflush;
      $finish;
    end

    callbacks();

    $vcdplusflush;
    $dumpflush;
  end

endmodule
