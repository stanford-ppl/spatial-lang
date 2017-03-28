// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Tracing implementation internals
#include "verilated_vcd_c.h"
#include "VTop__Syms.h"


//======================

void VTop::trace (VerilatedVcdC* tfp, int, int) {
    tfp->spTrace()->addCallback (&VTop::traceInit, &VTop::traceFull, &VTop::traceChg, this);
}
void VTop::traceInit(VerilatedVcd* vcdp, void* userthis, uint32_t code) {
    // Callback from vcd->open()
    VTop* t=(VTop*)userthis;
    VTop__Syms* __restrict vlSymsp = t->__VlSymsp; // Setup global symbol table
    if (!Verilated::calcUnusedSigs()) vl_fatal(__FILE__,__LINE__,__FILE__,"Turning on wave traces requires Verilated::traceEverOn(true) call before time 0.");
    vcdp->scopeEscape(' ');
    t->traceInitThis (vlSymsp, vcdp, code);
    vcdp->scopeEscape('.');
}
void VTop::traceFull(VerilatedVcd* vcdp, void* userthis, uint32_t code) {
    // Callback from vcd->dump()
    VTop* t=(VTop*)userthis;
    VTop__Syms* __restrict vlSymsp = t->__VlSymsp; // Setup global symbol table
    t->traceFullThis (vlSymsp, vcdp, code);
}

//======================


void VTop::traceInitThis(VTop__Syms* __restrict vlSymsp, VerilatedVcd* vcdp, uint32_t code) {
    VTop* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    int c=code;
    if (0 && vcdp && c) {}  // Prevent unused
    vcdp->module(vlSymsp->name()); // Setup signal names
    // Body
    {
	vlTOPp->traceInitThis__1(vlSymsp, vcdp, code);
    }
}

void VTop::traceFullThis(VTop__Syms* __restrict vlSymsp, VerilatedVcd* vcdp, uint32_t code) {
    VTop* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    int c=code;
    if (0 && vcdp && c) {}  // Prevent unused
    // Body
    {
	vlTOPp->traceFullThis__1(vlSymsp, vcdp, code);
    }
    // Final
    vlTOPp->__Vm_traceActivity = 0U;
}

