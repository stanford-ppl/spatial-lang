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

#include "vc_hdrs.h"
#include "svdpi_src.h"

#include <DRAMSim.h>

// DRAMSim3
DRAMSim::MultiChannelMemorySystem *mem = NULL;
bool useIdealDRAM = false;

extern uint64_t numCycles;
class DRAMRequest {
public:
  uint64_t addr;
  uint64_t tag;
  bool isWr;
  uint32_t *wdata;
  uint32_t delay;
  uint32_t elapsed;
  uint64_t issued;
  bool completed;

  DRAMRequest(uint64_t a, uint64_t t, bool wr, uint32_t *wd, uint64_t issueCycle) {
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
    issued = issueCycle;
    completed = false;
  }

  void print() {
    EPRINTF("[DRAMRequest] addr: %lx, tag: %lx, isWr: %d, delay: %u, issued=%lu\n", addr, tag, isWr, delay, issued);
  }

  ~DRAMRequest() {
    if (wdata != NULL) free(wdata);
  }
};

struct AddrTag {
  uint64_t addr;
  uint64_t tag;

  AddrTag(uint64_t a, uint64_t t) {
    addr = a;
    tag = t;
  }

	bool operator==(const AddrTag &o) const {
			return addr == o.addr && tag == o.tag;
	}

	bool operator<(const AddrTag &o) const {
			return addr < o.addr || (addr == o.addr && tag < o.tag);
	}
};

std::queue<DRAMRequest*> dramRequestQ;
std::map<struct AddrTag, DRAMRequest*> addrToReqMap;

void checkAndSendDRAMResponse() {
  // If request happens to be in front of queue, pop and poke DRAM response
  if (dramRequestQ.size() > 0) {
    DRAMRequest *req = dramRequestQ.front();

    if (useIdealDRAM) {
      req->elapsed++;
      if (req->elapsed >= req->delay) {
        req->completed = true;
        EPRINTF("[idealDRAM txComplete] addr = %p, tag = %lx, finished = %lu\n", (void*)req->addr, req->tag, numCycles);
      }
    }

    if (req->completed) {
      SV_BIT_PACKED_ARRAY(32, dramRespReady);
      getDRAMRespReady((svBitVec32*)&dramRespReady);
      uint32_t ready = (uint32_t)*dramRespReady;

      if (ready > 0) {
        dramRequestQ.pop();
        EPRINTF("[Sending DRAM resp to]: ");
        req->print();

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
      } else {
        EPRINTF("[SIM] dramResp not ready, numCycles = %ld\n", numCycles);
      }

    }
  }
}

class DRAMCallbackMethods {
public:
  void txComplete(unsigned id, uint64_t addr, uint64_t tag, uint64_t clock_cycle) {
    EPRINTF("[txComplete] addr = %p, tag = %lx, finished = %lu\n", (void*)addr, tag, numCycles);

    // Find transaction, mark it as done, remove entry from map
    struct AddrTag at(addr, tag);
    std::map<struct AddrTag, DRAMRequest*>::iterator it = addrToReqMap.find(at);
    ASSERT(it != addrToReqMap.end(), "address/tag tuple not found in addrToReqMap!");
    DRAMRequest* req = it->second;
    req->completed = true;
    addrToReqMap.erase(at);
  }
};

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

    DRAMRequest *req = new DRAMRequest(cmdAddr, cmdTag, cmdIsWr, wdata, numCycles);
    dramRequestQ.push(req);
    req->print();

    if (!useIdealDRAM) {
      mem->addTransaction(cmdIsWr, cmdAddr, cmdTag);
      struct AddrTag at(cmdAddr, cmdTag);
      addrToReqMap[at] = req;
    }
  }
}

void initDRAM() {
  char *idealDRAM = getenv("USE_IDEAL_DRAM");
  EPRINTF("idealDRAM = %s\n", idealDRAM);
  if (idealDRAM != NULL) {

    if (idealDRAM[0] != 0 && atoi(idealDRAM) > 0) {
      useIdealDRAM = true;
    }
  } else {
    useIdealDRAM = false;
  }

  if (!useIdealDRAM) {
    // Set up DRAMSim2 - currently hardcoding some values that should later be
    // in a config file somewhere
    char *dramSimHome = getenv("DRAMSIM_HOME");
    ASSERT(dramSimHome != NULL, "ERROR: DRAMSIM_HOME environment variable is not set")
    ASSERT(dramSimHome[0] != NULL, "ERROR: DRAMSIM_HOME environment variable set to null string")


    string memoryIni = string(dramSimHome) + string("/ini/DDR2_micron_16M_8b_x8_sg3E.ini");
    string systemIni = string(dramSimHome) + string("spatial.dram.ini");
    // Connect to DRAMSim2 directly here
    mem = DRAMSim::getMemorySystemInstance("ini/DDR2_micron_16M_8b_x8_sg3E.ini", "spatial.dram.ini", dramSimHome, "dramSimVCS", 16384);

    uint64_t hardwareClockHz = 150 * 1e6; // Fixing FPGA clock to 150 MHz
    mem->setCPUClockSpeed(hardwareClockHz);

    // Add callbacks
    DRAMCallbackMethods callbackMethods;
    DRAMSim::TransactionCompleteCB *rwCb = new DRAMSim::Callback<DRAMCallbackMethods, void, unsigned, uint64_t, uint64_t, uint64_t>(&callbackMethods, &DRAMCallbackMethods::txComplete);
    mem->RegisterCallbacks(rwCb, rwCb, NULL);
  }
}

