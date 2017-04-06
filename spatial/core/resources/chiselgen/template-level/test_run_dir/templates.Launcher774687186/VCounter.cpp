// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Design implementation internals
// See VCounter.h for the primary calling header

#include "VCounter.h"          // For This
#include "VCounter__Syms.h"

//--------------------
// STATIC VARIABLES


//--------------------

VL_CTOR_IMP(VCounter) {
    VCounter__Syms* __restrict vlSymsp = __VlSymsp = new VCounter__Syms(this, name());
    VCounter* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    // Reset internal values
    
    // Reset structure values
    clock = VL_RAND_RESET_I(1);
    reset = VL_RAND_RESET_I(1);
    io_input_starts_0 = VL_RAND_RESET_I(32);
    io_input_starts_1 = VL_RAND_RESET_I(32);
    io_input_starts_2 = VL_RAND_RESET_I(32);
    io_input_maxes_0 = VL_RAND_RESET_I(32);
    io_input_maxes_1 = VL_RAND_RESET_I(32);
    io_input_maxes_2 = VL_RAND_RESET_I(32);
    io_input_strides_0 = VL_RAND_RESET_I(32);
    io_input_strides_1 = VL_RAND_RESET_I(32);
    io_input_strides_2 = VL_RAND_RESET_I(32);
    io_input_gaps_0 = VL_RAND_RESET_I(32);
    io_input_gaps_1 = VL_RAND_RESET_I(32);
    io_input_gaps_2 = VL_RAND_RESET_I(32);
    io_input_reset = VL_RAND_RESET_I(1);
    io_input_enable = VL_RAND_RESET_I(1);
    io_input_saturate = VL_RAND_RESET_I(1);
    io_input_isStream = VL_RAND_RESET_I(1);
    io_output_counts_0 = VL_RAND_RESET_I(32);
    io_output_counts_1 = VL_RAND_RESET_I(32);
    io_output_counts_2 = VL_RAND_RESET_I(32);
    io_output_done = VL_RAND_RESET_I(1);
    io_output_extendedDone = VL_RAND_RESET_I(1);
    io_output_saturated = VL_RAND_RESET_I(1);
    v__DOT___T_41 = VL_RAND_RESET_I(1);
    v__DOT___T_42 = VL_RAND_RESET_I(1);
    v__DOT___T_43 = VL_RAND_RESET_I(1);
    v__DOT___T_44 = VL_RAND_RESET_I(1);
    v__DOT___T_45 = VL_RAND_RESET_I(1);
    v__DOT__isDone = VL_RAND_RESET_I(1);
    v__DOT__wasDone = VL_RAND_RESET_I(1);
    v__DOT__wasWasDone = VL_RAND_RESET_I(1);
    v__DOT__ctrs_0__DOT___T_32 = VL_RAND_RESET_I(1);
    v__DOT__ctrs_0__DOT___T_35 = VL_RAND_RESET_Q(34);
    v__DOT__ctrs_0__DOT___T_39 = VL_RAND_RESET_I(1);
    v__DOT__ctrs_0__DOT___T_42 = VL_RAND_RESET_I(1);
    v__DOT__ctrs_0__DOT___T_45 = VL_RAND_RESET_I(1);
    v__DOT__ctrs_0__DOT___T_48 = VL_RAND_RESET_Q(33);
    v__DOT__ctrs_0__DOT___T_61 = VL_RAND_RESET_I(1);
    v__DOT__ctrs_0__DOT__FF__DOT__ff = VL_RAND_RESET_I(32);
    v__DOT__ctrs_0__DOT__FF__DOT___T_19 = VL_RAND_RESET_I(32);
    v__DOT__ctrs_1__DOT___T_32 = VL_RAND_RESET_I(1);
    v__DOT__ctrs_1__DOT___T_35 = VL_RAND_RESET_Q(34);
    v__DOT__ctrs_1__DOT___T_39 = VL_RAND_RESET_I(1);
    v__DOT__ctrs_1__DOT___T_42 = VL_RAND_RESET_I(1);
    v__DOT__ctrs_1__DOT___T_45 = VL_RAND_RESET_I(1);
    v__DOT__ctrs_1__DOT___T_48 = VL_RAND_RESET_Q(33);
    v__DOT__ctrs_1__DOT___T_60 = VL_RAND_RESET_I(1);
    v__DOT__ctrs_1__DOT__FF__DOT__ff = VL_RAND_RESET_I(32);
    v__DOT__ctrs_1__DOT__FF__DOT___T_19 = VL_RAND_RESET_I(32);
    v__DOT__ctrs_2__DOT___T_32 = VL_RAND_RESET_I(1);
    v__DOT__ctrs_2__DOT___T_35 = VL_RAND_RESET_Q(34);
    v__DOT__ctrs_2__DOT___T_39 = VL_RAND_RESET_I(1);
    v__DOT__ctrs_2__DOT___T_42 = VL_RAND_RESET_I(1);
    v__DOT__ctrs_2__DOT___T_45 = VL_RAND_RESET_I(1);
    v__DOT__ctrs_2__DOT___T_48 = VL_RAND_RESET_Q(33);
    v__DOT__ctrs_2__DOT___T_60 = VL_RAND_RESET_I(1);
    v__DOT__ctrs_2__DOT__FF__DOT__ff = VL_RAND_RESET_I(32);
    v__DOT__ctrs_2__DOT__FF__DOT___T_19 = VL_RAND_RESET_I(32);
    __Vclklast__TOP__clock = VL_RAND_RESET_I(1);
    __Vm_traceActivity = VL_RAND_RESET_I(32);
}

