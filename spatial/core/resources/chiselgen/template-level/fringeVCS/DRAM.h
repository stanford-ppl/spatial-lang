#include <unistd.h>
#include <errno.h>
#include <cstring>
#include <string>
#include <cstdlib>
#include <stdio.h>
#include <vector>
#include <deque>
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

#define MAX_NUM_Q 128

// DRAMSim3
DRAMSim::MultiChannelMemorySystem *mem = NULL;
bool useIdealDRAM = false;
bool debug = false;

extern uint64_t numCycles;
uint32_t wordSizeBytes = 4;
uint32_t burstSizeBytes = 64;
uint32_t burstSizeWords = burstSizeBytes / wordSizeBytes;

uint64_t sparseRequestCounter = 0;  // Used to provide unique tags to each sparse request

class DRAMRequest {
public:
  uint64_t addr;
  uint64_t rawAddr;
  uint32_t streamId;
  uint64_t tag;
  uint64_t channelID;
  bool isWr;
  bool isSparse;
  uint32_t *wdata;
  uint32_t delay;
  uint32_t elapsed;
  uint64_t issued;
  bool completed;

  DRAMRequest(uint64_t a, uint64_t ra, uint32_t sid, uint64_t t, bool wr, bool sparse, uint32_t *wd, uint64_t issueCycle) {
    addr = a;
    rawAddr = ra;
    streamId = sid;
    tag = t;
    isWr = wr;
    isSparse = sparse;
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
    EPRINTF("[DRAMRequest CH=%lu] addr: %lx (%lx), streamId: %x, tag: %lx, isWr: %d, isSparse: %d, issued=%lu ", channelID, addr, rawAddr, streamId, tag, isWr, isSparse, issued);
    if (isWr) EPRINTF("wdata = %x\n", wdata[0]);
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

std::deque<DRAMRequest*> dramRequestQ[MAX_NUM_Q];
std::map<struct AddrTag, DRAMRequest*> addrToReqMap;

std::map<struct AddrTag, DRAMRequest**> sparseRequestCache;

uint32_t getWordOffset(uint64_t addr) {
  return (addr & (burstSizeBytes - 1)) >> 2;   // TODO: Use parameters above!
}

void printQueueStats(int id) {
  // Ensure that all top 16 requests have been completed
  deque<DRAMRequest*>::iterator it = dramRequestQ[id].begin();

  EPRINTF("==== dramRequestQ %d status =====\n", id);
  int k = 0;
  while (it != dramRequestQ[id].end()) {
    DRAMRequest *r = *it;
    EPRINTF("    %d. addr: %lx (%lx), tag: %lx, streamId: %d, sparse = %d, completed: %d\n", k, r->addr, r->rawAddr, r->tag, r->streamId, r->isSparse, r->completed);
    it++;
    k++;
    if (k > 20) break;
  }
  EPRINTF("==== END dramRequestQ %d status =====\n", id);
}

bool checkQAndRespond(int id) {
  // If request happens to be in front of deque, pop and poke DRAM response
  bool pokedResponse = false;
  if (dramRequestQ[id].size() > 0) {
    DRAMRequest *req = dramRequestQ[id].front();

    if (useIdealDRAM) {
      req->elapsed++;
      if (req->elapsed >= req->delay) {
        req->completed = true;
        if (debug) {
          EPRINTF("[idealDRAM txComplete] addr = %p, tag = %lx, finished = %lu\n", (void*)req->addr, req->tag, numCycles);
        }
      }
    }

    if (req->completed) {
      if (!req->isSparse) {
        dramRequestQ[id].pop_front();
        if (debug) {
          EPRINTF("[Sending DRAM resp to]: ");
          req->print();
        }

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
          pokedResponse = true;
        } else {
          // Ensure that there are at least 16 requests
          // Ensure that all top 16 requests have been completed
          deque<DRAMRequest*>::iterator it = dramRequestQ[id].begin();
          bool sparseReqCompleted = true;
          if (dramRequestQ[id].size() < 16) {
            sparseReqCompleted = false;
          } else {
            for (int i = 0; i < 16; i++) {
              DRAMRequest *r = *it;
              if (!r->completed) {
                sparseReqCompleted = false;
                break;
              }
              it++;
            }
          }


          if (sparseReqCompleted) {
            bool writeRequest = req->isWr;
            uint64_t tag = req->tag;

//            ASSERT(!writeRequest, "Sparse writes not yet supported!\n");
            uint64_t gatherAddr[16] = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
            uint64_t scatterAddr[16] = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
            uint64_t scatterData[16] = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
            uint32_t rdata[16] = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

            if (debug) {
              EPRINTF("[checkQ] sparse request completed:\n");
              printQueueStats(id);
            }
            for (int i = 0; i < 16; i++) {
              DRAMRequest *head = dramRequestQ[id].front();
              ASSERT(head->isSparse, "ERROR: Encountered non-sparse request at (%d) while popping sparse requests! (%lx, %lx)", i, head->addr, head->rawAddr);
              ASSERT(head->isWr == writeRequest, "ERROR: Sparse request type mismatch");
              ASSERT(head->tag == tag, "ERROR: Tag mismatch with sparse requests");
              if (!writeRequest) {
                uint32_t *raddr = (uint32_t*) head->rawAddr;
                if (debug) EPRINTF("-------- gatherAddr(%d) = %lx\n", i, raddr);
                rdata[i] = *raddr;
                gatherAddr[i] = head->rawAddr;
              } else {
//                ASSERT(false, "ERROR: Sparse writes not supported yet!");
                uint32_t *waddr = (uint32_t*) head->rawAddr;
                if (debug) EPRINTF("-------- scatterAddr(%d) = %lx\n", i, waddr);
                *waddr = head->wdata[0];
                scatterAddr[i] = head->rawAddr;
                scatterData[i] = head->wdata[0];

              }
              dramRequestQ[id].pop_front();
            }

            if (debug) {
              EPRINTF("[checkAndSendDRAMResponse] Sparse complete with following details:\n");
              for (int i = 0; i<16; i++) {
                if (writeRequest) {
                  EPRINTF("---- [scatter] addr %lx: data %x\n", scatterAddr[i], scatterData[i]);
                } else {
                  EPRINTF("---- addr %lx: data %x\n", gatherAddr[i], rdata[i]);
                }
              }
              printQueueStats(id);
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
            pokedResponse = true;
          }
        }
      }
    }
  return pokedResponse;
}

void checkAndSendDRAMResponse() {
  // Check if DRAM is ready
  SV_BIT_PACKED_ARRAY(32, dramRespReady);
  getDRAMRespReady((svBitVec32*)&dramRespReady);
  uint32_t ready = (uint32_t)*dramRespReady;

  if (ready > 0) {
    // Iterate over all queues and respond to the first non-empty queue
    for (int i =0; i < MAX_NUM_Q; i++) {
      if (checkQAndRespond(i)) break;
    }
  } else {
    if (debug) {
      EPRINTF("[SIM] dramResp not ready, numCycles = %ld\n", numCycles);
    }
  }
}

class DRAMCallbackMethods {
public:
  void txComplete(unsigned id, uint64_t addr, uint64_t tag, uint64_t clock_cycle) {
    if (debug) {
      EPRINTF("[txComplete] addr = %p, tag = %lx, finished = %lu\n", (void*)addr, tag, numCycles);
    }

    // Find transaction, mark it as done, remove entry from map
    struct AddrTag at(addr, tag);
    std::map<struct AddrTag, DRAMRequest*>::iterator it = addrToReqMap.find(at);
    ASSERT(it != addrToReqMap.end(), "address/tag tuple (%lx, %lx) not found in addrToReqMap!", addr, tag);
    DRAMRequest* req = it->second;
    req->completed = true;
    addrToReqMap.erase(at);

    if (req->isSparse) { // Mark all waiting transactions done
      at.tag = at.tag & 0xFFFFFFFF;
      std::map<struct AddrTag, DRAMRequest**>::iterator it = sparseRequestCache.find(at);
      ASSERT(it != sparseRequestCache.end(), "Could not find (%lx, %lx) in sparseRequestCache!", addr, tag);
      DRAMRequest **line = it->second;
      for (int i = 0; i < 16; i++) {
        if (line[i] != NULL) {
          line[i]->completed = true;
        }
      }
      sparseRequestCache.erase(at);
    }

  }
};

extern "C" {
  int sendDRAMRequest(
      long long addr,
      long long rawAddr,
      int streamId,
      int tag,
      int isWr,
      int isSparse,
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
    int dramReady = 1;  // 1 == ready, 0 == not ready (stall upstream)

    // view addr as uint64_t without doing sign extension
    uint64_t cmdAddr = *(uint64_t*)&addr;
    uint64_t cmdRawAddr = *(uint64_t*)&rawAddr;
    uint64_t cmdTag = (uint64_t)(*(uint32_t*)&tag);
    uint64_t cmdStreamId = (uint32_t)(*(uint32_t*)&streamId);
    bool cmdIsWr = isWr > 0;
    bool cmdIsSparse = isSparse > 0;
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


    DRAMRequest *req = new DRAMRequest(cmdAddr, cmdRawAddr, cmdStreamId, cmdTag, cmdIsWr, cmdIsSparse, wdata, numCycles);
    if (debug) {
//      EPRINTF("[sendDRAMRequest] Called with addr: %lx (%lx), streamId: %x, tag: %lx, isWr: %d, isSparse: %d\n", cmdAddr, cmdRawAddr, cmdStreamId, cmdTag, cmdIsWr, cmdIsSparse);
      EPRINTF("[sendDRAMRequest] Called with ");
      req->print();
    }

    if (!useIdealDRAM) {
      bool skipIssue = false;
      struct AddrTag at(cmdAddr, cmdTag);

			if (!cmdIsSparse) { // Dense request
        addrToReqMap[at] = req;
        skipIssue = false;
      } else {  // Sparse request
        std::map<struct AddrTag, DRAMRequest**>::iterator it = sparseRequestCache.find(at);
        if (debug) EPRINTF("[sendDRAMRequest] Sparse request, looking up (addr = %lx, tag = %lx)\n", at.addr, at.tag);
        if (it == sparseRequestCache.end()) { // MISS
          if (debug) EPRINTF("[sendDRAMRequest] MISS, creating new cache line:\n");
          skipIssue = false;
          DRAMRequest **line = new DRAMRequest*[16]; // One outstanding request per word
          memset(line, 0, 16*sizeof(DRAMRequest*));
          line[getWordOffset(cmdRawAddr)] = req;
          if (debug) {
            for (int i=0; i<16; i++) {
              EPRINTF("---- %p ", line[i]);
            }
            EPRINTF("\n");
          }

          sparseRequestCache[at] = line;

          // Disambiguate each request with unique tag in the addr -> req mapping
          uint64_t sparseTag = ((sparseRequestCounter++) << 32) | (cmdTag & 0xFFFFFFFF);
          at.tag = sparseTag;
          addrToReqMap[at] = req;
        } else {  // HIT
          if (debug) EPRINTF("[sendDRAMRequest] HIT, line:\n");
          skipIssue = true;
          DRAMRequest **line = it->second;
          if (debug) {
            for (int i=0; i<16; i++) {
              EPRINTF("---- %p ", line[i]);
            }
            EPRINTF("\n");
          }

          DRAMRequest *r = line[getWordOffset(cmdRawAddr)];

          if (r != NULL) {  // Already a request waiting, stall upstream
            if (debug) EPRINTF("[sendDRAMRequest] Req %lx (%lx) already present for given word, stall upstream\n", r->addr, r->rawAddr);
            dramReady = 0;
          } else {  // Update word offset with request pointer
            line[getWordOffset(cmdRawAddr)] = req;
          }
        }
      }

      if (!skipIssue) {
        mem->addTransaction(cmdIsWr, cmdAddr, at.tag);
        req->channelID = mem->findChannelNumber(cmdAddr);
        if (debug) {
          EPRINTF("[sendDRAMRequest] Issuing following command:");
          req->print();
        }
      } else {
        if (debug) {
          EPRINTF("[sendDRAMRequest] Skipping addr = %lx (%lx), tag = %lx\n", cmdAddr, cmdRawAddr, cmdTag);
        }
      }
    }

    if (dramReady == 1) {
      dramRequestQ[cmdStreamId].push_back(req);
    }

    return dramReady;
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

  char *debugVar = getenv("DRAM_DEBUG");
  if (debugVar != NULL) {
    if (debugVar[0] != 0 && atoi(debugVar) > 0) {
      debug = true;
      EPRINTF("[DRAM] Verbose debug messages enabled\n");
    }
  } else {
    EPRINTF("[DRAM] Verbose debug messages disabled \n");
    debug = false;
  }


  if (!useIdealDRAM) {
    // Set up DRAMSim2 - currently hardcoding some values that should later be
    // in a config file somewhere
    char *dramSimHome = getenv("DRAMSIM_HOME");
    ASSERT(dramSimHome != NULL, "ERROR: DRAMSIM_HOME environment variable is not set")
    ASSERT(dramSimHome[0] != NULL, "ERROR: DRAMSIM_HOME environment variable set to null string")


    string memoryIni = string(dramSimHome) + string("/ini/DDR3_micron_32M_8B_x4_sg125.ini");
    string systemIni = string(dramSimHome) + string("spatial.dram.ini");
    // Connect to DRAMSim2 directly here
    mem = DRAMSim::getMemorySystemInstance("ini/DDR3_micron_32M_8B_x4_sg125.ini", "spatial.dram.ini", dramSimHome, "dramSimVCS", 16384);

    uint64_t hardwareClockHz = 1 * 1e9; // Fixing Plasticine clock to 1 GHz
    mem->setCPUClockSpeed(hardwareClockHz);

    // Add callbacks
    DRAMCallbackMethods callbackMethods;
    DRAMSim::TransactionCompleteCB *rwCb = new DRAMSim::Callback<DRAMCallbackMethods, void, unsigned, uint64_t, uint64_t, uint64_t>(&callbackMethods, &DRAMCallbackMethods::txComplete);
    mem->RegisterCallbacks(rwCb, rwCb, NULL);
  }
}

