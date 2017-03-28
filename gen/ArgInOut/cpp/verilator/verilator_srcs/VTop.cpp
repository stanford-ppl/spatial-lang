// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Design implementation internals
// See VTop.h for the primary calling header

#include "VTop.h"              // For This
#include "VTop__Syms.h"

//--------------------
// STATIC VARIABLES


//--------------------

VL_CTOR_IMP(VTop) {
    VTop__Syms* __restrict vlSymsp = __VlSymsp = new VTop__Syms(this, name());
    VTop* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    // Reset internal values
    
    // Reset structure values
    clock = VL_RAND_RESET_I(1);
    reset = VL_RAND_RESET_I(1);
    io_raddr = VL_RAND_RESET_I(2);
    io_wen = VL_RAND_RESET_I(1);
    io_waddr = VL_RAND_RESET_I(2);
    io_wdata = VL_RAND_RESET_I(32);
    io_rdata = VL_RAND_RESET_I(32);
    io_dram_cmd_ready = VL_RAND_RESET_I(1);
    io_dram_cmd_valid = VL_RAND_RESET_I(1);
    io_dram_cmd_bits_addr = VL_RAND_RESET_I(32);
    io_dram_cmd_bits_isWr = VL_RAND_RESET_I(1);
    io_dram_cmd_bits_tag = VL_RAND_RESET_I(32);
    io_dram_cmd_bits_streamId = VL_RAND_RESET_I(32);
    io_dram_cmd_bits_wdata_0 = VL_RAND_RESET_I(32);
    io_dram_cmd_bits_wdata_1 = VL_RAND_RESET_I(32);
    io_dram_cmd_bits_wdata_2 = VL_RAND_RESET_I(32);
    io_dram_cmd_bits_wdata_3 = VL_RAND_RESET_I(32);
    io_dram_cmd_bits_wdata_4 = VL_RAND_RESET_I(32);
    io_dram_cmd_bits_wdata_5 = VL_RAND_RESET_I(32);
    io_dram_cmd_bits_wdata_6 = VL_RAND_RESET_I(32);
    io_dram_cmd_bits_wdata_7 = VL_RAND_RESET_I(32);
    io_dram_cmd_bits_wdata_8 = VL_RAND_RESET_I(32);
    io_dram_cmd_bits_wdata_9 = VL_RAND_RESET_I(32);
    io_dram_cmd_bits_wdata_10 = VL_RAND_RESET_I(32);
    io_dram_cmd_bits_wdata_11 = VL_RAND_RESET_I(32);
    io_dram_cmd_bits_wdata_12 = VL_RAND_RESET_I(32);
    io_dram_cmd_bits_wdata_13 = VL_RAND_RESET_I(32);
    io_dram_cmd_bits_wdata_14 = VL_RAND_RESET_I(32);
    io_dram_cmd_bits_wdata_15 = VL_RAND_RESET_I(32);
    io_dram_resp_ready = VL_RAND_RESET_I(1);
    io_dram_resp_valid = VL_RAND_RESET_I(1);
    io_dram_resp_bits_rdata_0 = VL_RAND_RESET_I(32);
    io_dram_resp_bits_rdata_1 = VL_RAND_RESET_I(32);
    io_dram_resp_bits_rdata_2 = VL_RAND_RESET_I(32);
    io_dram_resp_bits_rdata_3 = VL_RAND_RESET_I(32);
    io_dram_resp_bits_rdata_4 = VL_RAND_RESET_I(32);
    io_dram_resp_bits_rdata_5 = VL_RAND_RESET_I(32);
    io_dram_resp_bits_rdata_6 = VL_RAND_RESET_I(32);
    io_dram_resp_bits_rdata_7 = VL_RAND_RESET_I(32);
    io_dram_resp_bits_rdata_8 = VL_RAND_RESET_I(32);
    io_dram_resp_bits_rdata_9 = VL_RAND_RESET_I(32);
    io_dram_resp_bits_rdata_10 = VL_RAND_RESET_I(32);
    io_dram_resp_bits_rdata_11 = VL_RAND_RESET_I(32);
    io_dram_resp_bits_rdata_12 = VL_RAND_RESET_I(32);
    io_dram_resp_bits_rdata_13 = VL_RAND_RESET_I(32);
    io_dram_resp_bits_rdata_14 = VL_RAND_RESET_I(32);
    io_dram_resp_bits_rdata_15 = VL_RAND_RESET_I(32);
    io_dram_resp_bits_tag = VL_RAND_RESET_I(32);
    io_dram_resp_bits_streamId = VL_RAND_RESET_I(32);
    v__DOT___GEN_0 = VL_RAND_RESET_I(1);
    v__DOT__accel__DOT__AccelController_en = VL_RAND_RESET_I(1);
    v__DOT__accel__DOT___T_727 = VL_RAND_RESET_I(1);
    v__DOT__accel__DOT__x151_argout = VL_RAND_RESET_I(32);
    v__DOT__accel__DOT___GEN_0 = VL_RAND_RESET_I(1);
    v__DOT__accel__DOT___GEN_1 = VL_RAND_RESET_I(32);
    v__DOT__accel__DOT___GEN_2 = VL_RAND_RESET_I(32);
    v__DOT__accel__DOT___GEN_3 = VL_RAND_RESET_I(32);
    v__DOT__accel__DOT___GEN_4 = VL_RAND_RESET_I(1);
    v__DOT__accel__DOT___GEN_5 = VL_RAND_RESET_I(1);
    v__DOT__accel__DOT__AccelController_sm__DOT___T_36 = VL_RAND_RESET_I(3);
    v__DOT__accel__DOT__AccelController_sm__DOT___T_39 = VL_RAND_RESET_I(32);
    v__DOT__accel__DOT__AccelController_sm__DOT___T_48 = VL_RAND_RESET_I(1);
    v__DOT__accel__DOT__AccelController_sm__DOT___T_58 = VL_RAND_RESET_I(1);
    v__DOT__accel__DOT__AccelController_sm__DOT___GEN_4 = VL_RAND_RESET_I(2);
    v__DOT__accel__DOT__AccelController_sm__DOT___T_73 = VL_RAND_RESET_I(1);
    v__DOT__accel__DOT__AccelController_sm__DOT___T_74 = VL_RAND_RESET_I(1);
    v__DOT__accel__DOT__AccelController_sm__DOT___T_92 = VL_RAND_RESET_I(1);
    v__DOT__accel__DOT__AccelController_sm__DOT___T_93 = VL_RAND_RESET_I(1);
    v__DOT__accel__DOT__AccelController_sm__DOT___T_111 = VL_RAND_RESET_I(1);
    v__DOT__accel__DOT__AccelController_sm__DOT___GEN_26 = VL_RAND_RESET_I(1);
    v__DOT__accel__DOT__AccelController_sm__DOT___GEN_0 = VL_RAND_RESET_I(32);
    v__DOT__accel__DOT__AccelController_sm__DOT___GEN_6 = VL_RAND_RESET_I(32);
    v__DOT__accel__DOT__AccelController_sm__DOT___GEN_12 = VL_RAND_RESET_I(32);
    v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT___T_32 = VL_RAND_RESET_I(1);
    v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT___T_37 = VL_RAND_RESET_Q(34);
    v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT___T_42 = VL_RAND_RESET_I(1);
    v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT___T_45 = VL_RAND_RESET_I(1);
    v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT___T_48 = VL_RAND_RESET_Q(33);
    v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT___GEN_3 = VL_RAND_RESET_I(1);
    v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT__FF__DOT__ff = VL_RAND_RESET_I(32);
    v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT__FF__DOT___T_19 = VL_RAND_RESET_I(32);
    v__DOT__accel__DOT__done_latch__DOT___T_14 = VL_RAND_RESET_I(1);
    v__DOT__accel__DOT__done_latch__DOT___T_22 = VL_RAND_RESET_I(1);
    v__DOT__Fringe__DOT___GEN_1 = VL_RAND_RESET_I(1);
    v__DOT__Fringe__DOT___GEN_2 = VL_RAND_RESET_I(1);
    v__DOT__Fringe__DOT___GEN_3 = VL_RAND_RESET_I(1);
    v__DOT__Fringe__DOT__regs__DOT___GEN_0 = VL_RAND_RESET_I(1);
    v__DOT__Fringe__DOT__regs__DOT___GEN_1 = VL_RAND_RESET_I(1);
    v__DOT__Fringe__DOT__regs__DOT___GEN_2 = VL_RAND_RESET_I(32);
    v__DOT__Fringe__DOT__regs__DOT___GEN_3 = VL_RAND_RESET_I(32);
    v__DOT__Fringe__DOT__regs__DOT___GEN_4 = VL_RAND_RESET_I(32);
    v__DOT__Fringe__DOT__regs__DOT___GEN_5 = VL_RAND_RESET_I(32);
    v__DOT__Fringe__DOT__regs__DOT__regs_0__DOT__ff = VL_RAND_RESET_I(32);
    v__DOT__Fringe__DOT__regs__DOT__regs_0__DOT___GEN_1 = VL_RAND_RESET_I(32);
    v__DOT__Fringe__DOT__regs__DOT__regs_1__DOT__ff = VL_RAND_RESET_I(32);
    v__DOT__Fringe__DOT__regs__DOT__regs_1__DOT___GEN_1 = VL_RAND_RESET_I(32);
    v__DOT__Fringe__DOT__regs__DOT__regs_2__DOT__ff = VL_RAND_RESET_I(32);
    v__DOT__Fringe__DOT__regs__DOT__regs_2__DOT___GEN_1 = VL_RAND_RESET_I(32);
    v__DOT__Fringe__DOT__regs__DOT__regs_3__DOT__ff = VL_RAND_RESET_I(32);
    v__DOT__Fringe__DOT__depulser__DOT__r__DOT__ff = VL_RAND_RESET_I(1);
    v__DOT__Fringe__DOT__depulser__DOT__r__DOT___GEN_1 = VL_RAND_RESET_I(1);
    v__DOT__Fringe__DOT__mag__DOT___T_602 = VL_RAND_RESET_I(1);
    v__DOT__Fringe__DOT__mag__DOT___GEN_2 = VL_RAND_RESET_I(1);
    v__DOT__Fringe__DOT__mag__DOT___GEN_3 = VL_RAND_RESET_I(1);
    v__DOT__Fringe__DOT__mag__DOT___GEN_4 = VL_RAND_RESET_I(1);
    v__DOT__Fringe__DOT__mag__DOT___GEN_5 = VL_RAND_RESET_I(1);
    v__DOT__Fringe__DOT__mag__DOT___GEN_6 = VL_RAND_RESET_I(1);
    v__DOT__Fringe__DOT__mag__DOT___GEN_7 = VL_RAND_RESET_I(1);
    v__DOT__Fringe__DOT__mag__DOT__addrFifo__DOT___GEN_0 = VL_RAND_RESET_I(1);
    v__DOT__Fringe__DOT__mag__DOT__addrFifo__DOT___GEN_1 = VL_RAND_RESET_I(1);
    v__DOT__Fringe__DOT__mag__DOT__addrFifo__DOT___GEN_2 = VL_RAND_RESET_I(1);
    v__DOT__Fringe__DOT__mag__DOT__addrFifo__DOT__tagFF__DOT__ff = VL_RAND_RESET_I(1);
    v__DOT__Fringe__DOT__mag__DOT__addrFifo__DOT__tagFF__DOT___GEN_1 = VL_RAND_RESET_I(1);
    v__DOT__Fringe__DOT__mag__DOT__isWrFifo__DOT___GEN_0 = VL_RAND_RESET_I(1);
    v__DOT__Fringe__DOT__mag__DOT__isWrFifo__DOT___GEN_1 = VL_RAND_RESET_I(1);
    v__DOT__Fringe__DOT__mag__DOT__isWrFifo__DOT___GEN_2 = VL_RAND_RESET_I(1);
    v__DOT__Fringe__DOT__mag__DOT__isWrFifo__DOT__tagFF__DOT__ff = VL_RAND_RESET_I(1);
    v__DOT__Fringe__DOT__mag__DOT__isWrFifo__DOT__tagFF__DOT___GEN_1 = VL_RAND_RESET_I(1);
    v__DOT__Fringe__DOT__mag__DOT__sizeFifo__DOT___GEN_0 = VL_RAND_RESET_I(1);
    v__DOT__Fringe__DOT__mag__DOT__sizeFifo__DOT___GEN_1 = VL_RAND_RESET_I(1);
    v__DOT__Fringe__DOT__mag__DOT__sizeFifo__DOT___GEN_2 = VL_RAND_RESET_I(1);
    v__DOT__Fringe__DOT__mag__DOT__sizeFifo__DOT__tagFF__DOT__ff = VL_RAND_RESET_I(1);
    v__DOT__Fringe__DOT__mag__DOT__sizeFifo__DOT__tagFF__DOT___GEN_1 = VL_RAND_RESET_I(1);
    v__DOT__Fringe__DOT__mag__DOT__dataFifo__DOT___GEN_0 = VL_RAND_RESET_I(1);
    v__DOT__Fringe__DOT__mag__DOT__dataFifo__DOT___GEN_1 = VL_RAND_RESET_I(1);
    v__DOT__Fringe__DOT__mag__DOT__dataFifo__DOT___GEN_2 = VL_RAND_RESET_I(1);
    v__DOT__Fringe__DOT__mag__DOT__dataFifo__DOT__tagFF__DOT__ff = VL_RAND_RESET_I(1);
    v__DOT__Fringe__DOT__mag__DOT__dataFifo__DOT__tagFF__DOT___GEN_1 = VL_RAND_RESET_I(1);
    v__DOT__Fringe__DOT__mag__DOT__burstCounter__DOT__reg__024__DOT__ff = VL_RAND_RESET_I(32);
    v__DOT__Fringe__DOT__mag__DOT__burstCounter__DOT__reg__024__DOT___GEN_1 = VL_RAND_RESET_I(32);
    v__DOT__Fringe__DOT__mag__DOT__wrPhase__DOT___T_14 = VL_RAND_RESET_I(1);
    v__DOT__Fringe__DOT__mag__DOT__burstTagCounter__DOT__reg__024__DOT__ff = VL_RAND_RESET_I(11);
    v__DOT__Fringe__DOT__mag__DOT__burstTagCounter__DOT__reg__024__DOT___GEN_1 = VL_RAND_RESET_I(11);
    __Vclklast__TOP__clock = VL_RAND_RESET_I(1);
    __Vm_traceActivity = VL_RAND_RESET_I(32);
}