void VCounter::__Vconfigure(VCounter__Syms* vlSymsp, bool first) {
    if (0 && first) {}  // Prevent unused
    this->__VlSymsp = vlSymsp;
}

VCounter::~VCounter() {
    delete __VlSymsp; __VlSymsp=NULL;
}

//--------------------


void VCounter::eval() {
    VCounter__Syms* __restrict vlSymsp = this->__VlSymsp; // Setup global symbol table
    VCounter* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    // Initialize
    if (VL_UNLIKELY(!vlSymsp->__Vm_didInit)) _eval_initial_loop(vlSymsp);
    // Evaluate till stable
    VL_DEBUG_IF(VL_PRINTF("\n----TOP Evaluate VCounter::eval\n"); );
    int __VclockLoop = 0;
    IData __Vchange=1;
    while (VL_LIKELY(__Vchange)) {
	VL_DEBUG_IF(VL_PRINTF(" Clock loop\n"););
	vlSymsp->__Vm_activity = true;
	_eval(vlSymsp);
	__Vchange = _change_request(vlSymsp);
	if (++__VclockLoop > 100) vl_fatal(__FILE__,__LINE__,__FILE__,"Verilated model didn't converge");
    }
}

void VCounter::_eval_initial_loop(VCounter__Syms* __restrict vlSymsp) {
    vlSymsp->__Vm_didInit = true;
    _eval_initial(vlSymsp);
    vlSymsp->__Vm_activity = true;
    int __VclockLoop = 0;
    IData __Vchange=1;
    while (VL_LIKELY(__Vchange)) {
	_eval_settle(vlSymsp);
	_eval(vlSymsp);
	__Vchange = _change_request(vlSymsp);
	if (++__VclockLoop > 100) vl_fatal(__FILE__,__LINE__,__FILE__,"Verilated model didn't DC converge");
    }
}

//--------------------
// Internal Methods

