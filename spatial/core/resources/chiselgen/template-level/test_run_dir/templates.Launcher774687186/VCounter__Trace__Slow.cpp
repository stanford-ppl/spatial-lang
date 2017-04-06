// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Tracing implementation internals
#include "verilated_vcd_c.h"
#include "VCounter__Syms.h"


//======================

void VCounter::trace (VerilatedVcdC* tfp, int, int) {
    tfp->spTrace()->addCallback (&VCounter::traceInit, &VCounter::traceFull, &VCounter::traceChg, this);
}
void VCounter::traceInit(VerilatedVcd* vcdp, void* userthis, uint32_t code) {
    // Callback from vcd->open()
    VCounter* t=(VCounter*)userthis;
    VCounter__Syms* __restrict vlSymsp = t->__VlSymsp; // Setup global symbol table
    if (!Verilated::calcUnusedSigs()) vl_fatal(__FILE__,__LINE__,__FILE__,"Turning on wave traces requires Verilated::traceEverOn(true) call before time 0.");
    vcdp->scopeEscape(' ');
    t->traceInitThis (vlSymsp, vcdp, code);
    vcdp->scopeEscape('.');
}
void VCounter::traceFull(VerilatedVcd* vcdp, void* userthis, uint32_t code) {
    // Callback from vcd->dump()
    VCounter* t=(VCounter*)userthis;
    VCounter__Syms* __restrict vlSymsp = t->__VlSymsp; // Setup global symbol table
    t->traceFullThis (vlSymsp, vcdp, code);
}

//======================


void VCounter::traceInitThis(VCounter__Syms* __restrict vlSymsp, VerilatedVcd* vcdp, uint32_t code) {
    VCounter* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    int c=code;
    if (0 && vcdp && c) {}  // Prevent unused
    vcdp->module(vlSymsp->name()); // Setup signal names
    // Body
    {
	vlTOPp->traceInitThis__1(vlSymsp, vcdp, code);
    }
}

void VCounter::traceFullThis(VCounter__Syms* __restrict vlSymsp, VerilatedVcd* vcdp, uint32_t code) {
    VCounter* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    int c=code;
    if (0 && vcdp && c) {}  // Prevent unused
    // Body
    {
	vlTOPp->traceFullThis__1(vlSymsp, vcdp, code);
    }
    // Final
    vlTOPp->__Vm_traceActivity = 0;
}

