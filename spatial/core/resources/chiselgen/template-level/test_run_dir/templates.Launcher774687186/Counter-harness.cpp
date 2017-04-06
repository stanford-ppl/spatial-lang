#include "VCounter.h"
#include "verilated.h"
#include "veri_api.h"
#if VM_TRACE
#include "verilated_vcd_c.h"
#endif
#include <iostream>
class Counter_api_t: public sim_api_t<VerilatorDataWrapper*> {
public:
    Counter_api_t(VCounter* _dut) {
        dut = _dut;
        main_time = 0L;
        is_exit = false;
#if VM_TRACE
        tfp = NULL;
#endif
    }
    void init_sim_data() {
        sim_data.inputs.clear();
        sim_data.outputs.clear();
        sim_data.signals.clear();
        sim_data.inputs.push_back(new VerilatorCData(&(dut->io_input_isStream)));
        sim_data.inputs.push_back(new VerilatorCData(&(dut->io_input_saturate)));
        sim_data.inputs.push_back(new VerilatorCData(&(dut->io_input_enable)));
        sim_data.inputs.push_back(new VerilatorCData(&(dut->io_input_reset)));
        sim_data.inputs.push_back(new VerilatorIData(&(dut->io_input_gaps_0)));
        sim_data.inputs.push_back(new VerilatorIData(&(dut->io_input_gaps_1)));
        sim_data.inputs.push_back(new VerilatorIData(&(dut->io_input_gaps_2)));
        sim_data.inputs.push_back(new VerilatorIData(&(dut->io_input_strides_0)));
        sim_data.inputs.push_back(new VerilatorIData(&(dut->io_input_strides_1)));
        sim_data.inputs.push_back(new VerilatorIData(&(dut->io_input_strides_2)));
        sim_data.inputs.push_back(new VerilatorIData(&(dut->io_input_maxes_0)));
        sim_data.inputs.push_back(new VerilatorIData(&(dut->io_input_maxes_1)));
        sim_data.inputs.push_back(new VerilatorIData(&(dut->io_input_maxes_2)));
        sim_data.inputs.push_back(new VerilatorIData(&(dut->io_input_starts_0)));
        sim_data.inputs.push_back(new VerilatorIData(&(dut->io_input_starts_1)));
        sim_data.inputs.push_back(new VerilatorIData(&(dut->io_input_starts_2)));
        sim_data.outputs.push_back(new VerilatorCData(&(dut->io_output_saturated)));
        sim_data.outputs.push_back(new VerilatorCData(&(dut->io_output_extendedDone)));
        sim_data.outputs.push_back(new VerilatorCData(&(dut->io_output_done)));
        sim_data.outputs.push_back(new VerilatorIData(&(dut->io_output_counts_0)));
        sim_data.outputs.push_back(new VerilatorIData(&(dut->io_output_counts_1)));
        sim_data.outputs.push_back(new VerilatorIData(&(dut->io_output_counts_2)));
        sim_data.signals.push_back(new VerilatorCData(&(dut->reset)));
        sim_data.signal_map["Counter.reset"] = 0;
    }
#if VM_TRACE
     void init_dump(VerilatedVcdC* _tfp) { tfp = _tfp; }
#endif
    inline bool exit() { return is_exit; }
    virtual inline double get_time_stamp() {
        return main_time;
    }
private:
    VCounter* dut;
    bool is_exit;
    vluint64_t main_time;
#if VM_TRACE
    VerilatedVcdC* tfp;
#endif
    virtual inline size_t put_value(VerilatorDataWrapper* &sig, uint64_t* data, bool force=false) {
        return sig->put_value(data);
    }
    virtual inline size_t get_value(VerilatorDataWrapper* &sig, uint64_t* data) {
        return sig->get_value(data);
    }
    virtual inline size_t get_chunk(VerilatorDataWrapper* &sig) {
        return sig->get_num_words();
    } 
    virtual inline void reset() {
        dut->reset = 1;
        step();
    }
    virtual inline void start() {
        dut->reset = 0;
    }
    virtual inline void finish() {
        dut->eval();
        is_exit = true;
    }
    virtual inline void step() {
        dut->clock = 0;
        dut->eval();
#if VM_TRACE
        if (tfp) tfp->dump(main_time);
#endif
        main_time++;
        dut->clock = 1;
        dut->eval();
#if VM_TRACE
        if (tfp) tfp->dump(main_time);
#endif
        main_time++;
    }
    virtual inline void update() {
        dut->_eval_settle(dut->__VlSymsp);
    }
};
static Counter_api_t * _Top_api;
double sc_time_stamp () { return _Top_api->get_time_stamp(); }
int main(int argc, char **argv, char **env) {
    Verilated::commandArgs(argc, argv);
    VCounter* top = new VCounter;
    std::string vcdfile = "test_run_dir/templates.Launcher774687186/Counter.vcd";
    std::vector<std::string> args(argv+1, argv+argc);
    std::vector<std::string>::const_iterator it;
    for (it = args.begin() ; it != args.end() ; it++) {
      if (it->find("+waveform=") == 0) vcdfile = it->c_str()+10;
    }
#if VM_TRACE
    Verilated::traceEverOn(true);
    VL_PRINTF("Enabling waves..");
    VerilatedVcdC* tfp = new VerilatedVcdC;
    top->trace(tfp, 99);
    tfp->open(vcdfile.c_str());
#endif
    Counter_api_t api(top);
    _Top_api = &api; /* required for sc_time_stamp() */
    api.init_sim_data();
    api.init_channels();
#if VM_TRACE
    api.init_dump(tfp);
#endif
    while(!api.exit()) api.tick();
#if VM_TRACE
    if (tfp) tfp->close();
    delete tfp;
#endif
    delete top;
    exit(0);
}
