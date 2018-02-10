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

#include <AddrRemapper.h>
#include <DRAMSim.h>

#define MAX_NUM_Q             128
#define PAGE_SIZE_BYTES       4096
#define PAGE_OFFSET           (__builtin_ctz(PAGE_SIZE_BYTES))
#define PAGE_FRAME_NUM(addr)  (addr >> PAGE_OFFSET)

// N3XT constants
uint32_t N3XT_LOAD_DELAY = 3;
uint32_t N3XT_STORE_DELAY = 11;
uint32_t N3XT_NUM_CHANNELS = MAX_NUM_Q;

// Simulation constants
FILE *traceFp = NULL;

// DRAMSim2
DRAMSim::MultiChannelMemorySystem *mem = NULL;
bool useIdealDRAM = false;
bool debug = false;
AddrRemapper *remapper = NULL;

extern uint64_t numCycles;
uint32_t wordSizeBytes = 1;
uint32_t burstSizeBytes = 64;
uint32_t burstSizeWords = burstSizeBytes / wordSizeBytes;

uint64_t globalID = 0;

class DRAMRequest;

typedef union DRAMTag {
  struct {
    unsigned int uid : 32;
    unsigned int streamId : 32;
  };
  uint64_t tag;
} DRAMTag;

/**
 * DRAM Command received from the design
 * One object could potentially create multiple DRAMRequests
 */
class DRAMCommand {
public:
  uint64_t addr;
  uint32_t size;
  DRAMTag tag;
  uint64_t channelID;
  bool isWr;
  DRAMRequest **reqs = NULL;

  DRAMCommand(uint64_t a, uint32_t sz, DRAMTag t, bool wr) {
    addr = a;
    size = sz;
    tag = t;
    isWr = wr;
  }

  bool hasCompleted(); // Method definition after 'DRAMRequest' class due to forward reference

  ~DRAMCommand() {
    if (reqs != NULL) free(reqs);
  }
};


// DRAM Request Queue
std::deque<DRAMRequest*> dramRequestQ[MAX_NUM_Q];

/**
 * DRAM Request corresponding to 1-burst that is enqueued into DRAMSim2
 * The 'size' field reflects the size of the entire command of which
 * this request is part of (legacy, should be removed)
 */
class DRAMRequest {
public:
  uint64_t id;
  uint64_t addr;
  uint64_t rawAddr;
  uint64_t smallAddr;
  uint32_t size;
  DRAMTag tag;
  uint64_t channelID;
  bool isWr;
  uint8_t *wdata = NULL;
  uint32_t delay;
  uint32_t elapsed;
  uint64_t issued;
  bool completed;
  DRAMCommand *cmd;

  DRAMRequest(uint64_t a, uint64_t ra, uint32_t sz, DRAMTag t, bool wr, uint64_t issueCycle) {
    id = globalID++;
    addr = remapper->getBig(a);
    smallAddr = a;
    rawAddr = ra;
    size = sz;
    tag = t;
    isWr = wr;

    // N3xt delay
    if (isWr) {
      delay = N3XT_STORE_DELAY;
    } else {
      delay = N3XT_LOAD_DELAY;
    }
    if (useIdealDRAM) {
      channelID = id % N3XT_NUM_CHANNELS;
    }

    elapsed = 0;
    issued = issueCycle;
    completed = false;
    wdata = NULL;
  }

  void print() {
    EPRINTF("[DRAMRequest CH=%lu] addr: %lx (%lx), sizeInBursts: %u, streamId: %x, tag: %lx, isWr: %d, issued=%lu \n", channelID, addr, rawAddr, size, tag.streamId, tag.uid, isWr, issued);
  }

  void schedule() {
    if (!useIdealDRAM) {
      mem->addTransaction(isWr, addr, tag.tag);
      channelID = mem->findChannelNumber(addr);
    } else {
      dramRequestQ[channelID].push_back(this);
      ASSERT(channelID < MAX_NUM_Q, "channelID %d is greater than MAX_NUM_Q %u. Is N3XT_NUM_CHANNELS (%u) > MAX_NUM_Q (%u) ?\n", channelID, MAX_NUM_Q, MAX_NUM_Q, N3XT_NUM_CHANNELS);
    }
    if (debug) {
      EPRINTF("                  Issuing following command:");
      print();
    }

  }

