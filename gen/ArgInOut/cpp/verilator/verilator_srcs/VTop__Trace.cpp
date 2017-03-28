// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Tracing implementation internals
#include "verilated_vcd_c.h"
#include "VTop__Syms.h"


//======================

void VTop::traceChg(VerilatedVcd* vcdp, void* userthis, uint32_t code) {
    // Callback from vcd->dump()
    VTop* t=(VTop*)userthis;
    VTop__Syms* __restrict vlSymsp = t->__VlSymsp; // Setup global symbol table
    if (vlSymsp->getClearActivity()) {
	t->traceChgThis (vlSymsp, vcdp, code);
    }
}

//======================


void VTop::traceChgThis(VTop__Syms* __restrict vlSymsp, VerilatedVcd* vcdp, uint32_t code) {
    VTop* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    int c=code;
    if (0 && vcdp && c) {}  // Prevent unused
    // Body
    {
	if (VL_UNLIKELY((1U & (vlTOPp->__Vm_traceActivity 
			       | (vlTOPp->__Vm_traceActivity 
				  >> 1U))))) {
	    vlTOPp->traceChgThis__2(vlSymsp, vcdp, code);
	}
	if (VL_UNLIKELY((1U & ((vlTOPp->__Vm_traceActivity 
				| (vlTOPp->__Vm_traceActivity 
				   >> 1U)) | (vlTOPp->__Vm_traceActivity 
					      >> 2U))))) {
	    vlTOPp->traceChgThis__3(vlSymsp, vcdp, code);
	}
	if (VL_UNLIKELY((1U & (vlTOPp->__Vm_traceActivity 
			       | (vlTOPp->__Vm_traceActivity 
				  >> 2U))))) {
	    vlTOPp->traceChgThis__4(vlSymsp, vcdp, code);
	}
	if (VL_UNLIKELY((2U & vlTOPp->__Vm_traceActivity))) {
	    vlTOPp->traceChgThis__5(vlSymsp, vcdp, code);
	}
	vlTOPp->traceChgThis__6(vlSymsp, vcdp, code);
    }
    // Final
    vlTOPp->__Vm_traceActivity = 0U;
}

void VTop::traceChgThis__2(VTop__Syms* __restrict vlSymsp, VerilatedVcd* vcdp, uint32_t code) {
    VTop* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    int c=code;
    if (0 && vcdp && c) {}  // Prevent unused
    // Body
    {
	vcdp->chgBus  (c+3,(((0U == vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT__FF__DOT___T_19)
			      ? 0xaU : vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT__FF__DOT___T_19)),32);
	vcdp->chgBit  (c+4,(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT___T_32));
	vcdp->chgBus  (c+2,(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT__FF__DOT___T_19),32);
	vcdp->chgBit  (c+1,(vlTOPp->v__DOT__accel__DOT__done_latch__DOT___T_22));
	vcdp->chgBit  (c+5,(((~ (IData)(vlTOPp->v__DOT__Fringe__DOT___GEN_2)) 
			     & (IData)(vlTOPp->v__DOT__accel__DOT__done_latch__DOT___T_22))));
	vcdp->chgBit  (c+6,(((IData)(vlTOPp->v__DOT__accel__DOT__done_latch__DOT___T_22) 
			     | (IData)(vlTOPp->v__DOT__Fringe__DOT___GEN_2))));
	vcdp->chgBit  (c+7,((((IData)(vlTOPp->v__DOT__accel__DOT__done_latch__DOT___T_22) 
			      | (IData)(vlTOPp->v__DOT__Fringe__DOT___GEN_2))
			      ? ((~ (IData)(vlTOPp->v__DOT__Fringe__DOT___GEN_2)) 
				 & (IData)(vlTOPp->v__DOT__accel__DOT__done_latch__DOT___T_22))
			      : (IData)(vlTOPp->v__DOT__Fringe__DOT__depulser__DOT__r__DOT__ff))));
    }
}

