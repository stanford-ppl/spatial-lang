#include <unistd.h>
#include <errno.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <vector>
#include <poll.h>
#include <fcntl.h>

using namespace std;

#include "commonDefs.h"
#include "channel.h"
//#include "svdpi.h"
//#include "svImports.h"
#include "vc_hdrs.h"
#include "svdpi_src.h"

Channel *cmdChannel = NULL;
Channel *respChannel = NULL;

int sendResp(simCmd *cmd) {
	simCmd resp;
  resp.id = cmd->id;
  resp.cmd = cmd->cmd;
  resp.size = cmd->size;
	switch (cmd->cmd) {
		case READY:
			resp.size = 0;
			break;
		default:
			EPRINTF("[SIM] Command %d not supported!\n", cmd->cmd);
			exit(-1);
	}

	respChannel->send(&resp);
	return cmd->id;
}

int numCycles = 0;

extern "C" {
  // Function is called every clock cycle
  int tick() {
    bool exitTick = false;
    int finishSim = 0;
    numCycles++;
    while (!exitTick) {
      simCmd *cmd = cmdChannel->recv();
      simCmd readResp;
      uint32_t reg = 0, data = 0;
      switch (cmd->cmd) {
        case RESET:
          rst();
          exitTick = true;
          break;
        case START:
          start();
          exitTick = true;
          break;
        case STEP:
          exitTick = true;
          break;
        case READ_REG:
          reg = *((uint32_t*)cmd->data);
          EPRINTF("[SIM] READ_REG called for reg = %u\n", reg);

          // Perform read
          SV_BIT_PACKED_ARRAY(32, rdata);
          readReg(reg, (svBitVec32*)&rdata);

          // Construct and send response
          readResp.id = cmd->id;
          readResp.cmd = cmd->cmd;
          *(uint32_t*)readResp.data = (uint32_t)*rdata;
          readResp.size = sizeof(uint32_t);
          respChannel->send(&readResp);
          exitTick = true;
          break;
        case WRITE_REG:
          reg = *((uint32_t*)cmd->data);
          data = *((uint32_t*)cmd->data + 1);
          EPRINTF("[SIM] WRITE_REG called for reg = %u, data = %u\n", reg, data);
          // Perform write
          writeReg(reg, (svBitVecVal)data);
          exitTick = true;
          break;
        case FIN:
          finishSim = 1;
          exitTick = true;
          break;
        default:
          break;
      }
    }
    return finishSim;
  }

  // Called before simulation begins
  void sim_init() {
    EPRINTF("[SIM] Sim process started!\n");

    // 0. Create Channel structures
    cmdChannel = new Channel(SIM_CMD_FD, -1);
    respChannel = new Channel(-1, SIM_RESP_FD);

    // 1. Read command
    simCmd *cmd = cmdChannel->recv();
    EPRINTF("[SIM] Received:\n");
    cmdChannel->printPkt(cmd);

    // 2. Send response
    sendResp(cmd);
  }
}