  ~DRAMRequest() {
    if (wdata != NULL) free(wdata);
  }
};

bool DRAMCommand::hasCompleted() {
  bool completed = true;
  for (int i = 0; i < size; i++) {
    if (!reqs[i]->completed) {
      completed = false;
      break;
    }
  }
  return completed;
}


struct AddrTag {
  uint64_t addr;
  DRAMTag tag;

  AddrTag(uint64_t a, DRAMTag t) {
    addr = a;
    tag = t;
  }

  bool operator==(const AddrTag &o) const {
      return addr == o.addr && tag.tag == o.tag.tag;
  }

  bool operator<(const AddrTag &o) const {
      return addr < o.addr || (addr == o.addr && tag.tag < o.tag.tag);
  }
};


// WDATA Queue
std::deque<uint8_t*> wdataQ[MAX_NUM_Q];

// Current set of requests that will be getting their wdata in order
std::deque<DRAMRequest*> wrequestQ;

// Internal book-keeping data structures
std::map<struct AddrTag, DRAMRequest*> addrToReqMap;

uint32_t getWordOffset(uint64_t addr) {
  return (addr & (burstSizeBytes - 1)) >> 2;   // TODO: Use parameters above!
}

void printQueueStats(int id) {
  // Ensure that all top 16 requests have been completed
  if (dramRequestQ[id].size() > 0) {
    deque<DRAMRequest*>::iterator it = dramRequestQ[id].begin();

    EPRINTF("==== dramRequestQ %d status =====\n", id);
    int k = 0;
    while (it != dramRequestQ[id].end()) {
      DRAMRequest *r = *it;
      EPRINTF("    %d. addr: %lx (%lx), tag: %lx, streamId: %d, completed: %d\n", k, r->addr, r->rawAddr, r->tag.uid, r->tag.streamId, r->completed);
      it++;
      k++;
      //    if (k > 20) break;
    }
    EPRINTF("==== END dramRequestQ %d status =====\n", id);

  }
}

void updateIdealDRAMQ(int id) {
  if (dramRequestQ[id].size() > 0) {
    DRAMRequest *req = dramRequestQ[id].front();

    if (useIdealDRAM) {
      req->elapsed++;
      if (req->elapsed >= req->delay) {
        req->completed = true;
        if (debug) {
          EPRINTF("[idealDRAM txComplete] addr = %p, tag = %lx, finished = %lu\n", (void*)req->addr, req->tag.uid, numCycles);
        }

      }
    }
  }
}

int popWhenReady = -1; // dramRequestQ from which response was poked (to be popped when ready)

/**
 * DRAM Queue pop: Called from testbench when response ready & valid is high
 * Both read and write requests are in a single queue in the simulation
 * So both 'popDRAMReadQ' and 'popDRAMWriteQ' call the same internal function
 * The separation in the DPI API is for future-proofing when we truly support
 * independent read and write DRAM channel simulation
 */
void popDRAMQ() {
  ASSERT(popWhenReady != -1, "popWhenReady == -1! Popping before the first command was issued?\n");
  ASSERT(popWhenReady < MAX_NUM_Q, "popWhenReady = %d which is greater than MAX_NUM_Q (%d)!\n", popWhenReady, MAX_NUM_Q);

  DRAMRequest *req = dramRequestQ[popWhenReady].front();
  ASSERT(req->completed, "Request at the head of pop queue (%d) not completed!\n", popWhenReady);
  ASSERT(req != NULL, "Request at head of pop queue (%d) is null!\n", req);

  if (req->isWr) { // Write request
    ASSERT(req->cmd->hasCompleted(), "Write command at head of pop queue (%d) is not fully complete!\n", popWhenReady);
    DRAMCommand *cmd = req->cmd;
    DRAMRequest *front = req;
    // Do write data handling, then pop all requests belonging to finished cmd from FIFO
    while ((dramRequestQ[popWhenReady].size() > 0) && (front->cmd == cmd)) {
      uint8_t *front_wdata = front->wdata;
      uint8_t *front_waddr = (uint8_t*) front->addr;
      for (int i=0; i<burstSizeWords; i++) {
        front_waddr[i] = front_wdata[i];
      }
      dramRequestQ[popWhenReady].pop_front();
      delete front;
      front = dramRequestQ[popWhenReady].front();
    }
    delete cmd;

  } else {  // Read request: Just pop
    dramRequestQ[popWhenReady].pop_front();
    // TODO: Uncommenting lines below is causing a segfault
    // More cleaner garbage collection required, but isn't an immediate problem
//          if (req->cmd->hasCompleted()) {
//            delete req->cmd;
//          }
    delete req;
  }

  // Reset popWhenReady
  popWhenReady = -1;
}

