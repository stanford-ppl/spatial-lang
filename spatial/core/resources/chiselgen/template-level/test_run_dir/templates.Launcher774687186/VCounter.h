// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Primary design header
//
// This header should be included by all source files instantiating the design.
// The class here is then constructed to instantiate the design.
// See the Verilator manual for examples.

#ifndef _VCounter_H_
#define _VCounter_H_

#include "verilated.h"
class VCounter__Syms;
class VerilatedVcd;

//----------

VL_MODULE(VCounter) {
  public:
    // CELLS
    // Public to allow access to /*verilator_public*/ items;
    // otherwise the application code can consider these internals.
    
    // PORTS
    // The application code writes and reads these signals to
    // propagate new values into/out from the Verilated model.
    VL_IN8(clock,0,0);
    VL_IN8(reset,0,0);
    VL_IN8(io_input_reset,0,0);
    VL_IN8(io_input_enable,0,0);
    VL_IN8(io_input_saturate,0,0);
    VL_IN8(io_input_isStream,0,0);
    VL_OUT8(io_output_done,0,0);
    VL_OUT8(io_output_extendedDone,0,0);
    VL_OUT8(io_output_saturated,0,0);
    //char	__VpadToAlign9[3];
    VL_IN(io_input_starts_0,31,0);
    VL_IN(io_input_starts_1,31,0);
    VL_IN(io_input_starts_2,31,0);
    VL_IN(io_input_maxes_0,31,0);
    VL_IN(io_input_maxes_1,31,0);
    VL_IN(io_input_maxes_2,31,0);
    VL_IN(io_input_strides_0,31,0);
    VL_IN(io_input_strides_1,31,0);
    VL_IN(io_input_strides_2,31,0);
    VL_IN(io_input_gaps_0,31,0);
    VL_IN(io_input_gaps_1,31,0);
    VL_IN(io_input_gaps_2,31,0);
    VL_OUT(io_output_counts_0,31,0);
    VL_OUT(io_output_counts_1,31,0);
    VL_OUT(io_output_counts_2,31,0);
    
    // LOCAL SIGNALS
    // Internals; generally not touched by application code
    VL_SIG8(v__DOT___T_41,0,0);
    VL_SIG8(v__DOT___T_42,0,0);
    VL_SIG8(v__DOT___T_43,0,0);
    VL_SIG8(v__DOT___T_44,0,0);
    VL_SIG8(v__DOT___T_45,0,0);
    VL_SIG8(v__DOT__isDone,0,0);
    VL_SIG8(v__DOT__wasDone,0,0);
    VL_SIG8(v__DOT__wasWasDone,0,0);
    VL_SIG8(v__DOT__ctrs_0__DOT___T_32,0,0);
    VL_SIG8(v__DOT__ctrs_0__DOT___T_39,0,0);
    VL_SIG8(v__DOT__ctrs_0__DOT___T_42,0,0);
    VL_SIG8(v__DOT__ctrs_0__DOT___T_45,0,0);
    VL_SIG8(v__DOT__ctrs_0__DOT___T_61,0,0);
    VL_SIG8(v__DOT__ctrs_1__DOT___T_32,0,0);
    VL_SIG8(v__DOT__ctrs_1__DOT___T_39,0,0);
    VL_SIG8(v__DOT__ctrs_1__DOT___T_42,0,0);
    VL_SIG8(v__DOT__ctrs_1__DOT___T_45,0,0);
    VL_SIG8(v__DOT__ctrs_1__DOT___T_60,0,0);
    VL_SIG8(v__DOT__ctrs_2__DOT___T_32,0,0);
    VL_SIG8(v__DOT__ctrs_2__DOT___T_39,0,0);
    VL_SIG8(v__DOT__ctrs_2__DOT___T_42,0,0);
    VL_SIG8(v__DOT__ctrs_2__DOT___T_45,0,0);
    VL_SIG8(v__DOT__ctrs_2__DOT___T_60,0,0);
    //char	__VpadToAlign99[1];
    VL_SIG(v__DOT__ctrs_0__DOT__FF__DOT__ff,31,0);
    VL_SIG(v__DOT__ctrs_0__DOT__FF__DOT___T_19,31,0);
    VL_SIG(v__DOT__ctrs_1__DOT__FF__DOT__ff,31,0);
    VL_SIG(v__DOT__ctrs_1__DOT__FF__DOT___T_19,31,0);
    VL_SIG(v__DOT__ctrs_2__DOT__FF__DOT__ff,31,0);
    VL_SIG(v__DOT__ctrs_2__DOT__FF__DOT___T_19,31,0);
    //char	__VpadToAlign124[4];
    VL_SIG64(v__DOT__ctrs_0__DOT___T_35,33,0);
    VL_SIG64(v__DOT__ctrs_0__DOT___T_48,32,0);
    VL_SIG64(v__DOT__ctrs_1__DOT___T_35,33,0);
    VL_SIG64(v__DOT__ctrs_1__DOT___T_48,32,0);
    VL_SIG64(v__DOT__ctrs_2__DOT___T_35,33,0);
    VL_SIG64(v__DOT__ctrs_2__DOT___T_48,32,0);
    
    // LOCAL VARIABLES
    // Internals; generally not touched by application code
    VL_SIG8(__Vclklast__TOP__clock,0,0);
    //char	__VpadToAlign181[3];
    VL_SIG(__Vm_traceActivity,31,0);
    
    // INTERNAL VARIABLES
    // Internals; generally not touched by application code
    VCounter__Syms*	__VlSymsp;		// Symbol table
    
    // PARAMETERS
    // Parameters marked /*verilator public*/ for use by application code
    
    // CONSTRUCTORS
  private:
    VCounter& operator= (const VCounter&);	///< Copying not allowed
    VCounter(const VCounter&);	///< Copying not allowed
  public:
    /// Construct the model; called by application code
    /// The special name  may be used to make a wrapper with a
    /// single model invisible WRT DPI scope names.
    VCounter(const char* name="TOP");
    /// Destroy the model; called (often implicitly) by application code
    ~VCounter();
    /// Trace signals in the model; called by application code
    void trace (VerilatedVcdC* tfp, int levels, int options=0);
    
    // USER METHODS
    
    // API METHODS
    /// Evaluate the model.  Application must call when inputs change.
    void eval();
    /// Simulation complete, run final blocks.  Application must call on completion.
    void final();
    
    // INTERNAL METHODS
  private:
    static void _eval_initial_loop(VCounter__Syms* __restrict vlSymsp);
  public:
    void __Vconfigure(VCounter__Syms* symsp, bool first);
  private:
    static IData	_change_request(VCounter__Syms* __restrict vlSymsp);
  public:
    static void	_combo__TOP__10(VCounter__Syms* __restrict vlSymsp);
    static void	_combo__TOP__2(VCounter__Syms* __restrict vlSymsp);
    static void	_combo__TOP__4(VCounter__Syms* __restrict vlSymsp);
    static void	_combo__TOP__6(VCounter__Syms* __restrict vlSymsp);
    static void	_combo__TOP__8(VCounter__Syms* __restrict vlSymsp);
    static void	_eval(VCounter__Syms* __restrict vlSymsp);
    static void	_eval_initial(VCounter__Syms* __restrict vlSymsp);
    static void	_eval_settle(VCounter__Syms* __restrict vlSymsp);
    static void	_sequent__TOP__1(VCounter__Syms* __restrict vlSymsp);
    static void	_settle__TOP__11(VCounter__Syms* __restrict vlSymsp);
    static void	_settle__TOP__3(VCounter__Syms* __restrict vlSymsp);
    static void	_settle__TOP__5(VCounter__Syms* __restrict vlSymsp);
    static void	_settle__TOP__7(VCounter__Syms* __restrict vlSymsp);
    static void	_settle__TOP__9(VCounter__Syms* __restrict vlSymsp);
    static void	traceChgThis(VCounter__Syms* __restrict vlSymsp, VerilatedVcd* vcdp, uint32_t code);
    static void	traceChgThis__2(VCounter__Syms* __restrict vlSymsp, VerilatedVcd* vcdp, uint32_t code);
    static void	traceChgThis__3(VCounter__Syms* __restrict vlSymsp, VerilatedVcd* vcdp, uint32_t code);
    static void	traceChgThis__4(VCounter__Syms* __restrict vlSymsp, VerilatedVcd* vcdp, uint32_t code);
    static void	traceChgThis__5(VCounter__Syms* __restrict vlSymsp, VerilatedVcd* vcdp, uint32_t code);
    static void	traceFullThis(VCounter__Syms* __restrict vlSymsp, VerilatedVcd* vcdp, uint32_t code);
    static void	traceFullThis__1(VCounter__Syms* __restrict vlSymsp, VerilatedVcd* vcdp, uint32_t code);
    static void	traceInitThis(VCounter__Syms* __restrict vlSymsp, VerilatedVcd* vcdp, uint32_t code);
    static void	traceInitThis__1(VCounter__Syms* __restrict vlSymsp, VerilatedVcd* vcdp, uint32_t code);
    static void traceInit (VerilatedVcd* vcdp, void* userthis, uint32_t code);
    static void traceFull (VerilatedVcd* vcdp, void* userthis, uint32_t code);
    static void traceChg  (VerilatedVcd* vcdp, void* userthis, uint32_t code);
} VL_ATTR_ALIGNED(128);

#endif  /*guard*/