void VCounter::traceInitThis__1(VCounter__Syms* __restrict vlSymsp, VerilatedVcd* vcdp, uint32_t code) {
    VCounter* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    int c=code;
    if (0 && vcdp && c) {}  // Prevent unused
    // Body
    {
	vcdp->declBit  (c+29,"clock",-1);
	vcdp->declBit  (c+30,"reset",-1);
	vcdp->declBus  (c+31,"io_input_starts_0",-1,31,0);
	vcdp->declBus  (c+32,"io_input_starts_1",-1,31,0);
	vcdp->declBus  (c+33,"io_input_starts_2",-1,31,0);
	vcdp->declBus  (c+34,"io_input_maxes_0",-1,31,0);
	vcdp->declBus  (c+35,"io_input_maxes_1",-1,31,0);
	vcdp->declBus  (c+36,"io_input_maxes_2",-1,31,0);
	vcdp->declBus  (c+37,"io_input_strides_0",-1,31,0);
	vcdp->declBus  (c+38,"io_input_strides_1",-1,31,0);
	vcdp->declBus  (c+39,"io_input_strides_2",-1,31,0);
	vcdp->declBus  (c+40,"io_input_gaps_0",-1,31,0);
	vcdp->declBus  (c+41,"io_input_gaps_1",-1,31,0);
	vcdp->declBus  (c+42,"io_input_gaps_2",-1,31,0);
	vcdp->declBit  (c+43,"io_input_reset",-1);
	vcdp->declBit  (c+44,"io_input_enable",-1);
	vcdp->declBit  (c+45,"io_input_saturate",-1);
	vcdp->declBit  (c+46,"io_input_isStream",-1);
	vcdp->declBus  (c+47,"io_output_counts_0",-1,31,0);
	vcdp->declBus  (c+48,"io_output_counts_1",-1,31,0);
	vcdp->declBus  (c+49,"io_output_counts_2",-1,31,0);
	vcdp->declBit  (c+50,"io_output_done",-1);
	vcdp->declBit  (c+51,"io_output_extendedDone",-1);
	vcdp->declBit  (c+52,"io_output_saturated",-1);
	vcdp->declBit  (c+29,"v clock",-1);
	vcdp->declBit  (c+30,"v reset",-1);
	vcdp->declBus  (c+31,"v io_input_starts_0",-1,31,0);
	vcdp->declBus  (c+32,"v io_input_starts_1",-1,31,0);
	vcdp->declBus  (c+33,"v io_input_starts_2",-1,31,0);
	vcdp->declBus  (c+34,"v io_input_maxes_0",-1,31,0);
	vcdp->declBus  (c+35,"v io_input_maxes_1",-1,31,0);
	vcdp->declBus  (c+36,"v io_input_maxes_2",-1,31,0);
	vcdp->declBus  (c+37,"v io_input_strides_0",-1,31,0);
	vcdp->declBus  (c+38,"v io_input_strides_1",-1,31,0);
	vcdp->declBus  (c+39,"v io_input_strides_2",-1,31,0);
	vcdp->declBus  (c+40,"v io_input_gaps_0",-1,31,0);
	vcdp->declBus  (c+41,"v io_input_gaps_1",-1,31,0);
	vcdp->declBus  (c+42,"v io_input_gaps_2",-1,31,0);
	vcdp->declBit  (c+43,"v io_input_reset",-1);
	vcdp->declBit  (c+44,"v io_input_enable",-1);
	vcdp->declBit  (c+45,"v io_input_saturate",-1);
	vcdp->declBit  (c+46,"v io_input_isStream",-1);
	vcdp->declBus  (c+47,"v io_output_counts_0",-1,31,0);
	vcdp->declBus  (c+48,"v io_output_counts_1",-1,31,0);
	vcdp->declBus  (c+49,"v io_output_counts_2",-1,31,0);
	vcdp->declBit  (c+50,"v io_output_done",-1);
	vcdp->declBit  (c+51,"v io_output_extendedDone",-1);
	vcdp->declBit  (c+52,"v io_output_saturated",-1);
	vcdp->declBit  (c+29,"v ctrs_0_clock",-1);
	vcdp->declBit  (c+30,"v ctrs_0_reset",-1);
	vcdp->declBus  (c+31,"v ctrs_0_io_input_start",-1,31,0);
	vcdp->declBus  (c+34,"v ctrs_0_io_input_max",-1,31,0);
	vcdp->declBus  (c+37,"v ctrs_0_io_input_stride",-1,31,0);
	vcdp->declBus  (c+57,"v ctrs_0_io_input_gap",-1,31,0);
	vcdp->declBit  (c+43,"v ctrs_0_io_input_reset",-1);
	vcdp->declBit  (c+3,"v ctrs_0_io_input_enable",-1);
	vcdp->declBit  (c+45,"v ctrs_0_io_input_saturate",-1);
	vcdp->declBus  (c+4,"v ctrs_0_io_output_count_0",-1,31,0);
	vcdp->declBus  (c+53,"v ctrs_0_io_output_countWithoutWrap_0",-1,31,0);
	vcdp->declBit  (c+5,"v ctrs_0_io_output_done",-1);
	vcdp->declBit  (c+1,"v ctrs_0_io_output_extendedDone",-1);
	vcdp->declBit  (c+6,"v ctrs_0_io_output_saturated",-1);
	vcdp->declBit  (c+29,"v ctrs_1_clock",-1);
	vcdp->declBit  (c+30,"v ctrs_1_reset",-1);
	vcdp->declBus  (c+32,"v ctrs_1_io_input_start",-1,31,0);
	vcdp->declBus  (c+35,"v ctrs_1_io_input_max",-1,31,0);
	vcdp->declBus  (c+38,"v ctrs_1_io_input_stride",-1,31,0);
	vcdp->declBus  (c+57,"v ctrs_1_io_input_gap",-1,31,0);
	vcdp->declBit  (c+43,"v ctrs_1_io_input_reset",-1);
	vcdp->declBit  (c+7,"v ctrs_1_io_input_enable",-1);
	vcdp->declBit  (c+8,"v ctrs_1_io_input_saturate",-1);
	vcdp->declBus  (c+9,"v ctrs_1_io_output_count_0",-1,31,0);
	vcdp->declBus  (c+54,"v ctrs_1_io_output_countWithoutWrap_0",-1,31,0);
	vcdp->declBit  (c+10,"v ctrs_1_io_output_done",-1);
	vcdp->declBit  (c+2,"v ctrs_1_io_output_extendedDone",-1);
	vcdp->declBit  (c+11,"v ctrs_1_io_output_saturated",-1);
	vcdp->declBit  (c+29,"v ctrs_2_clock",-1);
	vcdp->declBit  (c+30,"v ctrs_2_reset",-1);
	vcdp->declBus  (c+33,"v ctrs_2_io_input_start",-1,31,0);
	vcdp->declBus  (c+36,"v ctrs_2_io_input_max",-1,31,0);
	vcdp->declBus  (c+39,"v ctrs_2_io_input_stride",-1,31,0);
	vcdp->declBus  (c+57,"v ctrs_2_io_input_gap",-1,31,0);
	vcdp->declBit  (c+43,"v ctrs_2_io_input_reset",-1);
	vcdp->declBit  (c+44,"v ctrs_2_io_input_enable",-1);
	vcdp->declBit  (c+12,"v ctrs_2_io_input_saturate",-1);
	vcdp->declBus  (c+13,"v ctrs_2_io_output_count_0",-1,31,0);
	vcdp->declBus  (c+55,"v ctrs_2_io_output_countWithoutWrap_0",-1,31,0);
	vcdp->declBit  (c+14,"v ctrs_2_io_output_done",-1);
	vcdp->declBit  (c+56,"v ctrs_2_io_output_extendedDone",-1);
	vcdp->declBit  (c+15,"v ctrs_2_io_output_saturated",-1);
	// Tracing: v _T_41 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:254
	// Tracing: v _T_42 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:255
	// Tracing: v _T_43 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:256
	// Tracing: v _T_44 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:257
	// Tracing: v _T_45 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:258
	// Tracing: v _T_46 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:259
	vcdp->declBit  (c+16,"v isDone",-1);
	vcdp->declBit  (c+24,"v wasDone",-1);
	// Tracing: v _GEN_0 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:262
	vcdp->declBit  (c+17,"v isSaturated",-1);
	vcdp->declBit  (c+25,"v wasWasDone",-1);
	// Tracing: v _GEN_1 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:265
	// Tracing: v _T_53 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:266
	// Tracing: v _T_54 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:267
	// Tracing: v _T_55 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:268
	// Tracing: v _T_56 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:269
	// Tracing: v _T_57 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:270
	// Tracing: v _T_58 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:271
	// Tracing: v _T_59 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:272
	// Tracing: v _T_60 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:273
	vcdp->declBit  (c+29,"v ctrs_0 clock",-1);
	vcdp->declBit  (c+30,"v ctrs_0 reset",-1);
	vcdp->declBus  (c+31,"v ctrs_0 io_input_start",-1,31,0);
	vcdp->declBus  (c+34,"v ctrs_0 io_input_max",-1,31,0);
	vcdp->declBus  (c+37,"v ctrs_0 io_input_stride",-1,31,0);
	vcdp->declBus  (c+57,"v ctrs_0 io_input_gap",-1,31,0);
	vcdp->declBit  (c+43,"v ctrs_0 io_input_reset",-1);
	vcdp->declBit  (c+3,"v ctrs_0 io_input_enable",-1);
	vcdp->declBit  (c+45,"v ctrs_0 io_input_saturate",-1);
	vcdp->declBus  (c+4,"v ctrs_0 io_output_count_0",-1,31,0);
	vcdp->declBus  (c+53,"v ctrs_0 io_output_countWithoutWrap_0",-1,31,0);
	vcdp->declBit  (c+5,"v ctrs_0 io_output_done",-1);
	vcdp->declBit  (c+1,"v ctrs_0 io_output_extendedDone",-1);
	vcdp->declBit  (c+6,"v ctrs_0 io_output_saturated",-1);
	vcdp->declBit  (c+29,"v ctrs_0 FF_clock",-1);
	vcdp->declBit  (c+30,"v ctrs_0 FF_reset",-1);
	vcdp->declBus  (c+18,"v ctrs_0 FF_io_input_data",-1,31,0);
	vcdp->declBus  (c+31,"v ctrs_0 FF_io_input_init",-1,31,0);
	vcdp->declBit  (c+19,"v ctrs_0 FF_io_input_enable",-1);
	vcdp->declBit  (c+43,"v ctrs_0 FF_io_input_reset",-1);
	vcdp->declBus  (c+4,"v ctrs_0 FF_io_output_data",-1,31,0);
	// Tracing: v ctrs_0 _T_32 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:81
	// Tracing: v ctrs_0 _T_34 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:82
	// Tracing: v ctrs_0 _GEN_0 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:83
	// Tracing: v ctrs_0 _T_35 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:84
	// Tracing: v ctrs_0 _T_36 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:85
	// Tracing: v ctrs_0 _GEN_1 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:86
	// Tracing: v ctrs_0 _T_37 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:87
	// Tracing: v ctrs_0 _T_38 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:88
	// Tracing: v ctrs_0 _GEN_2 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:89
	// Tracing: v ctrs_0 _T_39 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:90
	// Tracing: v ctrs_0 _T_42 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:91
	// Tracing: v ctrs_0 _GEN_3 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:92
	// Tracing: v ctrs_0 _T_45 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:93
	// Tracing: v ctrs_0 _GEN_5 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:94
	// Tracing: v ctrs_0 _T_46 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:95
	// Tracing: v ctrs_0 _T_47 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:96
	// Tracing: v ctrs_0 _T_48 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:97
	// Tracing: v ctrs_0 _T_50 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:98
	// Tracing: v ctrs_0 _T_51 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:99
	// Tracing: v ctrs_0 _T_52 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:100
	// Tracing: v ctrs_0 _T_54 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:101
	// Tracing: v ctrs_0 _T_55 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:102
	// Tracing: v ctrs_0 _GEN_4 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:103
	// Tracing: v ctrs_0 _T_58 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:104
	// Tracing: v ctrs_0 _T_59 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:105
	// Tracing: v ctrs_0 _T_60 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:106
	// Tracing: v ctrs_0 _T_61 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:107
	// Tracing: v ctrs_0 _T_62 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:108
	// Tracing: v ctrs_0 _T_63 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:109
	// Tracing: v ctrs_0 _T_64 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:110
	vcdp->declBit  (c+29,"v ctrs_0 FF clock",-1);
	vcdp->declBit  (c+30,"v ctrs_0 FF reset",-1);
	vcdp->declBus  (c+18,"v ctrs_0 FF io_input_data",-1,31,0);
	vcdp->declBus  (c+31,"v ctrs_0 FF io_input_init",-1,31,0);
	vcdp->declBit  (c+19,"v ctrs_0 FF io_input_enable",-1);
	vcdp->declBit  (c+43,"v ctrs_0 FF io_input_reset",-1);
	vcdp->declBus  (c+4,"v ctrs_0 FF io_output_data",-1,31,0);
	vcdp->declBus  (c+26,"v ctrs_0 FF ff",-1,31,0);
	// Tracing: v ctrs_0 FF _GEN_0 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:24
	// Tracing: v ctrs_0 FF _T_17 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:25
	// Tracing: v ctrs_0 FF _T_18 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:26
	// Tracing: v ctrs_0 FF _T_19 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:27
	vcdp->declBit  (c+29,"v ctrs_1 clock",-1);
	vcdp->declBit  (c+30,"v ctrs_1 reset",-1);
	vcdp->declBus  (c+32,"v ctrs_1 io_input_start",-1,31,0);
	vcdp->declBus  (c+35,"v ctrs_1 io_input_max",-1,31,0);
	vcdp->declBus  (c+38,"v ctrs_1 io_input_stride",-1,31,0);
	vcdp->declBus  (c+57,"v ctrs_1 io_input_gap",-1,31,0);
	vcdp->declBit  (c+43,"v ctrs_1 io_input_reset",-1);
	vcdp->declBit  (c+7,"v ctrs_1 io_input_enable",-1);
	vcdp->declBit  (c+8,"v ctrs_1 io_input_saturate",-1);
	vcdp->declBus  (c+9,"v ctrs_1 io_output_count_0",-1,31,0);
	vcdp->declBus  (c+54,"v ctrs_1 io_output_countWithoutWrap_0",-1,31,0);
	vcdp->declBit  (c+10,"v ctrs_1 io_output_done",-1);
	vcdp->declBit  (c+2,"v ctrs_1 io_output_extendedDone",-1);
	vcdp->declBit  (c+11,"v ctrs_1 io_output_saturated",-1);
	vcdp->declBit  (c+29,"v ctrs_1 FF_clock",-1);
	vcdp->declBit  (c+30,"v ctrs_1 FF_reset",-1);
	vcdp->declBus  (c+20,"v ctrs_1 FF_io_input_data",-1,31,0);
	vcdp->declBus  (c+32,"v ctrs_1 FF_io_input_init",-1,31,0);
	vcdp->declBit  (c+21,"v ctrs_1 FF_io_input_enable",-1);
	vcdp->declBit  (c+43,"v ctrs_1 FF_io_input_reset",-1);
	vcdp->declBus  (c+9,"v ctrs_1 FF_io_output_data",-1,31,0);
	// Tracing: v ctrs_1 _T_32 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:81
	// Tracing: v ctrs_1 _T_34 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:82
	// Tracing: v ctrs_1 _GEN_0 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:83
	// Tracing: v ctrs_1 _T_35 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:84
	// Tracing: v ctrs_1 _T_36 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:85
	// Tracing: v ctrs_1 _GEN_1 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:86
	// Tracing: v ctrs_1 _T_37 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:87
	// Tracing: v ctrs_1 _T_38 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:88
	// Tracing: v ctrs_1 _GEN_2 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:89
	// Tracing: v ctrs_1 _T_39 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:90
	// Tracing: v ctrs_1 _T_42 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:91
	// Tracing: v ctrs_1 _GEN_3 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:92
	// Tracing: v ctrs_1 _T_45 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:93
	// Tracing: v ctrs_1 _GEN_5 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:94
	// Tracing: v ctrs_1 _T_46 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:95
	// Tracing: v ctrs_1 _T_47 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:96
	// Tracing: v ctrs_1 _T_48 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:97
	// Tracing: v ctrs_1 _T_50 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:98
	// Tracing: v ctrs_1 _T_51 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:99
	// Tracing: v ctrs_1 _T_52 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:100
	// Tracing: v ctrs_1 _T_54 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:101
	// Tracing: v ctrs_1 _T_55 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:102
	// Tracing: v ctrs_1 _GEN_4 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:103
	// Tracing: v ctrs_1 _T_58 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:104
	// Tracing: v ctrs_1 _T_59 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:105
	// Tracing: v ctrs_1 _T_60 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:106
	// Tracing: v ctrs_1 _T_61 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:107
	// Tracing: v ctrs_1 _T_62 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:108
	// Tracing: v ctrs_1 _T_63 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:109
	// Tracing: v ctrs_1 _T_64 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:110
	vcdp->declBit  (c+29,"v ctrs_1 FF clock",-1);
	vcdp->declBit  (c+30,"v ctrs_1 FF reset",-1);
	vcdp->declBus  (c+20,"v ctrs_1 FF io_input_data",-1,31,0);
	vcdp->declBus  (c+32,"v ctrs_1 FF io_input_init",-1,31,0);
	vcdp->declBit  (c+21,"v ctrs_1 FF io_input_enable",-1);
	vcdp->declBit  (c+43,"v ctrs_1 FF io_input_reset",-1);
	vcdp->declBus  (c+9,"v ctrs_1 FF io_output_data",-1,31,0);
	vcdp->declBus  (c+27,"v ctrs_1 FF ff",-1,31,0);
	// Tracing: v ctrs_1 FF _GEN_0 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:24
	// Tracing: v ctrs_1 FF _T_17 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:25
	// Tracing: v ctrs_1 FF _T_18 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:26
	// Tracing: v ctrs_1 FF _T_19 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:27
	vcdp->declBit  (c+29,"v ctrs_2 clock",-1);
	vcdp->declBit  (c+30,"v ctrs_2 reset",-1);
	vcdp->declBus  (c+33,"v ctrs_2 io_input_start",-1,31,0);
	vcdp->declBus  (c+36,"v ctrs_2 io_input_max",-1,31,0);
	vcdp->declBus  (c+39,"v ctrs_2 io_input_stride",-1,31,0);
	vcdp->declBus  (c+57,"v ctrs_2 io_input_gap",-1,31,0);
	vcdp->declBit  (c+43,"v ctrs_2 io_input_reset",-1);
	vcdp->declBit  (c+44,"v ctrs_2 io_input_enable",-1);
	vcdp->declBit  (c+12,"v ctrs_2 io_input_saturate",-1);
	vcdp->declBus  (c+13,"v ctrs_2 io_output_count_0",-1,31,0);
	vcdp->declBus  (c+55,"v ctrs_2 io_output_countWithoutWrap_0",-1,31,0);
	vcdp->declBit  (c+14,"v ctrs_2 io_output_done",-1);
	vcdp->declBit  (c+56,"v ctrs_2 io_output_extendedDone",-1);
	vcdp->declBit  (c+15,"v ctrs_2 io_output_saturated",-1);
	vcdp->declBit  (c+29,"v ctrs_2 FF_clock",-1);
	vcdp->declBit  (c+30,"v ctrs_2 FF_reset",-1);
	vcdp->declBus  (c+22,"v ctrs_2 FF_io_input_data",-1,31,0);
	vcdp->declBus  (c+33,"v ctrs_2 FF_io_input_init",-1,31,0);
	vcdp->declBit  (c+23,"v ctrs_2 FF_io_input_enable",-1);
	vcdp->declBit  (c+43,"v ctrs_2 FF_io_input_reset",-1);
	vcdp->declBus  (c+13,"v ctrs_2 FF_io_output_data",-1,31,0);
	// Tracing: v ctrs_2 _T_32 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:81
	// Tracing: v ctrs_2 _T_34 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:82
	// Tracing: v ctrs_2 _GEN_0 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:83
	// Tracing: v ctrs_2 _T_35 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:84
	// Tracing: v ctrs_2 _T_36 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:85
	// Tracing: v ctrs_2 _GEN_1 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:86
	// Tracing: v ctrs_2 _T_37 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:87
	// Tracing: v ctrs_2 _T_38 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:88
	// Tracing: v ctrs_2 _GEN_2 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:89
	// Tracing: v ctrs_2 _T_39 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:90
	// Tracing: v ctrs_2 _T_42 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:91
	// Tracing: v ctrs_2 _GEN_3 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:92
	// Tracing: v ctrs_2 _T_45 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:93
	// Tracing: v ctrs_2 _GEN_5 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:94
	// Tracing: v ctrs_2 _T_46 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:95
	// Tracing: v ctrs_2 _T_47 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:96
	// Tracing: v ctrs_2 _T_48 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:97
	// Tracing: v ctrs_2 _T_50 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:98
	// Tracing: v ctrs_2 _T_51 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:99
	// Tracing: v ctrs_2 _T_52 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:100
	// Tracing: v ctrs_2 _T_54 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:101
	// Tracing: v ctrs_2 _T_55 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:102
	// Tracing: v ctrs_2 _GEN_4 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:103
	// Tracing: v ctrs_2 _T_58 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:104
	// Tracing: v ctrs_2 _T_59 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:105
	// Tracing: v ctrs_2 _T_60 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:106
	// Tracing: v ctrs_2 _T_61 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:107
	// Tracing: v ctrs_2 _T_62 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:108
	// Tracing: v ctrs_2 _T_63 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:109
	// Tracing: v ctrs_2 _T_64 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:110
	vcdp->declBit  (c+29,"v ctrs_2 FF clock",-1);
	vcdp->declBit  (c+30,"v ctrs_2 FF reset",-1);
	vcdp->declBus  (c+22,"v ctrs_2 FF io_input_data",-1,31,0);
	vcdp->declBus  (c+33,"v ctrs_2 FF io_input_init",-1,31,0);
	vcdp->declBit  (c+23,"v ctrs_2 FF io_input_enable",-1);
	vcdp->declBit  (c+43,"v ctrs_2 FF io_input_reset",-1);
	vcdp->declBus  (c+13,"v ctrs_2 FF io_output_data",-1,31,0);
	vcdp->declBus  (c+28,"v ctrs_2 FF ff",-1,31,0);
	// Tracing: v ctrs_2 FF _GEN_0 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:24
	// Tracing: v ctrs_2 FF _T_17 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:25
	// Tracing: v ctrs_2 FF _T_18 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:26
	// Tracing: v ctrs_2 FF _T_19 // Ignored: Inlined leading underscore at /local/ssd/home/mattfel/macro/spatial/spatial/core/resources/chiselgen/template-level/test_run_dir/templates.Launcher774687186/Counter.v:27
    }
}