void VCounter::_sequent__TOP__1(VCounter__Syms* __restrict vlSymsp) {
    VL_DEBUG_IF(VL_PRINTF("    VCounter::_sequent__TOP__1\n"); );
    VCounter* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    // Variables
    VL_SIG8(__Vdly__v__DOT__wasDone,0,0);
    //char	__VpadToAlign5[3];
    // Body
    __Vdly__v__DOT__wasDone = vlTOPp->v__DOT__wasDone;
    // ALWAYS at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:180
    vlTOPp->v__DOT__ctrs_2__DOT___T_45 = ((~ (IData)(vlTOPp->reset)) 
					  & (IData)(vlTOPp->io_input_enable));
    // ALWAYS at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:387
    __Vdly__v__DOT__wasDone = ((~ (IData)(vlTOPp->reset)) 
			       & (IData)(vlTOPp->v__DOT__isDone));
    vlTOPp->v__DOT__wasWasDone = ((~ (IData)(vlTOPp->reset)) 
				  & (IData)(vlTOPp->v__DOT__wasDone));
    // ALWAYS at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:180
    vlTOPp->v__DOT__ctrs_0__DOT___T_45 = ((~ (IData)(vlTOPp->reset)) 
					  & (IData)(vlTOPp->v__DOT___T_41));
    // ALWAYS at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:180
    vlTOPp->v__DOT__ctrs_1__DOT___T_45 = ((~ (IData)(vlTOPp->reset)) 
					  & (IData)(vlTOPp->v__DOT___T_42));
    // ALWAYS at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:175
    vlTOPp->v__DOT__ctrs_1__DOT___T_42 = ((~ (IData)(vlTOPp->reset)) 
					  & (IData)(vlTOPp->v__DOT__ctrs_1__DOT___T_39));
    // ALWAYS at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:175
    vlTOPp->v__DOT__ctrs_0__DOT___T_42 = ((~ (IData)(vlTOPp->reset)) 
					  & (IData)(vlTOPp->v__DOT__ctrs_0__DOT___T_39));
    // ALWAYS at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:175
    vlTOPp->v__DOT__ctrs_2__DOT___T_42 = ((~ (IData)(vlTOPp->reset)) 
					  & (IData)(vlTOPp->v__DOT__ctrs_2__DOT___T_39));
    // ALWAYS at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:44
    if (vlTOPp->reset) {
	vlTOPp->v__DOT__ctrs_1__DOT__FF__DOT__ff = vlTOPp->io_input_starts_1;
    } else {
	if (vlTOPp->io_input_reset) {
	    vlTOPp->v__DOT__ctrs_1__DOT__FF__DOT__ff 
		= vlTOPp->io_input_starts_1;
	} else {
	    if (vlTOPp->v__DOT__ctrs_1__DOT___T_32) {
		vlTOPp->v__DOT__ctrs_1__DOT__FF__DOT__ff 
		    = (IData)(vlTOPp->v__DOT__ctrs_1__DOT___T_48);
	    }
	}
    }
    // ALWAYS at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:44
    if (vlTOPp->reset) {
	vlTOPp->v__DOT__ctrs_0__DOT__FF__DOT__ff = vlTOPp->io_input_starts_0;
    } else {
	if (vlTOPp->io_input_reset) {
	    vlTOPp->v__DOT__ctrs_0__DOT__FF__DOT__ff 
		= vlTOPp->io_input_starts_0;
	} else {
	    if (vlTOPp->v__DOT__ctrs_0__DOT___T_32) {
		vlTOPp->v__DOT__ctrs_0__DOT__FF__DOT__ff 
		    = (IData)(vlTOPp->v__DOT__ctrs_0__DOT___T_48);
	    }
	}
    }
    // ALWAYS at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:44
    if (vlTOPp->reset) {
	vlTOPp->v__DOT__ctrs_2__DOT__FF__DOT__ff = vlTOPp->io_input_starts_2;
    } else {
	if (vlTOPp->io_input_reset) {
	    vlTOPp->v__DOT__ctrs_2__DOT__FF__DOT__ff 
		= vlTOPp->io_input_starts_2;
	} else {
	    if (vlTOPp->v__DOT__ctrs_2__DOT___T_32) {
		vlTOPp->v__DOT__ctrs_2__DOT__FF__DOT__ff 
		    = (IData)(vlTOPp->v__DOT__ctrs_2__DOT___T_48);
	    }
	}
    }
    vlTOPp->v__DOT__wasDone = __Vdly__v__DOT__wasDone;
}

void VCounter::_combo__TOP__2(VCounter__Syms* __restrict vlSymsp) {
    VL_DEBUG_IF(VL_PRINTF("    VCounter::_combo__TOP__2\n"); );
    VCounter* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    // Body
    vlTOPp->v__DOT__ctrs_2__DOT___T_32 = ((IData)(vlTOPp->io_input_reset) 
					  | (IData)(vlTOPp->io_input_enable));
    vlTOPp->v__DOT__ctrs_1__DOT__FF__DOT___T_19 = ((IData)(vlTOPp->io_input_reset)
						    ? vlTOPp->io_input_starts_1
						    : vlTOPp->v__DOT__ctrs_1__DOT__FF__DOT__ff);
    vlTOPp->v__DOT__ctrs_0__DOT__FF__DOT___T_19 = ((IData)(vlTOPp->io_input_reset)
						    ? vlTOPp->io_input_starts_0
						    : vlTOPp->v__DOT__ctrs_0__DOT__FF__DOT__ff);
    vlTOPp->v__DOT__ctrs_2__DOT__FF__DOT___T_19 = ((IData)(vlTOPp->io_input_reset)
						    ? vlTOPp->io_input_starts_2
						    : vlTOPp->v__DOT__ctrs_2__DOT__FF__DOT__ff);
}

