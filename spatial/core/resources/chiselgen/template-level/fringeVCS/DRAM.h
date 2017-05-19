#include <cstdlib>
#include <cstring>
#include <deque>
#include <errno.h>
#include <fcntl.h>
#include <map>
#include <memory>
#include <poll.h>
#include <signal.h>
#include <stdio.h>
#include <string>
#include <sys/mman.h>
#include <sys/prctl.h>
#include <unistd.h>
#include <vector>
using namespace std;

#include "svdpi_src.h"
#include "vc_hdrs.h"

#include <DRAMSim.h>

#define MAX_NUM_Q 128

#define BURST_SIZE_BYTES 64
#define WORD_SIZE_BYTES 4
#define BURST_SIZE_WORDS (BURST_SIZE_BYTES / WORD_SIZE_BYTES)

#define VECTOR_WIDTH BURST_SIZE_WORDS

#define SPARSE_CACHE_SETS 4

// DRAMSim3
DRAMSim::MultiChannelMemorySystem *mem = NULL;
bool useIdealDRAM = false;
bool debug = false;

extern uint64_t numCycles;

uint64_t sparseRequestCounter = 0;  // Used to provide unique tags to each sparse request
uint64_t sparseRequestEnqueCount = 0;
uint64_t sparseCompleteCount = 0;