void VTop::traceInitThis__1(VTop__Syms* __restrict vlSymsp, VerilatedVcd* vcdp, uint32_t code) {
    VTop* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    int c=code;
    if (0 && vcdp && c) {}  // Prevent unused
    // Body
    {
	vcdp->declBit  (c+56,"clock",-1);
	vcdp->declBit  (c+57,"reset",-1);
	vcdp->declBus  (c+58,"io_raddr",-1,1,0);
	vcdp->declBit  (c+59,"io_wen",-1);
	vcdp->declBus  (c+60,"io_waddr",-1,1,0);
	vcdp->declBus  (c+61,"io_wdata",-1,31,0);
	vcdp->declBus  (c+62,"io_rdata",-1,31,0);
	vcdp->declBit  (c+63,"io_dram_cmd_ready",-1);
	vcdp->declBit  (c+64,"io_dram_cmd_valid",-1);
	vcdp->declBus  (c+65,"io_dram_cmd_bits_addr",-1,31,0);
	vcdp->declBit  (c+66,"io_dram_cmd_bits_isWr",-1);
	vcdp->declBus  (c+67,"io_dram_cmd_bits_tag",-1,31,0);
	vcdp->declBus  (c+68,"io_dram_cmd_bits_streamId",-1,31,0);
	vcdp->declBus  (c+69,"io_dram_cmd_bits_wdata_0",-1,31,0);
	vcdp->declBus  (c+70,"io_dram_cmd_bits_wdata_1",-1,31,0);
	vcdp->declBus  (c+71,"io_dram_cmd_bits_wdata_2",-1,31,0);
	vcdp->declBus  (c+72,"io_dram_cmd_bits_wdata_3",-1,31,0);
	vcdp->declBus  (c+73,"io_dram_cmd_bits_wdata_4",-1,31,0);
	vcdp->declBus  (c+74,"io_dram_cmd_bits_wdata_5",-1,31,0);
	vcdp->declBus  (c+75,"io_dram_cmd_bits_wdata_6",-1,31,0);
	vcdp->declBus  (c+76,"io_dram_cmd_bits_wdata_7",-1,31,0);
	vcdp->declBus  (c+77,"io_dram_cmd_bits_wdata_8",-1,31,0);
	vcdp->declBus  (c+78,"io_dram_cmd_bits_wdata_9",-1,31,0);
	vcdp->declBus  (c+79,"io_dram_cmd_bits_wdata_10",-1,31,0);
	vcdp->declBus  (c+80,"io_dram_cmd_bits_wdata_11",-1,31,0);
	vcdp->declBus  (c+81,"io_dram_cmd_bits_wdata_12",-1,31,0);
	vcdp->declBus  (c+82,"io_dram_cmd_bits_wdata_13",-1,31,0);
	vcdp->declBus  (c+83,"io_dram_cmd_bits_wdata_14",-1,31,0);
	vcdp->declBus  (c+84,"io_dram_cmd_bits_wdata_15",-1,31,0);
	vcdp->declBit  (c+85,"io_dram_resp_ready",-1);
	vcdp->declBit  (c+86,"io_dram_resp_valid",-1);
	vcdp->declBus  (c+87,"io_dram_resp_bits_rdata_0",-1,31,0);
	vcdp->declBus  (c+88,"io_dram_resp_bits_rdata_1",-1,31,0);
	vcdp->declBus  (c+89,"io_dram_resp_bits_rdata_2",-1,31,0);
	vcdp->declBus  (c+90,"io_dram_resp_bits_rdata_3",-1,31,0);
	vcdp->declBus  (c+91,"io_dram_resp_bits_rdata_4",-1,31,0);
	vcdp->declBus  (c+92,"io_dram_resp_bits_rdata_5",-1,31,0);
	vcdp->declBus  (c+93,"io_dram_resp_bits_rdata_6",-1,31,0);
	vcdp->declBus  (c+94,"io_dram_resp_bits_rdata_7",-1,31,0);
	vcdp->declBus  (c+95,"io_dram_resp_bits_rdata_8",-1,31,0);
	vcdp->declBus  (c+96,"io_dram_resp_bits_rdata_9",-1,31,0);
	vcdp->declBus  (c+97,"io_dram_resp_bits_rdata_10",-1,31,0);
	vcdp->declBus  (c+98,"io_dram_resp_bits_rdata_11",-1,31,0);
	vcdp->declBus  (c+99,"io_dram_resp_bits_rdata_12",-1,31,0);
	vcdp->declBus  (c+100,"io_dram_resp_bits_rdata_13",-1,31,0);
	vcdp->declBus  (c+101,"io_dram_resp_bits_rdata_14",-1,31,0);
	vcdp->declBus  (c+102,"io_dram_resp_bits_rdata_15",-1,31,0);
	vcdp->declBus  (c+103,"io_dram_resp_bits_tag",-1,31,0);
	vcdp->declBus  (c+104,"io_dram_resp_bits_streamId",-1,31,0);
	vcdp->declBit  (c+56,"v clock",-1);
	vcdp->declBit  (c+57,"v reset",-1);
	vcdp->declBus  (c+58,"v io_raddr",-1,1,0);
	vcdp->declBit  (c+59,"v io_wen",-1);
	vcdp->declBus  (c+60,"v io_waddr",-1,1,0);
	vcdp->declBus  (c+61,"v io_wdata",-1,31,0);
	vcdp->declBus  (c+62,"v io_rdata",-1,31,0);
	vcdp->declBit  (c+63,"v io_dram_cmd_ready",-1);
	vcdp->declBit  (c+64,"v io_dram_cmd_valid",-1);
	vcdp->declBus  (c+65,"v io_dram_cmd_bits_addr",-1,31,0);
	vcdp->declBit  (c+66,"v io_dram_cmd_bits_isWr",-1);
	vcdp->declBus  (c+67,"v io_dram_cmd_bits_tag",-1,31,0);
	vcdp->declBus  (c+68,"v io_dram_cmd_bits_streamId",-1,31,0);
	vcdp->declBus  (c+69,"v io_dram_cmd_bits_wdata_0",-1,31,0);
	vcdp->declBus  (c+70,"v io_dram_cmd_bits_wdata_1",-1,31,0);
	vcdp->declBus  (c+71,"v io_dram_cmd_bits_wdata_2",-1,31,0);
	vcdp->declBus  (c+72,"v io_dram_cmd_bits_wdata_3",-1,31,0);
	vcdp->declBus  (c+73,"v io_dram_cmd_bits_wdata_4",-1,31,0);
	vcdp->declBus  (c+74,"v io_dram_cmd_bits_wdata_5",-1,31,0);
	vcdp->declBus  (c+75,"v io_dram_cmd_bits_wdata_6",-1,31,0);
	vcdp->declBus  (c+76,"v io_dram_cmd_bits_wdata_7",-1,31,0);
	vcdp->declBus  (c+77,"v io_dram_cmd_bits_wdata_8",-1,31,0);
	vcdp->declBus  (c+78,"v io_dram_cmd_bits_wdata_9",-1,31,0);
	vcdp->declBus  (c+79,"v io_dram_cmd_bits_wdata_10",-1,31,0);
	vcdp->declBus  (c+80,"v io_dram_cmd_bits_wdata_11",-1,31,0);
	vcdp->declBus  (c+81,"v io_dram_cmd_bits_wdata_12",-1,31,0);
	vcdp->declBus  (c+82,"v io_dram_cmd_bits_wdata_13",-1,31,0);
	vcdp->declBus  (c+83,"v io_dram_cmd_bits_wdata_14",-1,31,0);
	vcdp->declBus  (c+84,"v io_dram_cmd_bits_wdata_15",-1,31,0);
	vcdp->declBit  (c+85,"v io_dram_resp_ready",-1);
	vcdp->declBit  (c+86,"v io_dram_resp_valid",-1);
	vcdp->declBus  (c+87,"v io_dram_resp_bits_rdata_0",-1,31,0);
	vcdp->declBus  (c+88,"v io_dram_resp_bits_rdata_1",-1,31,0);
	vcdp->declBus  (c+89,"v io_dram_resp_bits_rdata_2",-1,31,0);
	vcdp->declBus  (c+90,"v io_dram_resp_bits_rdata_3",-1,31,0);
	vcdp->declBus  (c+91,"v io_dram_resp_bits_rdata_4",-1,31,0);
	vcdp->declBus  (c+92,"v io_dram_resp_bits_rdata_5",-1,31,0);
	vcdp->declBus  (c+93,"v io_dram_resp_bits_rdata_6",-1,31,0);
	vcdp->declBus  (c+94,"v io_dram_resp_bits_rdata_7",-1,31,0);
	vcdp->declBus  (c+95,"v io_dram_resp_bits_rdata_8",-1,31,0);
	vcdp->declBus  (c+96,"v io_dram_resp_bits_rdata_9",-1,31,0);
	vcdp->declBus  (c+97,"v io_dram_resp_bits_rdata_10",-1,31,0);
	vcdp->declBus  (c+98,"v io_dram_resp_bits_rdata_11",-1,31,0);
	vcdp->declBus  (c+99,"v io_dram_resp_bits_rdata_12",-1,31,0);
	vcdp->declBus  (c+100,"v io_dram_resp_bits_rdata_13",-1,31,0);
	vcdp->declBus  (c+101,"v io_dram_resp_bits_rdata_14",-1,31,0);
	vcdp->declBus  (c+102,"v io_dram_resp_bits_rdata_15",-1,31,0);
	vcdp->declBus  (c+103,"v io_dram_resp_bits_tag",-1,31,0);
	vcdp->declBus  (c+104,"v io_dram_resp_bits_streamId",-1,31,0);
	vcdp->declBit  (c+56,"v accel_clock",-1);
	vcdp->declBit  (c+57,"v accel_reset",-1);
	vcdp->declBit  (c+17,"v accel_io_enable",-1);
	vcdp->declBit  (c+1,"v accel_io_done",-1);
	vcdp->declBus  (c+18,"v accel_io_argIns_0",-1,31,0);
	vcdp->declBit  (c+113,"v accel_io_argOuts_0_ready",-1);
	vcdp->declBit  (c+12,"v accel_io_argOuts_0_valid",-1);
	vcdp->declBus  (c+19,"v accel_io_argOuts_0_bits",-1,31,0);
	vcdp->declBit  (c+56,"v Fringe_clock",-1);
	vcdp->declBit  (c+57,"v Fringe_reset",-1);
	vcdp->declBus  (c+58,"v Fringe_io_raddr",-1,1,0);
	vcdp->declBit  (c+59,"v Fringe_io_wen",-1);
	vcdp->declBus  (c+60,"v Fringe_io_waddr",-1,1,0);
	vcdp->declBus  (c+61,"v Fringe_io_wdata",-1,31,0);
	vcdp->declBus  (c+105,"v Fringe_io_rdata",-1,31,0);
	vcdp->declBit  (c+17,"v Fringe_io_enable",-1);
	vcdp->declBit  (c+1,"v Fringe_io_done",-1);
	vcdp->declBus  (c+18,"v Fringe_io_argIns_0",-1,31,0);
	vcdp->declBit  (c+114,"v Fringe_io_argOuts_0_ready",-1);
	vcdp->declBit  (c+115,"v Fringe_io_argOuts_0_valid",-1);
	vcdp->declBus  (c+19,"v Fringe_io_argOuts_0_bits",-1,31,0);
	vcdp->declBit  (c+63,"v Fringe_io_dram_cmd_ready",-1);
	vcdp->declBit  (c+116,"v Fringe_io_dram_cmd_valid",-1);
	vcdp->declBus  (c+20,"v Fringe_io_dram_cmd_bits_addr",-1,31,0);
	vcdp->declBit  (c+116,"v Fringe_io_dram_cmd_bits_isWr",-1);
	vcdp->declBus  (c+21,"v Fringe_io_dram_cmd_bits_tag",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe_io_dram_cmd_bits_streamId",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe_io_dram_cmd_bits_wdata_0",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe_io_dram_cmd_bits_wdata_1",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe_io_dram_cmd_bits_wdata_2",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe_io_dram_cmd_bits_wdata_3",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe_io_dram_cmd_bits_wdata_4",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe_io_dram_cmd_bits_wdata_5",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe_io_dram_cmd_bits_wdata_6",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe_io_dram_cmd_bits_wdata_7",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe_io_dram_cmd_bits_wdata_8",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe_io_dram_cmd_bits_wdata_9",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe_io_dram_cmd_bits_wdata_10",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe_io_dram_cmd_bits_wdata_11",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe_io_dram_cmd_bits_wdata_12",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe_io_dram_cmd_bits_wdata_13",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe_io_dram_cmd_bits_wdata_14",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe_io_dram_cmd_bits_wdata_15",-1,31,0);
	vcdp->declBit  (c+63,"v Fringe_io_dram_resp_ready",-1);
	vcdp->declBit  (c+86,"v Fringe_io_dram_resp_valid",-1);
	vcdp->declBus  (c+87,"v Fringe_io_dram_resp_bits_rdata_0",-1,31,0);
	vcdp->declBus  (c+88,"v Fringe_io_dram_resp_bits_rdata_1",-1,31,0);
	vcdp->declBus  (c+89,"v Fringe_io_dram_resp_bits_rdata_2",-1,31,0);
	vcdp->declBus  (c+90,"v Fringe_io_dram_resp_bits_rdata_3",-1,31,0);
	vcdp->declBus  (c+91,"v Fringe_io_dram_resp_bits_rdata_4",-1,31,0);
	vcdp->declBus  (c+92,"v Fringe_io_dram_resp_bits_rdata_5",-1,31,0);
	vcdp->declBus  (c+93,"v Fringe_io_dram_resp_bits_rdata_6",-1,31,0);
	vcdp->declBus  (c+94,"v Fringe_io_dram_resp_bits_rdata_7",-1,31,0);
	vcdp->declBus  (c+95,"v Fringe_io_dram_resp_bits_rdata_8",-1,31,0);
	vcdp->declBus  (c+96,"v Fringe_io_dram_resp_bits_rdata_9",-1,31,0);
	vcdp->declBus  (c+97,"v Fringe_io_dram_resp_bits_rdata_10",-1,31,0);
	vcdp->declBus  (c+98,"v Fringe_io_dram_resp_bits_rdata_11",-1,31,0);
	vcdp->declBus  (c+99,"v Fringe_io_dram_resp_bits_rdata_12",-1,31,0);
	vcdp->declBus  (c+100,"v Fringe_io_dram_resp_bits_rdata_13",-1,31,0);
	vcdp->declBus  (c+101,"v Fringe_io_dram_resp_bits_rdata_14",-1,31,0);
	vcdp->declBus  (c+102,"v Fringe_io_dram_resp_bits_rdata_15",-1,31,0);
	vcdp->declBus  (c+103,"v Fringe_io_dram_resp_bits_tag",-1,31,0);
	vcdp->declBus  (c+104,"v Fringe_io_dram_resp_bits_streamId",-1,31,0);
	// Tracing: v _GEN_0 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:2362
	// Tracing: v _GEN_1 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:2363
	vcdp->declBit  (c+56,"v accel clock",-1);
	vcdp->declBit  (c+57,"v accel reset",-1);
	vcdp->declBit  (c+17,"v accel io_enable",-1);
	vcdp->declBit  (c+1,"v accel io_done",-1);
	vcdp->declBus  (c+18,"v accel io_argIns_0",-1,31,0);
	vcdp->declBit  (c+113,"v accel io_argOuts_0_ready",-1);
	vcdp->declBit  (c+12,"v accel io_argOuts_0_valid",-1);
	vcdp->declBus  (c+19,"v accel io_argOuts_0_bits",-1,31,0);
	vcdp->declBit  (c+13,"v accel AccelController_done",-1);
	vcdp->declBus  (c+18,"v accel x153_readx150_number",-1,31,0);
	vcdp->declBit  (c+118,"v accel x153_readx150_debug_overflow",-1);
	// Tracing: v accel _T_724 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:509
	vcdp->declBit  (c+12,"v accel AccelController_en",-1);
	vcdp->declBit  (c+56,"v accel AccelController_sm_clock",-1);
	vcdp->declBit  (c+57,"v accel AccelController_sm_reset",-1);
	vcdp->declBit  (c+12,"v accel AccelController_sm_io_input_enable",-1);
	vcdp->declBit  (c+22,"v accel AccelController_sm_io_input_ctr_done",-1);
	vcdp->declBus  (c+119,"v accel AccelController_sm_io_input_ctr_maxIn_0",-1,31,0);
	vcdp->declBit  (c+116,"v accel AccelController_sm_io_input_forever",-1);
	vcdp->declBus  (c+120,"v accel AccelController_sm_io_input_nextState",-1,31,0);
	vcdp->declBus  (c+121,"v accel AccelController_sm_io_input_initState",-1,31,0);
	vcdp->declBit  (c+122,"v accel AccelController_sm_io_input_doneCondition",-1);
	vcdp->declBit  (c+13,"v accel AccelController_sm_io_output_done",-1);
	vcdp->declBit  (c+14,"v accel AccelController_sm_io_output_ctr_en",-1);
	vcdp->declBit  (c+8,"v accel AccelController_sm_io_output_ctr_inc",-1);
	vcdp->declBit  (c+9,"v accel AccelController_sm_io_output_rst_en",-1);
	vcdp->declBus  (c+23,"v accel AccelController_sm_io_output_ctr_maxOut_0",-1,31,0);
	vcdp->declBus  (c+123,"v accel AccelController_sm_io_output_state",-1,31,0);
	// Tracing: v accel _T_727 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:526
	// Tracing: v accel _GEN_8 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:527
	// Tracing: v accel _T_730 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:528
	vcdp->declBit  (c+56,"v accel done_latch_clock",-1);
	vcdp->declBit  (c+57,"v accel done_latch_reset",-1);
	vcdp->declBit  (c+13,"v accel done_latch_io_input_set",-1);
	vcdp->declBit  (c+116,"v accel done_latch_io_input_reset",-1);
	vcdp->declBit  (c+124,"v accel done_latch_io_input_asyn_reset",-1);
	vcdp->declBit  (c+1,"v accel done_latch_io_output_data",-1);
	// Tracing: v accel _T_738_number // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:535
	// Tracing: v accel _T_738_debug_overflow // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:536
	// Tracing: v accel _T_741 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:537
	// Tracing: v accel _T_742 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:538
	vcdp->declBus  (c+24,"v accel x154_sumx153_unk_number",-1,31,0);
	vcdp->declBit  (c+25,"v accel x154_sumx153_unk_debug_overflow",-1);
	// Tracing: v accel _T_749 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:541
	// Tracing: v accel _T_751 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:542
	// Tracing: v accel _T_752 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:543
	vcdp->declBus  (c+19,"v accel x151_argout",-1,31,0);
	// Tracing: v accel _GEN_9 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:545
	// Tracing: v accel _T_759 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:546
	// Tracing: v accel _GEN_0 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:547
	// Tracing: v accel _GEN_10 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:548
	// Tracing: v accel _GEN_1 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:549
	// Tracing: v accel _GEN_11 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:550
	// Tracing: v accel _GEN_2 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:551
	// Tracing: v accel _GEN_12 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:552
	// Tracing: v accel _GEN_3 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:553
	// Tracing: v accel _GEN_13 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:554
	// Tracing: v accel _GEN_4 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:555
	// Tracing: v accel _GEN_14 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:556
	// Tracing: v accel _GEN_5 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:557
	// Tracing: v accel _GEN_15 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:558
	// Tracing: v accel _GEN_6 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:559
	// Tracing: v accel _GEN_16 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:560
	// Tracing: v accel _GEN_7 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:561
	// Tracing: v accel _GEN_17 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:562
	vcdp->declBit  (c+56,"v accel AccelController_sm clock",-1);
	vcdp->declBit  (c+57,"v accel AccelController_sm reset",-1);
	vcdp->declBit  (c+12,"v accel AccelController_sm io_input_enable",-1);
	vcdp->declBit  (c+22,"v accel AccelController_sm io_input_ctr_done",-1);
	vcdp->declBus  (c+119,"v accel AccelController_sm io_input_ctr_maxIn_0",-1,31,0);
	vcdp->declBit  (c+116,"v accel AccelController_sm io_input_forever",-1);
	vcdp->declBus  (c+120,"v accel AccelController_sm io_input_nextState",-1,31,0);
	vcdp->declBus  (c+121,"v accel AccelController_sm io_input_initState",-1,31,0);
	vcdp->declBit  (c+122,"v accel AccelController_sm io_input_doneCondition",-1);
	vcdp->declBit  (c+13,"v accel AccelController_sm io_output_done",-1);
	vcdp->declBit  (c+14,"v accel AccelController_sm io_output_ctr_en",-1);
	vcdp->declBit  (c+8,"v accel AccelController_sm io_output_ctr_inc",-1);
	vcdp->declBit  (c+9,"v accel AccelController_sm io_output_rst_en",-1);
	vcdp->declBus  (c+23,"v accel AccelController_sm io_output_ctr_maxOut_0",-1,31,0);
	vcdp->declBus  (c+123,"v accel AccelController_sm io_output_state",-1,31,0);
	// Tracing: v accel AccelController_sm _T_36 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:209
	// Tracing: v accel AccelController_sm _GEN_19 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:210
	// Tracing: v accel AccelController_sm _T_39 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:211
	// Tracing: v accel AccelController_sm _GEN_20 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:212
	vcdp->declBit  (c+56,"v accel AccelController_sm SingleCounter_clock",-1);
	vcdp->declBit  (c+57,"v accel AccelController_sm SingleCounter_reset",-1);
	vcdp->declBus  (c+125,"v accel AccelController_sm SingleCounter_io_input_start",-1,31,0);
	vcdp->declBus  (c+126,"v accel AccelController_sm SingleCounter_io_input_max",-1,31,0);
	vcdp->declBus  (c+127,"v accel AccelController_sm SingleCounter_io_input_stride",-1,31,0);
	vcdp->declBus  (c+128,"v accel AccelController_sm SingleCounter_io_input_gap",-1,31,0);
	vcdp->declBit  (c+26,"v accel AccelController_sm SingleCounter_io_input_reset",-1);
	vcdp->declBit  (c+27,"v accel AccelController_sm SingleCounter_io_input_enable",-1);
	vcdp->declBit  (c+115,"v accel AccelController_sm SingleCounter_io_input_saturate",-1);
	vcdp->declBus  (c+2,"v accel AccelController_sm SingleCounter_io_output_count_0",-1,31,0);
	vcdp->declBus  (c+3,"v accel AccelController_sm SingleCounter_io_output_countWithoutWrap_0",-1,31,0);
	vcdp->declBit  (c+10,"v accel AccelController_sm SingleCounter_io_output_done",-1);
	vcdp->declBit  (c+11,"v accel AccelController_sm SingleCounter_io_output_extendedDone",-1);
	vcdp->declBit  (c+15,"v accel AccelController_sm SingleCounter_io_output_saturated",-1);
	// Tracing: v accel AccelController_sm _T_41 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:227
	// Tracing: v accel AccelController_sm _T_43 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:228
	// Tracing: v accel AccelController_sm _T_48 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:229
	// Tracing: v accel AccelController_sm _GEN_1 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:230
	// Tracing: v accel AccelController_sm _GEN_2 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:231
	// Tracing: v accel AccelController_sm _T_57 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:232
	// Tracing: v accel AccelController_sm _T_58 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:233
	// Tracing: v accel AccelController_sm _T_62 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:234
	// Tracing: v accel AccelController_sm _T_66 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:235
	// Tracing: v accel AccelController_sm _GEN_3 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:236
	// Tracing: v accel AccelController_sm _GEN_4 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:237
	// Tracing: v accel AccelController_sm _GEN_5 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:238
	// Tracing: v accel AccelController_sm _GEN_7 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:239
	// Tracing: v accel AccelController_sm _T_68 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:240
	// Tracing: v accel AccelController_sm _T_72 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:241
	// Tracing: v accel AccelController_sm _T_73 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:242
	// Tracing: v accel AccelController_sm _T_74 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:243
	// Tracing: v accel AccelController_sm _GEN_8 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:244
	// Tracing: v accel AccelController_sm _GEN_9 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:245
	// Tracing: v accel AccelController_sm _GEN_10 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:246
	// Tracing: v accel AccelController_sm _T_81 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:247
	// Tracing: v accel AccelController_sm _GEN_11 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:248
	// Tracing: v accel AccelController_sm _GEN_13 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:249
	// Tracing: v accel AccelController_sm _GEN_14 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:250
	// Tracing: v accel AccelController_sm _GEN_15 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:251
	// Tracing: v accel AccelController_sm _T_84 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:252
	// Tracing: v accel AccelController_sm _T_91 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:253
	// Tracing: v accel AccelController_sm _T_92 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:254
	// Tracing: v accel AccelController_sm _T_93 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:255
	// Tracing: v accel AccelController_sm _T_96 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:256
	// Tracing: v accel AccelController_sm _GEN_16 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:257
	// Tracing: v accel AccelController_sm _GEN_17 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:258
	// Tracing: v accel AccelController_sm _T_99 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:259
	// Tracing: v accel AccelController_sm _T_109 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:260
	// Tracing: v accel AccelController_sm _T_110 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:261
	// Tracing: v accel AccelController_sm _T_111 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:262
	// Tracing: v accel AccelController_sm _GEN_18 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:263
	// Tracing: v accel AccelController_sm _GEN_23 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:264
	// Tracing: v accel AccelController_sm _GEN_24 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:265
	// Tracing: v accel AccelController_sm _T_114 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:266
	// Tracing: v accel AccelController_sm _GEN_26 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:267
	// Tracing: v accel AccelController_sm _GEN_27 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:268
	// Tracing: v accel AccelController_sm _GEN_28 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:269
	// Tracing: v accel AccelController_sm _GEN_29 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:270
	// Tracing: v accel AccelController_sm _GEN_30 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:271
	// Tracing: v accel AccelController_sm _GEN_0 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:272
	// Tracing: v accel AccelController_sm _GEN_21 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:273
	// Tracing: v accel AccelController_sm _GEN_6 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:274
	// Tracing: v accel AccelController_sm _GEN_22 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:275
	// Tracing: v accel AccelController_sm _GEN_12 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:276
	// Tracing: v accel AccelController_sm _GEN_25 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:277
	vcdp->declBit  (c+56,"v accel AccelController_sm SingleCounter clock",-1);
	vcdp->declBit  (c+57,"v accel AccelController_sm SingleCounter reset",-1);
	vcdp->declBus  (c+125,"v accel AccelController_sm SingleCounter io_input_start",-1,31,0);
	vcdp->declBus  (c+126,"v accel AccelController_sm SingleCounter io_input_max",-1,31,0);
	vcdp->declBus  (c+127,"v accel AccelController_sm SingleCounter io_input_stride",-1,31,0);
	vcdp->declBus  (c+128,"v accel AccelController_sm SingleCounter io_input_gap",-1,31,0);
	vcdp->declBit  (c+26,"v accel AccelController_sm SingleCounter io_input_reset",-1);
	vcdp->declBit  (c+27,"v accel AccelController_sm SingleCounter io_input_enable",-1);
	vcdp->declBit  (c+115,"v accel AccelController_sm SingleCounter io_input_saturate",-1);
	vcdp->declBus  (c+2,"v accel AccelController_sm SingleCounter io_output_count_0",-1,31,0);
	vcdp->declBus  (c+3,"v accel AccelController_sm SingleCounter io_output_countWithoutWrap_0",-1,31,0);
	vcdp->declBit  (c+10,"v accel AccelController_sm SingleCounter io_output_done",-1);
	vcdp->declBit  (c+11,"v accel AccelController_sm SingleCounter io_output_extendedDone",-1);
	vcdp->declBit  (c+15,"v accel AccelController_sm SingleCounter io_output_saturated",-1);
	vcdp->declBit  (c+56,"v accel AccelController_sm SingleCounter FF_clock",-1);
	vcdp->declBit  (c+57,"v accel AccelController_sm SingleCounter FF_reset",-1);
	vcdp->declBus  (c+16,"v accel AccelController_sm SingleCounter FF_io_input_data",-1,31,0);
	vcdp->declBus  (c+125,"v accel AccelController_sm SingleCounter FF_io_input_init",-1,31,0);
	vcdp->declBit  (c+4,"v accel AccelController_sm SingleCounter FF_io_input_enable",-1);
	vcdp->declBit  (c+129,"v accel AccelController_sm SingleCounter FF_io_input_reset",-1);
	vcdp->declBus  (c+2,"v accel AccelController_sm SingleCounter FF_io_output_data",-1,31,0);
	// Tracing: v accel AccelController_sm SingleCounter _T_32 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:81
	// Tracing: v accel AccelController_sm SingleCounter _T_34 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:82
	// Tracing: v accel AccelController_sm SingleCounter _GEN_0 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:83
	// Tracing: v accel AccelController_sm SingleCounter _T_35 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:84
	// Tracing: v accel AccelController_sm SingleCounter _T_36 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:85
	// Tracing: v accel AccelController_sm SingleCounter _GEN_1 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:86
	// Tracing: v accel AccelController_sm SingleCounter _T_37 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:87
	// Tracing: v accel AccelController_sm SingleCounter _T_38 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:88
	// Tracing: v accel AccelController_sm SingleCounter _GEN_2 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:89
	// Tracing: v accel AccelController_sm SingleCounter _T_39 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:90
	// Tracing: v accel AccelController_sm SingleCounter _T_42 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:91
	// Tracing: v accel AccelController_sm SingleCounter _GEN_5 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:92
	// Tracing: v accel AccelController_sm SingleCounter _T_45 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:93
	// Tracing: v accel AccelController_sm SingleCounter _GEN_6 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:94
	// Tracing: v accel AccelController_sm SingleCounter _T_46 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:95
	// Tracing: v accel AccelController_sm SingleCounter _T_47 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:96
	// Tracing: v accel AccelController_sm SingleCounter _T_48 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:97
	// Tracing: v accel AccelController_sm SingleCounter _T_50 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:98
	// Tracing: v accel AccelController_sm SingleCounter _T_51 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:99
	// Tracing: v accel AccelController_sm SingleCounter _T_52 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:100
	// Tracing: v accel AccelController_sm SingleCounter _T_54 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:101
	// Tracing: v accel AccelController_sm SingleCounter _T_55 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:102
	// Tracing: v accel AccelController_sm SingleCounter _GEN_4 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:103
	// Tracing: v accel AccelController_sm SingleCounter _T_58 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:104
	// Tracing: v accel AccelController_sm SingleCounter _T_59 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:105
	// Tracing: v accel AccelController_sm SingleCounter _T_60 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:106
	// Tracing: v accel AccelController_sm SingleCounter _T_61 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:107
	// Tracing: v accel AccelController_sm SingleCounter _T_62 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:108
	// Tracing: v accel AccelController_sm SingleCounter _T_63 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:109
	// Tracing: v accel AccelController_sm SingleCounter _T_64 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:110
	// Tracing: v accel AccelController_sm SingleCounter _GEN_3 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:111
	// Tracing: v accel AccelController_sm SingleCounter _GEN_7 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:112
	vcdp->declBit  (c+56,"v accel AccelController_sm SingleCounter FF clock",-1);
	vcdp->declBit  (c+57,"v accel AccelController_sm SingleCounter FF reset",-1);
	vcdp->declBus  (c+16,"v accel AccelController_sm SingleCounter FF io_input_data",-1,31,0);
	vcdp->declBus  (c+125,"v accel AccelController_sm SingleCounter FF io_input_init",-1,31,0);
	vcdp->declBit  (c+4,"v accel AccelController_sm SingleCounter FF io_input_enable",-1);
	vcdp->declBit  (c+129,"v accel AccelController_sm SingleCounter FF io_input_reset",-1);
	vcdp->declBus  (c+2,"v accel AccelController_sm SingleCounter FF io_output_data",-1,31,0);
	vcdp->declBus  (c+28,"v accel AccelController_sm SingleCounter FF ff",-1,31,0);
	// Tracing: v accel AccelController_sm SingleCounter FF _GEN_0 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:24
	// Tracing: v accel AccelController_sm SingleCounter FF _T_17 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:25
	// Tracing: v accel AccelController_sm SingleCounter FF _T_18 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:26
	// Tracing: v accel AccelController_sm SingleCounter FF _T_19 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:27
	vcdp->declBit  (c+56,"v accel done_latch clock",-1);
	vcdp->declBit  (c+57,"v accel done_latch reset",-1);
	vcdp->declBit  (c+13,"v accel done_latch io_input_set",-1);
	vcdp->declBit  (c+116,"v accel done_latch io_input_reset",-1);
	vcdp->declBit  (c+124,"v accel done_latch io_input_asyn_reset",-1);
	vcdp->declBit  (c+1,"v accel done_latch io_output_data",-1);
	// Tracing: v accel done_latch _T_14 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:455
	// Tracing: v accel done_latch _GEN_0 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:456
	// Tracing: v accel done_latch _T_18 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:457
	// Tracing: v accel done_latch _T_19 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:458
	// Tracing: v accel done_latch _T_20 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:459
	// Tracing: v accel done_latch _T_22 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:460
	vcdp->declBit  (c+56,"v Fringe clock",-1);
	vcdp->declBit  (c+57,"v Fringe reset",-1);
	vcdp->declBus  (c+58,"v Fringe io_raddr",-1,1,0);
	vcdp->declBit  (c+59,"v Fringe io_wen",-1);
	vcdp->declBus  (c+60,"v Fringe io_waddr",-1,1,0);
	vcdp->declBus  (c+61,"v Fringe io_wdata",-1,31,0);
	vcdp->declBus  (c+105,"v Fringe io_rdata",-1,31,0);
	vcdp->declBit  (c+17,"v Fringe io_enable",-1);
	vcdp->declBit  (c+1,"v Fringe io_done",-1);
	vcdp->declBus  (c+18,"v Fringe io_argIns_0",-1,31,0);
	vcdp->declBit  (c+114,"v Fringe io_argOuts_0_ready",-1);
	vcdp->declBit  (c+115,"v Fringe io_argOuts_0_valid",-1);
	vcdp->declBus  (c+19,"v Fringe io_argOuts_0_bits",-1,31,0);
	vcdp->declBit  (c+63,"v Fringe io_dram_cmd_ready",-1);
	vcdp->declBit  (c+116,"v Fringe io_dram_cmd_valid",-1);
	vcdp->declBus  (c+20,"v Fringe io_dram_cmd_bits_addr",-1,31,0);
	vcdp->declBit  (c+116,"v Fringe io_dram_cmd_bits_isWr",-1);
	vcdp->declBus  (c+21,"v Fringe io_dram_cmd_bits_tag",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe io_dram_cmd_bits_streamId",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe io_dram_cmd_bits_wdata_0",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe io_dram_cmd_bits_wdata_1",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe io_dram_cmd_bits_wdata_2",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe io_dram_cmd_bits_wdata_3",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe io_dram_cmd_bits_wdata_4",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe io_dram_cmd_bits_wdata_5",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe io_dram_cmd_bits_wdata_6",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe io_dram_cmd_bits_wdata_7",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe io_dram_cmd_bits_wdata_8",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe io_dram_cmd_bits_wdata_9",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe io_dram_cmd_bits_wdata_10",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe io_dram_cmd_bits_wdata_11",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe io_dram_cmd_bits_wdata_12",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe io_dram_cmd_bits_wdata_13",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe io_dram_cmd_bits_wdata_14",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe io_dram_cmd_bits_wdata_15",-1,31,0);
	vcdp->declBit  (c+63,"v Fringe io_dram_resp_ready",-1);
	vcdp->declBit  (c+86,"v Fringe io_dram_resp_valid",-1);
	vcdp->declBus  (c+87,"v Fringe io_dram_resp_bits_rdata_0",-1,31,0);
	vcdp->declBus  (c+88,"v Fringe io_dram_resp_bits_rdata_1",-1,31,0);
	vcdp->declBus  (c+89,"v Fringe io_dram_resp_bits_rdata_2",-1,31,0);
	vcdp->declBus  (c+90,"v Fringe io_dram_resp_bits_rdata_3",-1,31,0);
	vcdp->declBus  (c+91,"v Fringe io_dram_resp_bits_rdata_4",-1,31,0);
	vcdp->declBus  (c+92,"v Fringe io_dram_resp_bits_rdata_5",-1,31,0);
	vcdp->declBus  (c+93,"v Fringe io_dram_resp_bits_rdata_6",-1,31,0);
	vcdp->declBus  (c+94,"v Fringe io_dram_resp_bits_rdata_7",-1,31,0);
	vcdp->declBus  (c+95,"v Fringe io_dram_resp_bits_rdata_8",-1,31,0);
	vcdp->declBus  (c+96,"v Fringe io_dram_resp_bits_rdata_9",-1,31,0);
	vcdp->declBus  (c+97,"v Fringe io_dram_resp_bits_rdata_10",-1,31,0);
	vcdp->declBus  (c+98,"v Fringe io_dram_resp_bits_rdata_11",-1,31,0);
	vcdp->declBus  (c+99,"v Fringe io_dram_resp_bits_rdata_12",-1,31,0);
	vcdp->declBus  (c+100,"v Fringe io_dram_resp_bits_rdata_13",-1,31,0);
	vcdp->declBus  (c+101,"v Fringe io_dram_resp_bits_rdata_14",-1,31,0);
	vcdp->declBus  (c+102,"v Fringe io_dram_resp_bits_rdata_15",-1,31,0);
	vcdp->declBus  (c+103,"v Fringe io_dram_resp_bits_tag",-1,31,0);
	vcdp->declBus  (c+104,"v Fringe io_dram_resp_bits_streamId",-1,31,0);
	vcdp->declBit  (c+56,"v Fringe regs_clock",-1);
	vcdp->declBit  (c+57,"v Fringe regs_reset",-1);
	vcdp->declBus  (c+58,"v Fringe regs_io_raddr",-1,1,0);
	vcdp->declBit  (c+59,"v Fringe regs_io_wen",-1);
	vcdp->declBus  (c+60,"v Fringe regs_io_waddr",-1,1,0);
	vcdp->declBus  (c+61,"v Fringe regs_io_wdata",-1,31,0);
	vcdp->declBus  (c+105,"v Fringe regs_io_rdata",-1,31,0);
	vcdp->declBus  (c+29,"v Fringe regs_io_argIns_0",-1,31,0);
	vcdp->declBus  (c+30,"v Fringe regs_io_argIns_1",-1,31,0);
	vcdp->declBus  (c+18,"v Fringe regs_io_argIns_2",-1,31,0);
	vcdp->declBit  (c+130,"v Fringe regs_io_argOuts_0_ready",-1);
	vcdp->declBit  (c+31,"v Fringe regs_io_argOuts_0_valid",-1);
	vcdp->declBus  (c+32,"v Fringe regs_io_argOuts_0_bits",-1,31,0);
	vcdp->declBit  (c+131,"v Fringe regs_io_argOuts_1_ready",-1);
	vcdp->declBit  (c+115,"v Fringe regs_io_argOuts_1_valid",-1);
	vcdp->declBus  (c+19,"v Fringe regs_io_argOuts_1_bits",-1,31,0);
	// Tracing: v Fringe _T_577 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:2016
	// Tracing: v Fringe _T_578 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:2017
	// Tracing: v Fringe _T_579 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:2018
	// Tracing: v Fringe _T_580 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:2019
	vcdp->declBit  (c+56,"v Fringe depulser_clock",-1);
	vcdp->declBit  (c+57,"v Fringe depulser_reset",-1);
	vcdp->declBit  (c+1,"v Fringe depulser_io_in",-1);
	vcdp->declBit  (c+132,"v Fringe depulser_io_rst",-1);
	vcdp->declBit  (c+31,"v Fringe depulser_io_out",-1);
	vcdp->declBit  (c+133,"v Fringe status_ready",-1);
	vcdp->declBit  (c+31,"v Fringe status_valid",-1);
	vcdp->declBus  (c+32,"v Fringe status_bits",-1,31,0);
	// Tracing: v Fringe _GEN_0 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:2028
	// Tracing: v Fringe _T_595 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:2029
	vcdp->declBit  (c+56,"v Fringe mag_clock",-1);
	vcdp->declBit  (c+57,"v Fringe mag_reset",-1);
	vcdp->declBit  (c+63,"v Fringe mag_io_dram_cmd_ready",-1);
	vcdp->declBit  (c+116,"v Fringe mag_io_dram_cmd_valid",-1);
	vcdp->declBus  (c+20,"v Fringe mag_io_dram_cmd_bits_addr",-1,31,0);
	vcdp->declBit  (c+116,"v Fringe mag_io_dram_cmd_bits_isWr",-1);
	vcdp->declBus  (c+21,"v Fringe mag_io_dram_cmd_bits_tag",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag_io_dram_cmd_bits_streamId",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag_io_dram_cmd_bits_wdata_0",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag_io_dram_cmd_bits_wdata_1",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag_io_dram_cmd_bits_wdata_2",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag_io_dram_cmd_bits_wdata_3",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag_io_dram_cmd_bits_wdata_4",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag_io_dram_cmd_bits_wdata_5",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag_io_dram_cmd_bits_wdata_6",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag_io_dram_cmd_bits_wdata_7",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag_io_dram_cmd_bits_wdata_8",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag_io_dram_cmd_bits_wdata_9",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag_io_dram_cmd_bits_wdata_10",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag_io_dram_cmd_bits_wdata_11",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag_io_dram_cmd_bits_wdata_12",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag_io_dram_cmd_bits_wdata_13",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag_io_dram_cmd_bits_wdata_14",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag_io_dram_cmd_bits_wdata_15",-1,31,0);
	vcdp->declBit  (c+134,"v Fringe mag_io_dram_resp_ready",-1);
	vcdp->declBit  (c+86,"v Fringe mag_io_dram_resp_valid",-1);
	vcdp->declBus  (c+87,"v Fringe mag_io_dram_resp_bits_rdata_0",-1,31,0);
	vcdp->declBus  (c+88,"v Fringe mag_io_dram_resp_bits_rdata_1",-1,31,0);
	vcdp->declBus  (c+89,"v Fringe mag_io_dram_resp_bits_rdata_2",-1,31,0);
	vcdp->declBus  (c+90,"v Fringe mag_io_dram_resp_bits_rdata_3",-1,31,0);
	vcdp->declBus  (c+91,"v Fringe mag_io_dram_resp_bits_rdata_4",-1,31,0);
	vcdp->declBus  (c+92,"v Fringe mag_io_dram_resp_bits_rdata_5",-1,31,0);
	vcdp->declBus  (c+93,"v Fringe mag_io_dram_resp_bits_rdata_6",-1,31,0);
	vcdp->declBus  (c+94,"v Fringe mag_io_dram_resp_bits_rdata_7",-1,31,0);
	vcdp->declBus  (c+95,"v Fringe mag_io_dram_resp_bits_rdata_8",-1,31,0);
	vcdp->declBus  (c+96,"v Fringe mag_io_dram_resp_bits_rdata_9",-1,31,0);
	vcdp->declBus  (c+97,"v Fringe mag_io_dram_resp_bits_rdata_10",-1,31,0);
	vcdp->declBus  (c+98,"v Fringe mag_io_dram_resp_bits_rdata_11",-1,31,0);
	vcdp->declBus  (c+99,"v Fringe mag_io_dram_resp_bits_rdata_12",-1,31,0);
	vcdp->declBus  (c+100,"v Fringe mag_io_dram_resp_bits_rdata_13",-1,31,0);
	vcdp->declBus  (c+101,"v Fringe mag_io_dram_resp_bits_rdata_14",-1,31,0);
	vcdp->declBus  (c+102,"v Fringe mag_io_dram_resp_bits_rdata_15",-1,31,0);
	vcdp->declBus  (c+103,"v Fringe mag_io_dram_resp_bits_tag",-1,31,0);
	vcdp->declBus  (c+104,"v Fringe mag_io_dram_resp_bits_streamId",-1,31,0);
	vcdp->declBit  (c+116,"v Fringe mag_io_config_scatterGather",-1);
	vcdp->declBit  (c+116,"v Fringe magConfig_scatterGather",-1);
	// Tracing: v Fringe _GEN_1 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:2076
	// Tracing: v Fringe _GEN_4 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:2077
	// Tracing: v Fringe _GEN_2 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:2078
	// Tracing: v Fringe _GEN_5 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:2079
	// Tracing: v Fringe _GEN_3 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:2080
	// Tracing: v Fringe _GEN_6 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:2081
	vcdp->declBit  (c+56,"v Fringe regs clock",-1);
	vcdp->declBit  (c+57,"v Fringe regs reset",-1);
	vcdp->declBus  (c+58,"v Fringe regs io_raddr",-1,1,0);
	vcdp->declBit  (c+59,"v Fringe regs io_wen",-1);
	vcdp->declBus  (c+60,"v Fringe regs io_waddr",-1,1,0);
	vcdp->declBus  (c+61,"v Fringe regs io_wdata",-1,31,0);
	vcdp->declBus  (c+105,"v Fringe regs io_rdata",-1,31,0);
	vcdp->declBus  (c+29,"v Fringe regs io_argIns_0",-1,31,0);
	vcdp->declBus  (c+30,"v Fringe regs io_argIns_1",-1,31,0);
	vcdp->declBus  (c+18,"v Fringe regs io_argIns_2",-1,31,0);
	vcdp->declBit  (c+130,"v Fringe regs io_argOuts_0_ready",-1);
	vcdp->declBit  (c+31,"v Fringe regs io_argOuts_0_valid",-1);
	vcdp->declBus  (c+32,"v Fringe regs io_argOuts_0_bits",-1,31,0);
	vcdp->declBit  (c+131,"v Fringe regs io_argOuts_1_ready",-1);
	vcdp->declBit  (c+115,"v Fringe regs io_argOuts_1_valid",-1);
	vcdp->declBus  (c+19,"v Fringe regs io_argOuts_1_bits",-1,31,0);
	vcdp->declBit  (c+56,"v Fringe regs regs_0_clock",-1);
	vcdp->declBit  (c+57,"v Fringe regs regs_0_reset",-1);
	vcdp->declBus  (c+61,"v Fringe regs regs_0_io_in",-1,31,0);
	vcdp->declBus  (c+135,"v Fringe regs regs_0_io_init",-1,31,0);
	vcdp->declBus  (c+29,"v Fringe regs regs_0_io_out",-1,31,0);
	vcdp->declBit  (c+106,"v Fringe regs regs_0_io_enable",-1);
	// Tracing: v Fringe regs _T_57 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:765
	// Tracing: v Fringe regs _T_58 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:766
	vcdp->declBit  (c+56,"v Fringe regs regs_1_clock",-1);
	vcdp->declBit  (c+57,"v Fringe regs regs_1_reset",-1);
	vcdp->declBus  (c+107,"v Fringe regs regs_1_io_in",-1,31,0);
	vcdp->declBus  (c+136,"v Fringe regs regs_1_io_init",-1,31,0);
	vcdp->declBus  (c+30,"v Fringe regs regs_1_io_out",-1,31,0);
	vcdp->declBit  (c+108,"v Fringe regs regs_1_io_enable",-1);
	// Tracing: v Fringe regs _T_59 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:773
	// Tracing: v Fringe regs _T_61 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:774
	// Tracing: v Fringe regs _T_62 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:775
	// Tracing: v Fringe regs _T_63 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:776
	vcdp->declBit  (c+56,"v Fringe regs regs_2_clock",-1);
	vcdp->declBit  (c+57,"v Fringe regs regs_2_reset",-1);
	vcdp->declBus  (c+61,"v Fringe regs regs_2_io_in",-1,31,0);
	vcdp->declBus  (c+137,"v Fringe regs regs_2_io_init",-1,31,0);
	vcdp->declBus  (c+18,"v Fringe regs regs_2_io_out",-1,31,0);
	vcdp->declBit  (c+109,"v Fringe regs regs_2_io_enable",-1);
	// Tracing: v Fringe regs _T_65 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:783
	// Tracing: v Fringe regs _T_66 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:784
	vcdp->declBit  (c+56,"v Fringe regs regs_3_clock",-1);
	vcdp->declBit  (c+57,"v Fringe regs regs_3_reset",-1);
	vcdp->declBus  (c+19,"v Fringe regs regs_3_io_in",-1,31,0);
	vcdp->declBus  (c+138,"v Fringe regs regs_3_io_init",-1,31,0);
	vcdp->declBus  (c+33,"v Fringe regs regs_3_io_out",-1,31,0);
	vcdp->declBit  (c+115,"v Fringe regs regs_3_io_enable",-1);
	// Tracing: v Fringe regs _T_67 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:791
	// Tracing: v Fringe regs _T_69 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:792
	// Tracing: v Fringe regs _T_70 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:793
	// Tracing: v Fringe regs _T_71 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:794
	vcdp->declBit  (c+56,"v Fringe regs rport_clock",-1);
	vcdp->declBit  (c+57,"v Fringe regs rport_reset",-1);
	vcdp->declBus  (c+29,"v Fringe regs rport_io_ins_0",-1,31,0);
	vcdp->declBus  (c+30,"v Fringe regs rport_io_ins_1",-1,31,0);
	vcdp->declBus  (c+18,"v Fringe regs rport_io_ins_2",-1,31,0);
	vcdp->declBus  (c+33,"v Fringe regs rport_io_ins_3",-1,31,0);
	vcdp->declBus  (c+58,"v Fringe regs rport_io_sel",-1,1,0);
	vcdp->declBus  (c+105,"v Fringe regs rport_io_out",-1,31,0);
	vcdp->declBus  (c+29,"v Fringe regs regOuts_0",-1,31,0);
	vcdp->declBus  (c+30,"v Fringe regs regOuts_1",-1,31,0);
	vcdp->declBus  (c+18,"v Fringe regs regOuts_2",-1,31,0);
	vcdp->declBus  (c+33,"v Fringe regs regOuts_3",-1,31,0);
	// Tracing: v Fringe regs _T_82_0 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:807
	// Tracing: v Fringe regs _T_82_1 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:808
	// Tracing: v Fringe regs _T_82_2 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:809
	// Tracing: v Fringe regs _GEN_0 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:810
	// Tracing: v Fringe regs _GEN_6 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:811
	// Tracing: v Fringe regs _GEN_1 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:812
	// Tracing: v Fringe regs _GEN_7 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:813
	// Tracing: v Fringe regs _GEN_2 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:814
	// Tracing: v Fringe regs _GEN_8 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:815
	// Tracing: v Fringe regs _GEN_3 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:816
	// Tracing: v Fringe regs _GEN_9 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:817
	// Tracing: v Fringe regs _GEN_4 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:818
	// Tracing: v Fringe regs _GEN_10 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:819
	// Tracing: v Fringe regs _GEN_5 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:820
	// Tracing: v Fringe regs _GEN_11 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:821
	vcdp->declBit  (c+56,"v Fringe regs regs_0 clock",-1);
	vcdp->declBit  (c+57,"v Fringe regs regs_0 reset",-1);
	vcdp->declBus  (c+61,"v Fringe regs regs_0 io_in",-1,31,0);
	vcdp->declBus  (c+135,"v Fringe regs regs_0 io_init",-1,31,0);
	vcdp->declBus  (c+29,"v Fringe regs regs_0 io_out",-1,31,0);
	vcdp->declBit  (c+106,"v Fringe regs regs_0 io_enable",-1);
	vcdp->declBus  (c+110,"v Fringe regs regs_0 d",-1,31,0);
	vcdp->declBus  (c+29,"v Fringe regs regs_0 ff",-1,31,0);
	// Tracing: v Fringe regs regs_0 _GEN_0 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:694
	// Tracing: v Fringe regs regs_0 _T_13 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:695
	// Tracing: v Fringe regs regs_0 _GEN_1 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:696
	vcdp->declBit  (c+56,"v Fringe regs regs_1 clock",-1);
	vcdp->declBit  (c+57,"v Fringe regs regs_1 reset",-1);
	vcdp->declBus  (c+107,"v Fringe regs regs_1 io_in",-1,31,0);
	vcdp->declBus  (c+136,"v Fringe regs regs_1 io_init",-1,31,0);
	vcdp->declBus  (c+30,"v Fringe regs regs_1 io_out",-1,31,0);
	vcdp->declBit  (c+108,"v Fringe regs regs_1 io_enable",-1);
	vcdp->declBus  (c+111,"v Fringe regs regs_1 d",-1,31,0);
	vcdp->declBus  (c+30,"v Fringe regs regs_1 ff",-1,31,0);
	// Tracing: v Fringe regs regs_1 _GEN_0 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:694
	// Tracing: v Fringe regs regs_1 _T_13 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:695
	// Tracing: v Fringe regs regs_1 _GEN_1 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:696
	vcdp->declBit  (c+56,"v Fringe regs regs_2 clock",-1);
	vcdp->declBit  (c+57,"v Fringe regs regs_2 reset",-1);
	vcdp->declBus  (c+61,"v Fringe regs regs_2 io_in",-1,31,0);
	vcdp->declBus  (c+137,"v Fringe regs regs_2 io_init",-1,31,0);
	vcdp->declBus  (c+18,"v Fringe regs regs_2 io_out",-1,31,0);
	vcdp->declBit  (c+109,"v Fringe regs regs_2 io_enable",-1);
	vcdp->declBus  (c+112,"v Fringe regs regs_2 d",-1,31,0);
	vcdp->declBus  (c+18,"v Fringe regs regs_2 ff",-1,31,0);
	// Tracing: v Fringe regs regs_2 _GEN_0 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:694
	// Tracing: v Fringe regs regs_2 _T_13 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:695
	// Tracing: v Fringe regs regs_2 _GEN_1 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:696
	vcdp->declBit  (c+56,"v Fringe regs regs_3 clock",-1);
	vcdp->declBit  (c+57,"v Fringe regs regs_3 reset",-1);
	vcdp->declBus  (c+19,"v Fringe regs regs_3 io_in",-1,31,0);
	vcdp->declBus  (c+138,"v Fringe regs regs_3 io_init",-1,31,0);
	vcdp->declBus  (c+33,"v Fringe regs regs_3 io_out",-1,31,0);
	vcdp->declBit  (c+115,"v Fringe regs regs_3 io_enable",-1);
	vcdp->declBus  (c+19,"v Fringe regs regs_3 d",-1,31,0);
	vcdp->declBus  (c+33,"v Fringe regs regs_3 ff",-1,31,0);
	// Tracing: v Fringe regs regs_3 _GEN_0 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:694
	// Tracing: v Fringe regs regs_3 _T_13 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:695
	// Tracing: v Fringe regs regs_3 _GEN_1 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:696
	vcdp->declBit  (c+56,"v Fringe regs rport clock",-1);
	vcdp->declBit  (c+57,"v Fringe regs rport reset",-1);
	vcdp->declBus  (c+29,"v Fringe regs rport io_ins_0",-1,31,0);
	vcdp->declBus  (c+30,"v Fringe regs rport io_ins_1",-1,31,0);
	vcdp->declBus  (c+18,"v Fringe regs rport io_ins_2",-1,31,0);
	vcdp->declBus  (c+33,"v Fringe regs rport io_ins_3",-1,31,0);
	vcdp->declBus  (c+58,"v Fringe regs rport io_sel",-1,1,0);
	vcdp->declBus  (c+105,"v Fringe regs rport io_out",-1,31,0);
	// Tracing: v Fringe regs rport _GEN_0 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:731
	// Tracing: v Fringe regs rport _GEN_1 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:732
	// Tracing: v Fringe regs rport _GEN_2 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:733
	// Tracing: v Fringe regs rport _GEN_3 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:734
	vcdp->declBit  (c+56,"v Fringe depulser clock",-1);
	vcdp->declBit  (c+57,"v Fringe depulser reset",-1);
	vcdp->declBit  (c+1,"v Fringe depulser io_in",-1);
	vcdp->declBit  (c+132,"v Fringe depulser io_rst",-1);
	vcdp->declBit  (c+31,"v Fringe depulser io_out",-1);
	vcdp->declBit  (c+56,"v Fringe depulser r_clock",-1);
	vcdp->declBit  (c+57,"v Fringe depulser r_reset",-1);
	vcdp->declBit  (c+5,"v Fringe depulser r_io_in",-1);
	vcdp->declBit  (c+116,"v Fringe depulser r_io_init",-1);
	vcdp->declBit  (c+31,"v Fringe depulser r_io_out",-1);
	vcdp->declBit  (c+6,"v Fringe depulser r_io_enable",-1);
	// Tracing: v Fringe depulser _T_9 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:999
	// Tracing: v Fringe depulser _T_11 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1000
	vcdp->declBit  (c+56,"v Fringe depulser r clock",-1);
	vcdp->declBit  (c+57,"v Fringe depulser r reset",-1);
	vcdp->declBit  (c+5,"v Fringe depulser r io_in",-1);
	vcdp->declBit  (c+116,"v Fringe depulser r io_init",-1);
	vcdp->declBit  (c+31,"v Fringe depulser r io_out",-1);
	vcdp->declBit  (c+6,"v Fringe depulser r io_enable",-1);
	vcdp->declBit  (c+7,"v Fringe depulser r d",-1);
	vcdp->declBit  (c+31,"v Fringe depulser r ff",-1);
	// Tracing: v Fringe depulser r _GEN_0 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:959
	// Tracing: v Fringe depulser r _T_13 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:960
	// Tracing: v Fringe depulser r _GEN_1 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:961
	vcdp->declBit  (c+56,"v Fringe mag clock",-1);
	vcdp->declBit  (c+57,"v Fringe mag reset",-1);
	vcdp->declBit  (c+63,"v Fringe mag io_dram_cmd_ready",-1);
	vcdp->declBit  (c+116,"v Fringe mag io_dram_cmd_valid",-1);
	vcdp->declBus  (c+20,"v Fringe mag io_dram_cmd_bits_addr",-1,31,0);
	vcdp->declBit  (c+116,"v Fringe mag io_dram_cmd_bits_isWr",-1);
	vcdp->declBus  (c+21,"v Fringe mag io_dram_cmd_bits_tag",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag io_dram_cmd_bits_streamId",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag io_dram_cmd_bits_wdata_0",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag io_dram_cmd_bits_wdata_1",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag io_dram_cmd_bits_wdata_2",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag io_dram_cmd_bits_wdata_3",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag io_dram_cmd_bits_wdata_4",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag io_dram_cmd_bits_wdata_5",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag io_dram_cmd_bits_wdata_6",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag io_dram_cmd_bits_wdata_7",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag io_dram_cmd_bits_wdata_8",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag io_dram_cmd_bits_wdata_9",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag io_dram_cmd_bits_wdata_10",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag io_dram_cmd_bits_wdata_11",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag io_dram_cmd_bits_wdata_12",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag io_dram_cmd_bits_wdata_13",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag io_dram_cmd_bits_wdata_14",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag io_dram_cmd_bits_wdata_15",-1,31,0);
	vcdp->declBit  (c+134,"v Fringe mag io_dram_resp_ready",-1);
	vcdp->declBit  (c+86,"v Fringe mag io_dram_resp_valid",-1);
	vcdp->declBus  (c+87,"v Fringe mag io_dram_resp_bits_rdata_0",-1,31,0);
	vcdp->declBus  (c+88,"v Fringe mag io_dram_resp_bits_rdata_1",-1,31,0);
	vcdp->declBus  (c+89,"v Fringe mag io_dram_resp_bits_rdata_2",-1,31,0);
	vcdp->declBus  (c+90,"v Fringe mag io_dram_resp_bits_rdata_3",-1,31,0);
	vcdp->declBus  (c+91,"v Fringe mag io_dram_resp_bits_rdata_4",-1,31,0);
	vcdp->declBus  (c+92,"v Fringe mag io_dram_resp_bits_rdata_5",-1,31,0);
	vcdp->declBus  (c+93,"v Fringe mag io_dram_resp_bits_rdata_6",-1,31,0);
	vcdp->declBus  (c+94,"v Fringe mag io_dram_resp_bits_rdata_7",-1,31,0);
	vcdp->declBus  (c+95,"v Fringe mag io_dram_resp_bits_rdata_8",-1,31,0);
	vcdp->declBus  (c+96,"v Fringe mag io_dram_resp_bits_rdata_9",-1,31,0);
	vcdp->declBus  (c+97,"v Fringe mag io_dram_resp_bits_rdata_10",-1,31,0);
	vcdp->declBus  (c+98,"v Fringe mag io_dram_resp_bits_rdata_11",-1,31,0);
	vcdp->declBus  (c+99,"v Fringe mag io_dram_resp_bits_rdata_12",-1,31,0);
	vcdp->declBus  (c+100,"v Fringe mag io_dram_resp_bits_rdata_13",-1,31,0);
	vcdp->declBus  (c+101,"v Fringe mag io_dram_resp_bits_rdata_14",-1,31,0);
	vcdp->declBus  (c+102,"v Fringe mag io_dram_resp_bits_rdata_15",-1,31,0);
	vcdp->declBus  (c+103,"v Fringe mag io_dram_resp_bits_tag",-1,31,0);
	vcdp->declBus  (c+104,"v Fringe mag io_dram_resp_bits_streamId",-1,31,0);
	vcdp->declBit  (c+116,"v Fringe mag io_config_scatterGather",-1);
	vcdp->declBit  (c+56,"v Fringe mag addrFifo_clock",-1);
	vcdp->declBit  (c+57,"v Fringe mag addrFifo_reset",-1);
	vcdp->declBus  (c+117,"v Fringe mag addrFifo_io_deq_0",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag addrFifo_io_deq_1",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag addrFifo_io_deq_2",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag addrFifo_io_deq_3",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag addrFifo_io_deq_4",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag addrFifo_io_deq_5",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag addrFifo_io_deq_6",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag addrFifo_io_deq_7",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag addrFifo_io_deq_8",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag addrFifo_io_deq_9",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag addrFifo_io_deq_10",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag addrFifo_io_deq_11",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag addrFifo_io_deq_12",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag addrFifo_io_deq_13",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag addrFifo_io_deq_14",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag addrFifo_io_deq_15",-1,31,0);
	vcdp->declBit  (c+116,"v Fringe mag addrFifo_io_deqVld",-1);
	vcdp->declBit  (c+115,"v Fringe mag addrFifo_io_empty",-1);
	vcdp->declBit  (c+139,"v Fringe mag addrFifo_io_forceTag_ready",-1);
	vcdp->declBit  (c+116,"v Fringe mag addrFifo_io_forceTag_valid",-1);
	vcdp->declBit  (c+140,"v Fringe mag addrFifo_io_forceTag_bits",-1);
	vcdp->declBit  (c+116,"v Fringe mag addrFifo_io_tag",-1);
	vcdp->declBit  (c+115,"v Fringe mag addrFifo_io_config_chainWrite",-1);
	vcdp->declBit  (c+115,"v Fringe mag addrFifo_io_config_chainRead",-1);
	vcdp->declBit  (c+115,"v Fringe mag addrFifoConfig_chainWrite",-1);
	vcdp->declBit  (c+115,"v Fringe mag addrFifoConfig_chainRead",-1);
	// Tracing: v Fringe mag _T_536 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1498
	vcdp->declBus  (c+141,"v Fringe mag burstAddrs_0",-1,25,0);
	vcdp->declBit  (c+56,"v Fringe mag isWrFifo_clock",-1);
	vcdp->declBit  (c+57,"v Fringe mag isWrFifo_reset",-1);
	vcdp->declBit  (c+116,"v Fringe mag isWrFifo_io_deq_0",-1);
	vcdp->declBit  (c+116,"v Fringe mag isWrFifo_io_deq_1",-1);
	vcdp->declBit  (c+116,"v Fringe mag isWrFifo_io_deq_2",-1);
	vcdp->declBit  (c+116,"v Fringe mag isWrFifo_io_deq_3",-1);
	vcdp->declBit  (c+116,"v Fringe mag isWrFifo_io_deq_4",-1);
	vcdp->declBit  (c+116,"v Fringe mag isWrFifo_io_deq_5",-1);
	vcdp->declBit  (c+116,"v Fringe mag isWrFifo_io_deq_6",-1);
	vcdp->declBit  (c+116,"v Fringe mag isWrFifo_io_deq_7",-1);
	vcdp->declBit  (c+116,"v Fringe mag isWrFifo_io_deq_8",-1);
	vcdp->declBit  (c+116,"v Fringe mag isWrFifo_io_deq_9",-1);
	vcdp->declBit  (c+116,"v Fringe mag isWrFifo_io_deq_10",-1);
	vcdp->declBit  (c+116,"v Fringe mag isWrFifo_io_deq_11",-1);
	vcdp->declBit  (c+116,"v Fringe mag isWrFifo_io_deq_12",-1);
	vcdp->declBit  (c+116,"v Fringe mag isWrFifo_io_deq_13",-1);
	vcdp->declBit  (c+116,"v Fringe mag isWrFifo_io_deq_14",-1);
	vcdp->declBit  (c+116,"v Fringe mag isWrFifo_io_deq_15",-1);
	vcdp->declBit  (c+116,"v Fringe mag isWrFifo_io_deqVld",-1);
	vcdp->declBit  (c+115,"v Fringe mag isWrFifo_io_empty",-1);
	vcdp->declBit  (c+142,"v Fringe mag isWrFifo_io_forceTag_ready",-1);
	vcdp->declBit  (c+116,"v Fringe mag isWrFifo_io_forceTag_valid",-1);
	vcdp->declBit  (c+143,"v Fringe mag isWrFifo_io_forceTag_bits",-1);
	vcdp->declBit  (c+116,"v Fringe mag isWrFifo_io_tag",-1);
	vcdp->declBit  (c+115,"v Fringe mag isWrFifo_io_config_chainWrite",-1);
	vcdp->declBit  (c+115,"v Fringe mag isWrFifo_io_config_chainRead",-1);
	vcdp->declBit  (c+115,"v Fringe mag isWrFifoConfig_chainWrite",-1);
	vcdp->declBit  (c+115,"v Fringe mag isWrFifoConfig_chainRead",-1);
	vcdp->declBit  (c+56,"v Fringe mag sizeFifo_clock",-1);
	vcdp->declBit  (c+57,"v Fringe mag sizeFifo_reset",-1);
	vcdp->declBus  (c+117,"v Fringe mag sizeFifo_io_deq_0",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag sizeFifo_io_deq_1",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag sizeFifo_io_deq_2",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag sizeFifo_io_deq_3",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag sizeFifo_io_deq_4",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag sizeFifo_io_deq_5",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag sizeFifo_io_deq_6",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag sizeFifo_io_deq_7",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag sizeFifo_io_deq_8",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag sizeFifo_io_deq_9",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag sizeFifo_io_deq_10",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag sizeFifo_io_deq_11",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag sizeFifo_io_deq_12",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag sizeFifo_io_deq_13",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag sizeFifo_io_deq_14",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag sizeFifo_io_deq_15",-1,31,0);
	vcdp->declBit  (c+116,"v Fringe mag sizeFifo_io_deqVld",-1);
	vcdp->declBit  (c+115,"v Fringe mag sizeFifo_io_empty",-1);
	vcdp->declBit  (c+144,"v Fringe mag sizeFifo_io_forceTag_ready",-1);
	vcdp->declBit  (c+116,"v Fringe mag sizeFifo_io_forceTag_valid",-1);
	vcdp->declBit  (c+145,"v Fringe mag sizeFifo_io_forceTag_bits",-1);
	vcdp->declBit  (c+116,"v Fringe mag sizeFifo_io_tag",-1);
	vcdp->declBit  (c+115,"v Fringe mag sizeFifo_io_config_chainWrite",-1);
	vcdp->declBit  (c+115,"v Fringe mag sizeFifo_io_config_chainRead",-1);
	vcdp->declBit  (c+115,"v Fringe mag sizeFifoConfig_chainWrite",-1);
	vcdp->declBit  (c+115,"v Fringe mag sizeFifoConfig_chainRead",-1);
	// Tracing: v Fringe mag _T_554 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1556
	// Tracing: v Fringe mag _T_555 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1557
	// Tracing: v Fringe mag _T_557 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1558
	// Tracing: v Fringe mag _GEN_0 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1559
	// Tracing: v Fringe mag _T_558 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1560
	vcdp->declBus  (c+141,"v Fringe mag sizeInBursts",-1,25,0);
	vcdp->declBit  (c+56,"v Fringe mag dataFifo_clock",-1);
	vcdp->declBit  (c+57,"v Fringe mag dataFifo_reset",-1);
	vcdp->declBus  (c+117,"v Fringe mag dataFifo_io_deq_0",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag dataFifo_io_deq_1",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag dataFifo_io_deq_2",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag dataFifo_io_deq_3",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag dataFifo_io_deq_4",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag dataFifo_io_deq_5",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag dataFifo_io_deq_6",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag dataFifo_io_deq_7",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag dataFifo_io_deq_8",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag dataFifo_io_deq_9",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag dataFifo_io_deq_10",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag dataFifo_io_deq_11",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag dataFifo_io_deq_12",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag dataFifo_io_deq_13",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag dataFifo_io_deq_14",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag dataFifo_io_deq_15",-1,31,0);
	vcdp->declBit  (c+116,"v Fringe mag dataFifo_io_deqVld",-1);
	vcdp->declBit  (c+115,"v Fringe mag dataFifo_io_empty",-1);
	vcdp->declBit  (c+146,"v Fringe mag dataFifo_io_forceTag_ready",-1);
	vcdp->declBit  (c+115,"v Fringe mag dataFifo_io_forceTag_valid",-1);
	vcdp->declBit  (c+116,"v Fringe mag dataFifo_io_forceTag_bits",-1);
	vcdp->declBit  (c+116,"v Fringe mag dataFifo_io_tag",-1);
	vcdp->declBit  (c+116,"v Fringe mag dataFifo_io_config_chainWrite",-1);
	vcdp->declBit  (c+116,"v Fringe mag dataFifo_io_config_chainRead",-1);
	vcdp->declBit  (c+116,"v Fringe mag dataFifoConfig_chainWrite",-1);
	vcdp->declBit  (c+116,"v Fringe mag dataFifoConfig_chainRead",-1);
	vcdp->declBit  (c+56,"v Fringe mag burstCounter_clock",-1);
	vcdp->declBit  (c+57,"v Fringe mag burstCounter_reset",-1);
	vcdp->declBus  (c+117,"v Fringe mag burstCounter_io_max",-1,31,0);
	vcdp->declBus  (c+127,"v Fringe mag burstCounter_io_stride",-1,31,0);
	vcdp->declBus  (c+34,"v Fringe mag burstCounter_io_out",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag burstCounter_io_next",-1,31,0);
	vcdp->declBit  (c+116,"v Fringe mag burstCounter_io_reset",-1);
	vcdp->declBit  (c+116,"v Fringe mag burstCounter_io_enable",-1);
	vcdp->declBit  (c+116,"v Fringe mag burstCounter_io_saturate",-1);
	vcdp->declBit  (c+116,"v Fringe mag burstCounter_io_done",-1);
	vcdp->declBit  (c+56,"v Fringe mag wrPhase_clock",-1);
	vcdp->declBit  (c+57,"v Fringe mag wrPhase_reset",-1);
	vcdp->declBit  (c+116,"v Fringe mag wrPhase_io_input_set",-1);
	vcdp->declBit  (c+35,"v Fringe mag wrPhase_io_input_reset",-1);
	vcdp->declBit  (c+147,"v Fringe mag wrPhase_io_input_asyn_reset",-1);
	vcdp->declBit  (c+36,"v Fringe mag wrPhase_io_output_data",-1);
	// Tracing: v Fringe mag _T_565 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1606
	// Tracing: v Fringe mag _T_566 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1607
	// Tracing: v Fringe mag _T_567 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1608
	// Tracing: v Fringe mag _T_568 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1609
	// Tracing: v Fringe mag _T_570 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1610
	vcdp->declBit  (c+116,"v Fringe mag burstVld",-1);
	// Tracing: v Fringe mag _T_573 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1612
	// Tracing: v Fringe mag _T_576 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1613
	// Tracing: v Fringe mag _T_577 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1614
	// Tracing: v Fringe mag _T_578 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1615
	vcdp->declBit  (c+56,"v Fringe mag burstTagCounter_clock",-1);
	vcdp->declBit  (c+57,"v Fringe mag burstTagCounter_reset",-1);
	vcdp->declBus  (c+148,"v Fringe mag burstTagCounter_io_max",-1,10,0);
	vcdp->declBus  (c+149,"v Fringe mag burstTagCounter_io_stride",-1,10,0);
	vcdp->declBus  (c+37,"v Fringe mag burstTagCounter_io_out",-1,10,0);
	vcdp->declBus  (c+38,"v Fringe mag burstTagCounter_io_next",-1,10,0);
	vcdp->declBit  (c+116,"v Fringe mag burstTagCounter_io_reset",-1);
	vcdp->declBit  (c+116,"v Fringe mag burstTagCounter_io_enable",-1);
	vcdp->declBit  (c+150,"v Fringe mag burstTagCounter_io_saturate",-1);
	vcdp->declBit  (c+116,"v Fringe mag burstTagCounter_io_done",-1);
	// Tracing: v Fringe mag _T_587 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1626
	// Tracing: v Fringe mag _T_588 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1627
	vcdp->declBit  (c+116,"v Fringe mag tagOut_streamTag",-1);
	vcdp->declBus  (c+39,"v Fringe mag tagOut_burstTag",-1,30,0);
	// Tracing: v Fringe mag _T_594 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1630
	// Tracing: v Fringe mag _GEN_1 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1631
	// Tracing: v Fringe mag _T_595 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1632
	// Tracing: v Fringe mag _T_596 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1633
	// Tracing: v Fringe mag _T_598 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1634
	// Tracing: v Fringe mag _T_599 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1635
	// Tracing: v Fringe mag _T_602 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1636
	// Tracing: v Fringe mag _GEN_8 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1637
	// Tracing: v Fringe mag _GEN_2 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1638
	// Tracing: v Fringe mag _GEN_9 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1639
	// Tracing: v Fringe mag _GEN_3 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1640
	// Tracing: v Fringe mag _GEN_10 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1641
	// Tracing: v Fringe mag _GEN_4 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1642
	// Tracing: v Fringe mag _GEN_11 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1643
	// Tracing: v Fringe mag _GEN_5 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1644
	// Tracing: v Fringe mag _GEN_12 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1645
	// Tracing: v Fringe mag _GEN_6 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1646
	// Tracing: v Fringe mag _GEN_13 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1647
	// Tracing: v Fringe mag _GEN_7 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1648
	// Tracing: v Fringe mag _GEN_14 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1649
	vcdp->declBit  (c+56,"v Fringe mag addrFifo clock",-1);
	vcdp->declBit  (c+57,"v Fringe mag addrFifo reset",-1);
	vcdp->declBus  (c+117,"v Fringe mag addrFifo io_deq_0",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag addrFifo io_deq_1",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag addrFifo io_deq_2",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag addrFifo io_deq_3",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag addrFifo io_deq_4",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag addrFifo io_deq_5",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag addrFifo io_deq_6",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag addrFifo io_deq_7",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag addrFifo io_deq_8",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag addrFifo io_deq_9",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag addrFifo io_deq_10",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag addrFifo io_deq_11",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag addrFifo io_deq_12",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag addrFifo io_deq_13",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag addrFifo io_deq_14",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag addrFifo io_deq_15",-1,31,0);
	vcdp->declBit  (c+116,"v Fringe mag addrFifo io_deqVld",-1);
	vcdp->declBit  (c+115,"v Fringe mag addrFifo io_empty",-1);
	vcdp->declBit  (c+139,"v Fringe mag addrFifo io_forceTag_ready",-1);
	vcdp->declBit  (c+116,"v Fringe mag addrFifo io_forceTag_valid",-1);
	vcdp->declBit  (c+140,"v Fringe mag addrFifo io_forceTag_bits",-1);
	vcdp->declBit  (c+116,"v Fringe mag addrFifo io_tag",-1);
	vcdp->declBit  (c+115,"v Fringe mag addrFifo io_config_chainWrite",-1);
	vcdp->declBit  (c+115,"v Fringe mag addrFifo io_config_chainRead",-1);
	vcdp->declBit  (c+56,"v Fringe mag addrFifo tagFF_clock",-1);
	vcdp->declBit  (c+57,"v Fringe mag addrFifo tagFF_reset",-1);
	vcdp->declBit  (c+151,"v Fringe mag addrFifo tagFF_io_in",-1);
	vcdp->declBit  (c+116,"v Fringe mag addrFifo tagFF_io_init",-1);
	vcdp->declBit  (c+40,"v Fringe mag addrFifo tagFF_io_out",-1);
	vcdp->declBit  (c+152,"v Fringe mag addrFifo tagFF_io_enable",-1);
	// Tracing: v Fringe mag addrFifo _T_162_0 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1052
	// Tracing: v Fringe mag addrFifo _T_162_1 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1053
	// Tracing: v Fringe mag addrFifo _T_162_2 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1054
	// Tracing: v Fringe mag addrFifo _T_162_3 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1055
	// Tracing: v Fringe mag addrFifo _T_162_4 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1056
	// Tracing: v Fringe mag addrFifo _T_162_5 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1057
	// Tracing: v Fringe mag addrFifo _T_162_6 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1058
	// Tracing: v Fringe mag addrFifo _T_162_7 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1059
	// Tracing: v Fringe mag addrFifo _T_162_8 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1060
	// Tracing: v Fringe mag addrFifo _T_162_9 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1061
	// Tracing: v Fringe mag addrFifo _T_162_10 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1062
	// Tracing: v Fringe mag addrFifo _T_162_11 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1063
	// Tracing: v Fringe mag addrFifo _T_162_12 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1064
	// Tracing: v Fringe mag addrFifo _T_162_13 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1065
	// Tracing: v Fringe mag addrFifo _T_162_14 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1066
	// Tracing: v Fringe mag addrFifo _T_162_15 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1067
	// Tracing: v Fringe mag addrFifo _GEN_0 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1068
	// Tracing: v Fringe mag addrFifo _GEN_3 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1069
	// Tracing: v Fringe mag addrFifo _GEN_1 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1070
	// Tracing: v Fringe mag addrFifo _GEN_4 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1071
	// Tracing: v Fringe mag addrFifo _GEN_2 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1072
	// Tracing: v Fringe mag addrFifo _GEN_5 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1073
	vcdp->declBit  (c+56,"v Fringe mag addrFifo tagFF clock",-1);
	vcdp->declBit  (c+57,"v Fringe mag addrFifo tagFF reset",-1);
	vcdp->declBit  (c+151,"v Fringe mag addrFifo tagFF io_in",-1);
	vcdp->declBit  (c+116,"v Fringe mag addrFifo tagFF io_init",-1);
	vcdp->declBit  (c+40,"v Fringe mag addrFifo tagFF io_out",-1);
	vcdp->declBit  (c+152,"v Fringe mag addrFifo tagFF io_enable",-1);
	vcdp->declBit  (c+41,"v Fringe mag addrFifo tagFF d",-1);
	vcdp->declBit  (c+40,"v Fringe mag addrFifo tagFF ff",-1);
	// Tracing: v Fringe mag addrFifo tagFF _GEN_0 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:959
	// Tracing: v Fringe mag addrFifo tagFF _T_13 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:960
	// Tracing: v Fringe mag addrFifo tagFF _GEN_1 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:961
	vcdp->declBit  (c+56,"v Fringe mag isWrFifo clock",-1);
	vcdp->declBit  (c+57,"v Fringe mag isWrFifo reset",-1);
	vcdp->declBit  (c+116,"v Fringe mag isWrFifo io_deq_0",-1);
	vcdp->declBit  (c+116,"v Fringe mag isWrFifo io_deq_1",-1);
	vcdp->declBit  (c+116,"v Fringe mag isWrFifo io_deq_2",-1);
	vcdp->declBit  (c+116,"v Fringe mag isWrFifo io_deq_3",-1);
	vcdp->declBit  (c+116,"v Fringe mag isWrFifo io_deq_4",-1);
	vcdp->declBit  (c+116,"v Fringe mag isWrFifo io_deq_5",-1);
	vcdp->declBit  (c+116,"v Fringe mag isWrFifo io_deq_6",-1);
	vcdp->declBit  (c+116,"v Fringe mag isWrFifo io_deq_7",-1);
	vcdp->declBit  (c+116,"v Fringe mag isWrFifo io_deq_8",-1);
	vcdp->declBit  (c+116,"v Fringe mag isWrFifo io_deq_9",-1);
	vcdp->declBit  (c+116,"v Fringe mag isWrFifo io_deq_10",-1);
	vcdp->declBit  (c+116,"v Fringe mag isWrFifo io_deq_11",-1);
	vcdp->declBit  (c+116,"v Fringe mag isWrFifo io_deq_12",-1);
	vcdp->declBit  (c+116,"v Fringe mag isWrFifo io_deq_13",-1);
	vcdp->declBit  (c+116,"v Fringe mag isWrFifo io_deq_14",-1);
	vcdp->declBit  (c+116,"v Fringe mag isWrFifo io_deq_15",-1);
	vcdp->declBit  (c+116,"v Fringe mag isWrFifo io_deqVld",-1);
	vcdp->declBit  (c+115,"v Fringe mag isWrFifo io_empty",-1);
	vcdp->declBit  (c+142,"v Fringe mag isWrFifo io_forceTag_ready",-1);
	vcdp->declBit  (c+116,"v Fringe mag isWrFifo io_forceTag_valid",-1);
	vcdp->declBit  (c+143,"v Fringe mag isWrFifo io_forceTag_bits",-1);
	vcdp->declBit  (c+116,"v Fringe mag isWrFifo io_tag",-1);
	vcdp->declBit  (c+115,"v Fringe mag isWrFifo io_config_chainWrite",-1);
	vcdp->declBit  (c+115,"v Fringe mag isWrFifo io_config_chainRead",-1);
	vcdp->declBit  (c+56,"v Fringe mag isWrFifo tagFF_clock",-1);
	vcdp->declBit  (c+57,"v Fringe mag isWrFifo tagFF_reset",-1);
	vcdp->declBit  (c+153,"v Fringe mag isWrFifo tagFF_io_in",-1);
	vcdp->declBit  (c+116,"v Fringe mag isWrFifo tagFF_io_init",-1);
	vcdp->declBit  (c+42,"v Fringe mag isWrFifo tagFF_io_out",-1);
	vcdp->declBit  (c+154,"v Fringe mag isWrFifo tagFF_io_enable",-1);
	// Tracing: v Fringe mag isWrFifo _T_162_0 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1177
	// Tracing: v Fringe mag isWrFifo _T_162_1 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1178
	// Tracing: v Fringe mag isWrFifo _T_162_2 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1179
	// Tracing: v Fringe mag isWrFifo _T_162_3 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1180
	// Tracing: v Fringe mag isWrFifo _T_162_4 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1181
	// Tracing: v Fringe mag isWrFifo _T_162_5 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1182
	// Tracing: v Fringe mag isWrFifo _T_162_6 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1183
	// Tracing: v Fringe mag isWrFifo _T_162_7 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1184
	// Tracing: v Fringe mag isWrFifo _T_162_8 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1185
	// Tracing: v Fringe mag isWrFifo _T_162_9 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1186
	// Tracing: v Fringe mag isWrFifo _T_162_10 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1187
	// Tracing: v Fringe mag isWrFifo _T_162_11 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1188
	// Tracing: v Fringe mag isWrFifo _T_162_12 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1189
	// Tracing: v Fringe mag isWrFifo _T_162_13 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1190
	// Tracing: v Fringe mag isWrFifo _T_162_14 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1191
	// Tracing: v Fringe mag isWrFifo _T_162_15 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1192
	// Tracing: v Fringe mag isWrFifo _GEN_0 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1193
	// Tracing: v Fringe mag isWrFifo _GEN_3 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1194
	// Tracing: v Fringe mag isWrFifo _GEN_1 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1195
	// Tracing: v Fringe mag isWrFifo _GEN_4 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1196
	// Tracing: v Fringe mag isWrFifo _GEN_2 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1197
	// Tracing: v Fringe mag isWrFifo _GEN_5 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1198
	vcdp->declBit  (c+56,"v Fringe mag isWrFifo tagFF clock",-1);
	vcdp->declBit  (c+57,"v Fringe mag isWrFifo tagFF reset",-1);
	vcdp->declBit  (c+153,"v Fringe mag isWrFifo tagFF io_in",-1);
	vcdp->declBit  (c+116,"v Fringe mag isWrFifo tagFF io_init",-1);
	vcdp->declBit  (c+42,"v Fringe mag isWrFifo tagFF io_out",-1);
	vcdp->declBit  (c+154,"v Fringe mag isWrFifo tagFF io_enable",-1);
	vcdp->declBit  (c+43,"v Fringe mag isWrFifo tagFF d",-1);
	vcdp->declBit  (c+42,"v Fringe mag isWrFifo tagFF ff",-1);
	// Tracing: v Fringe mag isWrFifo tagFF _GEN_0 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:959
	// Tracing: v Fringe mag isWrFifo tagFF _T_13 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:960
	// Tracing: v Fringe mag isWrFifo tagFF _GEN_1 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:961
	vcdp->declBit  (c+56,"v Fringe mag sizeFifo clock",-1);
	vcdp->declBit  (c+57,"v Fringe mag sizeFifo reset",-1);
	vcdp->declBus  (c+117,"v Fringe mag sizeFifo io_deq_0",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag sizeFifo io_deq_1",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag sizeFifo io_deq_2",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag sizeFifo io_deq_3",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag sizeFifo io_deq_4",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag sizeFifo io_deq_5",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag sizeFifo io_deq_6",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag sizeFifo io_deq_7",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag sizeFifo io_deq_8",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag sizeFifo io_deq_9",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag sizeFifo io_deq_10",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag sizeFifo io_deq_11",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag sizeFifo io_deq_12",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag sizeFifo io_deq_13",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag sizeFifo io_deq_14",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag sizeFifo io_deq_15",-1,31,0);
	vcdp->declBit  (c+116,"v Fringe mag sizeFifo io_deqVld",-1);
	vcdp->declBit  (c+115,"v Fringe mag sizeFifo io_empty",-1);
	vcdp->declBit  (c+144,"v Fringe mag sizeFifo io_forceTag_ready",-1);
	vcdp->declBit  (c+116,"v Fringe mag sizeFifo io_forceTag_valid",-1);
	vcdp->declBit  (c+145,"v Fringe mag sizeFifo io_forceTag_bits",-1);
	vcdp->declBit  (c+116,"v Fringe mag sizeFifo io_tag",-1);
	vcdp->declBit  (c+115,"v Fringe mag sizeFifo io_config_chainWrite",-1);
	vcdp->declBit  (c+115,"v Fringe mag sizeFifo io_config_chainRead",-1);
	vcdp->declBit  (c+56,"v Fringe mag sizeFifo tagFF_clock",-1);
	vcdp->declBit  (c+57,"v Fringe mag sizeFifo tagFF_reset",-1);
	vcdp->declBit  (c+155,"v Fringe mag sizeFifo tagFF_io_in",-1);
	vcdp->declBit  (c+116,"v Fringe mag sizeFifo tagFF_io_init",-1);
	vcdp->declBit  (c+44,"v Fringe mag sizeFifo tagFF_io_out",-1);
	vcdp->declBit  (c+156,"v Fringe mag sizeFifo tagFF_io_enable",-1);
	// Tracing: v Fringe mag sizeFifo _T_162_0 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1052
	// Tracing: v Fringe mag sizeFifo _T_162_1 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1053
	// Tracing: v Fringe mag sizeFifo _T_162_2 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1054
	// Tracing: v Fringe mag sizeFifo _T_162_3 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1055
	// Tracing: v Fringe mag sizeFifo _T_162_4 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1056
	// Tracing: v Fringe mag sizeFifo _T_162_5 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1057
	// Tracing: v Fringe mag sizeFifo _T_162_6 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1058
	// Tracing: v Fringe mag sizeFifo _T_162_7 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1059
	// Tracing: v Fringe mag sizeFifo _T_162_8 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1060
	// Tracing: v Fringe mag sizeFifo _T_162_9 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1061
	// Tracing: v Fringe mag sizeFifo _T_162_10 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1062
	// Tracing: v Fringe mag sizeFifo _T_162_11 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1063
	// Tracing: v Fringe mag sizeFifo _T_162_12 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1064
	// Tracing: v Fringe mag sizeFifo _T_162_13 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1065
	// Tracing: v Fringe mag sizeFifo _T_162_14 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1066
	// Tracing: v Fringe mag sizeFifo _T_162_15 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1067
	// Tracing: v Fringe mag sizeFifo _GEN_0 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1068
	// Tracing: v Fringe mag sizeFifo _GEN_3 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1069
	// Tracing: v Fringe mag sizeFifo _GEN_1 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1070
	// Tracing: v Fringe mag sizeFifo _GEN_4 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1071
	// Tracing: v Fringe mag sizeFifo _GEN_2 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1072
	// Tracing: v Fringe mag sizeFifo _GEN_5 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1073
	vcdp->declBit  (c+56,"v Fringe mag sizeFifo tagFF clock",-1);
	vcdp->declBit  (c+57,"v Fringe mag sizeFifo tagFF reset",-1);
	vcdp->declBit  (c+155,"v Fringe mag sizeFifo tagFF io_in",-1);
	vcdp->declBit  (c+116,"v Fringe mag sizeFifo tagFF io_init",-1);
	vcdp->declBit  (c+44,"v Fringe mag sizeFifo tagFF io_out",-1);
	vcdp->declBit  (c+156,"v Fringe mag sizeFifo tagFF io_enable",-1);
	vcdp->declBit  (c+45,"v Fringe mag sizeFifo tagFF d",-1);
	vcdp->declBit  (c+44,"v Fringe mag sizeFifo tagFF ff",-1);
	// Tracing: v Fringe mag sizeFifo tagFF _GEN_0 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:959
	// Tracing: v Fringe mag sizeFifo tagFF _T_13 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:960
	// Tracing: v Fringe mag sizeFifo tagFF _GEN_1 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:961
	vcdp->declBit  (c+56,"v Fringe mag dataFifo clock",-1);
	vcdp->declBit  (c+57,"v Fringe mag dataFifo reset",-1);
	vcdp->declBus  (c+117,"v Fringe mag dataFifo io_deq_0",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag dataFifo io_deq_1",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag dataFifo io_deq_2",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag dataFifo io_deq_3",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag dataFifo io_deq_4",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag dataFifo io_deq_5",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag dataFifo io_deq_6",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag dataFifo io_deq_7",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag dataFifo io_deq_8",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag dataFifo io_deq_9",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag dataFifo io_deq_10",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag dataFifo io_deq_11",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag dataFifo io_deq_12",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag dataFifo io_deq_13",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag dataFifo io_deq_14",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag dataFifo io_deq_15",-1,31,0);
	vcdp->declBit  (c+116,"v Fringe mag dataFifo io_deqVld",-1);
	vcdp->declBit  (c+115,"v Fringe mag dataFifo io_empty",-1);
	vcdp->declBit  (c+146,"v Fringe mag dataFifo io_forceTag_ready",-1);
	vcdp->declBit  (c+115,"v Fringe mag dataFifo io_forceTag_valid",-1);
	vcdp->declBit  (c+116,"v Fringe mag dataFifo io_forceTag_bits",-1);
	vcdp->declBit  (c+116,"v Fringe mag dataFifo io_tag",-1);
	vcdp->declBit  (c+116,"v Fringe mag dataFifo io_config_chainWrite",-1);
	vcdp->declBit  (c+116,"v Fringe mag dataFifo io_config_chainRead",-1);
	vcdp->declBit  (c+56,"v Fringe mag dataFifo tagFF_clock",-1);
	vcdp->declBit  (c+57,"v Fringe mag dataFifo tagFF_reset",-1);
	vcdp->declBit  (c+157,"v Fringe mag dataFifo tagFF_io_in",-1);
	vcdp->declBit  (c+116,"v Fringe mag dataFifo tagFF_io_init",-1);
	vcdp->declBit  (c+46,"v Fringe mag dataFifo tagFF_io_out",-1);
	vcdp->declBit  (c+158,"v Fringe mag dataFifo tagFF_io_enable",-1);
	// Tracing: v Fringe mag dataFifo _T_162_0 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1052
	// Tracing: v Fringe mag dataFifo _T_162_1 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1053
	// Tracing: v Fringe mag dataFifo _T_162_2 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1054
	// Tracing: v Fringe mag dataFifo _T_162_3 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1055
	// Tracing: v Fringe mag dataFifo _T_162_4 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1056
	// Tracing: v Fringe mag dataFifo _T_162_5 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1057
	// Tracing: v Fringe mag dataFifo _T_162_6 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1058
	// Tracing: v Fringe mag dataFifo _T_162_7 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1059
	// Tracing: v Fringe mag dataFifo _T_162_8 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1060
	// Tracing: v Fringe mag dataFifo _T_162_9 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1061
	// Tracing: v Fringe mag dataFifo _T_162_10 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1062
	// Tracing: v Fringe mag dataFifo _T_162_11 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1063
	// Tracing: v Fringe mag dataFifo _T_162_12 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1064
	// Tracing: v Fringe mag dataFifo _T_162_13 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1065
	// Tracing: v Fringe mag dataFifo _T_162_14 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1066
	// Tracing: v Fringe mag dataFifo _T_162_15 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1067
	// Tracing: v Fringe mag dataFifo _GEN_0 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1068
	// Tracing: v Fringe mag dataFifo _GEN_3 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1069
	// Tracing: v Fringe mag dataFifo _GEN_1 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1070
	// Tracing: v Fringe mag dataFifo _GEN_4 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1071
	// Tracing: v Fringe mag dataFifo _GEN_2 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1072
	// Tracing: v Fringe mag dataFifo _GEN_5 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1073
	vcdp->declBit  (c+56,"v Fringe mag dataFifo tagFF clock",-1);
	vcdp->declBit  (c+57,"v Fringe mag dataFifo tagFF reset",-1);
	vcdp->declBit  (c+157,"v Fringe mag dataFifo tagFF io_in",-1);
	vcdp->declBit  (c+116,"v Fringe mag dataFifo tagFF io_init",-1);
	vcdp->declBit  (c+46,"v Fringe mag dataFifo tagFF io_out",-1);
	vcdp->declBit  (c+158,"v Fringe mag dataFifo tagFF io_enable",-1);
	vcdp->declBit  (c+47,"v Fringe mag dataFifo tagFF d",-1);
	vcdp->declBit  (c+46,"v Fringe mag dataFifo tagFF ff",-1);
	// Tracing: v Fringe mag dataFifo tagFF _GEN_0 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:959
	// Tracing: v Fringe mag dataFifo tagFF _T_13 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:960
	// Tracing: v Fringe mag dataFifo tagFF _GEN_1 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:961
	vcdp->declBit  (c+56,"v Fringe mag burstCounter clock",-1);
	vcdp->declBit  (c+57,"v Fringe mag burstCounter reset",-1);
	vcdp->declBus  (c+117,"v Fringe mag burstCounter io_max",-1,31,0);
	vcdp->declBus  (c+127,"v Fringe mag burstCounter io_stride",-1,31,0);
	vcdp->declBus  (c+34,"v Fringe mag burstCounter io_out",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag burstCounter io_next",-1,31,0);
	vcdp->declBit  (c+116,"v Fringe mag burstCounter io_reset",-1);
	vcdp->declBit  (c+116,"v Fringe mag burstCounter io_enable",-1);
	vcdp->declBit  (c+116,"v Fringe mag burstCounter io_saturate",-1);
	vcdp->declBit  (c+116,"v Fringe mag burstCounter io_done",-1);
	vcdp->declBit  (c+56,"v Fringe mag burstCounter reg$_clock",-1);
	vcdp->declBit  (c+57,"v Fringe mag burstCounter reg$_reset",-1);
	vcdp->declBus  (c+117,"v Fringe mag burstCounter reg$_io_in",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag burstCounter reg$_io_init",-1,31,0);
	vcdp->declBus  (c+34,"v Fringe mag burstCounter reg$_io_out",-1,31,0);
	vcdp->declBit  (c+116,"v Fringe mag burstCounter reg$_io_enable",-1);
	// Tracing: v Fringe mag burstCounter _T_18 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1286
	vcdp->declQuad (c+48,"v Fringe mag burstCounter count",-1,32,0);
	// Tracing: v Fringe mag burstCounter _GEN_2 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1288
	// Tracing: v Fringe mag burstCounter _T_20 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1289
	vcdp->declQuad (c+50,"v Fringe mag burstCounter newval",-1,32,0);
	// Tracing: v Fringe mag burstCounter _GEN_3 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1291
	vcdp->declBit  (c+115,"v Fringe mag burstCounter isMax",-1);
	// Tracing: v Fringe mag burstCounter _T_21 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1293
	vcdp->declQuad (c+159,"v Fringe mag burstCounter next",-1,32,0);
	// Tracing: v Fringe mag burstCounter _T_23 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1295
	// Tracing: v Fringe mag burstCounter _GEN_1 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1296
	// Tracing: v Fringe mag burstCounter _T_24 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1297
	vcdp->declBit  (c+56,"v Fringe mag burstCounter reg$ clock",-1);
	vcdp->declBit  (c+57,"v Fringe mag burstCounter reg$ reset",-1);
	vcdp->declBus  (c+117,"v Fringe mag burstCounter reg$ io_in",-1,31,0);
	vcdp->declBus  (c+117,"v Fringe mag burstCounter reg$ io_init",-1,31,0);
	vcdp->declBus  (c+34,"v Fringe mag burstCounter reg$ io_out",-1,31,0);
	vcdp->declBit  (c+116,"v Fringe mag burstCounter reg$ io_enable",-1);
	vcdp->declBus  (c+34,"v Fringe mag burstCounter reg$ d",-1,31,0);
	vcdp->declBus  (c+34,"v Fringe mag burstCounter reg$ ff",-1,31,0);
	// Tracing: v Fringe mag burstCounter reg$ _GEN_0 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:694
	// Tracing: v Fringe mag burstCounter reg$ _T_13 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:695
	// Tracing: v Fringe mag burstCounter reg$ _GEN_1 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:696
	vcdp->declBit  (c+56,"v Fringe mag wrPhase clock",-1);
	vcdp->declBit  (c+57,"v Fringe mag wrPhase reset",-1);
	vcdp->declBit  (c+116,"v Fringe mag wrPhase io_input_set",-1);
	vcdp->declBit  (c+35,"v Fringe mag wrPhase io_input_reset",-1);
	vcdp->declBit  (c+147,"v Fringe mag wrPhase io_input_asyn_reset",-1);
	vcdp->declBit  (c+36,"v Fringe mag wrPhase io_output_data",-1);
	// Tracing: v Fringe mag wrPhase _T_14 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:455
	// Tracing: v Fringe mag wrPhase _GEN_0 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:456
	// Tracing: v Fringe mag wrPhase _T_18 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:457
	// Tracing: v Fringe mag wrPhase _T_19 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:458
	// Tracing: v Fringe mag wrPhase _T_20 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:459
	// Tracing: v Fringe mag wrPhase _T_22 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:460
	vcdp->declBit  (c+56,"v Fringe mag burstTagCounter clock",-1);
	vcdp->declBit  (c+57,"v Fringe mag burstTagCounter reset",-1);
	vcdp->declBus  (c+148,"v Fringe mag burstTagCounter io_max",-1,10,0);
	vcdp->declBus  (c+149,"v Fringe mag burstTagCounter io_stride",-1,10,0);
	vcdp->declBus  (c+37,"v Fringe mag burstTagCounter io_out",-1,10,0);
	vcdp->declBus  (c+38,"v Fringe mag burstTagCounter io_next",-1,10,0);
	vcdp->declBit  (c+116,"v Fringe mag burstTagCounter io_reset",-1);
	vcdp->declBit  (c+116,"v Fringe mag burstTagCounter io_enable",-1);
	vcdp->declBit  (c+150,"v Fringe mag burstTagCounter io_saturate",-1);
	vcdp->declBit  (c+116,"v Fringe mag burstTagCounter io_done",-1);
	vcdp->declBit  (c+56,"v Fringe mag burstTagCounter reg$_clock",-1);
	vcdp->declBit  (c+57,"v Fringe mag burstTagCounter reg$_reset",-1);
	vcdp->declBus  (c+38,"v Fringe mag burstTagCounter reg$_io_in",-1,10,0);
	vcdp->declBus  (c+161,"v Fringe mag burstTagCounter reg$_io_init",-1,10,0);
	vcdp->declBus  (c+37,"v Fringe mag burstTagCounter reg$_io_out",-1,10,0);
	vcdp->declBit  (c+116,"v Fringe mag burstTagCounter reg$_io_enable",-1);
	// Tracing: v Fringe mag burstTagCounter _T_18 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1382
	vcdp->declBus  (c+52,"v Fringe mag burstTagCounter count",-1,11,0);
	// Tracing: v Fringe mag burstTagCounter _GEN_2 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1384
	// Tracing: v Fringe mag burstTagCounter _T_20 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1385
	vcdp->declBus  (c+53,"v Fringe mag burstTagCounter newval",-1,11,0);
	// Tracing: v Fringe mag burstTagCounter _GEN_3 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1387
	vcdp->declBit  (c+54,"v Fringe mag burstTagCounter isMax",-1);
	// Tracing: v Fringe mag burstTagCounter _T_21 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1389
	vcdp->declBus  (c+55,"v Fringe mag burstTagCounter next",-1,11,0);
	// Tracing: v Fringe mag burstTagCounter _T_23 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1391
	// Tracing: v Fringe mag burstTagCounter _GEN_1 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1392
	// Tracing: v Fringe mag burstTagCounter _T_24 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1393
	vcdp->declBit  (c+56,"v Fringe mag burstTagCounter reg$ clock",-1);
	vcdp->declBit  (c+57,"v Fringe mag burstTagCounter reg$ reset",-1);
	vcdp->declBus  (c+38,"v Fringe mag burstTagCounter reg$ io_in",-1,10,0);
	vcdp->declBus  (c+161,"v Fringe mag burstTagCounter reg$ io_init",-1,10,0);
	vcdp->declBus  (c+37,"v Fringe mag burstTagCounter reg$ io_out",-1,10,0);
	vcdp->declBit  (c+116,"v Fringe mag burstTagCounter reg$ io_enable",-1);
	vcdp->declBus  (c+37,"v Fringe mag burstTagCounter reg$ d",-1,10,0);
	vcdp->declBus  (c+37,"v Fringe mag burstTagCounter reg$ ff",-1,10,0);
	// Tracing: v Fringe mag burstTagCounter reg$ _GEN_0 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1337
	// Tracing: v Fringe mag burstTagCounter reg$ _T_13 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1338
	// Tracing: v Fringe mag burstTagCounter reg$ _GEN_1 // Ignored: Inlined leading underscore at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1339
    }
}

void VTop::traceFullThis__1(VTop__Syms* __restrict vlSymsp, VerilatedVcd* vcdp, uint32_t code) {
    VTop* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    int c=code;
    if (0 && vcdp && c) {}  // Prevent unused
    // Body
    {
	vcdp->fullBus  (c+3,(((0U == vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT__FF__DOT___T_19)
			       ? 0xaU : vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT__FF__DOT___T_19)),32);
	vcdp->fullBit  (c+4,(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT___T_32));
	vcdp->fullBus  (c+2,(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT__FF__DOT___T_19),32);
	vcdp->fullBit  (c+1,(vlTOPp->v__DOT__accel__DOT__done_latch__DOT___T_22));
	vcdp->fullBit  (c+5,(((~ (IData)(vlTOPp->v__DOT__Fringe__DOT___GEN_2)) 
			      & (IData)(vlTOPp->v__DOT__accel__DOT__done_latch__DOT___T_22))));
	vcdp->fullBit  (c+6,(((IData)(vlTOPp->v__DOT__accel__DOT__done_latch__DOT___T_22) 
			      | (IData)(vlTOPp->v__DOT__Fringe__DOT___GEN_2))));
	vcdp->fullBit  (c+7,((((IData)(vlTOPp->v__DOT__accel__DOT__done_latch__DOT___T_22) 
			       | (IData)(vlTOPp->v__DOT__Fringe__DOT___GEN_2))
			       ? ((~ (IData)(vlTOPp->v__DOT__Fringe__DOT___GEN_2)) 
				  & (IData)(vlTOPp->v__DOT__accel__DOT__done_latch__DOT___T_22))
			       : (IData)(vlTOPp->v__DOT__Fringe__DOT__depulser__DOT__r__DOT__ff))));
	vcdp->fullBit  (c+8,(((IData)(vlTOPp->v__DOT__accel__DOT__AccelController_en) 
			      & ((IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_74) 
				 & (~ (IData)(vlTOPp->v__DOT__accel__DOT___T_727))))));
	vcdp->fullBit  (c+9,(((IData)(vlTOPp->v__DOT__accel__DOT__AccelController_en) 
			      & ((IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_58) 
				 & (~ ((1U == (IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_36)) 
				       & (VL_ULL(0xa) 
					  <= (VL_ULL(0x1ffffffff) 
					      & vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT___T_37))))))));
	vcdp->fullBit  (c+10,(((1U == (IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_36)) 
			       & (VL_ULL(0xa) <= (VL_ULL(0x1ffffffff) 
						  & vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT___T_37)))));
	vcdp->fullBit  (c+11,((((1U == (IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_36)) 
				| (IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT___T_45)) 
			       & ((VL_ULL(0xa) <= (VL_ULL(0x1ffffffff) 
						   & vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT___T_37)) 
				  | (IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT___T_42)))));
	vcdp->fullBit  (c+14,(((IData)(vlTOPp->v__DOT__accel__DOT__AccelController_en) 
			       & (IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_74))));
	vcdp->fullBit  (c+12,(vlTOPp->v__DOT__accel__DOT__AccelController_en));
	vcdp->fullBit  (c+15,((VL_ULL(0xa) <= (VL_ULL(0x1ffffffff) 
					       & vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT___T_37))));
	vcdp->fullBus  (c+16,((IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT___T_48)),32);
	vcdp->fullBit  (c+13,(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___GEN_26));
	vcdp->fullBit  (c+22,(vlTOPp->v__DOT__accel__DOT___T_727));
	vcdp->fullBus  (c+23,(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_39),32);
	vcdp->fullBus  (c+24,(((IData)(4U) + vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_2__DOT__ff)),32);
	vcdp->fullBit  (c+25,((1U & (IData)(((QData)((IData)(
							     ((IData)(4U) 
							      + vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_2__DOT__ff))) 
					     >> 0x20U)))));
	vcdp->fullBit  (c+26,((1U != (IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_36))));
	vcdp->fullBit  (c+27,((1U == (IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_36))));
	vcdp->fullBus  (c+28,(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT__FF__DOT__ff),32);
	vcdp->fullBit  (c+17,((1U & (vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_0__DOT__ff 
				     & (~ vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_1__DOT__ff)))));
	vcdp->fullBus  (c+32,((vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_0__DOT__ff 
			       & (IData)(vlTOPp->v__DOT__Fringe__DOT__depulser__DOT__r__DOT__ff))),32);
	vcdp->fullBus  (c+19,(vlTOPp->v__DOT__accel__DOT__x151_argout),32);
	vcdp->fullBus  (c+29,(vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_0__DOT__ff),32);
	vcdp->fullBus  (c+30,(vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_1__DOT__ff),32);
	vcdp->fullBus  (c+18,(vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_2__DOT__ff),32);
	vcdp->fullBus  (c+33,(vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_3__DOT__ff),32);
	vcdp->fullBit  (c+31,(vlTOPp->v__DOT__Fringe__DOT__depulser__DOT__r__DOT__ff));
	vcdp->fullBus  (c+20,((0xffffffc0U & ((IData)((QData)((IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__burstCounter__DOT__reg__024__DOT__ff))) 
					      << 6U))),32);
	vcdp->fullBus  (c+21,(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__burstTagCounter__DOT__reg__024__DOT__ff),32);
	vcdp->fullBit  (c+35,(vlTOPp->v__DOT__Fringe__DOT__mag__DOT___T_602));
	vcdp->fullBit  (c+36,(((~ (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT___GEN_6)) 
			       & (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__wrPhase__DOT___T_14))));
	vcdp->fullBus  (c+39,(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__burstTagCounter__DOT__reg__024__DOT__ff),31);
	vcdp->fullBit  (c+41,(((IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__addrFifo__DOT___GEN_2)
			        ? (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__addrFifo__DOT___GEN_1)
			        : (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__addrFifo__DOT__tagFF__DOT__ff))));
	vcdp->fullBit  (c+40,(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__addrFifo__DOT__tagFF__DOT__ff));
	vcdp->fullBit  (c+43,(((IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__isWrFifo__DOT___GEN_2)
			        ? (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__isWrFifo__DOT___GEN_1)
			        : (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__isWrFifo__DOT__tagFF__DOT__ff))));
	vcdp->fullBit  (c+42,(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__isWrFifo__DOT__tagFF__DOT__ff));
	vcdp->fullBit  (c+45,(((IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__sizeFifo__DOT___GEN_2)
			        ? (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__sizeFifo__DOT___GEN_1)
			        : (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__sizeFifo__DOT__tagFF__DOT__ff))));
	vcdp->fullBit  (c+44,(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__sizeFifo__DOT__tagFF__DOT__ff));
	vcdp->fullBit  (c+47,(((IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__dataFifo__DOT___GEN_2)
			        ? (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__dataFifo__DOT___GEN_1)
			        : (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__dataFifo__DOT__tagFF__DOT__ff))));
	vcdp->fullBit  (c+46,(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__dataFifo__DOT__tagFF__DOT__ff));
	vcdp->fullQuad (c+48,((QData)((IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__burstCounter__DOT__reg__024__DOT__ff))),33);
	vcdp->fullQuad (c+50,((VL_ULL(0x1ffffffff) 
			       & (VL_ULL(1) + (QData)((IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__burstCounter__DOT__reg__024__DOT__ff))))),33);
	vcdp->fullBus  (c+34,(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__burstCounter__DOT__reg__024__DOT__ff),32);
	vcdp->fullBus  (c+52,(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__burstTagCounter__DOT__reg__024__DOT__ff),12);
	vcdp->fullBus  (c+53,((0xfffU & ((IData)(1U) 
					 + (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__burstTagCounter__DOT__reg__024__DOT__ff)))),12);
	vcdp->fullBit  (c+54,((0x400U <= (0xfffU & 
					  ((IData)(1U) 
					   + (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__burstTagCounter__DOT__reg__024__DOT__ff))))));
	vcdp->fullBus  (c+55,((0xfffU & ((0x400U <= 
					  (0xfffU & 
					   ((IData)(1U) 
					    + (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__burstTagCounter__DOT__reg__024__DOT__ff))))
					  ? ((IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT___GEN_7)
					      ? (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__burstTagCounter__DOT__reg__024__DOT__ff)
					      : 0U)
					  : ((IData)(1U) 
					     + (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__burstTagCounter__DOT__reg__024__DOT__ff))))),12);
	vcdp->fullBus  (c+38,((0x7ffU & ((0x400U <= 
					  (0xfffU & 
					   ((IData)(1U) 
					    + (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__burstTagCounter__DOT__reg__024__DOT__ff))))
					  ? ((IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT___GEN_7)
					      ? (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__burstTagCounter__DOT__reg__024__DOT__ff)
					      : 0U)
					  : ((IData)(1U) 
					     + (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__burstTagCounter__DOT__reg__024__DOT__ff))))),11);
	vcdp->fullBus  (c+37,(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__burstTagCounter__DOT__reg__024__DOT__ff),11);
	vcdp->fullBus  (c+62,(vlTOPp->io_rdata),32);
	vcdp->fullBit  (c+64,(vlTOPp->io_dram_cmd_valid));
	vcdp->fullBus  (c+65,(vlTOPp->io_dram_cmd_bits_addr),32);
	vcdp->fullBit  (c+66,(vlTOPp->io_dram_cmd_bits_isWr));
	vcdp->fullBus  (c+67,(vlTOPp->io_dram_cmd_bits_tag),32);
	vcdp->fullBus  (c+68,(vlTOPp->io_dram_cmd_bits_streamId),32);
	vcdp->fullBus  (c+69,(vlTOPp->io_dram_cmd_bits_wdata_0),32);
	vcdp->fullBus  (c+70,(vlTOPp->io_dram_cmd_bits_wdata_1),32);
	vcdp->fullBus  (c+71,(vlTOPp->io_dram_cmd_bits_wdata_2),32);
	vcdp->fullBus  (c+72,(vlTOPp->io_dram_cmd_bits_wdata_3),32);
	vcdp->fullBus  (c+73,(vlTOPp->io_dram_cmd_bits_wdata_4),32);
	vcdp->fullBus  (c+74,(vlTOPp->io_dram_cmd_bits_wdata_5),32);
	vcdp->fullBus  (c+75,(vlTOPp->io_dram_cmd_bits_wdata_6),32);
	vcdp->fullBus  (c+76,(vlTOPp->io_dram_cmd_bits_wdata_7),32);
	vcdp->fullBus  (c+77,(vlTOPp->io_dram_cmd_bits_wdata_8),32);
	vcdp->fullBus  (c+78,(vlTOPp->io_dram_cmd_bits_wdata_9),32);
	vcdp->fullBus  (c+79,(vlTOPp->io_dram_cmd_bits_wdata_10),32);
	vcdp->fullBus  (c+80,(vlTOPp->io_dram_cmd_bits_wdata_11),32);
	vcdp->fullBus  (c+81,(vlTOPp->io_dram_cmd_bits_wdata_12),32);
	vcdp->fullBus  (c+82,(vlTOPp->io_dram_cmd_bits_wdata_13),32);
	vcdp->fullBus  (c+83,(vlTOPp->io_dram_cmd_bits_wdata_14),32);
	vcdp->fullBus  (c+84,(vlTOPp->io_dram_cmd_bits_wdata_15),32);
	vcdp->fullBit  (c+85,(vlTOPp->io_dram_resp_ready));
	vcdp->fullBit  (c+59,(vlTOPp->io_wen));
	vcdp->fullBus  (c+60,(vlTOPp->io_waddr),2);
	vcdp->fullBit  (c+106,(((IData)(vlTOPp->io_wen) 
				& (0U == (IData)(vlTOPp->io_waddr)))));
	vcdp->fullBus  (c+107,(((IData)(vlTOPp->v__DOT__Fringe__DOT__depulser__DOT__r__DOT__ff)
				 ? (vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_0__DOT__ff 
				    & (IData)(vlTOPp->v__DOT__Fringe__DOT__depulser__DOT__r__DOT__ff))
				 : vlTOPp->io_wdata)),32);
	vcdp->fullBit  (c+108,(((IData)(vlTOPp->v__DOT__Fringe__DOT__depulser__DOT__r__DOT__ff) 
				| ((IData)(vlTOPp->io_wen) 
				   & (1U == (IData)(vlTOPp->io_waddr))))));
	vcdp->fullBit  (c+109,(((IData)(vlTOPp->io_wen) 
				& (2U == (IData)(vlTOPp->io_waddr)))));
	vcdp->fullBus  (c+110,((((IData)(vlTOPp->io_wen) 
				 & (0U == (IData)(vlTOPp->io_waddr)))
				 ? vlTOPp->io_wdata
				 : vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_0__DOT__ff)),32);
	vcdp->fullBus  (c+111,((((IData)(vlTOPp->v__DOT__Fringe__DOT__depulser__DOT__r__DOT__ff) 
				 | ((IData)(vlTOPp->io_wen) 
				    & (1U == (IData)(vlTOPp->io_waddr))))
				 ? ((IData)(vlTOPp->v__DOT__Fringe__DOT__depulser__DOT__r__DOT__ff)
				     ? (vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_0__DOT__ff 
					& (IData)(vlTOPp->v__DOT__Fringe__DOT__depulser__DOT__r__DOT__ff))
				     : vlTOPp->io_wdata)
				 : vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_1__DOT__ff)),32);
	vcdp->fullBus  (c+61,(vlTOPp->io_wdata),32);
	vcdp->fullBus  (c+112,((((IData)(vlTOPp->io_wen) 
				 & (2U == (IData)(vlTOPp->io_waddr)))
				 ? vlTOPp->io_wdata
				 : vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_2__DOT__ff)),32);
	vcdp->fullBus  (c+58,(vlTOPp->io_raddr),2);
	vcdp->fullBus  (c+105,(((3U == (IData)(vlTOPp->io_raddr))
				 ? vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_3__DOT__ff
				 : ((2U == (IData)(vlTOPp->io_raddr))
				     ? vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_2__DOT__ff
				     : ((1U == (IData)(vlTOPp->io_raddr))
					 ? vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_1__DOT__ff
					 : vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_0__DOT__ff)))),32);
	vcdp->fullBit  (c+63,(vlTOPp->io_dram_cmd_ready));
	vcdp->fullBit  (c+86,(vlTOPp->io_dram_resp_valid));
	vcdp->fullBus  (c+87,(vlTOPp->io_dram_resp_bits_rdata_0),32);
	vcdp->fullBus  (c+88,(vlTOPp->io_dram_resp_bits_rdata_1),32);
	vcdp->fullBus  (c+89,(vlTOPp->io_dram_resp_bits_rdata_2),32);
	vcdp->fullBus  (c+90,(vlTOPp->io_dram_resp_bits_rdata_3),32);
	vcdp->fullBus  (c+91,(vlTOPp->io_dram_resp_bits_rdata_4),32);
	vcdp->fullBus  (c+92,(vlTOPp->io_dram_resp_bits_rdata_5),32);
	vcdp->fullBus  (c+93,(vlTOPp->io_dram_resp_bits_rdata_6),32);
	vcdp->fullBus  (c+94,(vlTOPp->io_dram_resp_bits_rdata_7),32);
	vcdp->fullBus  (c+95,(vlTOPp->io_dram_resp_bits_rdata_8),32);
	vcdp->fullBus  (c+96,(vlTOPp->io_dram_resp_bits_rdata_9),32);
	vcdp->fullBus  (c+97,(vlTOPp->io_dram_resp_bits_rdata_10),32);
	vcdp->fullBus  (c+98,(vlTOPp->io_dram_resp_bits_rdata_11),32);
	vcdp->fullBus  (c+99,(vlTOPp->io_dram_resp_bits_rdata_12),32);
	vcdp->fullBus  (c+100,(vlTOPp->io_dram_resp_bits_rdata_13),32);
	vcdp->fullBus  (c+101,(vlTOPp->io_dram_resp_bits_rdata_14),32);
	vcdp->fullBus  (c+102,(vlTOPp->io_dram_resp_bits_rdata_15),32);
	vcdp->fullBus  (c+103,(vlTOPp->io_dram_resp_bits_tag),32);
	vcdp->fullBus  (c+104,(vlTOPp->io_dram_resp_bits_streamId),32);
	vcdp->fullBit  (c+56,(vlTOPp->clock));
	vcdp->fullBit  (c+57,(vlTOPp->reset));
	vcdp->fullBit  (c+113,(vlTOPp->v__DOT___GEN_0));
	vcdp->fullBit  (c+114,(vlTOPp->v__DOT__Fringe__DOT___GEN_1));
	vcdp->fullBit  (c+118,(vlTOPp->v__DOT__accel__DOT___GEN_0));
	vcdp->fullBus  (c+119,(vlTOPp->v__DOT__accel__DOT___GEN_1),32);
	vcdp->fullBus  (c+120,(vlTOPp->v__DOT__accel__DOT___GEN_2),32);
	vcdp->fullBus  (c+121,(vlTOPp->v__DOT__accel__DOT___GEN_3),32);
	vcdp->fullBit  (c+122,(vlTOPp->v__DOT__accel__DOT___GEN_4));
	vcdp->fullBus  (c+123,(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___GEN_0),32);
	vcdp->fullBit  (c+124,(vlTOPp->v__DOT__accel__DOT___GEN_5));
	vcdp->fullBus  (c+126,(0xaU),32);
	vcdp->fullBus  (c+128,(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___GEN_12),32);
	vcdp->fullBit  (c+129,(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT___GEN_3));
	vcdp->fullBus  (c+125,(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___GEN_6),32);
	vcdp->fullBit  (c+130,(vlTOPp->v__DOT__Fringe__DOT__regs__DOT___GEN_0));
	vcdp->fullBit  (c+131,(vlTOPp->v__DOT__Fringe__DOT__regs__DOT___GEN_1));
	vcdp->fullBit  (c+132,(vlTOPp->v__DOT__Fringe__DOT___GEN_2));
	vcdp->fullBit  (c+133,(vlTOPp->v__DOT__Fringe__DOT___GEN_3));
	vcdp->fullBit  (c+134,(vlTOPp->v__DOT__Fringe__DOT__mag__DOT___GEN_2));
	vcdp->fullBus  (c+135,(vlTOPp->v__DOT__Fringe__DOT__regs__DOT___GEN_2),32);
	vcdp->fullBus  (c+136,(vlTOPp->v__DOT__Fringe__DOT__regs__DOT___GEN_3),32);
	vcdp->fullBus  (c+137,(vlTOPp->v__DOT__Fringe__DOT__regs__DOT___GEN_4),32);
	vcdp->fullBus  (c+138,(vlTOPp->v__DOT__Fringe__DOT__regs__DOT___GEN_5),32);
	vcdp->fullBit  (c+139,(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__addrFifo__DOT___GEN_0));
	vcdp->fullBit  (c+140,(vlTOPp->v__DOT__Fringe__DOT__mag__DOT___GEN_3));
	vcdp->fullBus  (c+141,(0U),26);
	vcdp->fullBit  (c+142,(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__isWrFifo__DOT___GEN_0));
	vcdp->fullBit  (c+143,(vlTOPp->v__DOT__Fringe__DOT__mag__DOT___GEN_4));
	vcdp->fullBit  (c+144,(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__sizeFifo__DOT___GEN_0));
	vcdp->fullBit  (c+145,(vlTOPp->v__DOT__Fringe__DOT__mag__DOT___GEN_5));
	vcdp->fullBit  (c+146,(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__dataFifo__DOT___GEN_0));
	vcdp->fullBit  (c+147,(vlTOPp->v__DOT__Fringe__DOT__mag__DOT___GEN_6));
	vcdp->fullBus  (c+148,(0x400U),11);
	vcdp->fullBus  (c+149,(1U),11);
	vcdp->fullBit  (c+150,(vlTOPp->v__DOT__Fringe__DOT__mag__DOT___GEN_7));
	vcdp->fullBit  (c+151,(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__addrFifo__DOT___GEN_1));
	vcdp->fullBit  (c+152,(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__addrFifo__DOT___GEN_2));
	vcdp->fullBit  (c+153,(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__isWrFifo__DOT___GEN_1));
	vcdp->fullBit  (c+154,(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__isWrFifo__DOT___GEN_2));
	vcdp->fullBit  (c+155,(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__sizeFifo__DOT___GEN_1));
	vcdp->fullBit  (c+156,(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__sizeFifo__DOT___GEN_2));
	vcdp->fullBit  (c+157,(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__dataFifo__DOT___GEN_1));
	vcdp->fullBit  (c+158,(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__dataFifo__DOT___GEN_2));
	vcdp->fullBus  (c+127,(1U),32);
	vcdp->fullBit  (c+115,(1U));
	vcdp->fullQuad (c+159,(VL_ULL(0)),33);
	vcdp->fullBus  (c+117,(0U),32);
	vcdp->fullBus  (c+161,(0U),11);
	vcdp->fullBit  (c+116,(0U));
    }
}