void VCounter::_settle__TOP__3(VCounter__Syms* __restrict vlSymsp) {
    VL_DEBUG_IF(VL_PRINTF("    VCounter::_settle__TOP__3\n"); );
    VCounter* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    // Body
    vlTOPp->v__DOT__ctrs_2__DOT___T_32 = ((IData)(vlTOPp->io_input_reset) 
					  | (IData)(vlTOPp->io_input_enable));
    vlTOPp->v__DOT__ctrs_1__DOT__FF__DOT___T_19 = ((IData)(vlTOPp->io_input_reset)
						    ? vlTOPp->io_input_starts_1
						    : vlTOPp->v__DOT__ctrs_1__DOT__FF__DOT__ff);
    vlTOPp->v__DOT__ctrs_0__DOT__FF__DOT___T_19 = ((IData)(vlTOPp->io_input_reset)
						    ? vlTOPp->io_input_starts_0
						    : vlTOPp->v__DOT__ctrs_0__DOT__FF__DOT__ff);
    vlTOPp->v__DOT__ctrs_2__DOT__FF__DOT___T_19 = ((IData)(vlTOPp->io_input_reset)
						    ? vlTOPp->io_input_starts_2
						    : vlTOPp->v__DOT__ctrs_2__DOT__FF__DOT__ff);
    vlTOPp->io_output_counts_1 = vlTOPp->v__DOT__ctrs_1__DOT__FF__DOT___T_19;
    vlTOPp->v__DOT__ctrs_1__DOT___T_35 = (VL_ULL(0x3ffffffff) 
					  & ((QData)((IData)(vlTOPp->v__DOT__ctrs_1__DOT__FF__DOT___T_19)) 
					     + (QData)((IData)(vlTOPp->io_input_strides_1))));
    vlTOPp->io_output_counts_0 = vlTOPp->v__DOT__ctrs_0__DOT__FF__DOT___T_19;
    vlTOPp->v__DOT__ctrs_0__DOT___T_35 = (VL_ULL(0x3ffffffff) 
					  & ((QData)((IData)(vlTOPp->v__DOT__ctrs_0__DOT__FF__DOT___T_19)) 
					     + (QData)((IData)(vlTOPp->io_input_strides_0))));
    vlTOPp->io_output_counts_2 = vlTOPp->v__DOT__ctrs_2__DOT__FF__DOT___T_19;
    vlTOPp->v__DOT__ctrs_2__DOT___T_35 = (VL_ULL(0x3ffffffff) 
					  & ((QData)((IData)(vlTOPp->v__DOT__ctrs_2__DOT__FF__DOT___T_19)) 
					     + (QData)((IData)(vlTOPp->io_input_strides_2))));
}

