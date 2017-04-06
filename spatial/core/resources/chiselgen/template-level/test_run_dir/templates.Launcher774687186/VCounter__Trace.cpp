// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Tracing implementation internals
#include "verilated_vcd_c.h"
#include "VCounter__Syms.h"


//======================

void VCounter::traceChg(VerilatedVcd* vcdp, void* userthis, uint32_t code) {
    // Callback from vcd->dump()
    VCounter* t=(VCounter*)userthis;
    VCounter__Syms* __restrict vlSymsp = t->__VlSymsp; // Setup global symbol table
    if (vlSymsp->getClearActivity()) {
	t->traceChgThis (vlSymsp, vcdp, code);
    }
}

//======================


void VCounter::traceChgThis(VCounter__Syms* __restrict vlSymsp, VerilatedVcd* vcdp, uint32_t code) {
    VCounter* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    int c=code;
    if (0 && vcdp && c) {}  // Prevent unused
    // Body
    {
	if (VL_UNLIKELY((1 & ((vlTOPp->__Vm_traceActivity 
			       | (vlTOPp->__Vm_traceActivity 
				  >> 1)) | (vlTOPp->__Vm_traceActivity 
					    >> 2))))) {
	    vlTOPp->traceChgThis__2(vlSymsp, vcdp, code);
	}
	if (VL_UNLIKELY((1 & (vlTOPp->__Vm_traceActivity 
			      | (vlTOPp->__Vm_traceActivity 
				 >> 2))))) {
	    vlTOPp->traceChgThis__3(vlSymsp, vcdp, code);
	}
	if (VL_UNLIKELY((2 & vlTOPp->__Vm_traceActivity))) {
	    vlTOPp->traceChgThis__4(vlSymsp, vcdp, code);
	}
	vlTOPp->traceChgThis__5(vlSymsp, vcdp, code);
    }
    // Final
    vlTOPp->__Vm_traceActivity = 0;
}

void VCounter::traceChgThis__2(VCounter__Syms* __restrict vlSymsp, VerilatedVcd* vcdp, uint32_t code) {
    VCounter* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    int c=code;
    if (0 && vcdp && c) {}  // Prevent unused
    // Body
    {
	vcdp->chgBit  (c+1,((((IData)(vlTOPp->v__DOT___T_41) 
			      | (IData)(vlTOPp->v__DOT__ctrs_0__DOT___T_45)) 
			     & ((IData)(vlTOPp->v__DOT__ctrs_0__DOT___T_39) 
				| (IData)(vlTOPp->v__DOT__ctrs_0__DOT___T_42)))));
	vcdp->chgBit  (c+2,((((IData)(vlTOPp->v__DOT___T_42) 
			      | (IData)(vlTOPp->v__DOT__ctrs_1__DOT___T_45)) 
			     & ((IData)(vlTOPp->v__DOT__ctrs_1__DOT___T_39) 
				| (IData)(vlTOPp->v__DOT__ctrs_1__DOT___T_42)))));
    }
}

void VCounter::traceChgThis__3(VCounter__Syms* __restrict vlSymsp, VerilatedVcd* vcdp, uint32_t code) {
    VCounter* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    int c=code;
    if (0 && vcdp && c) {}  // Prevent unused
    // Body
    {
	vcdp->chgBit  (c+3,(vlTOPp->v__DOT___T_41));
	vcdp->chgBit  (c+5,(((IData)(vlTOPp->v__DOT___T_41) 
			     & (IData)(vlTOPp->v__DOT__ctrs_0__DOT___T_39))));
	vcdp->chgBit  (c+6,(vlTOPp->v__DOT__ctrs_0__DOT___T_61));
	vcdp->chgBit  (c+7,(vlTOPp->v__DOT___T_42));
	vcdp->chgBit  (c+8,(vlTOPp->v__DOT___T_43));
	vcdp->chgBit  (c+10,(vlTOPp->v__DOT__ctrs_1__DOT___T_60));
	vcdp->chgBit  (c+11,(((IData)(vlTOPp->v__DOT___T_43) 
			      & (IData)(vlTOPp->v__DOT__ctrs_1__DOT___T_39))));
	vcdp->chgBit  (c+12,(vlTOPp->v__DOT___T_45));
	vcdp->chgBit  (c+14,(vlTOPp->v__DOT__ctrs_2__DOT___T_60));
	vcdp->chgBit  (c+15,(((IData)(vlTOPp->v__DOT___T_45) 
			      & (IData)(vlTOPp->v__DOT__ctrs_2__DOT___T_39))));
	vcdp->chgBit  (c+16,(vlTOPp->v__DOT__isDone));
	vcdp->chgBit  (c+17,(((IData)(vlTOPp->v__DOT___T_44) 
			      & ((IData)(vlTOPp->v__DOT___T_45) 
				 & (IData)(vlTOPp->v__DOT__ctrs_2__DOT___T_39)))));
	vcdp->chgBus  (c+18,((IData)(vlTOPp->v__DOT__ctrs_0__DOT___T_48)),32);
	vcdp->chgBit  (c+19,(vlTOPp->v__DOT__ctrs_0__DOT___T_32));
	vcdp->chgBus  (c+4,(vlTOPp->v__DOT__ctrs_0__DOT__FF__DOT___T_19),32);
	vcdp->chgBus  (c+20,((IData)(vlTOPp->v__DOT__ctrs_1__DOT___T_48)),32);
	vcdp->chgBit  (c+21,(vlTOPp->v__DOT__ctrs_1__DOT___T_32));
	vcdp->chgBus  (c+9,(vlTOPp->v__DOT__ctrs_1__DOT__FF__DOT___T_19),32);
	vcdp->chgBus  (c+22,((IData)(vlTOPp->v__DOT__ctrs_2__DOT___T_48)),32);
	vcdp->chgBit  (c+23,(vlTOPp->v__DOT__ctrs_2__DOT___T_32));
	vcdp->chgBus  (c+13,(vlTOPp->v__DOT__ctrs_2__DOT__FF__DOT___T_19),32);
    }
}