void VTop::__Vconfigure(VTop__Syms* vlSymsp, bool first) {
    if (0 && first) {}  // Prevent unused
    this->__VlSymsp = vlSymsp;
}

VTop::~VTop() {
    delete __VlSymsp; __VlSymsp=NULL;
}

//--------------------


void VTop::eval() {
    VTop__Syms* __restrict vlSymsp = this->__VlSymsp; // Setup global symbol table
    VTop* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    // Initialize
    if (VL_UNLIKELY(!vlSymsp->__Vm_didInit)) _eval_initial_loop(vlSymsp);
    // Evaluate till stable
    VL_DEBUG_IF(VL_PRINTF("\n----TOP Evaluate VTop::eval\n"); );
    int __VclockLoop = 0;
    QData __Vchange=1;
    while (VL_LIKELY(__Vchange)) {
	VL_DEBUG_IF(VL_PRINTF(" Clock loop\n"););
	vlSymsp->__Vm_activity = true;
	_eval(vlSymsp);
	__Vchange = _change_request(vlSymsp);
	if (++__VclockLoop > 100) vl_fatal(__FILE__,__LINE__,__FILE__,"Verilated model didn't converge");
    }
}

void VTop::_eval_initial_loop(VTop__Syms* __restrict vlSymsp) {
    vlSymsp->__Vm_didInit = true;
    _eval_initial(vlSymsp);
    vlSymsp->__Vm_activity = true;
    int __VclockLoop = 0;
    QData __Vchange=1;
    while (VL_LIKELY(__Vchange)) {
	_eval_settle(vlSymsp);
	_eval(vlSymsp);
	__Vchange = _change_request(vlSymsp);
	if (++__VclockLoop > 100) vl_fatal(__FILE__,__LINE__,__FILE__,"Verilated model didn't DC converge");
    }
}