void popDRAMReadQ() {
  popDRAMQ();
}

void popDRAMWriteQ() {
  popDRAMQ();
}

bool checkQAndRespond(int id) {
  // If request at front has completed, pop and poke DRAM response
  bool pokedResponse = false;
  if (dramRequestQ[id].size() > 0) {
    DRAMRequest *req = dramRequestQ[id].front();

//    if (useIdealDRAM) {
//      req->elapsed++;
//      if (req->elapsed >= req->delay) {
//        req->completed = true;
//        if (debug) {
//          EPRINTF("[idealDRAM txComplete] addr = %p, tag = %lx, finished = %lu\n", (void*)req->addr, req->tag, numCycles);
//        }
//      }
//    }

    if (req->completed) {
      uint8_t rdata[64] = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
      bool pokeResponse = false;

      if (req->isWr) { // Write request: Update 1 burst-length bytes at *addr
        uint8_t *wdata = req->wdata;
        uint8_t *waddr = (uint8_t*) req->addr;
        for (int i=0; i<burstSizeWords; i++) {
          waddr[i] = wdata[i];
        }
        if (req->cmd->hasCompleted()) {
          pokeResponse = true;
        }
      } else { // Read request: Read burst-length bytes at *addr
        uint8_t *raddr = (uint8_t*) req->addr;
        for (int i=0; i<burstSizeWords; i++) {
          rdata[i] = raddr[i];
        }
        pokeResponse = true;
      }

      if (pokeResponse) { // Send DRAM response
        if (debug) {
          EPRINTF("[Sending DRAM resp to]: ");
          req->print();
        }

      // N3Xt logging info
      fprintf(traceFp, "id: %lu\n", req->id);
      fprintf(traceFp, "issue: %lu\n", req->issued);
      if (req->isWr) {
        fprintf(traceFp, "type: STORE\n");
      } else {
        fprintf(traceFp, "type: LOAD\n");
      }
      fprintf(traceFp, "delay: %d\n", numCycles - req->issued);
      fprintf(traceFp, "addr: %lu\n", req->smallAddr);
      fprintf(traceFp, "size: %u\n", burstSizeBytes);
      fprintf(traceFp, "channel: %d\n", req->channelID);
      fprintf(traceFp, "\n");


        if (req->isWr) {
          pokeDRAMWriteResponse(req->tag.uid, req->tag.streamId);
        } else {
          pokeDRAMReadResponse(
            req->tag.uid,
            req->tag.streamId,
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
            rdata[15],
            rdata[16],
            rdata[17],
            rdata[18],
            rdata[19],
            rdata[20],
            rdata[21],
            rdata[22],
            rdata[23],
            rdata[24],
            rdata[25],
            rdata[26],
            rdata[27],
            rdata[28],
            rdata[29],
            rdata[30],
            rdata[31],
            rdata[32],
            rdata[33],
            rdata[34],
            rdata[35],
            rdata[36],
            rdata[37],
            rdata[38],
            rdata[39],
            rdata[40],
            rdata[41],
            rdata[42],
            rdata[43],
            rdata[44],
            rdata[45],
            rdata[46],
            rdata[47],
            rdata[48],
            rdata[49],
            rdata[50],
            rdata[51],
            rdata[52],
            rdata[53],
            rdata[54],
            rdata[55],
            rdata[56],
            rdata[57],
            rdata[58],
            rdata[59],
            rdata[60],
            rdata[61],
            rdata[62],
            rdata[63]

          );
        }
        pokedResponse = true;
      }
    }
  }
  return pokedResponse;
}