void VTop::traceChgThis__3(VTop__Syms* __restrict vlSymsp, VerilatedVcd* vcdp, uint32_t code) {
    VTop* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    int c=code;
    if (0 && vcdp && c) {}  // Prevent unused
    // Body
    {
	vcdp->chgBit  (c+8,(((IData)(vlTOPp->v__DOT__accel__DOT__AccelController_en) 
			     & ((IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_74) 
				& (~ (IData)(vlTOPp->v__DOT__accel__DOT___T_727))))));
	vcdp->chgBit  (c+9,(((IData)(vlTOPp->v__DOT__accel__DOT__AccelController_en) 
			     & ((IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_58) 
				& (~ ((1U == (IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_36)) 
				      & (VL_ULL(0xa) 
					 <= (VL_ULL(0x1ffffffff) 
					     & vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT___T_37))))))));
	vcdp->chgBit  (c+10,(((1U == (IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_36)) 
			      & (VL_ULL(0xa) <= (VL_ULL(0x1ffffffff) 
						 & vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT___T_37)))));
	vcdp->chgBit  (c+11,((((1U == (IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_36)) 
			       | (IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT___T_45)) 
			      & ((VL_ULL(0xa) <= (VL_ULL(0x1ffffffff) 
						  & vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT___T_37)) 
				 | (IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT___T_42)))));
    }
}

void VTop::traceChgThis__4(VTop__Syms* __restrict vlSymsp, VerilatedVcd* vcdp, uint32_t code) {
    VTop* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    int c=code;
    if (0 && vcdp && c) {}  // Prevent unused
    // Body
    {
	vcdp->chgBit  (c+14,(((IData)(vlTOPp->v__DOT__accel__DOT__AccelController_en) 
			      & (IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_74))));
	vcdp->chgBit  (c+12,(vlTOPp->v__DOT__accel__DOT__AccelController_en));
	vcdp->chgBit  (c+15,((VL_ULL(0xa) <= (VL_ULL(0x1ffffffff) 
					      & vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT___T_37))));
	vcdp->chgBus  (c+16,((IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT___T_48)),32);
	vcdp->chgBit  (c+13,(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___GEN_26));
    }
}

