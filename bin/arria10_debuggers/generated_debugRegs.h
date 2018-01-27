
  #ifndef __DEBUG_REGS_H__
  #define __DEBUG_REGS_H__

  #define NUM_DEBUG_SIGNALS 59

  const char *signalLabels[] = {
  
"Cycles (0)", "rdata_from_dram0_0 (1)", "rdata_from_dram0_1 (2)", "rdata_from_dram0_2 (3)", "rdata_from_dram0_3 (4)", "rdata_from_dram0_4 (5)", "rdata_from_dram0_5 (6)", "rdata_from_dram0_6 (7)", "rdata_from_dram0_7 (8)", "rdata_from_dram0_8 (9)", "rdata_from_dram0_9 (10)", "rdata_from_dram0_10 (11)", "rdata_from_dram0_11 (12)", "rdata_from_dram0_12 (13)", "rdata_from_dram0_13 (14)", "rdata_from_dram0_14 (15)", "rdata_from_dram0_15 (16)", "rdata_from_dram1_0 (17)", "rdata_from_dram1_1 (18)", "rdata_from_dram1_2 (19)", "rdata_from_dram1_3 (20)", "rdata_from_dram1_4 (21)", "rdata_from_dram1_5 (22)", "rdata_from_dram1_6 (23)", "rdata_from_dram1_7 (24)", "rdata_from_dram1_8 (25)", "rdata_from_dram1_9 (26)", "rdata_from_dram1_10 (27)", "rdata_from_dram1_11 (28)", "rdata_from_dram1_12 (29)", "rdata_from_dram1_13 (30)", "rdata_from_dram1_14 (31)", "rdata_from_dram1_15 (32)", "Num DRAM Commands (33)", "Total gaps in issue (34)", "Read Commands (35)", "Write Commands (36)", "cmdloadstream 0 (37)", "cmdstorestream 1 (38)", "LoadCmds from Accel (valid) 0 (39)", "LoadCmds from Accel (valid & ready) 0 (40)", "StoreCmds from Accel (valid) 0 (41)", "StoreCmds from Accel (valid & ready) 0 (42)", "First store cmd addr 0 (43)", "Last store cmd addr 0 (44)", "Num DRAM Responses (45)", "Total gaps in read responses (46)", "Total gaps in read responses (rresp.valid & ~rresp.ready) (47)", "Total gaps in read responses (~rresp.valid & rresp.ready) (48)", "Total gaps in read responses (~rresp.valid & ~rresp.ready) (49)", "resp loadstream 0 (50)", "resp storestream 1 (51)", "RResp valid enqueued somewhere (52)", "Rresp valid and ready (53)", "Resp valid and ready and enqueued somewhere (54)", "Resp valid and not ready (55)", "rdataFifo 0 enq (56)", "wrespFifo 0 enq (57)", "num wdata transferred (wvalid & wready) (58)"
};
#endif // __DEBUG_REGS_H__