void VCounter::_combo__TOP__4(VCounter__Syms* __restrict vlSymsp) {
    VL_DEBUG_IF(VL_PRINTF("    VCounter::_combo__TOP__4\n"); );
    VCounter* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    // Body
    vlTOPp->io_output_counts_1 = vlTOPp->v__DOT__ctrs_1__DOT__FF__DOT___T_19;
    vlTOPp->v__DOT__ctrs_1__DOT___T_35 = (VL_ULL(0x3ffffffff) 
					  & ((QData)((IData)(vlTOPp->v__DOT__ctrs_1__DOT__FF__DOT___T_19)) 
					     + (QData)((IData)(vlTOPp->io_input_strides_1))));
    vlTOPp->io_output_counts_0 = vlTOPp->v__DOT__ctrs_0__DOT__FF__DOT___T_19;
    vlTOPp->v__DOT__ctrs_0__DOT___T_35 = (VL_ULL(0x3ffffffff) 
					  & ((QData)((IData)(vlTOPp->v__DOT__ctrs_0__DOT__FF__DOT___T_19)) 
					     + (QData)((IData)(vlTOPp->io_input_strides_0))));
    vlTOPp->io_output_counts_2 = vlTOPp->v__DOT__ctrs_2__DOT__FF__DOT___T_19;
    vlTOPp->v__DOT__ctrs_2__DOT___T_35 = (VL_ULL(0x3ffffffff) 
					  & ((QData)((IData)(vlTOPp->v__DOT__ctrs_2__DOT__FF__DOT___T_19)) 
					     + (QData)((IData)(vlTOPp->io_input_strides_2))));
    vlTOPp->v__DOT__ctrs_1__DOT___T_39 = ((VL_ULL(0x1ffffffff) 
					   & vlTOPp->v__DOT__ctrs_1__DOT___T_35) 
					  >= (QData)((IData)(vlTOPp->io_input_maxes_1)));
    vlTOPp->v__DOT__ctrs_0__DOT___T_39 = ((VL_ULL(0x1ffffffff) 
					   & vlTOPp->v__DOT__ctrs_0__DOT___T_35) 
					  >= (QData)((IData)(vlTOPp->io_input_maxes_0)));
    vlTOPp->v__DOT__ctrs_2__DOT___T_39 = ((VL_ULL(0x1ffffffff) 
					   & vlTOPp->v__DOT__ctrs_2__DOT___T_35) 
					  >= (QData)((IData)(vlTOPp->io_input_maxes_2)));
}

void VCounter::_settle__TOP__5(VCounter__Syms* __restrict vlSymsp) {
    VL_DEBUG_IF(VL_PRINTF("    VCounter::_settle__TOP__5\n"); );
    VCounter* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    // Body
    vlTOPp->v__DOT__ctrs_1__DOT___T_39 = ((VL_ULL(0x1ffffffff) 
					   & vlTOPp->v__DOT__ctrs_1__DOT___T_35) 
					  >= (QData)((IData)(vlTOPp->io_input_maxes_1)));
    vlTOPp->v__DOT__ctrs_0__DOT___T_39 = ((VL_ULL(0x1ffffffff) 
					   & vlTOPp->v__DOT__ctrs_0__DOT___T_35) 
					  >= (QData)((IData)(vlTOPp->io_input_maxes_0)));
    vlTOPp->v__DOT__ctrs_2__DOT___T_39 = ((VL_ULL(0x1ffffffff) 
					   & vlTOPp->v__DOT__ctrs_2__DOT___T_35) 
					  >= (QData)((IData)(vlTOPp->io_input_maxes_2)));
    vlTOPp->v__DOT__ctrs_0__DOT___T_48 = (VL_ULL(0x1ffffffff) 
					  & ((IData)(vlTOPp->io_input_reset)
					      ? (QData)((IData)(vlTOPp->io_input_starts_0))
					      : ((IData)(vlTOPp->v__DOT__ctrs_0__DOT___T_39)
						  ? (QData)((IData)(
								    ((IData)(vlTOPp->io_input_saturate)
								      ? vlTOPp->v__DOT__ctrs_0__DOT__FF__DOT___T_19
								      : vlTOPp->io_input_starts_0)))
						  : vlTOPp->v__DOT__ctrs_0__DOT___T_35)));
    vlTOPp->v__DOT__ctrs_0__DOT___T_61 = ((IData)(vlTOPp->io_input_saturate) 
					  & (IData)(vlTOPp->v__DOT__ctrs_0__DOT___T_39));
    vlTOPp->v__DOT__ctrs_2__DOT___T_60 = ((IData)(vlTOPp->io_input_enable) 
					  & (IData)(vlTOPp->v__DOT__ctrs_2__DOT___T_39));
}

