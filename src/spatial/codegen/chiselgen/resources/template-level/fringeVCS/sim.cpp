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

//#include "dramDefs.h"
#include "simDefs.h"
#include "channel.h"
//#include "svdpi.h"
//#include "svImports.h"
#include "vc_hdrs.h"
#include "svdpi_src.h"

// Slave channels from HOST
Channel *cmdChannel = NULL;
Channel *respChannel = NULL;

// Master channels to DRAM
//Channel *dramCmdChannel = new Channel();
//Channel *dramRespChannel = new Channel();
//uint64_t globalDRAMID = 0;


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
    EPRINTF("isWr : %d\n", isWr);
    EPRINTF("delay: %u\n", delay);
    if (isWr) {
      EPRINTF("wdata0 : %u\n", wdata[0]);
      EPRINTF("wdata1 : %u\n", wdata[1]);
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
//            EPRINTF("rdata[%d] = %u\n", i, rdata[i]);
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

          // Send malloc request to DRAM
//          dramCmd dcmd;
//          dcmd.id = globalDRAMID++;
//          dcmd.cmd = MALLOC;
//          std::memcpy(dcmd.data, &size, sizeof(size_t));
//          dcmd.size = sizeof(size_t);
//          dramCmdChannel->send(&dcmd);
//          dramCmd *dresp = dramRespChannel->recv();
//          ASSERT(dcmd.id == dresp->id, "malloc resp->id does not match cmd.id!");
//          ASSERT(dcmd.cmd == dresp->cmd, "malloc resp->cmd does not match cmd.cmd!");
          break;
        }
        case FREE: {
          void *ptr = (void*)(*(uint64_t*)cmd->data);
          ASSERT(ptr != NULL, "Attempting to call free on null pointer\n");
          EPRINTF("[SIM] FREE(%p)\n", ptr);

          // Send free request to DRAM
//          dramCmd dcmd;
//          dcmd.id = globalID++;
//          dcmd.cmd = FREE;
//          std::memcpy(dcmd.data, &ptr, sizeof(uint64_t));
//          dcmd.size = sizeof(uint64_t);
//          dramCmdChannel->send(&dcmd);
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

    /** Master interfaces to peripheral simulators e.g. DRAM { */
//      posix_spawn_file_actions_t dramAction;
//      pid_t dram_pid;
//
//      posix_spawn_file_actions_init(&dramAction);
//
//      // Create cmdPipe (read) handle at SIM_CMD_FD, respPipe (write) handle at SIM_RESP_FD
//      // Close old descriptors after dup2
//      posix_spawn_file_actions_addclose(&dramAction, dramCmdChannel->writeFd());
//      posix_spawn_file_actions_addclose(&dramAction, dramRespChannel->readFd());
//      posix_spawn_file_actions_adddup2(&dramAction, dramCmdChannel->readFd(), DRAM_CMD_FD);
//      posix_spawn_file_actions_adddup2(&dramAction, dramRespChannel->writeFd(), DRAM_RESP_FD);
//
//      std::string argsmem[] = {"./dram"};
//      char *args[] = {&argsmem[0][0],nullptr};
//
//      if(posix_spawnp(&sim_pid, args[0], &dramAction, NULL, &args[0], NULL) != 0) {
//        EPRINTF("posix_spawnp failed, error = %s\n", strerror(errno));
//        exit(-1);
//      }
//
//      // Close Sim side of pipes
//      close(dramCmdChannel->readFd());
//      close(dramRespChannel->writeFd());
//
//      // Connect with dram simulator
//      dramCmd cmd;
//      cmd.id = globalDRAMID++;
//      cmd.cmd = READY;
//      cmd.size = 0;
//      dramCmdChannel->send(&cmd);
//      dramCmd *resp = recvResp();
//      ASSERT(resp->id == cmd.id, "DRAM init error: Received ID does not match sent ID\n");
//      ASSERT(resp->cmd == READY, "DRAM init error: Received cmd is not 'READY'\n");
//      EPRINTF("DRAM Connection successful!\n");
    /** } End master interface*/
  }
}
