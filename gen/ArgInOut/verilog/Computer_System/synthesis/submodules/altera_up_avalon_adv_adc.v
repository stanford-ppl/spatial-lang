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


// THIS FILE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
// THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
// FROM, OUT OF OR IN CONNECTION WITH THIS FILE OR THE USE OR OTHER DEALINGS
// IN THIS FILE.

//  - - - - - - - - - -ADC- - - - - - - - - - - - - - -
module altera_up_avalon_adv_adc (clock, reset, go, sclk, cs_n, din, dout, done, reading0, reading1, 
						reading2, reading3, reading4, reading5, reading6, reading7);
input go, dout, clock, reset;

output reg done;
output reg sclk, din, cs_n;
output reg [11:0] reading0, reading1, reading2, reading3, reading4, reading5, reading6, reading7;

parameter T_SCLK = 8'd4;
parameter NUM_CH = 4'd7;
parameter BOARD = "DE1-SoC";
parameter BOARD_REV = "Autodetect";

generate

    if (BOARD == "DE1-SoC" || BOARD == "DE0-Nano-SoC")
    begin
    
        //FSM state values
        parameter resetState = 3'd0, waitState=3'd1, transState=3'd2, doneState=3'd3, pauseState=3'd4, initCtrlRegState=3'd5, pauseStateNoAddrIncr=3'd6;

        reg [2:0] currState, nextState;
        reg [14:0] dout_shift_reg;
        reg [11:0] din_shift_reg;
        reg [7:0] counter;
        reg [12:0] pause_counter;
        reg [3:0] sclk_counter;
        reg [2:0] address, next_addr;
        reg [3:0] ad_or_ltc_error_count = 4'd0;
        wire isLTC;
        
        // if AD7928 chip, isLTC == 0. if LTC 2308 chip, isLTC == 1.
        if (BOARD == "DE1-SoC" && BOARD_REV == "Autodetect")
            assign isLTC = ad_or_ltc_error_count[3];
        else if (BOARD == "DE1-SoC" && BOARD_REV == "A - E")
            assign isLTC = 1'b0;
        else // DE0-Nano-SoC and DE1-SoC rev F+ have the LTC chip
            assign isLTC = 1'b1;

        wire transStateComplete = ((isLTC && sclk_counter==4'd11) || sclk_counter==4'd15) && counter==0;

        always @(posedge clock)
            currState <=nextState;
            
        // - - - - -NextState Selection Logic - - - - - - - -
            always @(*)
            begin
                din = din_shift_reg[11];
                if (reset)
                    nextState=resetState;
                case (currState)
                    resetState:begin
                        cs_n=1;
                        done=0;
                        /*if (isLTC)
                            nextState=waitState;
                        else*/
                            nextState=initCtrlRegState;
                    end
                    initCtrlRegState:begin
                        cs_n=0;
                        done=0;
                        if (transStateComplete && !sclk)
                            if (isLTC)
                                nextState = pauseStateNoAddrIncr;
                            else
                                nextState = waitState;
                        else
                            nextState=initCtrlRegState;
                    end
                    pauseStateNoAddrIncr:begin
                        cs_n=1;
                        done=0;
                        if (pause_counter > 13'd0)
                            nextState=pauseStateNoAddrIncr;
                        else
                            nextState=transState;
                    end
                    waitState:begin
                        cs_n=1;
                        done=0;
                        if (go)
                            nextState=transState;
                        else
                            nextState=waitState;
                    end
                    transState:begin
                        cs_n=0;
                        done=0;
                        if (transStateComplete && !sclk)
                            nextState=pauseState;
                        else
                            nextState=transState;
                    end
                    // pause state must be >= 50ns! This is the "tquiet" required between conversions (or tCONV = 1.6us for LTC)
                    pauseState:begin
                        cs_n=1;
                        done=0;
                        if (pause_counter > 13'd0)
                            nextState=pauseState;
                        else if(address==NUM_CH[2:0])
                            nextState=doneState;
                        else
                            nextState=transState;
                    end
                    doneState:begin
                        cs_n=1;
                        done=1;
                        if (go)
                            nextState=doneState;
                        else
                            nextState=resetState;
                    end
                    default:begin
                        cs_n=1;
                        done=0;
                        nextState = resetState;
                    end
                endcase
            end
        // - - - - - - - - - pause counter logic - - - - - - - - - - 
            always @(posedge clock)
            if (currState == pauseState || currState == pauseStateNoAddrIncr)
                pause_counter <= pause_counter - 13'd1;
            else 
                // T_SCLK is such that T_SCLK cycles of clock -> 20MHz or less SCLK.
                if (isLTC)
                    pause_counter <= {T_SCLK[7:0],5'd0};
                else
                    pause_counter <= {5'd0,T_SCLK[7:1],(T_SCLK[0]&&sclk)}-8'd1;
        // - - - - - - - - - counter logic - - - - - - - - - - 
            always @(posedge clock or posedge reset)
            if (reset)
                counter <= T_SCLK[7:1]+(T_SCLK[0]&&sclk)-8'd1;
            else if (cs_n)
                counter <= T_SCLK[7:1]+(T_SCLK[0]&&sclk)-8'd1;
            else if (counter == 0)
                counter <= T_SCLK[7:1]+(T_SCLK[0]&&sclk)-8'd1;
            else
                counter <= counter - 8'b1;
        // - - - - - - - - ADC_SCLK generation - - - - - - - - - 
            always @(posedge clock or posedge reset)
            if (reset)
                sclk <= ~isLTC;
            else if (cs_n)
                sclk <= ~isLTC;
            else if (counter == 0)
                sclk <= ~sclk;
        // - - - - - - - - - - - sclk_counter logic - - - - - - - -
            always @ (posedge clock)
                if (reset || currState == doneState || currState == pauseState || currState == pauseStateNoAddrIncr || currState == waitState || currState == resetState)
                    sclk_counter <=4'b0;
                else if (counter == 0 && !sclk)
                    sclk_counter <= sclk_counter + 4'b1;
        // - - - - - - - - - - readings logic - - - - - - - - - -
            always @(posedge clock)
                if (reset)
                begin
                    reading0 <= 12'd0;
                    //ad_or_ltc <= 1'b0;
                    ad_or_ltc_error_count <= 4'd0;
                end
                else if (transStateComplete && sclk)
                begin
                    if (!isLTC && dout_shift_reg[13:11] == address)
                        case (dout_shift_reg[13:11])
                            3'd0: reading0 <= {dout_shift_reg[10:0],dout}; // should be {dout_shift_reg[10:0],dout}
                            3'd1: reading1 <= {dout_shift_reg[10:0],dout};
                            3'd2: reading2 <= {dout_shift_reg[10:0],dout};
                            3'd3: reading3 <= {dout_shift_reg[10:0],dout};
                            3'd4: reading4 <= {dout_shift_reg[10:0],dout};
                            3'd5: reading5 <= {dout_shift_reg[10:0],dout};
                            3'd6: reading6 <= {dout_shift_reg[10:0],dout};
                            3'd7: reading7 <= {dout_shift_reg[10:0],dout};
                        endcase
                    else if (isLTC)
                        case (address)
                            3'd0: begin
                                if (NUM_CH[2:0] == 3'd0)
                                    reading0 <= {dout_shift_reg[10:0],dout};
                                else if (NUM_CH[2:0] == 3'd1)
                                    reading1 <= {dout_shift_reg[10:0],dout};
                                else if (NUM_CH[2:0] == 3'd2)
                                    reading2 <= {dout_shift_reg[10:0],dout};
                                else if (NUM_CH[2:0] == 3'd3)
                                    reading3 <= {dout_shift_reg[10:0],dout};
                                else if (NUM_CH[2:0] == 3'd4)
                                    reading4 <= {dout_shift_reg[10:0],dout};
                                else if (NUM_CH[2:0] == 3'd5)
                                    reading5 <= {dout_shift_reg[10:0],dout};
                                else if (NUM_CH[2:0] == 3'd6)
                                    reading6 <= {dout_shift_reg[10:0],dout};
                                else if (NUM_CH[2:0] == 3'd7)
                                    reading7 <= {dout_shift_reg[10:0],dout};
                            end
                            3'd1: reading0 <= {dout_shift_reg[10:0],dout};
                            3'd2: reading1 <= {dout_shift_reg[10:0],dout};
                            3'd3: reading2 <= {dout_shift_reg[10:0],dout};
                            3'd4: reading3 <= {dout_shift_reg[10:0],dout};
                            3'd5: reading4 <= {dout_shift_reg[10:0],dout};
                            3'd6: reading5 <= {dout_shift_reg[10:0],dout};
                            3'd7: reading6 <= {dout_shift_reg[10:0],dout};
                        endcase

                    else if (ad_or_ltc_error_count < 4'd15 && currState == transState)
                        ad_or_ltc_error_count <= ad_or_ltc_error_count + 4'd1;
                        //ad_or_ltc <= 1'b1;
                end
        // - - - - - - - - - address logic - - - - - - - - -
            always @(posedge clock)
                if (reset || currState == waitState || (currState == resetState && isLTC))
                    address <= 3'd0;
                else if (currState == pauseState && pause_counter == 13'd0)
                    if (address < NUM_CH[2:0])
                        address <= next_addr;
                    /*if (address >= NUM_CH[2:0])
                        address <= 3'd0;
                    else
                        address <= next_addr;*/
        // - - - - - - - - - - dout_shift_reg logic - - - - - - - - - - - - 
            always @(posedge clock)
                if (reset)
                    dout_shift_reg <= 15'd0;
                else if (counter==0 && ~isLTC == sclk && (!isLTC || sclk_counter != 4'd11) && sclk_counter != 4'd15)
                    dout_shift_reg [14:0] <= {dout_shift_reg [13:0], dout};
        // - - - - - - - - - - din_shift_reg logic - - - - - - - - -
            always @(posedge clock)
                if (currState == resetState)
                    if (isLTC)
                        din_shift_reg <= {1'b1,NUM_CH[0],NUM_CH[2],NUM_CH[1],2'b10,6'd0};// S/D, O/S, S1, S0, UNI, SLP
                    else
                        din_shift_reg <= {3'b110,NUM_CH[2:0],6'b111001};     //13'hDF9; // Ctrl reg initialize to 0xdf90. MSB is a dummy value that doesnt actually get used
                else if ((currState == waitState && go) || currState == pauseStateNoAddrIncr ||
                            (currState == pauseState && address != NUM_CH[2:0]) ||
                            (isLTC && currState == transState && counter != 0 &&  sclk_counter == 4'd0)) // For LTC, grab immediately after transition to transState
                    if (isLTC)
                        din_shift_reg <= {1'b1,address[0],address[2],address[1],2'b10,6'd0};// S/D, O/S, S1, S0, UNI, SLP
                    else
                        din_shift_reg <= {3'b010,NUM_CH[2:0],6'b111001};     //13'h5DF9  WRITE=0,SEQ=1,DONTCARE,ADDR2,ADDR1,ADDR0,DONTCARE*6
                else if (counter == 8'd0 && isLTC == sclk)
                    din_shift_reg <={din_shift_reg[10:0],1'b0};
        // - - - - - - - - - - next_addr logic - - - - - - - - - - - -
            always @(posedge clock)
                if (reset)
                    next_addr <= 3'd0;
                else
                    next_addr <= address + 3'b1;
	end
    else if (BOARD == "DE0-Nano")
    begin
        //FSM state values
        parameter resetState = 3'd0, waitState=3'd1, transState=3'd2, doneState=3'd3, pauseState=3'd4;

        reg [2:0] currState, nextState;
        reg [10:0] shift_reg;
        reg [5:0] addr_shift_reg;
        reg [7:0] counter;
        reg [3:0] sclk_counter;
        reg [2:0] address, next_addr;

        always @(posedge clock)
            currState <=nextState;
            
        // - - - - -NextState Selection Logic - - - - - - - -
            always @(*)
            begin
                din = addr_shift_reg[5];
                if (reset)
                    nextState=resetState;
                case (currState)
                    resetState:begin
                        cs_n=1;
                        done=0;
                        nextState=waitState;
                    end
                    waitState:begin
                        cs_n=1;
                        done=0;
                        if (go)
                            nextState=transState;
                        else
                            nextState=waitState;
                    end
                    transState:begin
                        cs_n=0;
                        done=0;
                        if (sclk_counter==4'd15&& counter==0 && !sclk)
                            nextState=pauseState;
                        else
                            nextState=transState;
                    end
                    pauseState:begin
                        cs_n=0;
                        done=0;
                        if(address==3'd0)
                            nextState=doneState;
                        else
                            nextState=transState;
                    end
                    doneState:begin
                        cs_n=1;
                        done=1;
                        if (go)
                            nextState=doneState;
                        else
                            nextState=waitState;
                    end
                    default:begin
                        cs_n=1;
                        done=0;
                        nextState = resetState;
                    end
                endcase
            end
        // - - - - - - - - - counter logic - - - - - - - - - - 
            always @(posedge clock or posedge reset)
            if (reset)
                counter <= T_SCLK[7:1]+(T_SCLK[0]&&sclk)-8'd1;
            else if (cs_n)
                counter <= T_SCLK[7:1]+(T_SCLK[0]&&sclk)-8'd1;
            else if (counter == 0)
                counter <= T_SCLK[7:1]+(T_SCLK[0]&&sclk)-8'd1;
            else
                counter <= counter - 8'b1;
        // - - - - - - - - ADC_SCLK generation - - - - - - - - - 
            always @(posedge clock or posedge reset)
            if (reset)
                sclk <= 1;
            else if (cs_n)
                sclk <= 1;
            else if (counter == 0)
                sclk <= ~sclk;
        // - - - - - - - - - - - sclk_counter logic - - - - - - - -
            always @ (posedge clock)
                if (currState == doneState || currState == waitState)
                    sclk_counter <=4'b0;
                else if (counter == 0 && !sclk)
                    sclk_counter <= sclk_counter + 4'b1;
        // - - - - - - - - - - readings logic - - - - - - - - - -
            always @(posedge clock)
                if (sclk_counter == 4'd15 && counter == 0 && !sclk)
                    if (address == 0)
                        case (NUM_CH)
                            4'd2: reading1 <= {shift_reg[10:0],dout};
                            4'd3: reading2 <= {shift_reg[10:0],dout};
                            4'd4: reading3 <= {shift_reg[10:0],dout};
                            4'd5: reading4 <= {shift_reg[10:0],dout};
                            4'd6: reading5 <= {shift_reg[10:0],dout};
                            4'd7: reading6 <= {shift_reg[10:0],dout};
                            4'd8: reading7 <= {shift_reg[10:0],dout};
                        endcase
                    else 
                        case (address)
                            3'd1: reading0 <= {shift_reg[10:0],dout};
                            3'd2: reading1 <= {shift_reg[10:0],dout};
                            3'd3: reading2 <= {shift_reg[10:0],dout};
                            3'd4: reading3 <= {shift_reg[10:0],dout};
                            3'd5: reading4 <= {shift_reg[10:0],dout};
                            3'd6: reading5 <= {shift_reg[10:0],dout};
                            3'd7: reading6 <= {shift_reg[10:0],dout};
                        endcase
        // - - - - - - - - - address logic - - - - - - - - -
            always @(posedge clock)
                if (currState == resetState)
                    address <= 3'd1;
                else if (currState == pauseState)
                    if (address >= (NUM_CH-1))
                        address <= 3'd0;
                    else
                        address <= next_addr;
        // - - - - - - - - - - shift_reg logic - - - - - - - - - - - - 
            always @(posedge clock)
                if (counter==0 && !sclk && sclk_counter != 4'd15)
                    shift_reg [10:0] <= {shift_reg [9:0], dout};
        // - - - - - - - - - - addr_shift_reg logic - - - - - - - - -
            always @(posedge clock)
                if (currState == waitState && go)
                    addr_shift_reg <= 6'b000001;
                else if (currState == pauseState)
                    if (address >= (NUM_CH-1))
                        addr_shift_reg <= 6'b0;
                    else
                        addr_shift_reg <= {3'b0, next_addr};
                else if (counter==0 && sclk)
                    addr_shift_reg <={addr_shift_reg[4:0],1'b0};
        // - - - - - - - - - - next_addr logic - - - - - - - - - - - -
            always @(posedge clock)
                next_addr <= address + 3'b1;
    end
    
endgenerate
        
endmodule 