void checkAndSendDRAMResponse() {
  // Check if DRAM is ready
  // TODO: This logic is sketchy for two reasons
  // 1. This checks ready first before asserting valid. This should not be the case; valid and ready must be independent
  // 2. read and write ready signals are combined, so logic stalls if EITHER is not ready. This is quite clunky and doesn't
  //    model real world cases
  SV_BIT_PACKED_ARRAY(32, dramReadRespReady);
  SV_BIT_PACKED_ARRAY(32, dramWriteRespReady);
  getDRAMReadRespReady((svBitVec32*)&dramReadRespReady);
  getDRAMWriteRespReady((svBitVec32*)&dramWriteRespReady);
//  uint32_t readReady = (uint32_t)*dramReadRespReady;
//  uint32_t writeReady = (uint32_t)*dramWriteRespReady;
//  uint32_t ready = readReady & writeReady;
//  if (ready > 0) {

  if (debug) {
    if ((numCycles % 5000) == 0) {
      for (int i = 0; i < MAX_NUM_Q; i++) {
        printQueueStats(i);
      }
    }
  }

  if (popWhenReady >= 0) { // A particular queue has already poked its response, call it again
    ASSERT(checkQAndRespond(popWhenReady), "popWhenReady (%d) >= 0, but no response generated from queue %d\n", popWhenReady, popWhenReady);
  } else {   // Iterate over all queues and respond to the first non-empty queue
    if (useIdealDRAM) {
      for (int i =0; i < MAX_NUM_Q; i++) {
        updateIdealDRAMQ(i);
      }
    }

    for (int i =0; i < MAX_NUM_Q; i++) {
      if (checkQAndRespond(i)) {
        popWhenReady = i;
        break;
      }
    }
  }
//  } else {
//    if (debug) {
//      EPRINTF("[SIM] dramResp not ready, numCycles = %ld\n", numCycles);
//    }
//  }
}

class DRAMCallbackMethods {
public:
  void txComplete(unsigned id, uint64_t addr, uint64_t tag, uint64_t clock_cycle) {
    DRAMTag cmdTag;
    cmdTag.tag = tag;
    if (debug) {
      EPRINTF("[txComplete] addr = %p, tag = %lx, finished = %lu\n", (void*)addr, cmdTag.tag, numCycles);
    }

    // Find transaction, mark it as done, remove entry from map
    struct AddrTag at(addr, cmdTag);
    std::map<struct AddrTag, DRAMRequest*>::iterator it = addrToReqMap.find(at);
    ASSERT(it != addrToReqMap.end(), "address/tag tuple (%lx, %lx) not found in addrToReqMap!", addr, cmdTag.tag);
    DRAMRequest* req = it->second;
    req->completed = true;
    addrToReqMap.erase(at);
  }
};