void VCounter::traceChgThis__4(VCounter__Syms* __restrict vlSymsp, VerilatedVcd* vcdp, uint32_t code) {
    VCounter* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    int c=code;
    if (0 && vcdp && c) {}  // Prevent unused
    // Body
    {
	vcdp->chgBit  (c+24,(vlTOPp->v__DOT__wasDone));
	vcdp->chgBit  (c+25,(vlTOPp->v__DOT__wasWasDone));
	vcdp->chgBus  (c+26,(vlTOPp->v__DOT__ctrs_0__DOT__FF__DOT__ff),32);
	vcdp->chgBus  (c+27,(vlTOPp->v__DOT__ctrs_1__DOT__FF__DOT__ff),32);
	vcdp->chgBus  (c+28,(vlTOPp->v__DOT__ctrs_2__DOT__FF__DOT__ff),32);
    }
}

void VCounter::traceChgThis__5(VCounter__Syms* __restrict vlSymsp, VerilatedVcd* vcdp, uint32_t code) {
    VCounter* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    int c=code;
    if (0 && vcdp && c) {}  // Prevent unused
    // Body
    {
	vcdp->chgBus  (c+40,(vlTOPp->io_input_gaps_0),32);
	vcdp->chgBus  (c+41,(vlTOPp->io_input_gaps_1),32);
	vcdp->chgBus  (c+42,(vlTOPp->io_input_gaps_2),32);
	vcdp->chgBit  (c+46,(vlTOPp->io_input_isStream));
	vcdp->chgBus  (c+47,(vlTOPp->io_output_counts_0),32);
	vcdp->chgBus  (c+48,(vlTOPp->io_output_counts_1),32);
	vcdp->chgBus  (c+49,(vlTOPp->io_output_counts_2),32);
	vcdp->chgBit  (c+50,(vlTOPp->io_output_done));
	vcdp->chgBit  (c+51,(vlTOPp->io_output_extendedDone));
	vcdp->chgBit  (c+52,(vlTOPp->io_output_saturated));
	vcdp->chgBus  (c+53,(((0 == vlTOPp->v__DOT__ctrs_0__DOT__FF__DOT___T_19)
			       ? vlTOPp->io_input_maxes_0
			       : vlTOPp->v__DOT__ctrs_0__DOT__FF__DOT___T_19)),32);
	vcdp->chgBus  (c+54,(((0 == vlTOPp->v__DOT__ctrs_1__DOT__FF__DOT___T_19)
			       ? vlTOPp->io_input_maxes_1
			       : vlTOPp->v__DOT__ctrs_1__DOT__FF__DOT___T_19)),32);
	vcdp->chgBus  (c+55,(((0 == vlTOPp->v__DOT__ctrs_2__DOT__FF__DOT___T_19)
			       ? vlTOPp->io_input_maxes_2
			       : vlTOPp->v__DOT__ctrs_2__DOT__FF__DOT___T_19)),32);
	vcdp->chgBit  (c+56,((((IData)(vlTOPp->io_input_enable) 
			       | (IData)(vlTOPp->v__DOT__ctrs_2__DOT___T_45)) 
			      & ((IData)(vlTOPp->v__DOT__ctrs_2__DOT___T_39) 
				 | (IData)(vlTOPp->v__DOT__ctrs_2__DOT___T_42)))));
	vcdp->chgBus  (c+34,(vlTOPp->io_input_maxes_0),32);
	vcdp->chgBus  (c+37,(vlTOPp->io_input_strides_0),32);
	vcdp->chgBit  (c+45,(vlTOPp->io_input_saturate));
	vcdp->chgBus  (c+31,(vlTOPp->io_input_starts_0),32);
	vcdp->chgBus  (c+35,(vlTOPp->io_input_maxes_1),32);
	vcdp->chgBus  (c+38,(vlTOPp->io_input_strides_1),32);
	vcdp->chgBus  (c+32,(vlTOPp->io_input_starts_1),32);
	vcdp->chgBus  (c+36,(vlTOPp->io_input_maxes_2),32);
	vcdp->chgBus  (c+39,(vlTOPp->io_input_strides_2),32);
	vcdp->chgBit  (c+44,(vlTOPp->io_input_enable));
	vcdp->chgBit  (c+29,(vlTOPp->clock));
	vcdp->chgBit  (c+30,(vlTOPp->reset));
	vcdp->chgBus  (c+33,(vlTOPp->io_input_starts_2),32);
	vcdp->chgBit  (c+43,(vlTOPp->io_input_reset));
    }
}