void VTop::traceChgThis__5(VTop__Syms* __restrict vlSymsp, VerilatedVcd* vcdp, uint32_t code) {
    VTop* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    int c=code;
    if (0 && vcdp && c) {}  // Prevent unused
    // Body
    {
	vcdp->chgBit  (c+22,(vlTOPp->v__DOT__accel__DOT___T_727));
	vcdp->chgBus  (c+23,(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_39),32);
	vcdp->chgBus  (c+24,(((IData)(4U) + vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_2__DOT__ff)),32);
	vcdp->chgBit  (c+25,((1U & (IData)(((QData)((IData)(
							    ((IData)(4U) 
							     + vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_2__DOT__ff))) 
					    >> 0x20U)))));
	vcdp->chgBit  (c+26,((1U != (IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_36))));
	vcdp->chgBit  (c+27,((1U == (IData)(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT___T_36))));
	vcdp->chgBus  (c+28,(vlTOPp->v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT__FF__DOT__ff),32);
	vcdp->chgBit  (c+17,((1U & (vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_0__DOT__ff 
				    & (~ vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_1__DOT__ff)))));
	vcdp->chgBus  (c+32,((vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_0__DOT__ff 
			      & (IData)(vlTOPp->v__DOT__Fringe__DOT__depulser__DOT__r__DOT__ff))),32);
	vcdp->chgBus  (c+19,(vlTOPp->v__DOT__accel__DOT__x151_argout),32);
	vcdp->chgBus  (c+29,(vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_0__DOT__ff),32);
	vcdp->chgBus  (c+30,(vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_1__DOT__ff),32);
	vcdp->chgBus  (c+18,(vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_2__DOT__ff),32);
	vcdp->chgBus  (c+33,(vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_3__DOT__ff),32);
	vcdp->chgBit  (c+31,(vlTOPp->v__DOT__Fringe__DOT__depulser__DOT__r__DOT__ff));
	vcdp->chgBus  (c+20,((0xffffffc0U & ((IData)((QData)((IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__burstCounter__DOT__reg__024__DOT__ff))) 
					     << 6U))),32);
	vcdp->chgBus  (c+21,(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__burstTagCounter__DOT__reg__024__DOT__ff),32);
	vcdp->chgBit  (c+35,(vlTOPp->v__DOT__Fringe__DOT__mag__DOT___T_602));
	vcdp->chgBit  (c+36,(((~ (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT___GEN_6)) 
			      & (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__wrPhase__DOT___T_14))));
	vcdp->chgBus  (c+39,(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__burstTagCounter__DOT__reg__024__DOT__ff),31);
	vcdp->chgBit  (c+41,(((IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__addrFifo__DOT___GEN_2)
			       ? (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__addrFifo__DOT___GEN_1)
			       : (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__addrFifo__DOT__tagFF__DOT__ff))));
	vcdp->chgBit  (c+40,(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__addrFifo__DOT__tagFF__DOT__ff));
	vcdp->chgBit  (c+43,(((IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__isWrFifo__DOT___GEN_2)
			       ? (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__isWrFifo__DOT___GEN_1)
			       : (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__isWrFifo__DOT__tagFF__DOT__ff))));
	vcdp->chgBit  (c+42,(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__isWrFifo__DOT__tagFF__DOT__ff));
	vcdp->chgBit  (c+45,(((IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__sizeFifo__DOT___GEN_2)
			       ? (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__sizeFifo__DOT___GEN_1)
			       : (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__sizeFifo__DOT__tagFF__DOT__ff))));
	vcdp->chgBit  (c+44,(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__sizeFifo__DOT__tagFF__DOT__ff));
	vcdp->chgBit  (c+47,(((IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__dataFifo__DOT___GEN_2)
			       ? (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__dataFifo__DOT___GEN_1)
			       : (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__dataFifo__DOT__tagFF__DOT__ff))));
	vcdp->chgBit  (c+46,(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__dataFifo__DOT__tagFF__DOT__ff));
	vcdp->chgQuad (c+48,((QData)((IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__burstCounter__DOT__reg__024__DOT__ff))),33);
	vcdp->chgQuad (c+50,((VL_ULL(0x1ffffffff) & 
			      (VL_ULL(1) + (QData)((IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__burstCounter__DOT__reg__024__DOT__ff))))),33);
	vcdp->chgBus  (c+34,(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__burstCounter__DOT__reg__024__DOT__ff),32);
	vcdp->chgBus  (c+52,(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__burstTagCounter__DOT__reg__024__DOT__ff),12);
	vcdp->chgBus  (c+53,((0xfffU & ((IData)(1U) 
					+ (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__burstTagCounter__DOT__reg__024__DOT__ff)))),12);
	vcdp->chgBit  (c+54,((0x400U <= (0xfffU & ((IData)(1U) 
						   + (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__burstTagCounter__DOT__reg__024__DOT__ff))))));
	vcdp->chgBus  (c+55,((0xfffU & ((0x400U <= 
					 (0xfffU & 
					  ((IData)(1U) 
					   + (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__burstTagCounter__DOT__reg__024__DOT__ff))))
					 ? ((IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT___GEN_7)
					     ? (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__burstTagCounter__DOT__reg__024__DOT__ff)
					     : 0U) : 
					((IData)(1U) 
					 + (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__burstTagCounter__DOT__reg__024__DOT__ff))))),12);
	vcdp->chgBus  (c+38,((0x7ffU & ((0x400U <= 
					 (0xfffU & 
					  ((IData)(1U) 
					   + (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__burstTagCounter__DOT__reg__024__DOT__ff))))
					 ? ((IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT___GEN_7)
					     ? (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__burstTagCounter__DOT__reg__024__DOT__ff)
					     : 0U) : 
					((IData)(1U) 
					 + (IData)(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__burstTagCounter__DOT__reg__024__DOT__ff))))),11);
	vcdp->chgBus  (c+37,(vlTOPp->v__DOT__Fringe__DOT__mag__DOT__burstTagCounter__DOT__reg__024__DOT__ff),11);
    }
}