extern "C" {
  int sendWdataStrb(
    int dramCmdValid,
    int dramReadySeen,
    int wdata0, int wdata1, int wdata2, int wdata3, int wdata4, int wdata5, int wdata6, int wdata7, int wdata8, int wdata9, int wdata10, int wdata11, int wdata12, int wdata13, int wdata14, int wdata15, int wdata16, int wdata17, int wdata18, int wdata19, int wdata20, int wdata21, int wdata22, int wdata23, int wdata24, int wdata25, int wdata26, int wdata27, int wdata28, int wdata29, int wdata30, int wdata31, int wdata32, int wdata33, int wdata34, int wdata35, int wdata36, int wdata37, int wdata38, int wdata39, int wdata40, int wdata41, int wdata42, int wdata43, int wdata44, int wdata45, int wdata46, int wdata47, int wdata48, int wdata49, int wdata50, int wdata51, int wdata52, int wdata53, int wdata54, int wdata55, int wdata56, int wdata57, int wdata58, int wdata59, int wdata60, int wdata61, int wdata62, int wdata63, int strb0,
    int strb1, int strb2, int strb3, int strb4, int strb5, int strb6, int strb7, int strb8, int strb9, int strb10, int strb11, int strb12, int strb13, int strb14, int strb15, int strb16, int strb17, int strb18, int strb19, int strb20, int strb21, int strb22, int strb23, int strb24, int strb25, int strb26, int strb27, int strb28, int strb29, int strb30, int strb31, int strb32, int strb33, int strb34, int strb35, int strb36, int strb37, int strb38, int strb39, int strb40, int strb41, int strb42, int strb43, int strb44, int strb45, int strb46, int strb47, int strb48, int strb49, int strb50, int strb51, int strb52, int strb53, int strb54, int strb55, int strb56, int strb57, int strb58, int strb59, int strb60, int strb61, int strb62, int strb63
  ) {

    // view addr as uint64_t without doing sign extension
    uint8_t cmdWdata0 = (*(uint8_t*)&wdata0);
    uint8_t cmdWdata1 = (*(uint8_t*)&wdata1);
    uint8_t cmdWdata2 = (*(uint8_t*)&wdata2);
    uint8_t cmdWdata3 = (*(uint8_t*)&wdata3);
    uint8_t cmdWdata4 = (*(uint8_t*)&wdata4);
    uint8_t cmdWdata5 = (*(uint8_t*)&wdata5);
    uint8_t cmdWdata6 = (*(uint8_t*)&wdata6);
    uint8_t cmdWdata7 = (*(uint8_t*)&wdata7);
    uint8_t cmdWdata8 = (*(uint8_t*)&wdata8);
    uint8_t cmdWdata9 = (*(uint8_t*)&wdata9);
    uint8_t cmdWdata10 = (*(uint8_t*)&wdata10);
    uint8_t cmdWdata11 = (*(uint8_t*)&wdata11);
    uint8_t cmdWdata12 = (*(uint8_t*)&wdata12);
    uint8_t cmdWdata13 = (*(uint8_t*)&wdata13);
    uint8_t cmdWdata14 = (*(uint8_t*)&wdata14);
    uint8_t cmdWdata15 = (*(uint8_t*)&wdata15);
    uint8_t cmdWdata16 = (*(uint8_t*)&wdata16);
    uint8_t cmdWdata17 = (*(uint8_t*)&wdata17);
    uint8_t cmdWdata18 = (*(uint8_t*)&wdata18);
    uint8_t cmdWdata19 = (*(uint8_t*)&wdata19);
    uint8_t cmdWdata20 = (*(uint8_t*)&wdata20);
    uint8_t cmdWdata21 = (*(uint8_t*)&wdata21);
    uint8_t cmdWdata22 = (*(uint8_t*)&wdata22);
    uint8_t cmdWdata23 = (*(uint8_t*)&wdata23);
    uint8_t cmdWdata24 = (*(uint8_t*)&wdata24);
    uint8_t cmdWdata25 = (*(uint8_t*)&wdata25);
    uint8_t cmdWdata26 = (*(uint8_t*)&wdata26);
    uint8_t cmdWdata27 = (*(uint8_t*)&wdata27);
    uint8_t cmdWdata28 = (*(uint8_t*)&wdata28);
    uint8_t cmdWdata29 = (*(uint8_t*)&wdata29);
    uint8_t cmdWdata30 = (*(uint8_t*)&wdata30);
    uint8_t cmdWdata31 = (*(uint8_t*)&wdata31);
    uint8_t cmdWdata32 = (*(uint8_t*)&wdata32);
    uint8_t cmdWdata33 = (*(uint8_t*)&wdata33);
    uint8_t cmdWdata34 = (*(uint8_t*)&wdata34);
    uint8_t cmdWdata35 = (*(uint8_t*)&wdata35);
    uint8_t cmdWdata36 = (*(uint8_t*)&wdata36);
    uint8_t cmdWdata37 = (*(uint8_t*)&wdata37);
    uint8_t cmdWdata38 = (*(uint8_t*)&wdata38);
    uint8_t cmdWdata39 = (*(uint8_t*)&wdata39);
    uint8_t cmdWdata40 = (*(uint8_t*)&wdata40);
    uint8_t cmdWdata41 = (*(uint8_t*)&wdata41);
    uint8_t cmdWdata42 = (*(uint8_t*)&wdata42);
    uint8_t cmdWdata43 = (*(uint8_t*)&wdata43);
    uint8_t cmdWdata44 = (*(uint8_t*)&wdata44);
    uint8_t cmdWdata45 = (*(uint8_t*)&wdata45);
    uint8_t cmdWdata46 = (*(uint8_t*)&wdata46);
    uint8_t cmdWdata47 = (*(uint8_t*)&wdata47);
    uint8_t cmdWdata48 = (*(uint8_t*)&wdata48);
    uint8_t cmdWdata49 = (*(uint8_t*)&wdata49);
    uint8_t cmdWdata50 = (*(uint8_t*)&wdata50);
    uint8_t cmdWdata51 = (*(uint8_t*)&wdata51);
    uint8_t cmdWdata52 = (*(uint8_t*)&wdata52);
    uint8_t cmdWdata53 = (*(uint8_t*)&wdata53);
    uint8_t cmdWdata54 = (*(uint8_t*)&wdata54);
    uint8_t cmdWdata55 = (*(uint8_t*)&wdata55);
    uint8_t cmdWdata56 = (*(uint8_t*)&wdata56);
    uint8_t cmdWdata57 = (*(uint8_t*)&wdata57);
    uint8_t cmdWdata58 = (*(uint8_t*)&wdata58);
    uint8_t cmdWdata59 = (*(uint8_t*)&wdata59);
    uint8_t cmdWdata60 = (*(uint8_t*)&wdata60);
    uint8_t cmdWdata61 = (*(uint8_t*)&wdata61);
    uint8_t cmdWdata62 = (*(uint8_t*)&wdata62);
    uint8_t cmdWdata63 = (*(uint8_t*)&wdata63);

    int write_all = (strb0 == 1 && strb1 == 1 && strb2 == 1 && strb3 == 1 && strb4 == 1 && strb5 == 1 && strb6 == 1 && strb7 == 1 && strb8 == 1 && strb9 == 1 && strb10 == 1 && strb11 == 1 && strb12 == 1 && strb13 == 1 && strb14 == 1 && strb15 == 1 && strb16 == 1 && strb17 == 1 && strb18 == 1 && strb19 == 1 && strb20 == 1 && strb21 == 1 && strb22 == 1 && strb23 == 1 && strb24 == 1 && strb25 == 1 && strb26 == 1 && strb27 == 1 && strb28 == 1 && strb29 == 1 && strb30 == 1 && strb31 == 1 && strb32 == 1 && strb33 == 1 && strb34 == 1 && strb35 == 1 && strb36 == 1 && strb37 == 1 && strb38 == 1 && strb39 == 1 && strb40 == 1 && strb41 == 1 && strb42 == 1 && strb43 == 1 && strb44 == 1 && strb45 == 1 && strb46 == 1 && strb47 == 1 && strb48 == 1 && strb49 == 1 && strb50 == 1 && strb51 == 1 && strb52 == 1 && strb53 == 1 && strb54 == 1 && strb55 == 1 && strb56 == 1 && strb57 == 1 && strb58 == 1 && strb59 == 1 && strb60 == 1 && strb61 == 1 && strb62 == 1 && strb63 == 1);

    if (write_all) {
      uint8_t *wdata = (uint8_t*) malloc(burstSizeBytes);
      wdata[0] = cmdWdata0;
      wdata[1] = cmdWdata1;
      wdata[2] = cmdWdata2;
      wdata[3] = cmdWdata3;
      wdata[4] = cmdWdata4;
      wdata[5] = cmdWdata5;
      wdata[6] = cmdWdata6;
      wdata[7] = cmdWdata7;
      wdata[8] = cmdWdata8;
      wdata[9] = cmdWdata9;
      wdata[10] = cmdWdata10;
      wdata[11] = cmdWdata11;
      wdata[12] = cmdWdata12;
      wdata[13] = cmdWdata13;
      wdata[14] = cmdWdata14;
      wdata[15] = cmdWdata15;
      wdata[16] = cmdWdata16;
      wdata[17] = cmdWdata17;
      wdata[18] = cmdWdata18;
      wdata[19] = cmdWdata19;
      wdata[20] = cmdWdata20;
      wdata[21] = cmdWdata21;
      wdata[22] = cmdWdata22;
      wdata[23] = cmdWdata23;
      wdata[24] = cmdWdata24;
      wdata[25] = cmdWdata25;
      wdata[26] = cmdWdata26;
      wdata[27] = cmdWdata27;
      wdata[28] = cmdWdata28;
      wdata[29] = cmdWdata29;
      wdata[30] = cmdWdata30;
      wdata[31] = cmdWdata31;
      wdata[32] = cmdWdata32;
      wdata[33] = cmdWdata33;
      wdata[34] = cmdWdata34;
      wdata[35] = cmdWdata35;
      wdata[36] = cmdWdata36;
      wdata[37] = cmdWdata37;
      wdata[38] = cmdWdata38;
      wdata[39] = cmdWdata39;
      wdata[40] = cmdWdata40;
      wdata[41] = cmdWdata41;
      wdata[42] = cmdWdata42;
      wdata[43] = cmdWdata43;
      wdata[44] = cmdWdata44;
      wdata[45] = cmdWdata45;
      wdata[46] = cmdWdata46;
      wdata[47] = cmdWdata47;
      wdata[48] = cmdWdata48;
      wdata[49] = cmdWdata49;
      wdata[50] = cmdWdata50;
      wdata[51] = cmdWdata51;
      wdata[52] = cmdWdata52;
      wdata[53] = cmdWdata53;
      wdata[54] = cmdWdata54;
      wdata[55] = cmdWdata55;
      wdata[56] = cmdWdata56;
      wdata[57] = cmdWdata57;
      wdata[58] = cmdWdata58;
      wdata[59] = cmdWdata59;
      wdata[60] = cmdWdata60;
      wdata[61] = cmdWdata61;
      wdata[62] = cmdWdata62;
      wdata[63] = cmdWdata63;


      if (debug) {
        EPRINTF("[sendWdataStrb dramCmdValid: %d, dramReadySeen: %d] %u %u %u %u\n", dramCmdValid, dramReadySeen, cmdWdata0, cmdWdata1, cmdWdata2, cmdWdata3);
        EPRINTF("                                                %u %u %u %u\n", cmdWdata4, cmdWdata5, cmdWdata6, cmdWdata7);
        EPRINTF("                                                %u %u %u %u\n", cmdWdata8, cmdWdata9, cmdWdata10, cmdWdata11);
        EPRINTF("                                                %u %u %u %u\n", cmdWdata12, cmdWdata13, cmdWdata14, cmdWdata15);
      }

      ASSERT(wrequestQ.size() > 0, "Wdata sent when no write requests were seen before!\n");

      DRAMRequest *req = wrequestQ.front();
      wrequestQ.pop_front();
      req->wdata = wdata;
      req->schedule();
    }


  }


  int sendDRAMRequest(
      long long addr,
      long long rawAddr,
      int size,
      int tag_uid,
      int tag_streamId,
      int isWr
    ) {
    int dramReady = 1;  // 1 == ready, 0 == not ready (stall upstream)

    // view addr as uint64_t without doing sign extension
    uint64_t cmdAddr = *(uint64_t*)&addr;
    uint64_t cmdRawAddr = *(uint64_t*)&rawAddr;
    DRAMTag cmdTag;
    cmdTag.uid = *(uint32_t*)&tag_uid;
    cmdTag.streamId = *(uint32_t*)&tag_streamId;
    uint32_t cmdSize = (uint32_t)(*(uint32_t*)&size);
    bool cmdIsWr = isWr > 0;

    // Create a DRAM Command
    DRAMCommand *cmd = new DRAMCommand(cmdAddr, cmdSize, cmdTag, cmdIsWr);

    // Create multiple DRAM requests, one per burst
    DRAMRequest **reqs = new DRAMRequest*[cmdSize];
    for (int i = 0; i<cmdSize; i++) {
      reqs[i] = new DRAMRequest(cmdAddr + i*burstSizeBytes, cmdRawAddr + i*burstSizeBytes, cmdSize, cmdTag, cmdIsWr, numCycles);
      reqs[i]->cmd = cmd;
    }
    cmd->reqs = reqs;

    if (debug) {
      EPRINTF("[sendDRAMRequest] Called with ");
      reqs[0]->print();
    }

//    if (!useIdealDRAM) {
      bool skipIssue = false;

      // For each burst request, create an AddrTag
      for (int i=0; i<cmdSize; i++) {
        DRAMRequest *req = reqs[i];
        struct AddrTag at(req->addr, req->tag);

        addrToReqMap[at] = req;
        skipIssue = false;

        // TODO: Re-examine gather-scatter flow
        if (!skipIssue) {
          if (cmdIsWr) {  // Schedule in sendWdata function, when wdata arrives
            wrequestQ.push_back(req);
          } else {
            req->schedule();  // Schedule request with DRAMSim2 / ideal DRAM
          }

        } else {
          if (debug) {
            EPRINTF("                  Skipping addr = %lx (%lx), tag = %lx\n", req->addr, req->rawAddr, req->tag);
          }
        }
      }

    // Push request into appropriate channel queue
    // Note that for ideal DRAM, since "scheduling" a request amounts to pushing the request
    // onto the right dramRequstQ, this happens within the schedule() method in DRAMRequest
    if (dramReady == 1) {
      for (int i=0; i<cmdSize; i++) {
        if (!useIdealDRAM) {
          dramRequestQ[cmdTag.streamId].push_back(reqs[i]);
        }// else {
//          dramRequestQ[reqs[i]->channelID].push_back(reqs[i]);
//        }
      }
    }

    return dramReady;
  }
}

