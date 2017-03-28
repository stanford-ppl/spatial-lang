// (C) 2001-2016 Intel Corporation. All rights reserved.
// Your use of Intel Corporation's design tools, logic functions and other 
// software and tools, and its AMPP partner logic functions, and any output 
// files any of the foregoing (including device programming or simulation 
// files), and any associated documentation or information are expressly subject 
// to the terms and conditions of the Intel Program License Subscription 
// Agreement, Intel MegaCore Function License Agreement, or other applicable 
// license agreement, including, without limitation, that your use is for the 
// sole purpose of programming logic devices manufactured by Intel and sold by 
// Intel or its authorized distributors.  Please refer to the applicable 
// agreement for further details.



// Your use of Altera Corporation's design tools, logic functions and other 
// software and tools, and its AMPP partner logic functions, and any output 
// files any of the foregoing (including device programming or simulation 
// files), and any associated documentation or information are expressly subject 
// to the terms and conditions of the Altera Program License Subscription 
// Agreement, Altera MegaCore Function License Agreement, or other applicable 
// license agreement, including, without limitation, that your use is for the 
// sole purpose of programming logic devices manufactured by Altera and sold by 
// Altera or its authorized distributors.  Please refer to the applicable 
// agreement for further details.


// $Id: //acds/rel/16.1/ip/merlin/altera_merlin_router/altera_merlin_router.sv.terp#1 $
// $Revision: #1 $
// $Date: 2016/08/07 $
// $Author: swbranch $

// -------------------------------------------------------
// Merlin Router
//
// Asserts the appropriate one-hot encoded channel based on 
// either (a) the address or (b) the dest id. The DECODER_TYPE
// parameter controls this behaviour. 0 means address decoder,
// 1 means dest id decoder.
//
// In the case of (a), it also sets the destination id.
// -------------------------------------------------------

