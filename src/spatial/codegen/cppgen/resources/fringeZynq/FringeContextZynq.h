#ifndef __FRINGE_CONTEXT_ZYNQ_H__
#define __FRINGE_CONTEXT_ZYNQ_H__

#include "FringeContextBase.h"
#include "ZynqAddressMap.h"
#include "ZynqUtils.h"
#include <cstring>
#include <stdlib.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <errno.h>

// The page frame shifted left by PAGE_SHIFT will give us the physcial address of the frame
// // Note that this number is architecture dependent. For me on x86_64 with 4096 page sizes,
// // it is defined as 12. If you're running something different, check the kernel source
// // for what it is defined as.
#define PAGE_SHIFT 12
#define PAGEMAP_LENGTH 8

/**
 * Zynq Fringe Context
 */
class FringeContextZynq : public FringeContextBase<void> {

  const uint32_t burstSizeBytes = 64;
  int fd = 0;
  u32 fringeScalarBase = 0;
  const u32 commandReg = 0;
  const u32 statusReg = 1;

  std::map<uint64_t, void*> physToVirtMap;

  void* physToVirt(uint64_t physAddr) {
    std::map<uint64_t, void*>::iterator iter = physToVirtMap.find(physAddr);
    if (iter == physToVirtMap.end()) {
      EPRINTF("Physical address '%x' not found in physToVirtMap\n. Was this allocated before?");
      exit(-1);
    }
    return iter->second;
  }

  uint64_t virtToPhys(void *virt) {
    uint64_t phys = 0;

    // Open the pagemap file for the current process
    FILE *pagemap = fopen("/proc/self/pagemap", "rb");
    FILE *origmap = pagemap;

    // Seek to the page that the buffer is on
    unsigned long offset = (unsigned long)virt/ getpagesize() * PAGEMAP_LENGTH;
    if(fseek(pagemap, (unsigned long)offset, SEEK_SET) != 0) {
      fprintf(stderr, "Failed to seek pagemap to proper location\n");
      exit(1);
    }

    // The page frame number is in bits 0-54 so read the first 7 bytes and clear the 55th bit
    unsigned long page_frame_number = 0;
    fread(&page_frame_number, 1, PAGEMAP_LENGTH-1, pagemap);

    page_frame_number &= 0x7FFFFFFFFFFFFF;

    fclose(origmap);

    // Find the difference from the virt to the page boundary
    unsigned int distance_from_page_boundary = (unsigned long)virt % getpagesize();
    // Determine how far to seek into memory to find the virt
    phys = (page_frame_number << PAGE_SHIFT) + distance_from_page_boundary;

    return phys;
  }

public:
  uint32_t numArgIns = 0;
  uint32_t numArgOuts = 0;
  std::string bitfile = "";

  FringeContextZynq(std::string path = "") : FringeContextBase(path) {
    bitfile = path;

    // open /dev/mem file
    int retval = setuid(0);
    ASSERT(retval == 0, "setuid(0) failed\n");
    fd = open ("/dev/mem", O_RDWR);
    if (fd < 1) {
      perror("error opening /dev/mem\n");
    }

    // Initialize pointers to fringeScalarBase
    void *ptr;
    ptr = mmap(NULL, MAP_LEN, PROT_READ|PROT_WRITE, MAP_SHARED, fd, FRINGE_SCALAR_BASEADDR);
    fringeScalarBase = (u32) ptr;
  }

  virtual void load() {
    std::string cmd = "prog_fpga " + bitfile;
    system(cmd.c_str());
  }

  size_t alignedSize(uint32_t alignment, size_t size) {
    if ((size % alignment) == 0) {
      return size;
    } else {
      return size + alignment - (size % alignment);
    }
  }

  virtual uint64_t malloc(size_t bytes) {
    size_t paddedSize = alignedSize(burstSizeBytes, bytes);
    void *ptr = aligned_alloc(burstSizeBytes, paddedSize);

    // Lock the page in memory
    // Do this before writing data to the buffer so that any copy-on-write
    // mechanisms will give us our own page locked in memory
    if(mlock(ptr, bytes) == -1) {
      fprintf(stderr, "Failed to lock page in memory: %s\n", strerror(errno));
      exit(1);
    }

    uint64_t physAddr = virtToPhys(ptr);
    physToVirtMap[physAddr] = ptr;

    return physAddr;
  }

  virtual void free(uint64_t buf) {
    std::free(physToVirt(buf));
  }

  virtual void memcpy(uint64_t devmem, void* hostmem, size_t size) {
    void *dst = physToVirt(devmem);
    std::memcpy(dst, hostmem, size);
  }

  virtual void memcpy(void* hostmem, uint64_t devmem, size_t size) {
    void *src = physToVirt(devmem);
    std::memcpy(hostmem, src, size);
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
    while (status == 1) {
      status = readReg(statusReg);
    }
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
    Xil_Out32(fringeScalarBase+reg*sizeof(u32), data);
  }

  virtual uint64_t readReg(uint32_t reg) {
    return Xil_In32(fringeScalarBase+reg*sizeof(u32));
  }

  ~FringeContextZynq() {
  }
};

// Fringe Simulation APIs
void fringeInit(int argc, char **argv) {
}
#endif