//--------------------
// Internal Methods

void VTop::_settle__TOP__1(VTop__Syms* __restrict vlSymsp) {
    VL_DEBUG_IF(VL_PRINTF("    VTop::_settle__TOP__1\n"); );
    VTop* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    // Body
    vlTOPp->io_dram_cmd_valid = 0U;
    vlTOPp->io_dram_cmd_bits_isWr = 0U;
    vlTOPp->io_dram_cmd_bits_streamId = 0U;
    vlTOPp->io_dram_cmd_bits_wdata_0 = 0U;
    vlTOPp->io_dram_cmd_bits_wdata_1 = 0U;
    vlTOPp->io_dram_cmd_bits_wdata_2 = 0U;
    vlTOPp->io_dram_cmd_bits_wdata_3 = 0U;
    vlTOPp->io_dram_cmd_bits_wdata_4 = 0U;
    vlTOPp->io_dram_cmd_bits_wdata_5 = 0U;
    vlTOPp->io_dram_cmd_bits_wdata_6 = 0U;
    vlTOPp->io_dram_cmd_bits_wdata_7 = 0U;
    vlTOPp->io_dram_cmd_bits_wdata_8 = 0U;
    vlTOPp->io_dram_cmd_bits_wdata_9 = 0U;
    vlTOPp->io_dram_cmd_bits_wdata_10 = 0U;
    vlTOPp->io_dram_cmd_bits_wdata_11 = 0U;
    vlTOPp->io_dram_cmd_bits_wdata_12 = 0U;
    vlTOPp->io_dram_cmd_bits_wdata_13 = 0U;
    vlTOPp->io_dram_cmd_bits_wdata_14 = 0U;
    vlTOPp->io_dram_cmd_bits_wdata_15 = 0U;
    vlTOPp->io_dram_resp_ready = vlTOPp->io_dram_cmd_ready;
}