void initDRAM() {
  char *idealDRAM = getenv("USE_IDEAL_DRAM");
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
      //sparseCacheSize = atoi(numOutstandingBursts);
    }
  }

  char *loadDelay = getenv("N3XT_LOAD_DELAY");
  if (loadDelay != NULL) {
    if (loadDelay[0] != 0 && atoi(loadDelay) > 0) {
      N3XT_LOAD_DELAY = (uint32_t) atoi(loadDelay);
    }
  }
  char *storeDelay = getenv("N3XT_STORE_DELAY");
  if (storeDelay != NULL) {
    if (storeDelay[0] != 0 && atoi(storeDelay) > 0) {
      N3XT_STORE_DELAY = (uint32_t) atoi(storeDelay);
    }
  }
  char *n3xtChannels = getenv("N3XT_NUM_CHANNELS");
  if (n3xtChannels != NULL) {
    if (n3xtChannels[0] != 0 && atoi(n3xtChannels) > 0) {
      N3XT_NUM_CHANNELS = (uint32_t) atoi(n3xtChannels);
    }
  }

  if (useIdealDRAM) {
    ASSERT(N3XT_NUM_CHANNELS < MAX_NUM_Q, "ERROR: N3XT_NUM_CHANNELS (%u) must be lesser than MAX_NUM_Q (%u)\n", N3XT_NUM_CHANNELS, MAX_NUM_Q);
    EPRINTF(" ****** Ideal DRAM configuration ******\n");
    EPRINTF("Num channels         : %u\n", N3XT_NUM_CHANNELS);
    EPRINTF("Load delay (cycles)  : %u\n", N3XT_LOAD_DELAY);
    EPRINTF("Store delay (cycles) : %u\n", N3XT_STORE_DELAY);
    EPRINTF(" **************************************\n");
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

  // Instantiate 64-to-32-bit address remapper
  remapper = new AddrRemapper();

  // Open trace file
  char *traceFileName = NULL;
  if (useIdealDRAM) {
    traceFileName = "trace_n3xt.log";
  } else {
    traceFileName = "trace_dramsim.log";
  }
  traceFp = fopen(traceFileName, "w");
  ASSERT(traceFp != NULL, "Unable to open file %s!\n", traceFileName);
}