`timescale 1 ns / 1 ns

module Computer_System_mm_interconnect_1_router_001_default_decode
  #(
     parameter DEFAULT_CHANNEL = 0,
               DEFAULT_WR_CHANNEL = -1,
               DEFAULT_RD_CHANNEL = -1,
               DEFAULT_DESTID = 29 
   )
  (output [110 - 106 : 0] default_destination_id,
   output [33-1 : 0] default_wr_channel,
   output [33-1 : 0] default_rd_channel,
   output [33-1 : 0] default_src_channel
  );

  assign default_destination_id = 
    DEFAULT_DESTID[110 - 106 : 0];

  generate
    if (DEFAULT_CHANNEL == -1) begin : no_default_channel_assignment
      assign default_src_channel = '0;
    end
    else begin : default_channel_assignment
      assign default_src_channel = 33'b1 << DEFAULT_CHANNEL;
    end
  endgenerate

  generate
    if (DEFAULT_RD_CHANNEL == -1) begin : no_default_rw_channel_assignment
      assign default_wr_channel = '0;
      assign default_rd_channel = '0;
    end
    else begin : default_rw_channel_assignment
      assign default_wr_channel = 33'b1 << DEFAULT_WR_CHANNEL;
      assign default_rd_channel = 33'b1 << DEFAULT_RD_CHANNEL;
    end
  endgenerate

endmodule


module Computer_System_mm_interconnect_1_router_001
(
    // -------------------
    // Clock & Reset
    // -------------------
    input clk,
    input reset,

    // -------------------
    // Command Sink (Input)
    // -------------------
    input                       sink_valid,
    input  [135-1 : 0]    sink_data,
    input                       sink_startofpacket,
    input                       sink_endofpacket,
    output                      sink_ready,

    // -------------------
    // Command Source (Output)
    // -------------------
    output                          src_valid,
    output reg [135-1    : 0] src_data,
    output reg [33-1 : 0] src_channel,
    output                          src_startofpacket,
    output                          src_endofpacket,
    input                           src_ready
);

    // -------------------------------------------------------
    // Local parameters and variables
    // -------------------------------------------------------
    localparam PKT_ADDR_H = 67;
    localparam PKT_ADDR_L = 36;
    localparam PKT_DEST_ID_H = 110;
    localparam PKT_DEST_ID_L = 106;
    localparam PKT_PROTECTION_H = 125;
    localparam PKT_PROTECTION_L = 123;
    localparam ST_DATA_W = 135;
    localparam ST_CHANNEL_W = 33;
    localparam DECODER_TYPE = 0;

    localparam PKT_TRANS_WRITE = 70;
    localparam PKT_TRANS_READ  = 71;

    localparam PKT_ADDR_W = PKT_ADDR_H-PKT_ADDR_L + 1;
    localparam PKT_DEST_ID_W = PKT_DEST_ID_H-PKT_DEST_ID_L + 1;



    // -------------------------------------------------------
    // Figure out the number of bits to mask off for each slave span
    // during address decoding
    // -------------------------------------------------------
    localparam PAD0 = log2ceil(64'h10 - 64'h0); 
    localparam PAD1 = log2ceil(64'h30 - 64'h20); 
    localparam PAD2 = log2ceil(64'h40 - 64'h30); 
    localparam PAD3 = log2ceil(64'h50 - 64'h40); 
    localparam PAD4 = log2ceil(64'h60 - 64'h50); 
    localparam PAD5 = log2ceil(64'h70 - 64'h60); 
    localparam PAD6 = log2ceil(64'h80 - 64'h70); 
    localparam PAD7 = log2ceil(64'h108 - 64'h100); 
    localparam PAD8 = log2ceil(64'h110 - 64'h108); 
    localparam PAD9 = log2ceil(64'h1008 - 64'h1000); 
    localparam PAD10 = log2ceil(64'h1010 - 64'h1008); 
    localparam PAD11 = log2ceil(64'h1028 - 64'h1020); 
    localparam PAD12 = log2ceil(64'h2020 - 64'h2000); 
    localparam PAD13 = log2ceil(64'h2040 - 64'h2020); 
    localparam PAD14 = log2ceil(64'h2048 - 64'h2040); 
    localparam PAD15 = log2ceil(64'h3010 - 64'h3000); 
    localparam PAD16 = log2ceil(64'h3030 - 64'h3020); 
    localparam PAD17 = log2ceil(64'h3040 - 64'h3030); 
    localparam PAD18 = log2ceil(64'h3050 - 64'h3040); 
    localparam PAD19 = log2ceil(64'h3070 - 64'h3060); 
    localparam PAD20 = log2ceil(64'h4020 - 64'h4000); 
    localparam PAD21 = log2ceil(64'h20000 - 64'h10000); 
    // -------------------------------------------------------
    // Work out which address bits are significant based on the
    // address range of the slaves. If the required width is too
    // large or too small, we use the address field width instead.
    // -------------------------------------------------------
    localparam ADDR_RANGE = 64'h20000;
    localparam RANGE_ADDR_WIDTH = log2ceil(ADDR_RANGE);
    localparam OPTIMIZED_ADDR_H = (RANGE_ADDR_WIDTH > PKT_ADDR_W) ||
                                  (RANGE_ADDR_WIDTH == 0) ?
                                        PKT_ADDR_H :
                                        PKT_ADDR_L + RANGE_ADDR_WIDTH - 1;

    localparam RG = RANGE_ADDR_WIDTH-1;
    localparam REAL_ADDRESS_RANGE = OPTIMIZED_ADDR_H - PKT_ADDR_L;

      reg [PKT_ADDR_W-1 : 0] address;
      always @* begin
        address = {PKT_ADDR_W{1'b0}};
        address [REAL_ADDRESS_RANGE:0] = sink_data[OPTIMIZED_ADDR_H : PKT_ADDR_L];
      end   

    // -------------------------------------------------------
    // Pass almost everything through, untouched
    // -------------------------------------------------------
    assign sink_ready        = src_ready;
    assign src_valid         = sink_valid;
    assign src_startofpacket = sink_startofpacket;
    assign src_endofpacket   = sink_endofpacket;
    wire [PKT_DEST_ID_W-1:0] default_destid;
    wire [33-1 : 0] default_src_channel;




    // -------------------------------------------------------
    // Write and read transaction signals
    // -------------------------------------------------------
    wire read_transaction;
    assign read_transaction  = sink_data[PKT_TRANS_READ];


    Computer_System_mm_interconnect_1_router_001_default_decode the_default_decode(
      .default_destination_id (default_destid),
      .default_wr_channel   (),
      .default_rd_channel   (),
      .default_src_channel  (default_src_channel)
    );

    always @* begin
        src_data    = sink_data;
        src_channel = default_src_channel;
        src_data[PKT_DEST_ID_H:PKT_DEST_ID_L] = default_destid;

        // --------------------------------------------------
        // Address Decoder
        // Sets the channel and destination ID based on the address
        // --------------------------------------------------

    // ( 0x0 .. 0x10 )
    if ( {address[RG:PAD0],{PAD0{1'b0}}} == 17'h0   ) begin
            src_channel = 33'b0000000000001000000000;
            src_data[PKT_DEST_ID_H:PKT_DEST_ID_L] = 15;
    end

    // ( 0x20 .. 0x30 )
    if ( {address[RG:PAD1],{PAD1{1'b0}}} == 17'h20   ) begin
            src_channel = 33'b0000000000010000000000;
            src_data[PKT_DEST_ID_H:PKT_DEST_ID_L] = 8;
    end

    // ( 0x30 .. 0x40 )
    if ( {address[RG:PAD2],{PAD2{1'b0}}} == 17'h30   ) begin
            src_channel = 33'b0000000000100000000000;
            src_data[PKT_DEST_ID_H:PKT_DEST_ID_L] = 9;
    end

    // ( 0x40 .. 0x50 )
    if ( {address[RG:PAD3],{PAD3{1'b0}}} == 17'h40  && read_transaction  ) begin
            src_channel = 33'b0000000001000000000000;
            src_data[PKT_DEST_ID_H:PKT_DEST_ID_L] = 23;
    end

    // ( 0x50 .. 0x60 )
    if ( {address[RG:PAD4],{PAD4{1'b0}}} == 17'h50   ) begin
            src_channel = 33'b0000000010000000000000;
            src_data[PKT_DEST_ID_H:PKT_DEST_ID_L] = 21;
    end

    // ( 0x60 .. 0x70 )
    if ( {address[RG:PAD5],{PAD5{1'b0}}} == 17'h60   ) begin
            src_channel = 33'b0000000100000000000000;
            src_data[PKT_DEST_ID_H:PKT_DEST_ID_L] = 3;
    end

    // ( 0x70 .. 0x80 )
    if ( {address[RG:PAD6],{PAD6{1'b0}}} == 17'h70   ) begin
            src_channel = 33'b0000001000000000000000;
            src_data[PKT_DEST_ID_H:PKT_DEST_ID_L] = 4;
    end

    // ( 0x100 .. 0x108 )
    if ( {address[RG:PAD7],{PAD7{1'b0}}} == 17'h100   ) begin
            src_channel = 33'b0000000000000000100000;
            src_data[PKT_DEST_ID_H:PKT_DEST_ID_L] = 19;
    end

    // ( 0x108 .. 0x110 )
    if ( {address[RG:PAD8],{PAD8{1'b0}}} == 17'h108   ) begin
            src_channel = 33'b0000000000000001000000;
            src_data[PKT_DEST_ID_H:PKT_DEST_ID_L] = 18;
    end

    // ( 0x1000 .. 0x1008 )
    if ( {address[RG:PAD9],{PAD9{1'b0}}} == 17'h1000   ) begin
            src_channel = 33'b0001000000000000000000;
            src_data[PKT_DEST_ID_H:PKT_DEST_ID_L] = 13;
    end

    // ( 0x1008 .. 0x1010 )
    if ( {address[RG:PAD10],{PAD10{1'b0}}} == 17'h1008   ) begin
            src_channel = 33'b0010000000000000000000;
            src_data[PKT_DEST_ID_H:PKT_DEST_ID_L] = 14;
    end

    // ( 0x1020 .. 0x1028 )
    if ( {address[RG:PAD11],{PAD11{1'b0}}} == 17'h1020   ) begin
            src_channel = 33'b0000000000000000010000;
            src_data[PKT_DEST_ID_H:PKT_DEST_ID_L] = 12;
    end

    // ( 0x2000 .. 0x2020 )
    if ( {address[RG:PAD12],{PAD12{1'b0}}} == 17'h2000   ) begin
            src_channel = 33'b0000010000000000000000;
            src_data[PKT_DEST_ID_H:PKT_DEST_ID_L] = 11;
    end

    // ( 0x2020 .. 0x2040 )
    if ( {address[RG:PAD13],{PAD13{1'b0}}} == 17'h2020   ) begin
            src_channel = 33'b0000100000000000000000;
            src_data[PKT_DEST_ID_H:PKT_DEST_ID_L] = 10;
    end

    // ( 0x2040 .. 0x2048 )
    if ( {address[RG:PAD14],{PAD14{1'b0}}} == 17'h2040  && read_transaction  ) begin
            src_channel = 33'b0000000000000100000000;
            src_data[PKT_DEST_ID_H:PKT_DEST_ID_L] = 24;
    end

    // ( 0x3000 .. 0x3010 )
    if ( {address[RG:PAD15],{PAD15{1'b0}}} == 17'h3000   ) begin
            src_channel = 33'b0000000000000000001000;
            src_data[PKT_DEST_ID_H:PKT_DEST_ID_L] = 1;
    end

    // ( 0x3020 .. 0x3030 )
    if ( {address[RG:PAD16],{PAD16{1'b0}}} == 17'h3020   ) begin
            src_channel = 33'b0100000000000000000000;
            src_data[PKT_DEST_ID_H:PKT_DEST_ID_L] = 20;
    end

    // ( 0x3030 .. 0x3040 )
    if ( {address[RG:PAD17],{PAD17{1'b0}}} == 17'h3030   ) begin
            src_channel = 33'b0000000000000010000000;
            src_data[PKT_DEST_ID_H:PKT_DEST_ID_L] = 25;
    end

    // ( 0x3040 .. 0x3050 )
    if ( {address[RG:PAD18],{PAD18{1'b0}}} == 17'h3040   ) begin
            src_channel = 33'b0000000000000000000100;
            src_data[PKT_DEST_ID_H:PKT_DEST_ID_L] = 2;
    end

    // ( 0x3060 .. 0x3070 )
    if ( {address[RG:PAD19],{PAD19{1'b0}}} == 17'h3060   ) begin
            src_channel = 33'b1000000000000000000000;
            src_data[PKT_DEST_ID_H:PKT_DEST_ID_L] = 28;
    end

    // ( 0x4000 .. 0x4020 )
    if ( {address[RG:PAD20],{PAD20{1'b0}}} == 17'h4000   ) begin
            src_channel = 33'b0000000000000000000010;
            src_data[PKT_DEST_ID_H:PKT_DEST_ID_L] = 0;
    end

    // ( 0x10000 .. 0x20000 )
    if ( {address[RG:PAD21],{PAD21{1'b0}}} == 17'h10000  && read_transaction  ) begin
            src_channel = 33'b0000000000000000000001;
            src_data[PKT_DEST_ID_H:PKT_DEST_ID_L] = 29;
    end

end


    // --------------------------------------------------
    // Ceil(log2()) function
    // --------------------------------------------------
    function integer log2ceil;
        input reg[65:0] val;
        reg [65:0] i;

        begin
            i = 1;
            log2ceil = 0;

            while (i < val) begin
                log2ceil = log2ceil + 1;
                i = i << 1;
            end
        end
    endfunction

endmodule