void VCounter::traceFullThis__1(VCounter__Syms* __restrict vlSymsp, VerilatedVcd* vcdp, uint32_t code) {
    VCounter* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    int c=code;
    if (0 && vcdp && c) {}  // Prevent unused
    // Body
    {
	vcdp->fullBit  (c+1,((((IData)(vlTOPp->v__DOT___T_41) 
			       | (IData)(vlTOPp->v__DOT__ctrs_0__DOT___T_45)) 
			      & ((IData)(vlTOPp->v__DOT__ctrs_0__DOT___T_39) 
				 | (IData)(vlTOPp->v__DOT__ctrs_0__DOT___T_42)))));
	vcdp->fullBit  (c+2,((((IData)(vlTOPp->v__DOT___T_42) 
			       | (IData)(vlTOPp->v__DOT__ctrs_1__DOT___T_45)) 
			      & ((IData)(vlTOPp->v__DOT__ctrs_1__DOT___T_39) 
				 | (IData)(vlTOPp->v__DOT__ctrs_1__DOT___T_42)))));
	vcdp->fullBit  (c+3,(vlTOPp->v__DOT___T_41));
	vcdp->fullBit  (c+5,(((IData)(vlTOPp->v__DOT___T_41) 
			      & (IData)(vlTOPp->v__DOT__ctrs_0__DOT___T_39))));
	vcdp->fullBit  (c+6,(vlTOPp->v__DOT__ctrs_0__DOT___T_61));
	vcdp->fullBit  (c+7,(vlTOPp->v__DOT___T_42));
	vcdp->fullBit  (c+8,(vlTOPp->v__DOT___T_43));
	vcdp->fullBit  (c+10,(vlTOPp->v__DOT__ctrs_1__DOT___T_60));
	vcdp->fullBit  (c+11,(((IData)(vlTOPp->v__DOT___T_43) 
			       & (IData)(vlTOPp->v__DOT__ctrs_1__DOT___T_39))));
	vcdp->fullBit  (c+12,(vlTOPp->v__DOT___T_45));
	vcdp->fullBit  (c+14,(vlTOPp->v__DOT__ctrs_2__DOT___T_60));
	vcdp->fullBit  (c+15,(((IData)(vlTOPp->v__DOT___T_45) 
			       & (IData)(vlTOPp->v__DOT__ctrs_2__DOT___T_39))));
	vcdp->fullBit  (c+16,(vlTOPp->v__DOT__isDone));
	vcdp->fullBit  (c+17,(((IData)(vlTOPp->v__DOT___T_44) 
			       & ((IData)(vlTOPp->v__DOT___T_45) 
				  & (IData)(vlTOPp->v__DOT__ctrs_2__DOT___T_39)))));
	vcdp->fullBus  (c+18,((IData)(vlTOPp->v__DOT__ctrs_0__DOT___T_48)),32);
	vcdp->fullBit  (c+19,(vlTOPp->v__DOT__ctrs_0__DOT___T_32));
	vcdp->fullBus  (c+4,(vlTOPp->v__DOT__ctrs_0__DOT__FF__DOT___T_19),32);
	vcdp->fullBus  (c+20,((IData)(vlTOPp->v__DOT__ctrs_1__DOT___T_48)),32);
	vcdp->fullBit  (c+21,(vlTOPp->v__DOT__ctrs_1__DOT___T_32));
	vcdp->fullBus  (c+9,(vlTOPp->v__DOT__ctrs_1__DOT__FF__DOT___T_19),32);
	vcdp->fullBus  (c+22,((IData)(vlTOPp->v__DOT__ctrs_2__DOT___T_48)),32);
	vcdp->fullBit  (c+23,(vlTOPp->v__DOT__ctrs_2__DOT___T_32));
	vcdp->fullBus  (c+13,(vlTOPp->v__DOT__ctrs_2__DOT__FF__DOT___T_19),32);
	vcdp->fullBit  (c+24,(vlTOPp->v__DOT__wasDone));
	vcdp->fullBit  (c+25,(vlTOPp->v__DOT__wasWasDone));
	vcdp->fullBus  (c+26,(vlTOPp->v__DOT__ctrs_0__DOT__FF__DOT__ff),32);
	vcdp->fullBus  (c+27,(vlTOPp->v__DOT__ctrs_1__DOT__FF__DOT__ff),32);
	vcdp->fullBus  (c+28,(vlTOPp->v__DOT__ctrs_2__DOT__FF__DOT__ff),32);
	vcdp->fullBus  (c+40,(vlTOPp->io_input_gaps_0),32);
	vcdp->fullBus  (c+41,(vlTOPp->io_input_gaps_1),32);
	vcdp->fullBus  (c+42,(vlTOPp->io_input_gaps_2),32);
	vcdp->fullBit  (c+46,(vlTOPp->io_input_isStream));
	vcdp->fullBus  (c+47,(vlTOPp->io_output_counts_0),32);
	vcdp->fullBus  (c+48,(vlTOPp->io_output_counts_1),32);
	vcdp->fullBus  (c+49,(vlTOPp->io_output_counts_2),32);
	vcdp->fullBit  (c+50,(vlTOPp->io_output_done));
	vcdp->fullBit  (c+51,(vlTOPp->io_output_extendedDone));
	vcdp->fullBit  (c+52,(vlTOPp->io_output_saturated));
	vcdp->fullBus  (c+53,(((0 == vlTOPp->v__DOT__ctrs_0__DOT__FF__DOT___T_19)
			        ? vlTOPp->io_input_maxes_0
			        : vlTOPp->v__DOT__ctrs_0__DOT__FF__DOT___T_19)),32);
	vcdp->fullBus  (c+54,(((0 == vlTOPp->v__DOT__ctrs_1__DOT__FF__DOT___T_19)
			        ? vlTOPp->io_input_maxes_1
			        : vlTOPp->v__DOT__ctrs_1__DOT__FF__DOT___T_19)),32);
	vcdp->fullBus  (c+55,(((0 == vlTOPp->v__DOT__ctrs_2__DOT__FF__DOT___T_19)
			        ? vlTOPp->io_input_maxes_2
			        : vlTOPp->v__DOT__ctrs_2__DOT__FF__DOT___T_19)),32);
	vcdp->fullBit  (c+56,((((IData)(vlTOPp->io_input_enable) 
				| (IData)(vlTOPp->v__DOT__ctrs_2__DOT___T_45)) 
			       & ((IData)(vlTOPp->v__DOT__ctrs_2__DOT___T_39) 
				  | (IData)(vlTOPp->v__DOT__ctrs_2__DOT___T_42)))));
	vcdp->fullBus  (c+34,(vlTOPp->io_input_maxes_0),32);
	vcdp->fullBus  (c+37,(vlTOPp->io_input_strides_0),32);
	vcdp->fullBit  (c+45,(vlTOPp->io_input_saturate));
	vcdp->fullBus  (c+31,(vlTOPp->io_input_starts_0),32);
	vcdp->fullBus  (c+35,(vlTOPp->io_input_maxes_1),32);
	vcdp->fullBus  (c+38,(vlTOPp->io_input_strides_1),32);
	vcdp->fullBus  (c+32,(vlTOPp->io_input_starts_1),32);
	vcdp->fullBus  (c+36,(vlTOPp->io_input_maxes_2),32);
	vcdp->fullBus  (c+39,(vlTOPp->io_input_strides_2),32);
	vcdp->fullBit  (c+44,(vlTOPp->io_input_enable));
	vcdp->fullBit  (c+29,(vlTOPp->clock));
	vcdp->fullBit  (c+30,(vlTOPp->reset));
	vcdp->fullBus  (c+33,(vlTOPp->io_input_starts_2),32);
	vcdp->fullBit  (c+43,(vlTOPp->io_input_reset));
	vcdp->fullBus  (c+57,(0),32);
    }
}
