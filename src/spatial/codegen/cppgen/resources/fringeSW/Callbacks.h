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
  std::cout << "addr: " << addr << ", tag: " << tag << ", isWr: " << isWr << std::endl;

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
    tester->poke(&(dut->io_dram_resp_bits_rdata_0), raddr[0 + 8]); // First 8 words are header
    tester->poke(&(dut->io_dram_resp_bits_rdata_1), raddr[1 + 8]); // First 8 words are header
    tester->poke(&(dut->io_dram_resp_bits_rdata_2), raddr[2 + 8]); // First 8 words are header
    tester->poke(&(dut->io_dram_resp_bits_rdata_3), raddr[3 + 8]); // First 8 words are header
    tester->poke(&(dut->io_dram_resp_bits_rdata_4), raddr[4 + 8]); // First 8 words are header
    tester->poke(&(dut->io_dram_resp_bits_rdata_5), raddr[5 + 8]); // First 8 words are header
    tester->poke(&(dut->io_dram_resp_bits_rdata_6), raddr[6 + 8]); // First 8 words are header
    tester->poke(&(dut->io_dram_resp_bits_rdata_7), raddr[7 + 8]); // First 8 words are header
    tester->poke(&(dut->io_dram_resp_bits_rdata_8), raddr[8 + 8]); // First 8 words are header
    tester->poke(&(dut->io_dram_resp_bits_rdata_9), raddr[9 + 8]); // First 8 words are header
    tester->poke(&(dut->io_dram_resp_bits_rdata_10), raddr[10 + 8]); // First 8 words are header
    tester->poke(&(dut->io_dram_resp_bits_rdata_11), raddr[11 + 8]); // First 8 words are header
    tester->poke(&(dut->io_dram_resp_bits_rdata_12), raddr[12 + 8]); // First 8 words are header
    tester->poke(&(dut->io_dram_resp_bits_rdata_13), raddr[13 + 8]); // First 8 words are header
    tester->poke(&(dut->io_dram_resp_bits_rdata_14), raddr[14 + 8]); // First 8 words are header
    tester->poke(&(dut->io_dram_resp_bits_rdata_15), raddr[15 + 8]); // First 8 words are header
  }

  // Common part of response
  tester->poke(&(dut->io_dram_resp_bits_tag), tag);
  tester->poke(&(dut->io_dram_resp_valid), 1);
  tester->step(1);
  tester->poke(&(dut->io_dram_resp_valid), 0);
}

#endif
