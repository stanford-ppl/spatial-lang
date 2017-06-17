module spatial_memory_controller #(
  parameter DATA_WIDTH=512,
  parameter WORD_WIDTH=32
)(
  input clk,
  input rst_n,

  // Spatial/MAG signals
  input[63:0]                   addr,
  input[31:0]                   size,
  input                         addr_size_valid,
  input[DATA_WIDTH-1:0]         wdata,
  input                         wdata_valid,
  input                         write_mode,
  output logic[DATA_WIDTH-1:0]  rdata,
  output logic                  rdata_valid,
  output logic                  ready_for_wdata,
  output logic                  ready_for_next_cmd,
  
  // TST signals
  output logic[31:0]            cfg_addr,
  output logic[31:0]            cfg_wdata,
  output logic                  cfg_wr,
  output logic                  cfg_rd,
  // output logic                  DIRECT_force_burst_wdata,
  output logic[DATA_WIDTH-1:0]  DIRECT_wdata,
  input                         DIRECT_rdata_valid,
  input[DATA_WIDTH-1:0]         DIRECT_rdata,
  input                         tst_cfg_ack//,
  // input[31:0]                   tst_cfg_rdata
  
);

// parameter DATA_DW = DATA_WIDTH / 32;

//--------------------------
// Internal signals and assignments
//--------------------------

//--------------------------
// State Machine
//--------------------------

/**

State machine: Go from DDR commands -> MMIO commands

*/

