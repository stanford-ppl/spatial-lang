// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Primary design header
//
// This header should be included by all source files instantiating the design.
// The class here is then constructed to instantiate the design.
// See the Verilator manual for examples.

#ifndef _VTop_H_
#define _VTop_H_

#include "verilated.h"
class VTop__Syms;
class VerilatedVcd;

//----------

VL_MODULE(VTop) {
  public:
    // CELLS
    // Public to allow access to /*verilator_public*/ items;
    // otherwise the application code can consider these internals.
    
    // PORTS
    // The application code writes and reads these signals to
    // propagate new values into/out from the Verilated model.
    VL_IN8(clock,0,0);
    VL_IN8(reset,0,0);
    VL_IN8(io_raddr,1,0);
    VL_IN8(io_wen,0,0);
    VL_IN8(io_waddr,1,0);
    VL_IN8(io_dram_cmd_ready,0,0);
    VL_OUT8(io_dram_cmd_valid,0,0);
    VL_OUT8(io_dram_cmd_bits_isWr,0,0);
    VL_OUT8(io_dram_resp_ready,0,0);
    VL_IN8(io_dram_resp_valid,0,0);
    //char	__VpadToAlign10[2];
    VL_IN(io_wdata,31,0);
    VL_OUT(io_rdata,31,0);
    VL_OUT(io_dram_cmd_bits_addr,31,0);
    VL_OUT(io_dram_cmd_bits_tag,31,0);
    VL_OUT(io_dram_cmd_bits_streamId,31,0);
    VL_OUT(io_dram_cmd_bits_wdata_0,31,0);
    VL_OUT(io_dram_cmd_bits_wdata_1,31,0);
    VL_OUT(io_dram_cmd_bits_wdata_2,31,0);
    VL_OUT(io_dram_cmd_bits_wdata_3,31,0);
    VL_OUT(io_dram_cmd_bits_wdata_4,31,0);
    VL_OUT(io_dram_cmd_bits_wdata_5,31,0);
    VL_OUT(io_dram_cmd_bits_wdata_6,31,0);
    VL_OUT(io_dram_cmd_bits_wdata_7,31,0);
    VL_OUT(io_dram_cmd_bits_wdata_8,31,0);
    VL_OUT(io_dram_cmd_bits_wdata_9,31,0);
    VL_OUT(io_dram_cmd_bits_wdata_10,31,0);
    VL_OUT(io_dram_cmd_bits_wdata_11,31,0);
    VL_OUT(io_dram_cmd_bits_wdata_12,31,0);
    VL_OUT(io_dram_cmd_bits_wdata_13,31,0);
    VL_OUT(io_dram_cmd_bits_wdata_14,31,0);
    VL_OUT(io_dram_cmd_bits_wdata_15,31,0);
    VL_IN(io_dram_resp_bits_rdata_0,31,0);
    VL_IN(io_dram_resp_bits_rdata_1,31,0);
    VL_IN(io_dram_resp_bits_rdata_2,31,0);
    VL_IN(io_dram_resp_bits_rdata_3,31,0);
    VL_IN(io_dram_resp_bits_rdata_4,31,0);
    VL_IN(io_dram_resp_bits_rdata_5,31,0);
    VL_IN(io_dram_resp_bits_rdata_6,31,0);
    VL_IN(io_dram_resp_bits_rdata_7,31,0);
    VL_IN(io_dram_resp_bits_rdata_8,31,0);
    VL_IN(io_dram_resp_bits_rdata_9,31,0);
    VL_IN(io_dram_resp_bits_rdata_10,31,0);
    VL_IN(io_dram_resp_bits_rdata_11,31,0);
    VL_IN(io_dram_resp_bits_rdata_12,31,0);
    VL_IN(io_dram_resp_bits_rdata_13,31,0);
    VL_IN(io_dram_resp_bits_rdata_14,31,0);
    VL_IN(io_dram_resp_bits_rdata_15,31,0);
    VL_IN(io_dram_resp_bits_tag,31,0);
    VL_IN(io_dram_resp_bits_streamId,31,0);
    
    // LOCAL SIGNALS
    // Internals; generally not touched by application code
    VL_SIG8(v__DOT___GEN_0,0,0);
    VL_SIG8(v__DOT__accel__DOT__AccelController_en,0,0);
    VL_SIG8(v__DOT__accel__DOT___T_727,0,0);
    VL_SIG8(v__DOT__accel__DOT___GEN_0,0,0);
    VL_SIG8(v__DOT__accel__DOT___GEN_4,0,0);
    VL_SIG8(v__DOT__accel__DOT___GEN_5,0,0);
    VL_SIG8(v__DOT__accel__DOT__AccelController_sm__DOT___T_36,2,0);
    VL_SIG8(v__DOT__accel__DOT__AccelController_sm__DOT___T_48,0,0);
    VL_SIG8(v__DOT__accel__DOT__AccelController_sm__DOT___T_58,0,0);
    VL_SIG8(v__DOT__accel__DOT__AccelController_sm__DOT___GEN_4,1,0);
    VL_SIG8(v__DOT__accel__DOT__AccelController_sm__DOT___T_73,0,0);
    VL_SIG8(v__DOT__accel__DOT__AccelController_sm__DOT___T_74,0,0);
    VL_SIG8(v__DOT__accel__DOT__AccelController_sm__DOT___T_92,0,0);
    VL_SIG8(v__DOT__accel__DOT__AccelController_sm__DOT___T_93,0,0);
    VL_SIG8(v__DOT__accel__DOT__AccelController_sm__DOT___T_111,0,0);
    VL_SIG8(v__DOT__accel__DOT__AccelController_sm__DOT___GEN_26,0,0);
    VL_SIG8(v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT___T_32,0,0);
    VL_SIG8(v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT___T_42,0,0);
    VL_SIG8(v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT___T_45,0,0);
    VL_SIG8(v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT___GEN_3,0,0);
    VL_SIG8(v__DOT__accel__DOT__done_latch__DOT___T_14,0,0);
    VL_SIG8(v__DOT__accel__DOT__done_latch__DOT___T_22,0,0);
    VL_SIG8(v__DOT__Fringe__DOT___GEN_1,0,0);
    VL_SIG8(v__DOT__Fringe__DOT___GEN_2,0,0);
    VL_SIG8(v__DOT__Fringe__DOT___GEN_3,0,0);
    VL_SIG8(v__DOT__Fringe__DOT__regs__DOT___GEN_0,0,0);
    VL_SIG8(v__DOT__Fringe__DOT__regs__DOT___GEN_1,0,0);
    VL_SIG8(v__DOT__Fringe__DOT__depulser__DOT__r__DOT__ff,0,0);
    VL_SIG8(v__DOT__Fringe__DOT__depulser__DOT__r__DOT___GEN_1,0,0);
    VL_SIG8(v__DOT__Fringe__DOT__mag__DOT___T_602,0,0);
    VL_SIG8(v__DOT__Fringe__DOT__mag__DOT___GEN_2,0,0);
    VL_SIG8(v__DOT__Fringe__DOT__mag__DOT___GEN_3,0,0);
    VL_SIG8(v__DOT__Fringe__DOT__mag__DOT___GEN_4,0,0);
    VL_SIG8(v__DOT__Fringe__DOT__mag__DOT___GEN_5,0,0);
    VL_SIG8(v__DOT__Fringe__DOT__mag__DOT___GEN_6,0,0);
    VL_SIG8(v__DOT__Fringe__DOT__mag__DOT___GEN_7,0,0);
    VL_SIG8(v__DOT__Fringe__DOT__mag__DOT__addrFifo__DOT___GEN_0,0,0);
    VL_SIG8(v__DOT__Fringe__DOT__mag__DOT__addrFifo__DOT___GEN_1,0,0);
    VL_SIG8(v__DOT__Fringe__DOT__mag__DOT__addrFifo__DOT___GEN_2,0,0);
    VL_SIG8(v__DOT__Fringe__DOT__mag__DOT__addrFifo__DOT__tagFF__DOT__ff,0,0);
    VL_SIG8(v__DOT__Fringe__DOT__mag__DOT__addrFifo__DOT__tagFF__DOT___GEN_1,0,0);
    VL_SIG8(v__DOT__Fringe__DOT__mag__DOT__isWrFifo__DOT___GEN_0,0,0);
    VL_SIG8(v__DOT__Fringe__DOT__mag__DOT__isWrFifo__DOT___GEN_1,0,0);
    VL_SIG8(v__DOT__Fringe__DOT__mag__DOT__isWrFifo__DOT___GEN_2,0,0);
    VL_SIG8(v__DOT__Fringe__DOT__mag__DOT__isWrFifo__DOT__tagFF__DOT__ff,0,0);
    VL_SIG8(v__DOT__Fringe__DOT__mag__DOT__isWrFifo__DOT__tagFF__DOT___GEN_1,0,0);
    VL_SIG8(v__DOT__Fringe__DOT__mag__DOT__sizeFifo__DOT___GEN_0,0,0);
    VL_SIG8(v__DOT__Fringe__DOT__mag__DOT__sizeFifo__DOT___GEN_1,0,0);
    VL_SIG8(v__DOT__Fringe__DOT__mag__DOT__sizeFifo__DOT___GEN_2,0,0);
    VL_SIG8(v__DOT__Fringe__DOT__mag__DOT__sizeFifo__DOT__tagFF__DOT__ff,0,0);
    VL_SIG8(v__DOT__Fringe__DOT__mag__DOT__sizeFifo__DOT__tagFF__DOT___GEN_1,0,0);
    VL_SIG8(v__DOT__Fringe__DOT__mag__DOT__dataFifo__DOT___GEN_0,0,0);
    VL_SIG8(v__DOT__Fringe__DOT__mag__DOT__dataFifo__DOT___GEN_1,0,0);
    VL_SIG8(v__DOT__Fringe__DOT__mag__DOT__dataFifo__DOT___GEN_2,0,0);
    VL_SIG8(v__DOT__Fringe__DOT__mag__DOT__dataFifo__DOT__tagFF__DOT__ff,0,0);
    VL_SIG8(v__DOT__Fringe__DOT__mag__DOT__dataFifo__DOT__tagFF__DOT___GEN_1,0,0);
    VL_SIG8(v__DOT__Fringe__DOT__mag__DOT__wrPhase__DOT___T_14,0,0);
    //char	__VpadToAlign229[1];
    VL_SIG16(v__DOT__Fringe__DOT__mag__DOT__burstTagCounter__DOT__reg__024__DOT__ff,10,0);
    VL_SIG16(v__DOT__Fringe__DOT__mag__DOT__burstTagCounter__DOT__reg__024__DOT___GEN_1,10,0);
    //char	__VpadToAlign234[2];
    VL_SIG(v__DOT__accel__DOT__x151_argout,31,0);
    VL_SIG(v__DOT__accel__DOT___GEN_1,31,0);
    VL_SIG(v__DOT__accel__DOT___GEN_2,31,0);
    VL_SIG(v__DOT__accel__DOT___GEN_3,31,0);
    VL_SIG(v__DOT__accel__DOT__AccelController_sm__DOT___T_39,31,0);
    VL_SIG(v__DOT__accel__DOT__AccelController_sm__DOT___GEN_0,31,0);
    VL_SIG(v__DOT__accel__DOT__AccelController_sm__DOT___GEN_6,31,0);
    VL_SIG(v__DOT__accel__DOT__AccelController_sm__DOT___GEN_12,31,0);
    VL_SIG(v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT__FF__DOT__ff,31,0);
    VL_SIG(v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT__FF__DOT___T_19,31,0);
    VL_SIG(v__DOT__Fringe__DOT__regs__DOT___GEN_2,31,0);
    VL_SIG(v__DOT__Fringe__DOT__regs__DOT___GEN_3,31,0);
    VL_SIG(v__DOT__Fringe__DOT__regs__DOT___GEN_4,31,0);
    VL_SIG(v__DOT__Fringe__DOT__regs__DOT___GEN_5,31,0);
    VL_SIG(v__DOT__Fringe__DOT__regs__DOT__regs_0__DOT__ff,31,0);
    VL_SIG(v__DOT__Fringe__DOT__regs__DOT__regs_0__DOT___GEN_1,31,0);
    VL_SIG(v__DOT__Fringe__DOT__regs__DOT__regs_1__DOT__ff,31,0);
    VL_SIG(v__DOT__Fringe__DOT__regs__DOT__regs_1__DOT___GEN_1,31,0);
    VL_SIG(v__DOT__Fringe__DOT__regs__DOT__regs_2__DOT__ff,31,0);
    VL_SIG(v__DOT__Fringe__DOT__regs__DOT__regs_2__DOT___GEN_1,31,0);
    VL_SIG(v__DOT__Fringe__DOT__regs__DOT__regs_3__DOT__ff,31,0);
    VL_SIG(v__DOT__Fringe__DOT__mag__DOT__burstCounter__DOT__reg__024__DOT__ff,31,0);
    VL_SIG(v__DOT__Fringe__DOT__mag__DOT__burstCounter__DOT__reg__024__DOT___GEN_1,31,0);
    VL_SIG64(v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT___T_37,33,0);
    VL_SIG64(v__DOT__accel__DOT__AccelController_sm__DOT__SingleCounter__DOT___T_48,32,0);
    
    // LOCAL VARIABLES
    // Internals; generally not touched by application code
    VL_SIG8(__Vclklast__TOP__clock,0,0);
    //char	__VpadToAlign349[3];
    VL_SIG(__Vm_traceActivity,31,0);
    
    // INTERNAL VARIABLES
    // Internals; generally not touched by application code
    VTop__Syms*	__VlSymsp;		// Symbol table
    
    // PARAMETERS
    // Parameters marked /*verilator public*/ for use by application code
    
    // CONSTRUCTORS
  private:
    VTop& operator= (const VTop&);	///< Copying not allowed
    VTop(const VTop&);	///< Copying not allowed
  public:
    /// Construct the model; called by application code
    /// The special name  may be used to make a wrapper with a
    /// single model invisible WRT DPI scope names.
    VTop(const char* name="TOP");
    /// Destroy the model; called (often implicitly) by application code
    ~VTop();
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
    static void _eval_initial_loop(VTop__Syms* __restrict vlSymsp);
  public:
    void __Vconfigure(VTop__Syms* symsp, bool first);
  private:
    static QData	_change_request(VTop__Syms* __restrict vlSymsp);
  public:
    static void	_combo__TOP__2(VTop__Syms* __restrict vlSymsp);
    static void	_combo__TOP__5(VTop__Syms* __restrict vlSymsp);
    static void	_eval(VTop__Syms* __restrict vlSymsp);
    static void	_eval_initial(VTop__Syms* __restrict vlSymsp);
    static void	_eval_settle(VTop__Syms* __restrict vlSymsp);
    static void	_sequent__TOP__3(VTop__Syms* __restrict vlSymsp);
    static void	_sequent__TOP__6(VTop__Syms* __restrict vlSymsp);
    static void	_sequent__TOP__8(VTop__Syms* __restrict vlSymsp);
    static void	_settle__TOP__1(VTop__Syms* __restrict vlSymsp);
    static void	_settle__TOP__4(VTop__Syms* __restrict vlSymsp);
    static void	_settle__TOP__7(VTop__Syms* __restrict vlSymsp);
    static void	traceChgThis(VTop__Syms* __restrict vlSymsp, VerilatedVcd* vcdp, uint32_t code);
    static void	traceChgThis__2(VTop__Syms* __restrict vlSymsp, VerilatedVcd* vcdp, uint32_t code);
    static void	traceChgThis__3(VTop__Syms* __restrict vlSymsp, VerilatedVcd* vcdp, uint32_t code);
    static void	traceChgThis__4(VTop__Syms* __restrict vlSymsp, VerilatedVcd* vcdp, uint32_t code);
    static void	traceChgThis__5(VTop__Syms* __restrict vlSymsp, VerilatedVcd* vcdp, uint32_t code);
    static void	traceChgThis__6(VTop__Syms* __restrict vlSymsp, VerilatedVcd* vcdp, uint32_t code);
    static void	traceFullThis(VTop__Syms* __restrict vlSymsp, VerilatedVcd* vcdp, uint32_t code);
    static void	traceFullThis__1(VTop__Syms* __restrict vlSymsp, VerilatedVcd* vcdp, uint32_t code);
    static void	traceInitThis(VTop__Syms* __restrict vlSymsp, VerilatedVcd* vcdp, uint32_t code);
    static void	traceInitThis__1(VTop__Syms* __restrict vlSymsp, VerilatedVcd* vcdp, uint32_t code);
    static void traceInit (VerilatedVcd* vcdp, void* userthis, uint32_t code);
    static void traceFull (VerilatedVcd* vcdp, void* userthis, uint32_t code);
    static void traceChg  (VerilatedVcd* vcdp, void* userthis, uint32_t code);
} VL_ATTR_ALIGNED(128);

#endif  /*guard*/