VL_INLINE_OPT void VTop::_combo__TOP__2(VTop__Syms* __restrict vlSymsp) {
    VL_DEBUG_IF(VL_PRINTF("    VTop::_combo__TOP__2\n"); );
    VTop* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    // Body
    vlTOPp->io_dram_resp_ready = vlTOPp->io_dram_cmd_ready;
}

VL_INLINE_OPT void VTop::_sequent__TOP__3(VTop__Syms* __restrict vlSymsp) {
    VL_DEBUG_IF(VL_PRINTF("    VTop::_sequent__TOP__3\n"); );
    VTop* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    // Body
    // ALWAYS at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:478
    if (vlTOPp->reset) {
	vlTOPp->v__DOT__Fringe__DOT__mag__DOT__wrPhase__DOT___T_14 = 0U;
    } else {
	if (vlTOPp->v__DOT__Fringe__DOT__mag__DOT___GEN_6) {
	    vlTOPp->v__DOT__Fringe__DOT__mag__DOT__wrPhase__DOT___T_14 = 0U;
	} else {
	    if (vlTOPp->v__DOT__Fringe__DOT__mag__DOT___T_602) {
		vlTOPp->v__DOT__Fringe__DOT__mag__DOT__wrPhase__DOT___T_14 = 0U;
	    }
	}
    }
    // ALWAYS at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:978
    vlTOPp->v__DOT__Fringe__DOT__mag__DOT__addrFifo__DOT__tagFF__DOT__ff 
	= ((~ (IData)(vlTOPp->reset)) & (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__addrFifo__DOT__tagFF__DOT___GEN_1));
    // ALWAYS at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:978
    vlTOPp->v__DOT__Fringe__DOT__mag__DOT__isWrFifo__DOT__tagFF__DOT__ff 
	= ((~ (IData)(vlTOPp->reset)) & (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__isWrFifo__DOT__tagFF__DOT___GEN_1));
    // ALWAYS at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:978
    vlTOPp->v__DOT__Fringe__DOT__mag__DOT__sizeFifo__DOT__tagFF__DOT__ff 
	= ((~ (IData)(vlTOPp->reset)) & (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__sizeFifo__DOT__tagFF__DOT___GEN_1));
    // ALWAYS at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:978
    vlTOPp->v__DOT__Fringe__DOT__mag__DOT__dataFifo__DOT__tagFF__DOT__ff 
	= ((~ (IData)(vlTOPp->reset)) & (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__dataFifo__DOT__tagFF__DOT___GEN_1));
    // ALWAYS at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:713
    vlTOPp->v__DOT__Fringe__DOT__mag__DOT__burstCounter__DOT__reg__024__DOT__ff 
	= ((IData)(vlTOPp->reset) ? 0U : vlTOPp->v__DOT__Fringe__DOT__mag__DOT__burstCounter__DOT__reg__024__DOT___GEN_1);
    // ALWAYS at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1356
    vlTOPp->v__DOT__Fringe__DOT__mag__DOT__burstTagCounter__DOT__reg__024__DOT__ff 
	= ((IData)(vlTOPp->reset) ? 0U : (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__burstTagCounter__DOT__reg__024__DOT___GEN_1));
    // ALWAYS at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:181
    vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT___T_42 
	= ((~ (IData)(vlTOPp->reset)) & (VL_ULL(0xa) 
					 <= (VL_ULL(0x1ffffffff) 
					     & vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT___T_37)));
    // ALWAYS at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:978
    vlTOPp->v__DOT__Fringe__DOT__depulser__DOT__r__DOT__ff 
	= ((~ (IData)(vlTOPp->reset)) & (IData)(vlTOPp->v__DOT__Fringe__DOT__depulser__DOT__r__DOT___GEN_1));
    // ALWAYS at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:713
    vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_1__DOT__ff 
	= ((IData)(vlTOPp->reset) ? vlTOPp->v__DOT__Fringe__DOT__regs__DOT___GEN_3
	    : vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_1__DOT___GEN_1);
    // ALWAYS at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:713
    vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_0__DOT__ff 
	= ((IData)(vlTOPp->reset) ? vlTOPp->v__DOT__Fringe__DOT__regs__DOT___GEN_2
	    : vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_0__DOT___GEN_1);
    // ALWAYS at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:713
    vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_3__DOT__ff 
	= ((IData)(vlTOPp->reset) ? vlTOPp->v__DOT__Fringe__DOT__regs__DOT___GEN_5
	    : vlTOPp->v__DOT__accel__DOT__x151_argout);
    // ALWAYS at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:478
    if (vlTOPp->reset) {
	vlTOPp->v__DOT__accel__DOT__done_latch__DOT___T_14 = 0U;
    } else {
	if (vlTOPp->v__DOT__accel__DOT___GEN_5) {
	    vlTOPp->v__DOT__accel__DOT__done_latch__DOT___T_14 = 0U;
	} else {
	    if (vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___GEN_26) {
		vlTOPp->v__DOT__accel__DOT__done_latch__DOT___T_14 = 1U;
	    }
	}
    }
    // ALWAYS at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:44
    if (vlTOPp->reset) {
	vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT__FF__DOT__ff 
	    = vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___GEN_6;
    } else {
	if (vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT___GEN_3) {
	    vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT__FF__DOT__ff 
		= vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___GEN_6;
	} else {
	    if (vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT___T_32) {
		vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT__FF__DOT__ff 
		    = (IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT___T_48);
	    }
	}
    }
    // ALWAYS at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:186
    vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT___T_45 
	= ((~ (IData)(vlTOPp->reset)) & (1U == (IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_36)));
    // ALWAYS at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:426
    if (vlTOPp->reset) {
	vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_39 = 0U;
    } else {
	if (vlTOPp->v__DOT__accel__DOT__AccelController_en) {
	    if (vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_74) {
		if (vlTOPp->v__DOT__accel__DOT___T_727) {
		    vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_39 = 0U;
		} else {
		    if ((0U == (IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_36))) {
			vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_39 
			    = vlTOPp->v__DOT__accel__DOT___GEN_1;
		    }
		}
	    } else {
		if ((0U == (IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_36))) {
		    vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_39 
			= vlTOPp->v__DOT__accel__DOT___GEN_1;
		}
	    }
	}
    }
    // ALWAYS at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:1935
    vlTOPp->v__DOT__Fringe__DOT__mag__DOT___T_602 = 0U;
    vlTOPp->v__DOT__Fringe__DOT__mag__DOT__addrFifo__DOT__tagFF__DOT___GEN_1 
	= ((IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__addrFifo__DOT___GEN_2)
	    ? (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__addrFifo__DOT___GEN_1)
	    : (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__addrFifo__DOT__tagFF__DOT__ff));
    vlTOPp->v__DOT__Fringe__DOT__mag__DOT__isWrFifo__DOT__tagFF__DOT___GEN_1 
	= ((IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__isWrFifo__DOT___GEN_2)
	    ? (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__isWrFifo__DOT___GEN_1)
	    : (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__isWrFifo__DOT__tagFF__DOT__ff));
    vlTOPp->v__DOT__Fringe__DOT__mag__DOT__sizeFifo__DOT__tagFF__DOT___GEN_1 
	= ((IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__sizeFifo__DOT___GEN_2)
	    ? (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__sizeFifo__DOT___GEN_1)
	    : (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__sizeFifo__DOT__tagFF__DOT__ff));
    vlTOPp->v__DOT__Fringe__DOT__mag__DOT__dataFifo__DOT__tagFF__DOT___GEN_1 
	= ((IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__dataFifo__DOT___GEN_2)
	    ? (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__dataFifo__DOT___GEN_1)
	    : (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__dataFifo__DOT__tagFF__DOT__ff));
    vlTOPp->io_dram_cmd_bits_addr = (0xffffffc0U & 
				     ((IData)((QData)((IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__burstCounter__DOT__reg__024__DOT__ff))) 
				      << 6U));
    vlTOPp->v__DOT__Fringe__DOT__mag__DOT__burstCounter__DOT__reg__024__DOT___GEN_1 
	= vlTOPp->v__DOT__Fringe__DOT__mag__DOT__burstCounter__DOT__reg__024__DOT__ff;
    vlTOPp->io_dram_cmd_bits_tag = vlTOPp->v__DOT__Fringe__DOT__mag__DOT__burstTagCounter__DOT__reg__024__DOT__ff;
    vlTOPp->v__DOT__Fringe__DOT__mag__DOT__burstTagCounter__DOT__reg__024__DOT___GEN_1 
	= vlTOPp->v__DOT__Fringe__DOT__mag__DOT__burstTagCounter__DOT__reg__024__DOT__ff;
    // ALWAYS at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:675
    if (vlTOPp->reset) {
	vlTOPp->v__DOT__accel__DOT__x151_argout = 0U;
    } else {
	if (vlTOPp->v__DOT__accel__DOT__AccelController_en) {
	    vlTOPp->v__DOT__accel__DOT__x151_argout 
		= ((IData)(4U) + vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_2__DOT__ff);
	}
    }
    vlTOPp->v__DOT__accel__DOT__done_latch__DOT___T_22 
	= ((~ (IData)(vlTOPp->v__DOT__accel__DOT___GEN_5)) 
	   & (IData)(vlTOPp->v__DOT__accel__DOT__done_latch__DOT___T_14));
    vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT__FF__DOT___T_19 
	= ((IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT___GEN_3)
	    ? vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___GEN_6
	    : vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT__FF__DOT__ff);
    // ALWAYS at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:383
    if (vlTOPp->reset) {
	vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_36 = 0U;
    } else {
	if (vlTOPp->v__DOT__accel__DOT__AccelController_en) {
	    if (vlTOPp->v__DOT__accel__DOT__AccelController_en) {
		if (vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_111) {
		    vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_36 = 4U;
		} else {
		    if (vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_93) {
			vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_36 = 1U;
		    } else {
			if (vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_74) {
			    if (vlTOPp->v__DOT__accel__DOT___T_727) {
				if (vlTOPp->v__DOT__accel__DOT___T_727) {
				    vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_36 = 3U;
				} else {
				    if (vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_58) {
					vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_36 
					    = vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___GEN_4;
				    } else {
					if (vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_48) {
					    vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_36 = 1U;
					}
				    }
				}
			    } else {
				vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_36 = 2U;
			    }
			} else {
			    if (vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_58) {
				vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_36 
				    = vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___GEN_4;
			    } else {
				if (vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_48) {
				    vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_36 = 1U;
				}
			    }
			}
		    }
		}
	    }
	} else {
	    vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_36 = 0U;
	}
    }
    // ALWAYS at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:713
    vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_2__DOT__ff 
	= ((IData)(vlTOPp->reset) ? vlTOPp->v__DOT__Fringe__DOT__regs__DOT___GEN_4
	    : vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_2__DOT___GEN_1);
    // ALWAYS at /home/tianzhao/Development/spatial_de1soc/spatial/spatial-lang/gen/ArgInOut/cpp/verilator/top.Instantiator1429013324/Top.v:671
    vlTOPp->v__DOT__accel__DOT___T_727 = ((~ (IData)(vlTOPp->reset)) 
					  & ((IData)(vlTOPp->v__DOT__accel__DOT__AccelController_en) 
					     & (IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_74)));
    vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_48 
	= (0U == (IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_36));
    vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_58 
	= ((0U != (IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_36)) 
	   & (1U == (IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_36)));
    vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT___T_32 
	= ((1U != (IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_36)) 
	   | (1U == (IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_36)));
    vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_73 
	= ((0U != (IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_36)) 
	   & (1U != (IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_36)));
}