typedef enum logic [3:0] {MC_IDLE         = 4'd0,
                          MC_INIT_1       = 4'd1,
                          MC_ADDR_LO      = 4'd2,
                          MC_ADDR_HI      = 4'd3,
                          MC_INIT_2       = 4'd4,
                          MC_LAUNCH       = 4'd5,
                          MC_WAIT_FOR_DDR = 4'd6} mc_state_t;

mc_state_t curr_state;
logic load_state;
logic [31:0] total_wd_count;
logic [31:0] num_bursts_completed;
logic [63:0] current_burst_addr;
logic [31:0] current_burst_size;

assign rdata = DIRECT_rdata;
assign rdata_valid = DIRECT_rdata_valid;
// assign DIRECT_wdata = wdata;             // TODO: Add back later to eliminate the 1 cycle delay for DIRECT_wdata (and the registers)
assign ready_for_wdata = (!load_state) && ( ((curr_state === MC_WAIT_FOR_DDR) && (num_bursts_completed < current_burst_size-1)) || ((curr_state === MC_LAUNCH) && tst_cfg_ack) );
/* // For now, setting this high entire time design runs
always_ff @(posedge clk) begin
  if (!rst_n) begin
    DIRECT_force_burst_wdata <= 0;
  end else begin
    DIRECT_force_burst_wdata <= ready_for_wdata; // could also assign this to wdata_valid, or set high entire time design is running
  end
end
*/

/*
always_ff @(posedge clk) begin
  // if (addr_size_valid) begin
    $display("[%t] ------------------------------", $realtime);
    $display("  [MC]  input -- curr_state = %d", curr_state);
    $display("  [MC]  input -- addr_size_valid = %d", addr_size_valid);
    $display("  [MC]  input -- addr = 0x%x", addr);
    $display("  [MC]  input -- size = %d", size);
    $display("  [MC]  input -- DIRECT_wdata = 0x%x", DIRECT_wdata);
    // $display("  [MC]  input -- wdata_valid = %d", wdata_valid);
    $display("  [MC]  input -- write_mode = %d", write_mode);
    $display("  [MC]  output -- ready_for_wdata = %d", ready_for_wdata);
    $display("  [MC]  output -- ready_for_next_cmd = %d", ready_for_next_cmd);
    $display("  [MC]  DIRECT_rdata = 0x%x", DIRECT_rdata);
    $display("  [MC]  DIRECT_rdata_valid = %d", DIRECT_rdata_valid);
    // $display("  [MC]  DIRECT_force_burst_wdata = %d", DIRECT_force_burst_wdata);
    $display("  [MC]  current_burst_size = %d", current_burst_size);
    $display("  [MC]  current_burst_addr = 0x%x", current_burst_addr);
    $display("  [MC]  load_state = %d", load_state);
    $display("  [MC]  total_wd_count = %d", total_wd_count);
    $display("  [MC]  num_bursts_completed = %d", num_bursts_completed);
    $display("  [MC]  tst_cfg_ack = %d", tst_cfg_ack);
    $display("  [MC]  cfg_wr = %d", cfg_wr);
    $display("  [MC]  cfg_rd = %d", cfg_rd);
    $display("  [MC]  cfg_wdata = %d", cfg_wdata);
    $display("  [MC]  cfg_addr = 0x%x", cfg_addr);
end
*/

// Output logic
always_comb
begin
  if (load_state) begin
    if (curr_state === MC_INIT_1) begin
      cfg_addr <= 32'h3c;
      cfg_wdata <= 0;
      cfg_wr <= 1;
      cfg_rd <= 0;
    end else if (curr_state === MC_ADDR_LO) begin
      cfg_addr <= 32'h40;
      cfg_wdata <= current_burst_addr[31:0];
      cfg_wr <= 1;
      cfg_rd <= 0;
    end else if (curr_state === MC_ADDR_HI) begin
      cfg_addr <= 32'h44;
      cfg_wdata <= current_burst_addr[63:32];
      cfg_wr <= 1;
      cfg_rd <= 0;
    end else if (curr_state === MC_INIT_2) begin
      cfg_addr <= 32'h4c;
      cfg_wdata <= current_burst_size-1;
      cfg_wr <= 1;
      cfg_rd <= 0;
    end else if (curr_state === MC_LAUNCH) begin
      cfg_addr <= 32'h08;
      cfg_wdata <= 32'h0000_0002;
      cfg_wr <= 1;
      cfg_rd <= 0;
    end else if (curr_state === MC_WAIT_FOR_DDR) begin
      cfg_addr <= 32'h08;
      cfg_wdata <= 32'h0000_0000;
      cfg_wr <= 0;
      cfg_rd <= 1;
    end else begin
      cfg_addr <= 0;
      cfg_wdata <= 0;
      cfg_wr <= 0;
      cfg_rd <= 0;
    end
  end
  else begin
    if (curr_state === MC_INIT_1) begin
      cfg_addr <= 32'h1c;
      cfg_wdata <= 0;
      cfg_wr <= 1;
      cfg_rd <= 0;
    end else if (curr_state === MC_ADDR_LO) begin
      cfg_addr <= 32'h20;
      cfg_wdata <= current_burst_addr[31:0];
      cfg_wr <= 1;
      cfg_rd <= 0;
    end else if (curr_state === MC_ADDR_HI) begin
      cfg_addr <= 32'h24;
      cfg_wdata <= current_burst_addr[63:32];
      cfg_wr <= 1;
      cfg_rd <= 0;
    end else if (curr_state === MC_INIT_2) begin
      cfg_addr <= 32'h2c;
      cfg_wdata <= current_burst_size-1;
      cfg_wr <= 1;
      cfg_rd <= 0;
    end else if (curr_state === MC_LAUNCH) begin
      cfg_addr <= 32'h08;
      cfg_wdata <= 32'h0000_0001;
      cfg_wr <= 1;
      cfg_rd <= 0;
    end else begin
      cfg_addr <= 0;
      cfg_wdata <= 0;
      cfg_wr <= 0;
      cfg_rd <= 0;
    end
  end
end


// Next state logic
always_ff @(posedge clk or negedge rst_n) begin
  if (!rst_n) begin
    curr_state <= MC_IDLE;
    load_state <= 0;
    total_wd_count <= 0;
    num_bursts_completed <= 0;
    current_burst_addr <= 0;
    current_burst_size <= 0;
    ready_for_next_cmd <= 0;
    DIRECT_wdata <= 0;
  end
  else begin
    case(curr_state)
      MC_IDLE: begin
              if (addr_size_valid) begin
                load_state <= !write_mode;
                current_burst_addr <= addr;
                current_burst_size <= size;
                curr_state <= MC_INIT_1;
                ready_for_next_cmd <= 1;
                if (write_mode) begin
                  DIRECT_wdata <= wdata;
                end
              end else begin
                load_state <= 0;
                curr_state <= MC_IDLE;
              end
            end
      MC_INIT_1: begin
              if (tst_cfg_ack) begin
                curr_state <= MC_ADDR_LO;
              end else begin
                curr_state <= MC_INIT_1;
              end
              ready_for_next_cmd <= 0;
              
              // TODO: Remove this if block, not needed after Raghu's fix
              if (addr_size_valid) begin
                current_burst_addr <= addr;
                if (write_mode) begin
                  DIRECT_wdata <= wdata;
                end
              end
              
            end
      MC_ADDR_LO: begin
              if (tst_cfg_ack) begin
                curr_state <= MC_ADDR_HI;
              end else begin
                curr_state <= MC_ADDR_LO;
              end
            end
      MC_ADDR_HI: begin
              if (tst_cfg_ack) begin
                curr_state <= MC_INIT_2;
              end else begin
                curr_state <= MC_ADDR_HI;
              end
            end
      MC_INIT_2: begin
              if (tst_cfg_ack) begin
                curr_state <= MC_LAUNCH;
              end else begin
                curr_state <= MC_INIT_2;
              end
            end
      MC_LAUNCH: begin
              if (tst_cfg_ack) begin
                curr_state <= MC_WAIT_FOR_DDR;
              end else begin
                curr_state <= MC_LAUNCH;
              end
            end
      MC_WAIT_FOR_DDR: begin
              if ( (load_state && DIRECT_rdata_valid) || (!load_state) ) begin
                // TODO: Add back later to eliminate the 1 cycle delay
                /*
                if (num_bursts_completed === current_burst_size-1) begin
                  num_bursts_completed <= 0;
                  if (addr_size_valid) begin
                    // Copied from MC_IDLE
                    load_state <= !write_mode;
                    current_burst_addr <= addr;
                    current_burst_size <= size;
                    curr_state <= MC_INIT_1;
                    ready_for_next_cmd <= 1;
                    if (write_mode) begin
                      DIRECT_wdata <= wdata;
                    end
                  end else begin
                */
                    load_state <= 0;
                    curr_state <= MC_IDLE;
                /*
                  end
                end else begin
                  curr_state <= MC_WAIT_FOR_DDR;
                  num_bursts_completed <= num_bursts_completed + 1;
                end
                */
              end else begin
                curr_state <= MC_WAIT_FOR_DDR;
              end
            end
      default: begin
              curr_state <= MC_IDLE;
              load_state <= 0;
            end
    endcase
  end
end

endmodule
