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


`timescale 1ns / 1ns

// -----------------------------------------------
// AXI slave network interface
//
// Converts incoming packets into AXI transactions
// -----------------------------------------------

module altera_merlin_axi_slave_ni
#(
    // -----------------------------------------------
    // Packet format parameters
    // -----------------------------------------------
	parameter PKT_ORI_BURST_SIZE_H        = 120,
	parameter PKT_ORI_BURST_SIZE_L 		  = 118,
    parameter PKT_QOS_H                   = 117,
    parameter PKT_QOS_L                   = 114,
    parameter PKT_THREAD_ID_H             = 113,
    parameter PKT_THREAD_ID_L             = 112,
    parameter PKT_RESPONSE_STATUS_H       = 111, 
    parameter PKT_RESPONSE_STATUS_L       = 110,
    parameter PKT_BEGIN_BURST             = 109,
    parameter PKT_CACHE_H                 = 108,
    parameter PKT_CACHE_L                 = 105,
    parameter PKT_DATA_SIDEBAND_H         = 104,
    parameter PKT_DATA_SIDEBAND_L         = 97,
    parameter PKT_ADDR_SIDEBAND_H         = 96, 
    parameter PKT_ADDR_SIDEBAND_L         = 92,
    parameter PKT_BURST_TYPE_H            = 91,
    parameter PKT_BURST_TYPE_L            = 90,
    parameter PKT_PROTECTION_H            = 89, 
    parameter PKT_PROTECTION_L            = 87,
    parameter PKT_BURST_SIZE_H            = 86,
    parameter PKT_BURST_SIZE_L            = 84,
    parameter PKT_BURSTWRAP_H             = 83,
    parameter PKT_BURSTWRAP_L             = 81,
    parameter PKT_BYTE_CNT_H              = 80,
    parameter PKT_BYTE_CNT_L              = 78,
    parameter PKT_ADDR_H                  = 77,
    parameter PKT_ADDR_L                  = 46,
    parameter PKT_TRANS_EXCLUSIVE         = 45,
    parameter PKT_TRANS_LOCK              = 44,
    parameter PKT_TRANS_COMPRESSED_READ   = 43,
    parameter PKT_TRANS_POSTED            = 42,
    parameter PKT_TRANS_WRITE             = 41,
    parameter PKT_TRANS_READ              = 40,
    parameter PKT_DATA_H                  = 39,
    parameter PKT_DATA_L                  = 8,
    parameter PKT_BYTEEN_H                = 7,
    parameter PKT_BYTEEN_L                = 4,
    parameter PKT_SRC_ID_H                = 3,
    parameter PKT_SRC_ID_L                = 2,
    parameter PKT_DEST_ID_H               = 1,
    parameter PKT_DEST_ID_L               = 0,

    // -----------------------------------------------
    // Component parameters
    // -----------------------------------------------
    parameter ST_DATA_W                   = 121,
    parameter ADDR_WIDTH                  = 32,
    parameter RDATA_WIDTH                 = 32,
    parameter WDATA_WIDTH                 = 32,
    parameter ST_CHANNEL_W                = 1,
    parameter AXI_SLAVE_ID_W              = 4,  
    parameter ADDR_USER_WIDTH             = 5, 
    parameter WRITE_ACCEPTANCE_CAPABILITY = 16,
    parameter READ_ACCEPTANCE_CAPABILITY  = 16,
    parameter PASS_ID_TO_SLAVE            = 0,
    parameter AXI_VERSION                 = "AXI3",

    // -----------------------------------------------
    // Derived parameters 
    // -----------------------------------------------
    parameter RESPONSE_W                  = PKT_RESPONSE_STATUS_H - PKT_RESPONSE_STATUS_L + 1,
    parameter AXI_WSTRB_W                 = PKT_BYTEEN_H - PKT_BYTEEN_L + 1,
    parameter PKT_DATA_W                  = PKT_DATA_H - PKT_DATA_L + 1,
    parameter NUMSYMBOLS                  = PKT_DATA_W / 8,
    //DATA_USER_WIDTH: w/ruser is not supported in AXI3. set it to default 1 and axi trasnlator will terminate this connection appropriately
    parameter DATA_USER_WIDTH             = 8,
    parameter AXI_LOCK_WIDTH              = (AXI_VERSION == "AXI4") ? 1:2,
    parameter AXI_BURST_LENGTH_WIDTH      = (AXI_VERSION == "AXI4") ? 8:4

)
(
    input aclk,
    input aresetn,

    // AXI write channels
    output reg         [AXI_SLAVE_ID_W-1:0]     awid,
    output             [ADDR_WIDTH-1:0]         awaddr,
    output reg         [AXI_BURST_LENGTH_WIDTH-1:0] awlen, 
    output             [2:0]                    awsize,
    output             [1:0]                    awburst,
    output             [AXI_LOCK_WIDTH-1:0]     awlock,
    output             [3:0]                    awcache,
    output             [2:0]                    awprot,
    output             [3:0]                    awqos,
    output             [3:0]                    awregion,
    output                                      awvalid,
    output             [ADDR_USER_WIDTH-1:0]    awuser,
    input                                       awready,

    output reg         [AXI_SLAVE_ID_W-1:0]     wid,
    output             [PKT_DATA_W-1:0]         wdata,
    output             [AXI_WSTRB_W-1:0]        wstrb,
    output             [DATA_USER_WIDTH-1:0]    wuser,
    output                                      wlast,
    output                                      wvalid,
    input                                       wready,

    input             [AXI_SLAVE_ID_W-1:0]      bid,
    input             [1:0]                     bresp,
    input             [DATA_USER_WIDTH-1:0]     buser,
    input                                       bvalid,
    output                                      bready,

    // AXI read channels
    output reg         [AXI_SLAVE_ID_W-1:0]     arid,
    output             [ADDR_WIDTH-1:0]         araddr,
    output             [AXI_BURST_LENGTH_WIDTH-1:0] arlen,
    output             [2:0]                    arsize,
    output             [1:0]                    arburst,
    output             [AXI_LOCK_WIDTH-1:0]     arlock,
    output             [3:0]                    arcache,
    output             [2:0]                    arprot,
    output             [3:0]                    arqos,
    output             [3:0]                    arregion,       
    output                                      arvalid,
    output             [ADDR_USER_WIDTH-1:0]    aruser,
    input                                       arready,

    input             [AXI_SLAVE_ID_W-1:0]      rid,
    input             [PKT_DATA_W-1:0]          rdata,
    input             [RESPONSE_W-1:0]          rresp,
    input             [DATA_USER_WIDTH-1:0]     ruser,
    input                                       rlast,
    input                                       rvalid,
    output                                      rready,  

    // Av-st read sink command packet interface
    output                                      read_cp_ready,
    input                                       read_cp_valid,
    input             [ST_DATA_W-1:0]           read_cp_data,
    input             [ST_CHANNEL_W-1:0]        read_cp_channel,
    input                                       read_cp_startofpacket,
    input                                       read_cp_endofpacket,

    // Av-st read source response packet interface
    input                                       read_rp_ready,
    output                                      read_rp_valid,
    output reg         [ST_DATA_W-1:0]          read_rp_data,
    output                                      read_rp_startofpacket,
    output                                      read_rp_endofpacket,

    // Av-st write sink command packet interface
    output reg                                  write_cp_ready,
    input                                       write_cp_valid,
    input             [ST_DATA_W-1:0]           write_cp_data,
    input             [ST_CHANNEL_W-1:0]        write_cp_channel,
    input                                       write_cp_startofpacket,
    input                                       write_cp_endofpacket,

    // Av-st write source response packet interface
    input                                       write_rp_ready,
    output                                      write_rp_valid,
    output reg         [ST_DATA_W-1:0]          write_rp_data,
    output reg                                  write_rp_startofpacket,
    output reg                                  write_rp_endofpacket
);

    
    // --------------------------------------------------
    // Bunch-o-local parameters
    // --------------------------------------------------
    localparam  MASTER_ID_W         = PKT_SRC_ID_H - PKT_SRC_ID_L + 1;
    localparam  SLAVE_ID_W          = PKT_DEST_ID_H - PKT_DEST_ID_L + 1;
    localparam  THREAD_ID_W         = PKT_THREAD_ID_H - PKT_THREAD_ID_L + 1;
    localparam  BURST_SIZE_W        = PKT_BURST_SIZE_H - PKT_BURST_SIZE_L + 1;
    localparam  BURST_TYPE_W        = 2;
    localparam  BYTECOUNT_W         = PKT_BYTE_CNT_H - PKT_BYTE_CNT_L + 1;
    localparam  BURSTWRAP_W         = PKT_BURSTWRAP_H - PKT_BURSTWRAP_L + 1;
    localparam  PROTECTION_W        = PKT_PROTECTION_H - PKT_PROTECTION_L + 1;
    localparam  CACHE_W             = PKT_CACHE_H - PKT_CACHE_L + 1;
    localparam  BYTEEN_W            = PKT_BYTEEN_H - PKT_BYTEEN_L + 1;
    localparam  USER_W              = PKT_ADDR_SIDEBAND_H - PKT_ADDR_SIDEBAND_L + 1;

    // --------------------------------------------------
    // Ceil(log2()) function
    // --------------------------------------------------
    function integer log2ceil;
        input reg[63:0] val;
        reg [63:0] i;

        begin
            i = 1;
            log2ceil = 0;

            while (i < val) begin
                log2ceil = log2ceil + 1;
                i = i << 1;
            end
        end
    endfunction   

    // ------------------------------------------------
    // Signals
    // ------------------------------------------------
    wire [PKT_DATA_W-1:0]     write_cmd_data;
    wire [AXI_WSTRB_W-1:0]    write_cmd_byteen;
    reg  [ADDR_WIDTH-1:0]     write_cmd_addr;
    reg  [BYTECOUNT_W-1:0]    write_cmd_bytecount;
    wire [MASTER_ID_W-1:0]    write_cmd_mid;
    wire [THREAD_ID_W-1:0]    write_cmd_threadid;
    wire [1:0]                write_cmd_lock;
    reg  [BURST_TYPE_W-1:0]   write_cmd_bursttype;
    wire [BURST_SIZE_W-1:0]   write_cmd_size;
    wire [PROTECTION_W-1:0]   write_cmd_protection;
    wire [CACHE_W-1:0]        write_cmd_cache;
    wire [USER_W-1:0]         write_cmd_user;
  
    reg                       internal_write_cp_endofburst;
    reg                       internal_read_cp_startofburst;
    reg                       internal_read_cp_endofburst;  
  
    wire [ADDR_WIDTH-1:0]     read_cmd_addr;
    wire [ADDR_WIDTH-1:0]     read_cmd_addr_aligned;
    wire [BURST_SIZE_W-1:0]   read_cmd_size;
    reg  [BYTECOUNT_W-1:0]    read_cmd_bytecount;
    wire [MASTER_ID_W-1:0]    read_cmd_mid;
    wire [THREAD_ID_W-1:0]    read_cmd_threadid;
    wire [1:0]                read_cmd_lock;
    wire [PROTECTION_W-1:0]   read_cmd_protection;
    wire [CACHE_W-1:0]        read_cmd_cache;
    reg  [BURST_TYPE_W-1:0]   read_cmd_bursttype;
    wire [USER_W-1:0]         read_cmd_user;
    wire                      read_cmd_compressed;
  
    wire                      awvalid_suppress; 
    wire                      wvalid_suppress;
  
    wire [ST_DATA_W:0]        write_rsp_fifo_indata; // one extra bit to store write_cp_endofpacket
    wire [ST_DATA_W:0]        write_rsp_fifo_outdata;
    wire                      write_rsp_fifo_invalid;
    wire                      write_rsp_fifo_inready;
    wire                      write_rsp_fifo_outvalid;
    wire                      write_rsp_fifo_outready;
    wire [ST_DATA_W:0]        read_rsp_fifo_indata;  // one extra bit to store read_cp_endofpacket
    wire [ST_DATA_W:0]        read_rsp_fifo_outdata;
    wire                      read_rsp_fifo_invalid;
    wire                      read_rsp_fifo_inready;
    wire                      read_rsp_fifo_outvalid;
    wire                      read_rsp_fifo_outready;  
    wire                      write_rsp_fifo_posted;
  
  
    wire areset = ~aresetn;  // altera_sc_fifo has an active high reset
  
    reg                       read_sop_enable;
    reg                       address_taken;
    reg                       data_taken;
    reg  [BYTECOUNT_W-1:0]    minimum_bytecount;
    wire [31:0]               awlen_wire; // to solve qis warnings
    wire [31:0]               arlen_wire; // to solve qis warnings
    reg                       write_rsp_fifo_sop; // create a start of first subburst to clear the storage of the bresp merging mechanism
    wire                      write_rsp_fifo_eop;
    reg [RESPONSE_W-1:0]      bresp_merged;  // merges the response according to the following SLVERR->DECERR->OKAY->EXOKAY
    reg [RESPONSE_W-1:0]      previous_response_in;
    reg                       reset_merged_output; 
  
    typedef enum bit [1:0] 
    {
      FIXED       = 2'b00,
      INCR        = 2'b01,
      WRAP        = 2'b10,
      RESERVED    = 2'b11
    } AxiBurstType;

    //------------------------------------
    // Write Command Packet -> AXI Transaction
    //------------------------------------

    // DATA PATH START

    assign write_cmd_data        = write_cp_data[PKT_DATA_H : PKT_DATA_L];
    assign write_cmd_byteen      = write_cp_data[PKT_BYTEEN_H : PKT_BYTEEN_L];
    assign write_cmd_mid         = write_cp_data[PKT_SRC_ID_H : PKT_SRC_ID_L];
    assign write_cmd_size        = write_cp_data[PKT_BURST_SIZE_H : PKT_BURST_SIZE_L];
    assign write_cmd_lock        = { 1'b0, write_cp_data[PKT_TRANS_EXCLUSIVE] };
    assign write_cmd_addr        = write_cp_data[PKT_ADDR_H : PKT_ADDR_L];
    assign write_cmd_bytecount   = write_cp_data[PKT_BYTE_CNT_H : PKT_BYTE_CNT_L]; 
    assign write_cmd_threadid    = write_cp_data[PKT_THREAD_ID_H : PKT_THREAD_ID_L];
    assign write_cmd_protection  = write_cp_data[PKT_PROTECTION_H : PKT_PROTECTION_L];
    assign write_cmd_cache       = write_cp_data[PKT_CACHE_H : PKT_CACHE_L];
    assign write_cmd_bursttype   = write_cp_data[PKT_BURST_TYPE_H : PKT_BURST_TYPE_L];
    assign write_cmd_user        = write_cp_data[PKT_ADDR_SIDEBAND_H : PKT_ADDR_SIDEBAND_L];
    assign awlen_wire            = (write_cmd_bytecount >> log2ceil(PKT_DATA_W / 8)) - 1;

    // assign all signals to pass through to AXI AW* and W* channels, except for last, valid and ready 
    always @* begin
        awid             = {AXI_SLAVE_ID_W{1'b0}};
        awid             = PASS_ID_TO_SLAVE ? {write_cmd_mid, write_cmd_threadid} : '0;
    end
    assign awaddr        = write_cmd_addr;
    assign awlen         = awlen_wire[AXI_BURST_LENGTH_WIDTH-1:0];
    assign awsize        = write_cmd_size[2:0];
    assign awcache       = write_cmd_cache;
    assign awprot        = write_cmd_protection;
    assign awlock        = write_cmd_lock[AXI_LOCK_WIDTH-1:0];
    assign awuser        = write_cmd_user;
    assign awburst       = write_cmd_bursttype;

    always @* begin
        wid             = {AXI_SLAVE_ID_W{1'b0}};
        wid             = PASS_ID_TO_SLAVE ? {write_cmd_mid, write_cmd_threadid} : '0;
    end   
    assign wdata         = write_cmd_data;
    assign wstrb         = write_cmd_byteen;
    assign wlast         = internal_write_cp_endofburst;
    
    generate
        if (AXI_VERSION == "AXI4") begin
            wire [DATA_USER_WIDTH-1:0] write_cmd_wuser;
            wire [3:0]                 write_cmd_qos;
            assign write_cmd_wuser       = write_cp_data[PKT_DATA_SIDEBAND_H : PKT_DATA_SIDEBAND_L];
            assign write_cmd_qos         = write_cp_data[PKT_QOS_H : PKT_QOS_L];
            assign wuser         = write_cmd_wuser;
            assign awqos         = write_cmd_qos;
            assign awregion      = 4'b0;
        end
        else begin
            assign wuser         = '0;
            assign awqos         = '0;
            assign awregion      = '0;   
        end
    endgenerate

    reg [RESPONSE_W-1:0]  previous_response;

    //----------------------------------
    // Write Response Status Merging
    // 
    // Write responses need to be merged when there are subbursts.
    // Each subburst will have a response, which will need to be
    // combined into one write response status.
    //
    // The merged response must follow the following precedence: 
    // SLVERR -> DECERR -> OKAY -> EXOKAY  (10 -> 11 -> 00 -> 01)
    //
    //  bresp       00  01  11  10
    //  previous 
    //  00          00  00  11  10
    //  01          00  01  11  10
    //  11          11  11  11  11
    //  10          10  10  11  10
    //-----------------------------------

    function reg [1:0] merged_response;
        input [3:0] previous_and_current_responses;
 
        begin
            case (previous_and_current_responses)
   
                4'b1101: merged_response = 2'b11;
                4'b1100: merged_response = 2'b11;
                4'b1111: merged_response = 2'b11;
                4'b1110: merged_response = 2'b11;

                4'b1001: merged_response = 2'b10;
                4'b1000: merged_response = 2'b10;
                4'b1011: merged_response = 2'b11;
                4'b1010: merged_response = 2'b10; 
   
                4'b0001: merged_response = 2'b00;
                4'b0000: merged_response = 2'b00;
                4'b0011: merged_response = 2'b11;
                4'b0010: merged_response = 2'b10;
   
                4'b0101: merged_response = 2'b01;
                4'b0100: merged_response = 2'b00;
                4'b0111: merged_response = 2'b11;
                4'b0110: merged_response = 2'b10;
   
                default: merged_response = 2'b01;
            endcase
        end
    endfunction   

    always_ff @(posedge aclk, negedge aresetn) begin
        if (!aresetn) begin
            write_rsp_fifo_sop  <= 1'b1;
        end 
        else begin
            if (write_rsp_fifo_outready) begin
                write_rsp_fifo_sop <= 1'b0;
                if (write_rsp_fifo_eop)
                    write_rsp_fifo_sop <= 1'b1;
            end
        end
    end

    always_comb begin
        reset_merged_output = write_rsp_fifo_sop && bvalid;
        previous_response_in = reset_merged_output ? bresp : previous_response;
        bresp_merged = merged_response( {previous_response_in, bresp} );
    end

    always_ff @(posedge aclk or negedge aresetn) begin
        if (!aresetn) begin 
            previous_response <= 2'b01;
        end
        else begin
            if (bvalid) begin
                previous_response <= bresp_merged;
            end
        end
    end

    //------------------------------------
    // Write response packet formation
    //------------------------------------
    assign write_rsp_fifo_indata = {write_cp_endofpacket, write_cp_data};
    always_comb
    begin
        write_rp_data = write_rsp_fifo_outdata[ST_DATA_W-1:0];
        write_rp_data[PKT_DEST_ID_H:PKT_DEST_ID_L]       			= write_rsp_fifo_outdata[PKT_SRC_ID_H:PKT_SRC_ID_L];
        write_rp_data[PKT_SRC_ID_H:PKT_SRC_ID_L]         			= write_rsp_fifo_outdata[PKT_DEST_ID_H:PKT_DEST_ID_L];
		write_rp_data[PKT_ORI_BURST_SIZE_H:PKT_ORI_BURST_SIZE_L]	= write_rsp_fifo_outdata[PKT_ORI_BURST_SIZE_H:PKT_ORI_BURST_SIZE_L];
        if (PASS_ID_TO_SLAVE) begin
            { write_rp_data[PKT_DEST_ID_H:PKT_DEST_ID_L], write_rp_data[PKT_THREAD_ID_H:PKT_THREAD_ID_L] }  = bid;
        end
        if (AXI_VERSION == "AXI4") begin
            write_rp_data[PKT_DATA_SIDEBAND_H : PKT_DATA_SIDEBAND_L]           = buser;
        end
        else begin
            write_rp_data[PKT_DATA_SIDEBAND_H : PKT_DATA_SIDEBAND_L]           = '0;
        end   
        write_rp_data[PKT_RESPONSE_STATUS_H:PKT_RESPONSE_STATUS_L] = bresp_merged;
    end


    //-----------------------------------------
    // Write response merging
    //
    // Write responses are only one cycle long, so always
    // assert startofpacket and endofpacket.
    //-----------------------------------------
    assign write_rsp_fifo_eop     = write_rsp_fifo_outdata[ST_DATA_W];
    assign write_rp_startofpacket = 1'b1;
    assign write_rp_endofpacket   = 1'b1;
    
    // DATA PATH END

    // CONTROL LOGIC START


    //------------------------------------
    // Control logic for internal startofpacket and endofpacket generation.
    //
    // The need for internal sop / eop: some masters can produce bursts of 
    // length > 16 while AXI slaves can only accept a maximum of 16. As a result, 
    // the burst adapter will split the burst (within the same packet).
    //
    // For example, a burst of 18 from a master will be converted into 2 bursts of 
    // 16+2 by the burst adapter in the SAME packet. So, the bytecount generated in 
    // the packet (assuming a 32bit data width) is <sop>64,60,56,..,12,8,4,8,4<eop>. 
    // The last "8,4" represents the 2nd burst with length of 2.
    // 
    // We need to convert this into <sop>64,60,56,..,12,8,4<eop><sop>8,4<eop> internally.
    //------------------------------------

    wire [31:0] minimum_bytecount_wire = PKT_DATA_W / 8; // to solve qis warning
    assign minimum_bytecount = minimum_bytecount_wire[BYTECOUNT_W-1:0];

    assign internal_write_cp_endofburst = (write_cmd_bytecount == minimum_bytecount);

    //------------------------------------
    // Control logic for awvalid, wvalid, write_cp_ready suppression
    // awvalid and wvalid suprression:  At the first transaction within a burst, a merlin packet will contain both address and data with valid asserted.
    //                                  the slave may accept the data and address by asserting wready / awready respectively. If awready is asserted before wready is asserted, the NI
    //                                  will need to suppress awvalid into the slave until wready is asserted, in order to avoid taking in unused addresses. 
    //                                  This is also true if wready is asserted, wvalid will be suppressed until awready is asserted.
    //                                  For subsequent transaction within a burst, awvalid should continue to be suppressed because only data contains valid information.
    //                                  No suppression is needed for wvalid.
    //  write_cp_ready assertion:       At first burst transaction, write_cp_ready is asserted when both awready and wready is asserted. on subsequent transaction, write_cp_ready
    //                                  is asserted depending on when wready is asserted.
    //  backpressure mechanism:         write_cp_ready will be deasserted whenever write rsp fifo is full. this will cause write_cp_valid to be held high, and my cause the slave to
    //                                  continuously taking in new write commands errornously, if the write acceptance capability of the slave is > fifo depth.
    //                                  Hence awvalid and wvalid needs to be held low to prevent this from happening.
    //------------------------------------
    assign  write_cp_ready = (awready && wready && write_rsp_fifo_inready) || (address_taken && wready) || (data_taken && awready);
    
    assign  awvalid = write_cp_valid && !address_taken && write_rsp_fifo_inready;  // awvalid suppression
    assign  wvalid  = write_cp_valid && !data_taken && write_rsp_fifo_inready;     // wvalid suppression

    always_ff @(posedge aclk or negedge aresetn) begin
        if (!aresetn) begin
            address_taken   <= '0;
        end
        else begin
            if (awvalid && awready)
                address_taken   <= '1;
            if (write_cp_valid && write_cp_ready && internal_write_cp_endofburst) // address is considered taken until end of packet
                address_taken   <= '0;
        end        
    end

    always_ff @(posedge aclk or negedge aresetn) begin
        if  (!aresetn) begin
            data_taken  <= '0;
        end
        else begin
            if (wvalid && wready)
                data_taken  <= '1;
            if (write_cp_valid && write_cp_ready)
                data_taken  <= '0;
        end
    end

    //------------------------------------
    // bready and bvalid
    //
    // We need to assert bready when a posted write transaction occurs. This is because the network
    // will not send a ready signal through write_rp_ready, but the slave will need bready to 
    // deassert bvalid and continue to the next response.
    //------------------------------------
    assign write_rsp_fifo_posted  = write_rsp_fifo_outdata[PKT_TRANS_POSTED];
    // the last OR statement is for multiple nonposted subburst in a packet, for the non-final subburst, 
    // we need to fake a bready response to prevent the bvalid from being asserted forever and hang
    // for the last bvalid in a packet, it will wait for the write_rp_ready, which signals the system is ready to accept a write response
    assign bready                 = write_rp_ready || (bvalid && write_rsp_fifo_posted) ||  bvalid && write_rsp_fifo_outvalid && !write_rsp_fifo_posted && !write_rsp_fifo_eop;  

    // only send a write response when there is a valid non-posted fifo entry
    // also only send on the endofpacket
    assign write_rp_valid         = bvalid && write_rsp_fifo_outvalid && !write_rsp_fifo_posted && write_rsp_fifo_eop;
    //assign write_rp_valid         = bvalid && write_rsp_fifo_outvalid && !write_rsp_fifo_posted;

    // write to the response fifo at the end of each sub-burst
    assign write_rsp_fifo_invalid  = write_cp_ready && write_cp_valid && internal_write_cp_endofburst;

    // only pop entries off the fifo when the write response is accepted (or is posted)
    //assign write_rsp_fifo_outready = bvalid && (write_rp_ready || write_rsp_fifo_posted);
    assign write_rsp_fifo_outready = bvalid && bready;

    // CONTROL LOGIC END
    
    //------------------------------------
    // Read Transaction
    //------------------------------------

    // assign command fields
    assign read_cmd_addr            = read_cp_data[PKT_ADDR_H : PKT_ADDR_L ];
    assign read_cmd_mid             = read_cp_data[PKT_SRC_ID_H :PKT_SRC_ID_L];
    assign read_cmd_bytecount       = read_cp_data[PKT_BYTE_CNT_H :PKT_BYTE_CNT_L];    
    assign read_cmd_size            = read_cp_data[PKT_BURST_SIZE_H:PKT_BURST_SIZE_L];
    assign read_cmd_threadid        = read_cp_data[PKT_THREAD_ID_H:PKT_THREAD_ID_L];
    assign read_cmd_protection      = read_cp_data[PKT_PROTECTION_H:PKT_PROTECTION_L];
    assign read_cmd_cache           = read_cp_data[PKT_CACHE_H:PKT_CACHE_L];
    assign read_cmd_bursttype       = read_cp_data[PKT_BURST_TYPE_H:PKT_BURST_TYPE_L];
    assign read_cmd_user            = read_cp_data[PKT_ADDR_SIDEBAND_H : PKT_ADDR_SIDEBAND_L];
    assign read_cmd_compressed      = read_cp_data[PKT_TRANS_COMPRESSED_READ];
    assign read_cmd_lock            = { 1'b0, read_cp_data[PKT_TRANS_EXCLUSIVE] };
    assign arlen_wire               = read_cmd_compressed ? (read_cmd_bytecount >> log2ceil(PKT_DATA_W / 8)) - 1 : '0;

    // assign all signals to pass through to AR* channels, except for last, valid and ready
    always @* begin
        arid             = {AXI_SLAVE_ID_W{1'b0}};
        arid             = PASS_ID_TO_SLAVE ? {read_cmd_mid, read_cmd_threadid} : '0;
    end   
    assign araddr       = read_cmd_addr;
    assign arlen        = arlen_wire[AXI_BURST_LENGTH_WIDTH-1:0];         
    assign arsize       = read_cmd_size[2:0];
    assign arlock       = read_cmd_lock[AXI_LOCK_WIDTH-1:0];
    assign arcache      = read_cmd_cache;
    assign arprot       = read_cmd_protection;
    assign aruser       = read_cmd_user;
    assign arburst      = read_cmd_bursttype;
    generate
        if (AXI_VERSION == "AXI4") begin
            wire [3:0]  read_cmd_qos;
            assign read_cmd_qos = read_cp_data[PKT_QOS_H : PKT_QOS_L];
            assign arqos        = read_cmd_qos;
            assign arregion     = 4'b0;
        end
        else begin
            assign arqos        = '0;
            assign arregion     = '0;
        end
    endgenerate
    // ----------------------------------------
    // Align the input address to burst uncompressor, as 
    // we want the return addresses are ligned
    // ----------------------------------------
    reg [ADDR_WIDTH + (BURSTWRAP_W-1) + BURST_SIZE_W + BURST_TYPE_W - 1:0]     address_for_alignment;
    reg [ADDR_WIDTH + log2ceil(NUMSYMBOLS)-1:0] address_after_aligned;

    assign address_for_alignment    = {read_cmd_addr, read_cmd_size};
    assign read_cmd_addr_aligned    = address_after_aligned[ADDR_WIDTH-1:0];
    
    altera_merlin_address_alignment
        #(
          .ADDR_W            (ADDR_WIDTH),
          .BURSTWRAP_W       (BURSTWRAP_W),
          .INCREMENT_ADDRESS (0),
          .NUMSYMBOLS        (NUMSYMBOLS),
          .SIZE_W            (BURST_SIZE_W)
          ) check_and_align_address_to_size
            (
             .clk(aclk),
             .reset(aresetn),
             .in_data(address_for_alignment),
             .out_data(address_after_aligned),
             .in_valid(),
             .in_sop(),
             .in_eop(),
             .out_ready()
             );
    //------------------------------------
    // Response packet formation
    //------------------------------------
    reg [ST_DATA_W-1:0]           read_cp_data_for_fifo;
    // Replace address in read command to aligned address
    always_comb
        begin
            read_cp_data_for_fifo                           = read_cp_data;
            read_cp_data_for_fifo[PKT_ADDR_H : PKT_ADDR_L ] = read_cmd_addr_aligned;
        end
    // Puss it back to fifo
    assign read_rsp_fifo_indata = {read_cp_endofpacket, read_cp_data_for_fifo}; 

  

    wire                        uncompressor_outvalid;
    wire                        uncompressor_inready;
    wire                        read_rsp_fifo_outdata_eop;
    wire                        read_rsp_fifo_outdata_is_comp;
    reg                         read_rsp_fifo_outdata_sop;
    wire [BURSTWRAP_W-1 : 0]    read_rsp_fifo_outdata_burstwrap;
    wire [ADDR_WIDTH-1 : 0]     read_rsp_fifo_outdata_addr;
    wire [BYTECOUNT_W-1: 0]     read_rsp_fifo_outdata_bytecount;
    wire [BURST_SIZE_W-1 : 0]   read_rsp_fifo_outdata_burstsize;
    wire                        uncompressor_is_comp;
    wire [BURSTWRAP_W-1 : 0]    uncompressor_burstwrap;
    wire [ADDR_WIDTH-1 : 0]     uncompressor_addr;
    wire [BYTECOUNT_W-1: 0]     uncompressor_bytecount;   
    wire [BURST_SIZE_W-1:0]     uncompressor_burstsize;

    always_comb
        begin
            read_rp_data = read_rsp_fifo_outdata[ST_DATA_W-1:0];
            read_rp_data[PKT_DATA_H : PKT_DATA_L]           = rdata;
            read_rp_data[PKT_ADDR_H : PKT_ADDR_L]           = uncompressor_addr;
            read_rp_data[PKT_BYTE_CNT_H : PKT_BYTE_CNT_L]   = uncompressor_bytecount;
            read_rp_data[PKT_TRANS_COMPRESSED_READ]         = uncompressor_is_comp;
            read_rp_data[PKT_BURSTWRAP_H:PKT_BURSTWRAP_L]   = uncompressor_burstwrap;
            read_rp_data[PKT_BURST_SIZE_H:PKT_BURST_SIZE_L] = uncompressor_burstsize;
            read_rp_data[PKT_DEST_ID_H:PKT_DEST_ID_L]       = read_rsp_fifo_outdata[PKT_SRC_ID_H:PKT_SRC_ID_L];
            read_rp_data[PKT_SRC_ID_H:PKT_SRC_ID_L]         = read_rsp_fifo_outdata[PKT_DEST_ID_H:PKT_DEST_ID_L];
			read_rp_data[PKT_ORI_BURST_SIZE_H:PKT_ORI_BURST_SIZE_L] = read_rsp_fifo_outdata[PKT_ORI_BURST_SIZE_H:PKT_ORI_BURST_SIZE_L];
            read_rp_data[PKT_RESPONSE_STATUS_H:PKT_RESPONSE_STATUS_L] = rresp;

            if (PASS_ID_TO_SLAVE) begin
                { read_rp_data[PKT_DEST_ID_H:PKT_DEST_ID_L], read_rp_data[PKT_THREAD_ID_H:PKT_THREAD_ID_L] } = rid;
            end

            if (AXI_VERSION == "AXI4") begin
                read_rp_data[PKT_DATA_SIDEBAND_H : PKT_DATA_SIDEBAND_L]           = ruser;
            end
            else begin
                read_rp_data[PKT_DATA_SIDEBAND_H : PKT_DATA_SIDEBAND_L]           = '0;
            end

        end      

// start of packet assertion should only happen at the start of burst and should not be asserted at the start of sub-burst
    always_ff @ (posedge aclk or negedge aresetn)
    if  (!aresetn)
        read_rsp_fifo_outdata_sop <= 1'b1;
    else
    begin
        if (read_rp_ready & rvalid)
            read_rsp_fifo_outdata_sop <= 1'b0;
        if (read_rp_ready & rvalid & read_rp_endofpacket)
            read_rsp_fifo_outdata_sop <= 1'b1;
    end    

    // CONTROL PATH START

    assign read_cp_ready    = arready && read_rsp_fifo_inready; //backpressure when the fifo is full
    assign arvalid          = read_cp_valid && read_rsp_fifo_inready; // its the job of the network to hold the valid signal, until the slave asserts the arready.
    assign rready           = read_rp_ready;
    assign read_rp_valid    = rvalid && uncompressor_outvalid;

    // control signals for fifo
    assign read_rsp_fifo_invalid    = read_cp_ready && read_cp_valid;    // pulse valid into FIFO  
    assign read_rsp_fifo_outready   = uncompressor_inready;  // pop off entry on last transfer in burst

    // CONTROL PATH END

    assign read_rsp_fifo_outdata_eop        = read_rsp_fifo_outdata[ST_DATA_W];
    assign read_rsp_fifo_outdata_burstwrap  = read_rsp_fifo_outdata[PKT_BURSTWRAP_H:PKT_BURSTWRAP_L];
    assign read_rsp_fifo_outdata_addr       = read_rsp_fifo_outdata[PKT_ADDR_H:PKT_ADDR_L];         
    assign read_rsp_fifo_outdata_bytecount  = read_rsp_fifo_outdata[PKT_BYTE_CNT_H:PKT_BYTE_CNT_L]; 
    assign read_rsp_fifo_outdata_is_comp    = read_rsp_fifo_outdata[PKT_TRANS_COMPRESSED_READ];
    assign read_rsp_fifo_outdata_burstsize  = read_rsp_fifo_outdata[PKT_BURST_SIZE_H:PKT_BURST_SIZE_L];

    //---------------------------------------------
    // Storing source and destination ID into FIFO.
    // Previously, the implementation was to transmit the source ID as the AWID, 
    // but problems occur when the slave can reorder the transaction.
    //
    // For slaves that reorder, we need to transmit a dummy "0" AWID out so that 
    // transactions will not be reordered. 
    // A FIFO buffers the ID to be popped out when required.
    //---------------------------------------------

    altera_merlin_burst_uncompressor #(
        .ADDR_W         (ADDR_WIDTH),
        .BURSTWRAP_W    (BURSTWRAP_W),
        .BYTE_CNT_W     (BYTECOUNT_W),
        .PKT_SYMBOLS    (NUMSYMBOLS),
        .BURST_SIZE_W   (BURST_SIZE_W)
    ) read_burst_uncompressor (
        .clk                    (aclk),
        .reset                  (areset),
        .sink_startofpacket     (read_rsp_fifo_outdata_sop),
        .sink_endofpacket       (read_rsp_fifo_outdata_eop),
        .sink_valid             (rvalid),
        .sink_ready             (uncompressor_inready),
        .sink_addr              (read_rsp_fifo_outdata_addr),
        .sink_burstwrap         (read_rsp_fifo_outdata_burstwrap),
        .sink_byte_cnt          (read_rsp_fifo_outdata_bytecount),
        .sink_is_compressed     (read_rsp_fifo_outdata_is_comp),
        .sink_burstsize         (read_rsp_fifo_outdata_burstsize),

        .source_startofpacket   (read_rp_startofpacket),
        .source_endofpacket     (read_rp_endofpacket),
        .source_valid           (uncompressor_outvalid),
        .source_ready           (read_rp_ready),
        .source_addr            (uncompressor_addr),
        .source_burstwrap       (uncompressor_burstwrap),
        .source_byte_cnt        (uncompressor_bytecount),
        .source_is_compressed   (uncompressor_is_comp),
        .source_burstsize       (uncompressor_burstsize)
    );

    altera_avalon_sc_fifo #(
        .SYMBOLS_PER_BEAT    (1),
        .BITS_PER_SYMBOL     (ST_DATA_W + 1),
        .FIFO_DEPTH          (WRITE_ACCEPTANCE_CAPABILITY),
        .CHANNEL_WIDTH       (0),
        .ERROR_WIDTH         (0),
        .USE_PACKETS         (0),
        .USE_FILL_LEVEL      (0),
        .EMPTY_LATENCY       (1),
        .USE_MEMORY_BLOCKS   (0),
        .USE_STORE_FORWARD   (0),
        .USE_ALMOST_FULL_IF  (0),
        .USE_ALMOST_EMPTY_IF (0)
    ) write_rsp_fifo (
        .clk               (aclk),                                 // clk.clk
        .reset             (areset),                               // clk_reset.reset
        .in_data           (write_rsp_fifo_indata),                // posted,src id, dest id
        .in_valid          (write_rsp_fifo_invalid),               // takes in a valid pulse when both arredy and rready is asserted
        .in_ready          (write_rsp_fifo_inready),               // FIFO will never be full
        .out_data          (write_rsp_fifo_outdata),               // When receive, response. take out ID and pass to BID
        .out_valid         (write_rsp_fifo_outvalid),              // 
        .out_ready         (write_rsp_fifo_outready),              // 
        .csr_address       (2'b00),                                // (terminated)
        .csr_read          (1'b0),                                 // (terminated)
        .csr_write         (1'b0),                                 // (terminated)
        .csr_readdata      (),                                     // (terminated)
        .csr_writedata     (32'b00000000000000000000000000000000), // (terminated)
        .almost_full_data  (),                                     // (terminated)
        .almost_empty_data (),                                     // (terminated)
        .in_startofpacket  (1'b0),                                 // (terminated)
        .in_endofpacket    (1'b0),                                 // (terminated)
        .out_startofpacket (),                                     // (terminated)
        .out_endofpacket   (),                                     // (terminated)
        .in_empty          (1'b0),                                 // (terminated)
        .out_empty         (),                                     // (terminated)
        .in_error          (1'b0),                                 // (terminated)
        .out_error         (),                                     // (terminated)
        .in_channel        (1'b0),                                 // (terminated)
        .out_channel       ()                                      // (terminated)
    );         

    altera_avalon_sc_fifo #(
        .SYMBOLS_PER_BEAT    (1),
        .BITS_PER_SYMBOL     (ST_DATA_W + 1),  //one extra bite for read_cp_endofpacket
        .FIFO_DEPTH          (READ_ACCEPTANCE_CAPABILITY),
        .CHANNEL_WIDTH       (0),
        .ERROR_WIDTH         (0),
        .USE_PACKETS         (0),
        .USE_FILL_LEVEL      (0),
        .EMPTY_LATENCY       (1),
        .USE_MEMORY_BLOCKS   (0),
        .USE_STORE_FORWARD   (0),
        .USE_ALMOST_FULL_IF  (0),
        .USE_ALMOST_EMPTY_IF (0)
    ) read_rsp_fifo (
        .clk               (aclk),                                 // clk.clk
        .reset             (areset),                               // clk_reset.reset
        .in_data           (read_rsp_fifo_indata),                 // posted, src id, dest id
        .in_valid          (read_rsp_fifo_invalid),                // takes in a valid pulse when both arredy and rready is asserted
        .in_ready          (read_rsp_fifo_inready),                // FIFO will never be full
        .out_data          (read_rsp_fifo_outdata),                // When receive, response. take out ID and pass to BID
        .out_valid         (read_rsp_fifo_outvalid),               //
        .out_ready         (read_rsp_fifo_outready),               //
        .csr_address       (2'b00),                                // (terminated)
        .csr_read          (1'b0),                                 // (terminated)
        .csr_write         (1'b0),                                 // (terminated)
        .csr_readdata      (),                                     // (terminated)
        .csr_writedata     (32'b00000000000000000000000000000000), // (terminated)
        .almost_full_data  (),                                     // (terminated)
        .almost_empty_data (),                                     // (terminated)
        .in_startofpacket  (1'b0),                                 // (terminated)
        .in_endofpacket    (1'b0),                                 // (terminated)
        .out_startofpacket (),                                     // (terminated)
        .out_endofpacket   (),                                     // (terminated)
        .in_empty          (1'b0),                                 // (terminated)
        .out_empty         (),                                     // (terminated)
        .in_error          (1'b0),                                 // (terminated)
        .out_error         (),                                     // (terminated)
        .in_channel        (1'b0),                                 // (terminated)
        .out_channel       ()                                      // (terminated)
    );      

//--------------------------------------
// Assertion: for write / read FIFOs
//--------------------------------------
// synthesis translate_off      
ERROR_write_transaction_occurs_when_write_rsp_fifo_is_full:
    assert property ( @(posedge aclk)
        disable iff (areset) (!write_rsp_fifo_inready |-> !write_rsp_fifo_invalid)
    );

ERROR_read_transaction_occurs_when_read_rsp_fifo_is_full:
    assert property ( @(posedge aclk)
        disable iff (areset) (!read_rsp_fifo_inready |-> !read_rsp_fifo_invalid)
    );    

ERROR_write_response_occurs_when_write_rsp_fifo_is_empty:
    assert property ( @(posedge aclk)
        disable iff (areset) (!write_rsp_fifo_outvalid |-> !bvalid)
    );   

ERROR_readdata_receive_but_read_ID_fifo_is_empty:
    assert property ( @(posedge aclk)
        disable iff (areset) (!read_rsp_fifo_outvalid |-> !rvalid)
    );

ERROR_pkt_trans_write_not_asserted_when_write_cp_valid_is_asserted:
    assert property ( @(posedge aclk)
        disable iff (areset) (write_cp_valid |-> write_cp_data[PKT_TRANS_WRITE])
    );

// synthesis translate_on


endmodule