void VCounter::_combo__TOP__6(VCounter__Syms* __restrict vlSymsp) {
    VL_DEBUG_IF(VL_PRINTF("    VCounter::_combo__TOP__6\n"); );
    VCounter* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    // Body
    vlTOPp->v__DOT__ctrs_0__DOT___T_48 = (VL_ULL(0x1ffffffff) 
					  & ((IData)(vlTOPp->io_input_reset)
					      ? (QData)((IData)(vlTOPp->io_input_starts_0))
					      : ((IData)(vlTOPp->v__DOT__ctrs_0__DOT___T_39)
						  ? (QData)((IData)(
								    ((IData)(vlTOPp->io_input_saturate)
								      ? vlTOPp->v__DOT__ctrs_0__DOT__FF__DOT___T_19
								      : vlTOPp->io_input_starts_0)))
						  : vlTOPp->v__DOT__ctrs_0__DOT___T_35)));
    vlTOPp->v__DOT__ctrs_0__DOT___T_61 = ((IData)(vlTOPp->io_input_saturate) 
					  & (IData)(vlTOPp->v__DOT__ctrs_0__DOT___T_39));
    vlTOPp->v__DOT__ctrs_2__DOT___T_60 = ((IData)(vlTOPp->io_input_enable) 
					  & (IData)(vlTOPp->v__DOT__ctrs_2__DOT___T_39));
    vlTOPp->v__DOT___T_43 = ((IData)(vlTOPp->io_input_saturate) 
			     & (IData)(vlTOPp->v__DOT__ctrs_0__DOT___T_61));
    vlTOPp->v__DOT___T_42 = ((IData)(vlTOPp->v__DOT__ctrs_2__DOT___T_60) 
			     & (IData)(vlTOPp->io_input_enable));
}

void VCounter::_settle__TOP__7(VCounter__Syms* __restrict vlSymsp) {
    VL_DEBUG_IF(VL_PRINTF("    VCounter::_settle__TOP__7\n"); );
    VCounter* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    // Body
    vlTOPp->v__DOT___T_43 = ((IData)(vlTOPp->io_input_saturate) 
			     & (IData)(vlTOPp->v__DOT__ctrs_0__DOT___T_61));
    vlTOPp->v__DOT___T_42 = ((IData)(vlTOPp->v__DOT__ctrs_2__DOT___T_60) 
			     & (IData)(vlTOPp->io_input_enable));
    vlTOPp->v__DOT__ctrs_1__DOT___T_48 = (VL_ULL(0x1ffffffff) 
					  & ((IData)(vlTOPp->io_input_reset)
					      ? (QData)((IData)(vlTOPp->io_input_starts_1))
					      : ((IData)(vlTOPp->v__DOT__ctrs_1__DOT___T_39)
						  ? (QData)((IData)(
								    ((IData)(vlTOPp->v__DOT___T_43)
								      ? vlTOPp->v__DOT__ctrs_1__DOT__FF__DOT___T_19
								      : vlTOPp->io_input_starts_1)))
						  : vlTOPp->v__DOT__ctrs_1__DOT___T_35)));
    vlTOPp->v__DOT___T_44 = ((IData)(vlTOPp->v__DOT__ctrs_0__DOT___T_61) 
			     & ((IData)(vlTOPp->v__DOT___T_43) 
				& (IData)(vlTOPp->v__DOT__ctrs_1__DOT___T_39)));
    vlTOPp->v__DOT__ctrs_1__DOT___T_32 = ((IData)(vlTOPp->io_input_reset) 
					  | (IData)(vlTOPp->v__DOT___T_42));
    vlTOPp->v__DOT__ctrs_1__DOT___T_60 = ((IData)(vlTOPp->v__DOT___T_42) 
					  & (IData)(vlTOPp->v__DOT__ctrs_1__DOT___T_39));
}

void VCounter::_combo__TOP__8(VCounter__Syms* __restrict vlSymsp) {
    VL_DEBUG_IF(VL_PRINTF("    VCounter::_combo__TOP__8\n"); );
    VCounter* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    // Body
    vlTOPp->v__DOT__ctrs_1__DOT___T_48 = (VL_ULL(0x1ffffffff) 
					  & ((IData)(vlTOPp->io_input_reset)
					      ? (QData)((IData)(vlTOPp->io_input_starts_1))
					      : ((IData)(vlTOPp->v__DOT__ctrs_1__DOT___T_39)
						  ? (QData)((IData)(
								    ((IData)(vlTOPp->v__DOT___T_43)
								      ? vlTOPp->v__DOT__ctrs_1__DOT__FF__DOT___T_19
								      : vlTOPp->io_input_starts_1)))
						  : vlTOPp->v__DOT__ctrs_1__DOT___T_35)));
    vlTOPp->v__DOT___T_44 = ((IData)(vlTOPp->v__DOT__ctrs_0__DOT___T_61) 
			     & ((IData)(vlTOPp->v__DOT___T_43) 
				& (IData)(vlTOPp->v__DOT__ctrs_1__DOT___T_39)));
    vlTOPp->v__DOT__ctrs_1__DOT___T_32 = ((IData)(vlTOPp->io_input_reset) 
					  | (IData)(vlTOPp->v__DOT___T_42));
    vlTOPp->v__DOT__ctrs_1__DOT___T_60 = ((IData)(vlTOPp->v__DOT___T_42) 
					  & (IData)(vlTOPp->v__DOT__ctrs_1__DOT___T_39));
    vlTOPp->v__DOT___T_45 = ((IData)(vlTOPp->io_input_saturate) 
			     & (IData)(vlTOPp->v__DOT___T_44));
    vlTOPp->v__DOT___T_41 = ((IData)(vlTOPp->v__DOT__ctrs_1__DOT___T_60) 
			     & (IData)(vlTOPp->io_input_enable));
}