void VTop::_settle__TOP__4(VTop__Syms* __restrict vlSymsp) {
    VL_DEBUG_IF(VL_PRINTF("    VTop::_settle__TOP__4\n"); );
    VTop* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    // Body
    vlTOPp->v__DOT__Fringe__DOT__mag__DOT__addrFifo__DOT__tagFF__DOT___GEN_1 
	= ((IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__addrFifo__DOT___GEN_2)
	    ? (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__addrFifo__DOT___GEN_1)
	    : (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__addrFifo__DOT__tagFF__DOT__ff));
    vlTOPp->v__DOT__Fringe__DOT__mag__DOT__isWrFifo__DOT__tagFF__DOT___GEN_1 
	= ((IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__isWrFifo__DOT___GEN_2)
	    ? (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__isWrFifo__DOT___GEN_1)
	    : (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__isWrFifo__DOT__tagFF__DOT__ff));
    vlTOPp->v__DOT__Fringe__DOT__mag__DOT__sizeFifo__DOT__tagFF__DOT___GEN_1 
	= ((IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__sizeFifo__DOT___GEN_2)
	    ? (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__sizeFifo__DOT___GEN_1)
	    : (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__sizeFifo__DOT__tagFF__DOT__ff));
    vlTOPp->v__DOT__Fringe__DOT__mag__DOT__dataFifo__DOT__tagFF__DOT___GEN_1 
	= ((IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__dataFifo__DOT___GEN_2)
	    ? (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__dataFifo__DOT___GEN_1)
	    : (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__dataFifo__DOT__tagFF__DOT__ff));
    vlTOPp->io_dram_cmd_bits_addr = (0xffffffc0U & 
				     ((IData)((QData)((IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__burstCounter__DOT__reg__024__DOT__ff))) 
				      << 6U));
    vlTOPp->v__DOT__Fringe__DOT__mag__DOT__burstCounter__DOT__reg__024__DOT___GEN_1 
	= vlTOPp->v__DOT__Fringe__DOT__mag__DOT__burstCounter__DOT__reg__024__DOT__ff;
    vlTOPp->io_dram_cmd_bits_tag = vlTOPp->v__DOT__Fringe__DOT__mag__DOT__burstTagCounter__DOT__reg__024__DOT__ff;
    vlTOPp->v__DOT__Fringe__DOT__mag__DOT__burstTagCounter__DOT__reg__024__DOT___GEN_1 
	= vlTOPp->v__DOT__Fringe__DOT__mag__DOT__burstTagCounter__DOT__reg__024__DOT__ff;
    vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_0__DOT___GEN_1 
	= (((IData)(vlTOPp->io_wen) & (0U == (IData)(vlTOPp->io_waddr)))
	    ? vlTOPp->io_wdata : vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_0__DOT__ff);
    vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_1__DOT___GEN_1 
	= (((IData)(vlTOPp->v__DOT__Fringe__DOT__depulser__DOT__r__DOT__ff) 
	    | ((IData)(vlTOPp->io_wen) & (1U == (IData)(vlTOPp->io_waddr))))
	    ? ((IData)(vlTOPp->v__DOT__Fringe__DOT__depulser__DOT__r__DOT__ff)
	        ? (vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_0__DOT__ff 
		   & (IData)(vlTOPp->v__DOT__Fringe__DOT__depulser__DOT__r__DOT__ff))
	        : vlTOPp->io_wdata) : vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_1__DOT__ff);
    vlTOPp->v__DOT__accel__DOT__done_latch__DOT___T_22 
	= ((~ (IData)(vlTOPp->v__DOT__accel__DOT___GEN_5)) 
	   & (IData)(vlTOPp->v__DOT__accel__DOT__done_latch__DOT___T_14));
    vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT__FF__DOT___T_19 
	= ((IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT___GEN_3)
	    ? vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___GEN_6
	    : vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT__FF__DOT__ff);
    vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_48 
	= (0U == (IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_36));
    vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_58 
	= ((0U != (IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_36)) 
	   & (1U == (IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_36)));
    vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT___T_32 
	= ((1U != (IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_36)) 
	   | (1U == (IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_36)));
    vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_73 
	= ((0U != (IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_36)) 
	   & (1U != (IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_36)));
    vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_2__DOT___GEN_1 
	= (((IData)(vlTOPp->io_wen) & (2U == (IData)(vlTOPp->io_waddr)))
	    ? vlTOPp->io_wdata : vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_2__DOT__ff);
    vlTOPp->io_rdata = ((3U == (IData)(vlTOPp->io_raddr))
			 ? vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_3__DOT__ff
			 : ((2U == (IData)(vlTOPp->io_raddr))
			     ? vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_2__DOT__ff
			     : ((1U == (IData)(vlTOPp->io_raddr))
				 ? vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_1__DOT__ff
				 : vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_0__DOT__ff)));
    vlTOPp->v__DOT__Fringe__DOT__depulser__DOT__r__DOT___GEN_1 
	= (((IData)(vlTOPp->v__DOT__accel__DOT__done_latch__DOT___T_22) 
	    | (IData)(vlTOPp->v__DOT__Fringe__DOT___GEN_2))
	    ? ((~ (IData)(vlTOPp->v__DOT__Fringe__DOT___GEN_2)) 
	       & (IData)(vlTOPp->v__DOT__accel__DOT__done_latch__DOT___T_22))
	    : (IData)(vlTOPp->v__DOT__Fringe__DOT__depulser__DOT__r__DOT__ff));
    vlTOPp->v__DOT__accel__DOT__AccelController_en 
	= (1U & ((vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_0__DOT__ff 
		  & (~ vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_1__DOT__ff)) 
		 & (~ (IData)(vlTOPp->v__DOT__accel__DOT__done_latch__DOT___T_22))));
    vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT___T_37 
	= (VL_ULL(0x3ffffffff) & ((VL_ULL(0x1ffffffff) 
				   & (VL_ULL(1) + (QData)((IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT__FF__DOT___T_19)))) 
				  + (QData)((IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___GEN_12))));
    vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_74 
	= ((IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_73) 
	   & (2U == (IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_36)));
    vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_92 
	= ((IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_73) 
	   & (2U != (IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_36)));
}

