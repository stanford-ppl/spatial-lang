#include <spawn.h>
#include <unistd.h>
#include <errno.h>
#include <cstring>
#include <string>
#include <cstdlib>
#include <stdio.h>
#include <vector>
#include <queue>
#include <map>
#include <poll.h>
#include <fcntl.h>
#include <signal.h>
#include <sys/mman.h>
#include <sys/prctl.h>

using namespace std;

#include "simDefs.h"
#include "channel.h"

#include "vc_hdrs.h"
#include "svdpi_src.h"

#include <DRAM.h>
#include <Streams.h>

extern char **environ;

// Slave channels from HOST
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

uint64_t numCycles = 0;
queue<simCmd*> pendingOps;

extern "C" {
  // Callback function from SV when there is valid data
  // Currently output stream is always ready, so there is no feedback going from C++ -> SV
  void readOutputStream(int data, int tag, int last) {
    // view addr as uint64_t without doing sign extension
    uint32_t udata = *(uint32_t*)&data;
    uint32_t utag = *(uint32_t*)&tag;
    bool blast = last > 0;

    // Currently just print read data out to console
    outStream->recv(udata, utag, blast);
  }
}

extern "C" {
  // Function is called every clock cycle
  int tick() {
    bool exitTick = false;
    int finishSim = 0;
    numCycles++;

    // Handle pending operations, if any
    if (pendingOps.size() > 0) {
      simCmd *cmd = pendingOps.front();
      pendingOps.pop();

      switch (cmd->cmd) {
        case READ_REG:
          // Construct and send response
          simCmd resp;
          resp.id = cmd->id;
          resp.cmd = cmd->cmd;
          SV_BIT_PACKED_ARRAY(32, rdataHi);
          SV_BIT_PACKED_ARRAY(32, rdataLo);
          readRegRdataHi32((svBitVec32*)&rdataHi);
          readRegRdataLo32((svBitVec32*)&rdataLo);
          *(uint32_t*)resp.data = (uint32_t)*rdataLo;
          *((uint32_t*)resp.data + 1) = (uint32_t)*rdataHi;
          resp.size = sizeof(uint64_t);
          respChannel->send(&resp);
          break;
        default:
          EPRINTF("[SIM] Ignoring unknown pending command %u\n", cmd->cmd);
          break;
      }
      free(cmd);
    }

    // Drain an element from DRAM queue if it exists
    checkAndSendDRAMResponse();

    // Check if input stream has new data
    inStream->send();

    // Handle new incoming operations
    while (!exitTick) {
      simCmd *cmd = (simCmd*) cmdChannel->recv();
      simCmd readResp;
      uint32_t reg = 0;
      uint64_t data = 0;
      switch (cmd->cmd) {
        case MALLOC: {
          size_t size = *(size_t*)cmd->data;
          int fd = open("/dev/zero", O_RDWR);
          void *ptr = mmap(0, size, PROT_READ|PROT_WRITE, MAP_PRIVATE, fd, 0);
          close(fd);

          simCmd resp;
          resp.id = cmd->id;
          resp.cmd = cmd->cmd;
          *(uint64_t*)resp.data = (uint64_t)ptr;
          resp.size = sizeof(size_t);
          EPRINTF("[SIM] MALLOC(%lu), returning %p\n", size, (void*)ptr);
          respChannel->send(&resp);
          break;
        }
        case FREE: {
          void *ptr = (void*)(*(uint64_t*)cmd->data);
          ASSERT(ptr != NULL, "Attempting to call free on null pointer\n");
          EPRINTF("[SIM] FREE(%p)\n", ptr);
          break;
        }
        case MEMCPY_H2D: {
          uint64_t *data = (uint64_t*)cmd->data;
          void *dst = (void*)data[0];
          size_t size = data[1];

          EPRINTF("[SIM] Received memcpy request to %p, size %lu\n", (void*)dst, size);

          // Now to receive 'size' bytes from the cmd stream
          cmdChannel->recvFixedBytes(dst, size);

          // Send ack back indicating end of memcpy
          simCmd resp;
          resp.id = cmd->id;
          resp.cmd = cmd->cmd;
          resp.size = 0;
          respChannel->send(&resp);
          break;
        }
        case MEMCPY_D2H: {
          // Transfer 'size' bytes from src
          uint64_t *data = (uint64_t*)cmd->data;
          void *src = (void*)data[0];
          size_t size = data[1];

          // Now to receive 'size' bytes from the cmd stream
          respChannel->sendFixedBytes(src, size);
          break;
        }
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
          if (!useIdealDRAM) {
            mem->update();
          }
          break;
        case READ_REG: {
            reg = *((uint32_t*)cmd->data);

            // Issue read addr
            readRegRaddr(reg);

            // Append to pending ops - will return in the next cycle
            simCmd *pendingCmd = (simCmd*) malloc(sizeof(simCmd));
            memcpy(pendingCmd, cmd, sizeof(simCmd));
            pendingOps.push(pendingCmd);

            exitTick = true;
            break;
         }
        case WRITE_REG: {
            reg = *((uint32_t*)cmd->data);
            data = *((uint64_t*)((uint32_t*)cmd->data + 1));

            // Perform write
            writeReg(reg, data);
            exitTick = true;
            break;
          }
        case FIN:
          if (!useIdealDRAM) {
            mem->printStats(true);
          }
          finishSim = 1;
          exitTick = true;
          break;
        default:
          break;
      }
    }
    return finishSim;
  }

  void printAllEnv() {
    int tmp = 0;
    while (environ[tmp]) {
      EPRINTF("[SIM] environ[%d] = %s\n", tmp, environ[tmp]);
      tmp++;
    }
  }

  // Called before simulation begins
  void sim_init() {
    EPRINTF("[SIM] Sim process started!\n");
    prctl(PR_SET_PDEATHSIG, SIGHUP);

    /**
     * Slave interface to host {
     */
      // 0. Create Channel structures
      cmdChannel = new Channel(SIM_CMD_FD, -1, sizeof(simCmd));
      respChannel = new Channel(-1, SIM_RESP_FD, sizeof(simCmd));

      // 1. Read command
      simCmd *cmd = (simCmd*) cmdChannel->recv();

      // 2. Send response
      sendResp(cmd);
    /**} End Slave interface to Host */

    initDRAM();

    initStreams();
  }
}
