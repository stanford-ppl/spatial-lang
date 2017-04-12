#ifndef __FRINGE_CONTEXT_ZYNQ_H__
#define __FRINGE_CONTEXT_ZYNQ_H__

#define VIDEO_IN_ENABLE 0x00000004
#define HW_REGS_BASE ( 0xff200000 )
#define HW_REGS_SPAN ( 0x00200000 )
#define HW_REGS_MASK ( HW_REGS_SPAN - 1 )
#define ARG_IN_BASE 0x00040000

#include "FringeContextBase.h"
#include "DE1SoCAddressMap.h"
#include "ZynqUtils.h"
#include <cstring>
#include <stdlib.h>
#include <fcntl.h>
#include <sys/mman.h>

typedef unsigned long u32;

class FringeContextDE1SoC : public FringeContextBase<void> {

  const uint32_t burstSizeBytes = 16;
  int fd = 0;
  volatile unsigned int* fringeScalarBase = 0;
	volatile int* videoInPtr = NULL;
	const u32 commandReg = 0;
	const u32	statusReg = 1;
public:
  uint32_t numArgIns = 0;
  uint32_t numArgOuts = 0;
  std::string bitfile = "";

  FringeContextDE1SoC(std::string path = "") : FringeContextBase(path) {
    bitfile = path;

    // open /dev/mem file
    int retval = setuid(0);
    ASSERT(retval == 0, "setuid(0) failed\n");
    fd = open( "/dev/mem", (O_RDWR | O_SYNC));
    if (fd < 1) {
      perror("error opening /dev/mem\n");
    }

    // Initialize pointers to fringeScalarBase
    void *virtual_base = mmap(NULL, HW_REGS_SPAN, (PROT_READ | PROT_WRITE), MAP_SHARED, fd, HW_REGS_BASE);
		fringeScalarBase = (volatile unsigned int*) (virtual_base + (ARG_IN_BASE & HW_REGS_MASK)); 

		// Initialize pointers to video IP core
		videoInPtr = (int *)(virtual_base + ((VIDEO_IN_BASE - LEDR_BASE) & (HW_REGS_MASK)));
  }

  virtual void load() {
    std::string cmd = "./program_de1soc.sh " + bitfile;
		printf("Running cmd %s\n", cmd.c_str());
    system(cmd.c_str());

		// enable video
		int video_in = VIDEO_IN_ENABLE;
  	*(videoInPtr + 3)	= video_in;
  }

  size_t alignedSize(uint32_t alignment, size_t size) {
		printf("TODO: alignedSize not implemented.\n");
		return 0;
  }
  virtual uint64_t malloc(size_t bytes) {
   	printf("TODO: malloc not implemented.\n");
		return 0;
  }

  virtual void free(uint64_t buf) {
		printf("TODO: free not implemented.\n");
  }

  virtual void memcpy(uint64_t devmem, void* hostmem, size_t size) {
		printf("TODO: memcpy not implemented.\n");
  }

  virtual void memcpy(void* hostmem, uint64_t devmem, size_t size) {
		printf("TODO: memcpy not implemented.\n");
  }

  void dumpRegs() {
    fprintf(stderr, "---- DUMPREGS ----\n");
    for (int i=0; i<4; i++) {
      fprintf(stderr, "reg[%d] = %u\n", i, readReg(i));
    }
    fprintf(stderr, "---- END DUMPREGS ----\n");
  }

  virtual void run() {
     // Current assumption is that the design sets arguments individually
    uint32_t status = 0;

    // Implement 4-way handshake
    writeReg(statusReg, 0);
    writeReg(commandReg, 1);

    fprintf(stderr, "Running design..\n");
    double startTime = getTime();
    while((status == 0)) {
      status = readReg(statusReg);
    }
    double endTime = getTime();
    fprintf(stderr, "Design done, ran for %lf secs\n", endTime - startTime);
    writeReg(commandReg, 0);
//    while (status == 1) {
//      status = readReg(statusReg);
//    }
  }

  virtual void setArg(uint32_t arg, uint64_t data) {
		writeReg(arg+2, data);
    numArgIns++;
  }

  virtual uint64_t getArg(uint32_t arg) {
    numArgOuts++;
    return readReg(numArgIns+2+arg);

  }

  virtual void writeReg(uint32_t reg, uint64_t data) {
		volatile unsigned int *regPtr = fringeScalarBase + reg;
		*regPtr = (int)data;
  }

  virtual uint64_t readReg(uint32_t reg) {
		volatile unsigned int *regPtr = fringeScalarBase + reg;
		return (uint64_t)*regPtr;
  }

  ~FringeContextDE1SoC() {
  }
};

// Fringe Simulation APIs
void fringeInit(int argc, char **argv) {
}
#endif