int sparseCacheSize = -1; // Max. number of lines in cache; -1 == infinity
class DRAMRequest {
public:
  uint64_t addr;
  uint64_t rawAddr;
  uint32_t streamId;
  uint64_t tag;
  uint64_t channelID;
  bool isWr;
  bool isSparse;
  std::array<uint32_t, BURST_SIZE_WORDS> wdata;
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
      for (int i = 0; i < wdata.size(); i++) {
        wdata[i] = wd[i];
      }
    }

    delay = abs(rand()) % 150 + 50;
    elapsed = 0;
    issued = issueCycle;
    completed = false;
  }

  void print() {
    EPRINTF("[DRAMRequest CH=%lu] addr: %lx (%lx), streamId: %x, tag: %lx, isWr: %d, isSparse: %d, issued=%lu \n", channelID, addr, rawAddr, streamId, tag, isWr, isSparse, issued);
    if (isWr) EPRINTF("wdata = %x\n", wdata[0]);
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

std::deque<std::shared_ptr<DRAMRequest>> dramRequestQ[MAX_NUM_Q];
std::map<struct AddrTag, std::shared_ptr<DRAMRequest>> addrToReqMap;

struct CacheLine {
  std::array<std::shared_ptr<DRAMRequest>, BURST_SIZE_WORDS> requests;

  void print() {
    for (auto &request : requests) {
      EPRINTF("---- 0x%lx ", request ? request->addr : 0);
    }
    EPRINTF("\n");
  }
};

struct SparseCache {
  bool contains(AddrTag &at) {
    bool contains = false;
    for (auto &set : sets) {
      if (set.find(at) != set.end()) {
        contains = true;
        break;
      }
    }
    return contains;
  }
   
  using SparseRequestCache = std::map<struct AddrTag, CacheLine>;
  std::array<SparseRequestCache, SPARSE_CACHE_SETS> sets;
};

SparseCache sparseCache;

void printQueueStats(int id) {
  // Ensure that all top 16 requests have been completed
  auto it = dramRequestQ[id].begin();

  EPRINTF("==== dramRequestQ %d status =====\n", id);
  int k = 0;
  while (it != dramRequestQ[id].end()) {
    auto &r = *it;
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
    auto &req = dramRequestQ[id].front();

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

        uint32_t rdata[BURST_SIZE_WORDS] = {0};

        if (req->isWr) {
          // Write request: Update 1 burst-length bytes at *addr
          uint32_t *waddr = (uint32_t*) req->addr;
          for (int i=0; i<req->wdata.size(); i++) {
            waddr[i] = req->wdata[i];
          }
        } else {
          // Read request: Read burst-length bytes at *addr
          uint32_t *raddr = (uint32_t*) req->addr;
          for (int i=0; i<BURST_SIZE_WORDS; i++) {
            rdata[i] = raddr[i];
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
          auto it = dramRequestQ[id].begin();
          bool sparseReqCompleted = true;
          if (dramRequestQ[id].size() < VECTOR_WIDTH) {
            sparseReqCompleted = false;
          } else {
            for (int i = 0; i < VECTOR_WIDTH; i++) {
              auto &r = *it;
              if (!r->completed) {
                sparseReqCompleted = false;
                break;
              }
              it++;
            }
          }


          if (sparseReqCompleted) {
            sparseCompleteCount++;
            bool writeRequest = req->isWr;
            uint64_t tag = req->tag;

            uint64_t gatherAddr[VECTOR_WIDTH] = {0};
            uint64_t scatterAddr[VECTOR_WIDTH] = {0};
            uint64_t scatterData[VECTOR_WIDTH] = {0};
            uint32_t rdata[VECTOR_WIDTH] = {0};

            if (debug) {
              EPRINTF("[checkQ] sparse request completed:\n");
              printQueueStats(id);
            }
            for (int i = 0; i < VECTOR_WIDTH; i++) {
              auto &head = dramRequestQ[id].front();
              ASSERT(head->isSparse, "ERROR: Encountered non-sparse request at (%d) while popping sparse requests! (%lx, %lx)", i, head->addr, head->rawAddr);
              ASSERT(head->isWr == writeRequest, "ERROR: Sparse request type mismatch");
              ASSERT(head->tag == tag, "ERROR: Tag mismatch with sparse requests");
              if (!writeRequest) {
                uint32_t *raddr = (uint32_t*) head->rawAddr;
                if (debug) EPRINTF("-------- gatherAddr(%d) = %lx\n", i, raddr);
                rdata[i] = *raddr;
                gatherAddr[i] = head->rawAddr;
              } else {
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
              for (int i = 0; i < VECTOR_WIDTH; i++) {
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
    auto it = addrToReqMap.find(at);
    ASSERT(it != addrToReqMap.end(), "address/tag tuple (%lx, %lx) not found in addrToReqMap!", addr, tag);
    auto &req = it->second;
    req->completed = true;
    addrToReqMap.erase(at);

    if (req->isSparse) { // Mark all waiting transactions done
      at.tag = at.tag & 0xFFFFFFFF;
      bool found = false;
      for (auto &set : sparseCache.sets) {
        auto it = set.find(at);
        if (it != set.end()) {
          found = true;
          for (auto &r : it->second.requests) {
            if (r) r->completed = true;
          }
          set.erase(at);
        }
      }
      ASSERT(found, "Could not find (%lx, %lx) in sparseCache!", addr, tag);
    }

  }
};

bool sparseCacheFull() {
  if (sparseCacheSize == -1) return false;  // sparseCache is infinitely large
  else if (sparseCache.sets.size() <= sparseCacheSize) return false;
  else return true;
}

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

    uint32_t wdata[] = { cmdWdata0, cmdWdata1, cmdWdata2, cmdWdata3, cmdWdata4, cmdWdata5, cmdWdata6, cmdWdata7, cmdWdata8, cmdWdata9, cmdWdata10, cmdWdata11, cmdWdata12, cmdWdata13, cmdWdata14, cmdWdata15};


    auto req = std::make_shared<DRAMRequest>(cmdAddr, cmdRawAddr, cmdStreamId, cmdTag, cmdIsWr, cmdIsSparse, wdata, numCycles);
    if (debug) {
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
        // Early out if cache is full
        if (sparseCacheFull()) {
          skipIssue = true;
          dramReady = 0;
        } else {
          auto sparseIssueCount = sparseRequestEnqueCount / BURST_SIZE_WORDS;
          auto sparseSetIndex = sparseIssueCount % SPARSE_CACHE_SETS;
          auto sparseRequestIndex = sparseRequestEnqueCount % BURST_SIZE_WORDS;
          bool firstRequest = sparseRequestIndex == 0;

          auto &cacheSet = sparseCache.sets[sparseSetIndex];
          // check if request is in progress across any of the sets
          bool miss = !sparseCache.contains(at);
          if (debug) EPRINTF("[sendDRAMRequest] Sparse request, looking up (addr = %lx, tag = %lx)\n", at.addr, at.tag);
          if (miss) { // MISS
            if (debug) EPRINTF("[sendDRAMRequest] MISS, creating new cache line:\n");
            skipIssue = false;

            auto &line = cacheSet[at];
            line.requests[sparseRequestIndex] = req;

            if (debug) {
              line.print();
            }

            // Disambiguate each request with unique tag in the addr -> req mapping
            uint64_t sparseTag = ((sparseRequestCounter++) << 32) | (cmdTag & 0xFFFFFFFF);
            at.tag = sparseTag;
            addrToReqMap[at] = req;
          } else {  // HIT
            if (debug) EPRINTF("[sendDRAMRequest] HIT, line:\n");
            skipIssue = true;
            
            bool cacheSetBusy = cacheSet.find(at) != cacheSet.end();
            if (firstRequest && cacheSetBusy) {
              if (debug) {
                EPRINTF("[sendDRAMRequest] Request already waiting; %lu outstanding requests\n", sparseIssueCount - sparseCompleteCount);
              }
              // block request if this cache set is in use at the first request
              dramReady = 0;
            } else {  // Update word offset with request pointer
              auto &line = cacheSet[at];
              auto &r = line.requests[sparseRequestIndex];
              ASSERT(!r, "Line should have been cleared from cache by now after memory reponse.");
              r = req;
            }
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
      sparseRequestEnqueCount++;
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

  char *numOutstandingBursts = getenv("DRAM_NUM_OUTSTANDING_BURSTS");
  if (numOutstandingBursts != NULL) {
    if (numOutstandingBursts[0] != 0 && atoi(numOutstandingBursts) > 0) {
      sparseCacheSize = atoi(numOutstandingBursts);
    }
  }
  EPRINTF("[DRAM] Sparse cache size = %d\n", sparseCacheSize);


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

