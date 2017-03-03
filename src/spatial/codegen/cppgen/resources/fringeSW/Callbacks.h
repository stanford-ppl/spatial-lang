#ifndef __CALLBACKS_H__
#define __CALLBACKS_H__

#include "DUT.h"
#include "PeekPokeTester.h"

// Callback function when a valid DRAM request is seen
void handleDRAMRequest(DUT *dut, PeekPokeTester *tester) {
  std::cout << "DRAM request detected:" << std::endl;
  uint64_t addr = tester->peek(&(dut->io_dram_cmd_bits_addr));
  uint64_t tag = tester->peek(&(dut->io_dram_cmd_bits_tag));
  uint64_t isWr = tester->peek(&(dut->io_dram_cmd_bits_isWr));
  uint32_t streamId = (tag >> 0) & 3;
  uint32_t base = tester->getMemStream(streamId);
  std::cout << "addr: " << addr << ", tag: " << tag << ", (id = " << streamId << ", base = " << base << "), isWr: " << isWr << std::endl;

  // Note: addr must have been allocated previously, else will cause segfault
  // Note: Currently assumes burst size to be 64 bytes
  if (isWr) {
    // Write request: Update 1 burst-length bytes at *addr
    uint32_t *waddr = (uint32_t*) addr;
    waddr[0] = tester->peek(&(dut->io_dram_cmd_bits_wdata_0));
    waddr[1] = tester->peek(&(dut->io_dram_cmd_bits_wdata_1));
    waddr[2] = tester->peek(&(dut->io_dram_cmd_bits_wdata_2));
    waddr[3] = tester->peek(&(dut->io_dram_cmd_bits_wdata_3));
    waddr[4] = tester->peek(&(dut->io_dram_cmd_bits_wdata_4));
    waddr[5] = tester->peek(&(dut->io_dram_cmd_bits_wdata_5));
    waddr[6] = tester->peek(&(dut->io_dram_cmd_bits_wdata_6));
    waddr[7] = tester->peek(&(dut->io_dram_cmd_bits_wdata_7));
    waddr[8] = tester->peek(&(dut->io_dram_cmd_bits_wdata_8));
    waddr[9] = tester->peek(&(dut->io_dram_cmd_bits_wdata_9));
    waddr[10] = tester->peek(&(dut->io_dram_cmd_bits_wdata_10));
    waddr[11] = tester->peek(&(dut->io_dram_cmd_bits_wdata_11));
    waddr[12] = tester->peek(&(dut->io_dram_cmd_bits_wdata_12));
    waddr[13] = tester->peek(&(dut->io_dram_cmd_bits_wdata_13));
    waddr[14] = tester->peek(&(dut->io_dram_cmd_bits_wdata_14));
    waddr[15] = tester->peek(&(dut->io_dram_cmd_bits_wdata_15));
  } else {
    // Read request: Read burst-length bytes at *addr
    uint32_t *raddr = (uint32_t*) addr;
    // tester->poke(&(dut->io_dram_resp_bits_rdata_0), *(raddr+base));
    // tester->poke(&(dut->io_dram_resp_bits_rdata_1), *(raddr+1);
    // tester->poke(&(dut->io_dram_resp_bits_rdata_2), raddr);
    // tester->poke(&(dut->io_dram_resp_bits_rdata_3), raddr);
    // tester->poke(&(dut->io_dram_resp_bits_rdata_4), raddr);
    // tester->poke(&(dut->io_dram_resp_bits_rdata_5), raddr);
    // tester->poke(&(dut->io_dram_resp_bits_rdata_6), raddr);
    // tester->poke(&(dut->io_dram_resp_bits_rdata_7), raddr);
    // tester->poke(&(dut->io_dram_resp_bits_rdata_8), raddr);
    // tester->poke(&(dut->io_dram_resp_bits_rdata_9), raddr);
    // tester->poke(&(dut->io_dram_resp_bits_rdata_10), raddr);
    // tester->poke(&(dut->io_dram_resp_bits_rdata_11), raddr);
    // tester->poke(&(dut->io_dram_resp_bits_rdata_12), raddr);
    // tester->poke(&(dut->io_dram_resp_bits_rdata_13), raddr);
    // tester->poke(&(dut->io_dram_resp_bits_rdata_14), raddr);
    // tester->poke(&(dut->io_dram_resp_bits_rdata_15), raddr);
  }

  // Common part of response
  tester->poke(&(dut->io_dram_resp_bits_tag), tag);
  tester->poke(&(dut->io_dram_resp_valid), 1);
  tester->step(1);
  tester->poke(&(dut->io_dram_resp_valid), 0);
}

#endif