void VCounter::_settle__TOP__9(VCounter__Syms* __restrict vlSymsp) {
    VL_DEBUG_IF(VL_PRINTF("    VCounter::_settle__TOP__9\n"); );
    VCounter* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    // Body
    vlTOPp->v__DOT___T_45 = ((IData)(vlTOPp->io_input_saturate) 
			     & (IData)(vlTOPp->v__DOT___T_44));
    vlTOPp->v__DOT___T_41 = ((IData)(vlTOPp->v__DOT__ctrs_1__DOT___T_60) 
			     & (IData)(vlTOPp->io_input_enable));
    vlTOPp->io_output_saturated = ((IData)(vlTOPp->io_input_saturate) 
				   & ((IData)(vlTOPp->v__DOT___T_44) 
				      & ((IData)(vlTOPp->v__DOT___T_45) 
					 & (IData)(vlTOPp->v__DOT__ctrs_2__DOT___T_39))));
    vlTOPp->v__DOT__ctrs_2__DOT___T_48 = (VL_ULL(0x1ffffffff) 
					  & ((IData)(vlTOPp->io_input_reset)
					      ? (QData)((IData)(vlTOPp->io_input_starts_2))
					      : ((IData)(vlTOPp->v__DOT__ctrs_2__DOT___T_39)
						  ? (QData)((IData)(
								    ((IData)(vlTOPp->v__DOT___T_45)
								      ? vlTOPp->v__DOT__ctrs_2__DOT__FF__DOT___T_19
								      : vlTOPp->io_input_starts_2)))
						  : vlTOPp->v__DOT__ctrs_2__DOT___T_35)));
    vlTOPp->v__DOT__ctrs_0__DOT___T_32 = ((IData)(vlTOPp->io_input_reset) 
					  | (IData)(vlTOPp->v__DOT___T_41));
    vlTOPp->v__DOT__isDone = ((((IData)(vlTOPp->v__DOT___T_41) 
				& (IData)(vlTOPp->v__DOT__ctrs_0__DOT___T_39)) 
			       & (IData)(vlTOPp->v__DOT__ctrs_1__DOT___T_60)) 
			      & (IData)(vlTOPp->v__DOT__ctrs_2__DOT___T_60));
}

void VCounter::_combo__TOP__10(VCounter__Syms* __restrict vlSymsp) {
    VL_DEBUG_IF(VL_PRINTF("    VCounter::_combo__TOP__10\n"); );
    VCounter* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    // Body
    vlTOPp->io_output_saturated = ((IData)(vlTOPp->io_input_saturate) 
				   & ((IData)(vlTOPp->v__DOT___T_44) 
				      & ((IData)(vlTOPp->v__DOT___T_45) 
					 & (IData)(vlTOPp->v__DOT__ctrs_2__DOT___T_39))));
    vlTOPp->v__DOT__ctrs_2__DOT___T_48 = (VL_ULL(0x1ffffffff) 
					  & ((IData)(vlTOPp->io_input_reset)
					      ? (QData)((IData)(vlTOPp->io_input_starts_2))
					      : ((IData)(vlTOPp->v__DOT__ctrs_2__DOT___T_39)
						  ? (QData)((IData)(
								    ((IData)(vlTOPp->v__DOT___T_45)
								      ? vlTOPp->v__DOT__ctrs_2__DOT__FF__DOT___T_19
								      : vlTOPp->io_input_starts_2)))
						  : vlTOPp->v__DOT__ctrs_2__DOT___T_35)));
    vlTOPp->v__DOT__ctrs_0__DOT___T_32 = ((IData)(vlTOPp->io_input_reset) 
					  | (IData)(vlTOPp->v__DOT___T_41));
    vlTOPp->v__DOT__isDone = ((((IData)(vlTOPp->v__DOT___T_41) 
				& (IData)(vlTOPp->v__DOT__ctrs_0__DOT___T_39)) 
			       & (IData)(vlTOPp->v__DOT__ctrs_1__DOT___T_60)) 
			      & (IData)(vlTOPp->v__DOT__ctrs_2__DOT___T_60));
    vlTOPp->io_output_extendedDone = (((IData)(vlTOPp->io_input_enable) 
				       & (IData)(vlTOPp->v__DOT__isDone)) 
				      & (~ (IData)(vlTOPp->v__DOT__wasWasDone)));
    vlTOPp->io_output_done = ((((IData)(vlTOPp->io_input_isStream) 
				| (IData)(vlTOPp->io_input_enable)) 
			       & (IData)(vlTOPp->v__DOT__isDone)) 
			      & (~ (IData)(vlTOPp->v__DOT__wasDone)));
}