void VTop::traceChgThis__6(VTop__Syms* __restrict vlSymsp, VerilatedVcd* vcdp, uint32_t code) {
    VTop* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    int c=code;
    if (0 && vcdp && c) {}  // Prevent unused
    // Body
    {
	vcdp->chgBus  (c+62,(vlTOPp->io_rdata),32);
	vcdp->chgBit  (c+64,(vlTOPp->io_dram_cmd_valid));
	vcdp->chgBus  (c+65,(vlTOPp->io_dram_cmd_bits_addr),32);
	vcdp->chgBit  (c+66,(vlTOPp->io_dram_cmd_bits_isWr));
	vcdp->chgBus  (c+67,(vlTOPp->io_dram_cmd_bits_tag),32);
	vcdp->chgBus  (c+68,(vlTOPp->io_dram_cmd_bits_streamId),32);
	vcdp->chgBus  (c+69,(vlTOPp->io_dram_cmd_bits_wdata_0),32);
	vcdp->chgBus  (c+70,(vlTOPp->io_dram_cmd_bits_wdata_1),32);
	vcdp->chgBus  (c+71,(vlTOPp->io_dram_cmd_bits_wdata_2),32);
	vcdp->chgBus  (c+72,(vlTOPp->io_dram_cmd_bits_wdata_3),32);
	vcdp->chgBus  (c+73,(vlTOPp->io_dram_cmd_bits_wdata_4),32);
	vcdp->chgBus  (c+74,(vlTOPp->io_dram_cmd_bits_wdata_5),32);
	vcdp->chgBus  (c+75,(vlTOPp->io_dram_cmd_bits_wdata_6),32);
	vcdp->chgBus  (c+76,(vlTOPp->io_dram_cmd_bits_wdata_7),32);
	vcdp->chgBus  (c+77,(vlTOPp->io_dram_cmd_bits_wdata_8),32);
	vcdp->chgBus  (c+78,(vlTOPp->io_dram_cmd_bits_wdata_9),32);
	vcdp->chgBus  (c+79,(vlTOPp->io_dram_cmd_bits_wdata_10),32);
	vcdp->chgBus  (c+80,(vlTOPp->io_dram_cmd_bits_wdata_11),32);
	vcdp->chgBus  (c+81,(vlTOPp->io_dram_cmd_bits_wdata_12),32);
	vcdp->chgBus  (c+82,(vlTOPp->io_dram_cmd_bits_wdata_13),32);
	vcdp->chgBus  (c+83,(vlTOPp->io_dram_cmd_bits_wdata_14),32);
	vcdp->chgBus  (c+84,(vlTOPp->io_dram_cmd_bits_wdata_15),32);
	vcdp->chgBit  (c+85,(vlTOPp->io_dram_resp_ready));
	vcdp->chgBit  (c+59,(vlTOPp->io_wen));
	vcdp->chgBus  (c+60,(vlTOPp->io_waddr),2);
	vcdp->chgBit  (c+106,(((IData)(vlTOPp->io_wen) 
			       & (0U == (IData)(vlTOPp->io_waddr)))));
	vcdp->chgBus  (c+107,(((IData)(vlTOPp->v__DOT__Fringe__DOT__depulser__DOT__r__DOT__ff)
			        ? (vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_0__DOT__ff 
				   & (IData)(vlTOPp->v__DOT__Fringe__DOT__depulser__DOT__r__DOT__ff))
			        : vlTOPp->io_wdata)),32);
	vcdp->chgBit  (c+108,(((IData)(vlTOPp->v__DOT__Fringe__DOT__depulser__DOT__r__DOT__ff) 
			       | ((IData)(vlTOPp->io_wen) 
				  & (1U == (IData)(vlTOPp->io_waddr))))));
	vcdp->chgBit  (c+109,(((IData)(vlTOPp->io_wen) 
			       & (2U == (IData)(vlTOPp->io_waddr)))));
	vcdp->chgBus  (c+110,((((IData)(vlTOPp->io_wen) 
				& (0U == (IData)(vlTOPp->io_waddr)))
			        ? vlTOPp->io_wdata : vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_0__DOT__ff)),32);
	vcdp->chgBus  (c+111,((((IData)(vlTOPp->v__DOT__Fringe__DOT__depulser__DOT__r__DOT__ff) 
				| ((IData)(vlTOPp->io_wen) 
				   & (1U == (IData)(vlTOPp->io_waddr))))
			        ? ((IData)(vlTOPp->v__DOT__Fringe__DOT__depulser__DOT__r__DOT__ff)
				    ? (vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_0__DOT__ff 
				       & (IData)(vlTOPp->v__DOT__Fringe__DOT__depulser__DOT__r__DOT__ff))
				    : vlTOPp->io_wdata)
			        : vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_1__DOT__ff)),32);
	vcdp->chgBus  (c+61,(vlTOPp->io_wdata),32);
	vcdp->chgBus  (c+112,((((IData)(vlTOPp->io_wen) 
				& (2U == (IData)(vlTOPp->io_waddr)))
			        ? vlTOPp->io_wdata : vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_2__DOT__ff)),32);
	vcdp->chgBus  (c+58,(vlTOPp->io_raddr),2);
	vcdp->chgBus  (c+105,(((3U == (IData)(vlTOPp->io_raddr))
			        ? vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_3__DOT__ff
			        : ((2U == (IData)(vlTOPp->io_raddr))
				    ? vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_2__DOT__ff
				    : ((1U == (IData)(vlTOPp->io_raddr))
				        ? vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_1__DOT__ff
				        : vlTOPp->v__DOT__Fringe__DOT__regs__DOT__regs_0__DOT__ff)))),32);
	vcdp->chgBit  (c+63,(vlTOPp->io_dram_cmd_ready));
	vcdp->chgBit  (c+86,(vlTOPp->io_dram_resp_valid));
	vcdp->chgBus  (c+87,(vlTOPp->io_dram_resp_bits_rdata_0),32);
	vcdp->chgBus  (c+88,(vlTOPp->io_dram_resp_bits_rdata_1),32);
	vcdp->chgBus  (c+89,(vlTOPp->io_dram_resp_bits_rdata_2),32);
	vcdp->chgBus  (c+90,(vlTOPp->io_dram_resp_bits_rdata_3),32);
	vcdp->chgBus  (c+91,(vlTOPp->io_dram_resp_bits_rdata_4),32);
	vcdp->chgBus  (c+92,(vlTOPp->io_dram_resp_bits_rdata_5),32);
	vcdp->chgBus  (c+93,(vlTOPp->io_dram_resp_bits_rdata_6),32);
	vcdp->chgBus  (c+94,(vlTOPp->io_dram_resp_bits_rdata_7),32);
	vcdp->chgBus  (c+95,(vlTOPp->io_dram_resp_bits_rdata_8),32);
	vcdp->chgBus  (c+96,(vlTOPp->io_dram_resp_bits_rdata_9),32);
	vcdp->chgBus  (c+97,(vlTOPp->io_dram_resp_bits_rdata_10),32);
	vcdp->chgBus  (c+98,(vlTOPp->io_dram_resp_bits_rdata_11),32);
	vcdp->chgBus  (c+99,(vlTOPp->io_dram_resp_bits_rdata_12),32);
	vcdp->chgBus  (c+100,(vlTOPp->io_dram_resp_bits_rdata_13),32);
	vcdp->chgBus  (c+101,(vlTOPp->io_dram_resp_bits_rdata_14),32);
	vcdp->chgBus  (c+102,(vlTOPp->io_dram_resp_bits_rdata_15),32);
	vcdp->chgBus  (c+103,(vlTOPp->io_dram_resp_bits_tag),32);
	vcdp->chgBus  (c+104,(vlTOPp->io_dram_resp_bits_streamId),32);
	vcdp->chgBit  (c+56,(vlTOPp->clock));
	vcdp->chgBit  (c+57,(vlTOPp->reset));
    }
}