VL_INLINE_OPT void VTop::_combo__TOP__5(VTop__Syms* __restrict vlSymsp) {
    VL_DEBUG_IF(VL_PRINTF("    VTop::_combo__TOP__5\n"); );
    VTop* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    // Body
    vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_0__DOT___GEN_1 
	= (((IData)(vlTOPp->io_wen) & (0U == (IData)(vlTOPp->io_waddr)))
	    ? vlTOPp->io_wdata : vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_0__DOT__ff);
    vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_1__DOT___GEN_1 
	= (((IData)(vlTOPp->v__DOT__Fringe__DOT__depulser__DOT__r__DOT__ff) 
	    | ((IData)(vlTOPp->io_wen) & (1U == (IData)(vlTOPp->io_waddr))))
	    ? ((IData)(vlTOPp->v__DOT__Fringe__DOT__depulser__DOT__r__DOT__ff)
	        ? (vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_0__DOT__ff 
		   & (IData)(vlTOPp->v__DOT__Fringe__DOT__depulser__DOT__r__DOT__ff))
	        : vlTOPp->io_wdata) : vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_1__DOT__ff);
    vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_2__DOT___GEN_1 
	= (((IData)(vlTOPp->io_wen) & (2U == (IData)(vlTOPp->io_waddr)))
	    ? vlTOPp->io_wdata : vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_2__DOT__ff);
    vlTOPp->io_rdata = ((3U == (IData)(vlTOPp->io_raddr))
			 ? vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_3__DOT__ff
			 : ((2U == (IData)(vlTOPp->io_raddr))
			     ? vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_2__DOT__ff
			     : ((1U == (IData)(vlTOPp->io_raddr))
				 ? vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_1__DOT__ff
				 : vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_0__DOT__ff)));
}