void VCounter::_settle__TOP__11(VCounter__Syms* __restrict vlSymsp) {
    VL_DEBUG_IF(VL_PRINTF("    VCounter::_settle__TOP__11\n"); );
    VCounter* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    // Body
    vlTOPp->io_output_extendedDone = (((IData)(vlTOPp->io_input_enable) 
				       & (IData)(vlTOPp->v__DOT__isDone)) 
				      & (~ (IData)(vlTOPp->v__DOT__wasWasDone)));
    vlTOPp->io_output_done = ((((IData)(vlTOPp->io_input_isStream) 
				| (IData)(vlTOPp->io_input_enable)) 
			       & (IData)(vlTOPp->v__DOT__isDone)) 
			      & (~ (IData)(vlTOPp->v__DOT__wasDone)));
}

void VCounter::_eval(VCounter__Syms* __restrict vlSymsp) {
    VL_DEBUG_IF(VL_PRINTF("    VCounter::_eval\n"); );
    VCounter* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    // Body
    if (((IData)(vlTOPp->clock) & (~ (IData)(vlTOPp->__Vclklast__TOP__clock)))) {
	vlTOPp->_sequent__TOP__1(vlSymsp);
	vlTOPp->__Vm_traceActivity = (2 | vlTOPp->__Vm_traceActivity);
    }
    vlTOPp->_combo__TOP__2(vlSymsp);
    vlTOPp->__Vm_traceActivity = (4 | vlTOPp->__Vm_traceActivity);
    vlTOPp->_combo__TOP__4(vlSymsp);
    vlTOPp->_combo__TOP__6(vlSymsp);
    vlTOPp->_combo__TOP__8(vlSymsp);
    vlTOPp->_combo__TOP__10(vlSymsp);
    // Final
    vlTOPp->__Vclklast__TOP__clock = vlTOPp->clock;
}

void VCounter::_eval_initial(VCounter__Syms* __restrict vlSymsp) {
    VL_DEBUG_IF(VL_PRINTF("    VCounter::_eval_initial\n"); );
    VCounter* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
}

void VCounter::final() {
    VL_DEBUG_IF(VL_PRINTF("    VCounter::final\n"); );
    // Variables
    VCounter__Syms* __restrict vlSymsp = this->__VlSymsp;
    VCounter* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
}

void VCounter::_eval_settle(VCounter__Syms* __restrict vlSymsp) {
    VL_DEBUG_IF(VL_PRINTF("    VCounter::_eval_settle\n"); );
    VCounter* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    // Body
    vlTOPp->_settle__TOP__3(vlSymsp);
    vlTOPp->__Vm_traceActivity = (1 | vlTOPp->__Vm_traceActivity);
    vlTOPp->_settle__TOP__5(vlSymsp);
    vlTOPp->_settle__TOP__7(vlSymsp);
    vlTOPp->_settle__TOP__9(vlSymsp);
    vlTOPp->_settle__TOP__11(vlSymsp);
}

IData VCounter::_change_request(VCounter__Syms* __restrict vlSymsp) {
    VL_DEBUG_IF(VL_PRINTF("    VCounter::_change_request\n"); );
    VCounter* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    // Body
    // Change detection
    IData __req = false;  // Logically a bool
    return __req;
}
