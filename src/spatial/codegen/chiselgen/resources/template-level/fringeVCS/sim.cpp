#include <unistd.h>
#include <errno.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <vector>
#include <queue>
#include <poll.h>
#include <fcntl.h>
#include <sys/mman.h>

using namespace std;

#include "simDefs.h"
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
queue<simCmd*> pendingOps;

class DRAMRequest {
public:
  uint64_t addr;
  uint64_t tag;
  bool isWr;
  uint32_t *wdata;
  uint32_t delay;
  uint32_t elapsed;

  DRAMRequest(uint64_t a, uint64_t t, bool wr, uint32_t *wd) {
    addr = a;
    tag = t;
    isWr = wr;
    if (isWr) {
      wdata = (uint32_t*) malloc(16 * sizeof(uint32_t));
      for (int i=0; i<16; i++) {
        wdata[i] = wd[i];
      }
    } else {
      wdata = NULL;
    }

    delay = abs(rand()) % 150 + 50;
    elapsed = 0;
  }

  void print() {
    EPRINTF("---- DRAM REQ ----\n");
    EPRINTF("addr : %lx\n", addr);
    EPRINTF("tag  : %lx\n", tag);
    EPRINTF("isWr : %lx\n", isWr);
    EPRINTF("delay: %u\n", delay);
    if (isWr) {
      EPRINTF("wdata0 : %u\n", wdata[0]);
      EPRINTF("wdata1 : %u\n", wdata[1]);
      EPRINTF("wdata2 : %u\n", wdata[2]);
      EPRINTF("wdata3 : %u\n", wdata[3]);
      EPRINTF("wdata4 : %u\n", wdata[4]);
      EPRINTF("wdata5 : %u\n", wdata[5]);
      EPRINTF("wdata6 : %u\n", wdata[6]);
      EPRINTF("wdata7 : %u\n", wdata[7]);
      EPRINTF("wdata8 : %u\n", wdata[8]);
      EPRINTF("wdata9 : %u\n", wdata[9]);
      EPRINTF("wdata10 : %u\n", wdata[10]);
      EPRINTF("wdata11 : %u\n", wdata[11]);
      EPRINTF("wdata12 : %u\n", wdata[12]);
      EPRINTF("wdata13 : %u\n", wdata[13]);
      EPRINTF("wdata14 : %u\n", wdata[14]);
      EPRINTF("wdata15 : %u\n", wdata[15]);
    }
    EPRINTF("------------------\n");
  }

  ~DRAMRequest() {
    if (wdata != NULL) free(wdata);
  }
};

std::queue<DRAMRequest*> dramRequestQ;

extern "C" {
  void sendDRAMRequest(
      long long addr,
      int tag,
      int isWr,
      int wdata0,
      int wdata1,
      int wdata2,
      int wdata3,
      int wdata4,
      int wdata5,
      int wdata6,
      int wdata7,
      int wdata8,
      int wdata9,
      int wdata10,
      int wdata11,
      int wdata12,
      int wdata13,
      int wdata14,
      int wdata15
    ) {
    // view addr as uint64_t without doing sign extension
    uint64_t cmdAddr = *(uint64_t*)&addr;
    uint64_t cmdTag = (uint64_t)(*(uint32_t*)&tag);
    bool cmdIsWr = isWr > 0;
    uint32_t cmdWdata0 = (*(uint32_t*)&wdata0);
    uint32_t cmdWdata1 = (*(uint32_t*)&wdata1);
    uint32_t cmdWdata2 = (*(uint32_t*)&wdata2);
    uint32_t cmdWdata3 = (*(uint32_t*)&wdata3);
    uint32_t cmdWdata4 = (*(uint32_t*)&wdata4);
    uint32_t cmdWdata5 = (*(uint32_t*)&wdata5);
    uint32_t cmdWdata6 = (*(uint32_t*)&wdata6);
    uint32_t cmdWdata7 = (*(uint32_t*)&wdata7);
    uint32_t cmdWdata8 = (*(uint32_t*)&wdata8);
    uint32_t cmdWdata9 = (*(uint32_t*)&wdata9);
    uint32_t cmdWdata10 = (*(uint32_t*)&wdata10);
    uint32_t cmdWdata11 = (*(uint32_t*)&wdata11);
    uint32_t cmdWdata12 = (*(uint32_t*)&wdata12);
    uint32_t cmdWdata13 = (*(uint32_t*)&wdata13);
    uint32_t cmdWdata14 = (*(uint32_t*)&wdata14);
    uint32_t cmdWdata15 = (*(uint32_t*)&wdata15);



    uint32_t wdata[16] = { cmdWdata0, cmdWdata1, cmdWdata2, cmdWdata3, cmdWdata4, cmdWdata5, cmdWdata6, cmdWdata7, cmdWdata8, cmdWdata9, cmdWdata10, cmdWdata11, cmdWdata12, cmdWdata13, cmdWdata14, cmdWdata15};
    DRAMRequest *req = new DRAMRequest(cmdAddr, cmdTag, cmdIsWr, wdata);
    dramRequestQ.push(req);
    req->print();
  }

  void checkDRAMResponse() {
    if (dramRequestQ.size() > 0) {
      DRAMRequest *req = dramRequestQ.front();
      req->elapsed++;
      if(req->elapsed == req->delay) {
        dramRequestQ.pop();

        uint32_t rdata[16] = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

        if (req->isWr) {
          // Write request: Update 1 burst-length bytes at *addr
          uint32_t *waddr = (uint32_t*) req->addr;
          for (int i=0; i<16; i++) {
            waddr[i] = req->wdata[i];
          }
        } else {
          // Read request: Read burst-length bytes at *addr
          uint32_t *raddr = (uint32_t*) req->addr;
          for (int i=0; i<16; i++) {
            rdata[i] = raddr[i];
            EPRINTF("rdata[%d] = %u\n", i, rdata[i]);
          }

        }
        pokeDRAMResponse(
            req->tag,
            rdata[0],
            rdata[1],
            rdata[2],
            rdata[3],
            rdata[4],
            rdata[5],
            rdata[6],
            rdata[7],
            rdata[8],
            rdata[9],
            rdata[10],
            rdata[11],
            rdata[12],
            rdata[13],
            rdata[14],
            rdata[15]
          );
     

      }


    }
  }

  // Function is called every clock cycle
  int tick() {
    bool exitTick = false;
    int finishSim = 0;
    numCycles++;

   // Check for DRAM response and send it to design

   checkDRAMResponse();

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

    // Handle new incoming operations
    while (!exitTick) {
      simCmd *cmd = cmdChannel->recv();
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
          EPRINTF("[SIM] MALLOC(%u), returning %lx\n", size, ptr);
          respChannel->send(&resp);
          break;
        }
        case FREE: {
          void *ptr = (void*)(*(uint64_t*)cmd->data);
          ASSERT(ptr != NULL, "Attempting to call free on null pointer\n");
          EPRINTF("[SIM] FREE(%lx)\n", ptr);

          break;
        }
        case MEMCPY_H2D: {
          uint64_t *data = (uint64_t*)cmd->data;
          void *dst = (void*)data[0];
          size_t size = data[1];

          EPRINTF("[SIM] Received memcpy request to %lx, size %u\n", dst, size);

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
//            writeReg(reg, (svBitVecVal)data);
            writeReg(reg, data);
            exitTick = true;
            break;
          }
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