VL_INLINE_OPT void VTop::_sequent__TOP__6(VTop__Syms* __restrict vlSymsp) {
    VL_DEBUG_IF(VL_PRINTF("    VTop::_sequent__TOP__6\n"); );
    VTop* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    // Body
    vlTOPp->v__DOT__Fringe__DOT__depulser__DOT__r__DOT___GEN_1 
	= (((IData)(vlTOPp->v__DOT__accel__DOT__done_latch__DOT___T_22) 
	    | (IData)(vlTOPp->v__DOT__Fringe__DOT___GEN_2))
	    ? ((~ (IData)(vlTOPp->v__DOT__Fringe__DOT___GEN_2)) 
	       & (IData)(vlTOPp->v__DOT__accel__DOT__done_latch__DOT___T_22))
	    : (IData)(vlTOPp->v__DOT__Fringe__DOT__depulser__DOT__r__DOT__ff));
    vlTOPp->v__DOT__accel__DOT__AccelController_en 
	= (1U & ((vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_0__DOT__ff 
		  & (~ vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_1__DOT__ff)) 
		 & (~ (IData)(vlTOPp->v__DOT__accel__DOT__done_latch__DOT___T_22))));
    vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT___T_37 
	= (VL_ULL(0x3ffffffff) & ((VL_ULL(0x1ffffffff) 
				   & (VL_ULL(1) + (QData)((IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT__FF__DOT___T_19)))) 
				  + (QData)((IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___GEN_12))));
    vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_74 
	= ((IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_73) 
	   & (2U == (IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_36)));
    vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_92 
	= ((IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_73) 
	   & (2U != (IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_36)));
    vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT___T_48 
	= (VL_ULL(0x1ffffffff) & ((1U != (IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_36))
				   ? (QData)((IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___GEN_6))
				   : ((VL_ULL(0xa) 
				       <= (VL_ULL(0x1ffffffff) 
					   & vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT___T_37))
				       ? (QData)((IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT__FF__DOT___T_19))
				       : vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT___T_37)));
    vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___GEN_4 
	= (((1U == (IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_36)) 
	    & (VL_ULL(0xa) <= (VL_ULL(0x1ffffffff) 
			       & vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT___T_37)))
	    ? ((IData)(vlTOPp->v__DOT__accel__DOT___T_727)
	        ? 3U : 2U) : ((IData)(vlTOPp->v__DOT__accel__DOT___T_727)
			       ? 3U : 1U));
    vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_111 
	= (((IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_92) 
	    & (3U != (IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_36))) 
	   & (4U == (IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_36)));
    vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_93 
	= ((IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_92) 
	   & (3U == (IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_36)));
}

void VTop::_settle__TOP__7(VTop__Syms* __restrict vlSymsp) {
    VL_DEBUG_IF(VL_PRINTF("    VTop::_settle__TOP__7\n"); );
    VTop* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    // Body
    vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT___T_48 
	= (VL_ULL(0x1ffffffff) & ((1U != (IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_36))
				   ? (QData)((IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___GEN_6))
				   : ((VL_ULL(0xa) 
				       <= (VL_ULL(0x1ffffffff) 
					   & vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT___T_37))
				       ? (QData)((IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT__FF__DOT___T_19))
				       : vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT___T_37)));
    vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___GEN_4 
	= (((1U == (IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_36)) 
	    & (VL_ULL(0xa) <= (VL_ULL(0x1ffffffff) 
			       & vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT___T_37)))
	    ? ((IData)(vlTOPp->v__DOT__accel__DOT___T_727)
	        ? 3U : 2U) : ((IData)(vlTOPp->v__DOT__accel__DOT___T_727)
			       ? 3U : 1U));
    vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_111 
	= (((IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_92) 
	    & (3U != (IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_36))) 
	   & (4U == (IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_36)));
    vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_93 
	= ((IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_92) 
	   & (3U == (IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_36)));
    vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___GEN_26 
	= ((IData)(vlTOPp->v__DOT__accel__DOT__AccelController_en)
	    ? (IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_93)
	    : (IData)(vlTOPp->v__DOT__accel__DOT___T_727));
}

VL_INLINE_OPT void VTop::_sequent__TOP__8(VTop__Syms* __restrict vlSymsp) {
    VL_DEBUG_IF(VL_PRINTF("    VTop::_sequent__TOP__8\n"); );
    VTop* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    // Body
    vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___GEN_26 
	= ((IData)(vlTOPp->v__DOT__accel__DOT__AccelController_en)
	    ? (IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_93)
	    : (IData)(vlTOPp->v__DOT__accel__DOT___T_727));
}

void VTop::_eval(VTop__Syms* __restrict vlSymsp) {
    VL_DEBUG_IF(VL_PRINTF("    VTop::_eval\n"); );
    VTop* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    // Body
    vlTOPp->_combo__TOP__2(vlSymsp);
    if (((IData)(vlTOPp->clock) & (~ (IData)(vlTOPp->__Vclklast__TOP__clock)))) {
	vlTOPp->_sequent__TOP__3(vlSymsp);
	vlTOPp->__Vm_traceActivity = (2U | vlTOPp->__Vm_traceActivity);
    }
    vlTOPp->_combo__TOP__5(vlSymsp);
    if (((IData)(vlTOPp->clock) & (~ (IData)(vlTOPp->__Vclklast__TOP__clock)))) {
	vlTOPp->_sequent__TOP__6(vlSymsp);
	vlTOPp->__Vm_traceActivity = (4U | vlTOPp->__Vm_traceActivity);
	vlTOPp->_sequent__TOP__8(vlSymsp);
    }
    // Final
    vlTOPp->__Vclklast__TOP__clock = vlTOPp->clock;
}

void VTop::_eval_initial(VTop__Syms* __restrict vlSymsp) {
    VL_DEBUG_IF(VL_PRINTF("    VTop::_eval_initial\n"); );
    VTop* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
}

void VTop::final() {
    VL_DEBUG_IF(VL_PRINTF("    VTop::final\n"); );
    // Variables
    VTop__Syms* __restrict vlSymsp = this->__VlSymsp;
    VTop* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
}

void VTop::_eval_settle(VTop__Syms* __restrict vlSymsp) {
    VL_DEBUG_IF(VL_PRINTF("    VTop::_eval_settle\n"); );
    VTop* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    // Body
    vlTOPp->_settle__TOP__1(vlSymsp);
    vlTOPp->__Vm_traceActivity = (1U | vlTOPp->__Vm_traceActivity);
    vlTOPp->_settle__TOP__4(vlSymsp);
    vlTOPp->_settle__TOP__7(vlSymsp);
}

VL_INLINE_OPT QData VTop::_change_request(VTop__Syms* __restrict vlSymsp) {
    VL_DEBUG_IF(VL_PRINTF("    VTop::_change_request\n"); );
    VTop* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    // Body
    // Change detection
    QData __req = false;  // Logically a bool
    return __req;
}
