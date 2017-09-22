#ifndef __FRINGE_CONTEXT_ZYNQ_H__
#define __FRINGE_CONTEXT_ZYNQ_H__

#define VIDEO_IN_ENABLE 0x00000004
#define HW_REGS_BASE ( 0xff200000 )
#define HW_REGS_SPAN ( 0x00200000 )
#define HW_REGS_MASK ( HW_REGS_SPAN - 1 )
#define ARG_IN_BASE 0x00040000
// include for constants
#define BUFSIZE 2048
#define SERVICE_PORT (21234)
#define PIXEL_BUFFER_SPAN (FPGA_ONCHIP_END - FPGA_ONCHIP_BASE + 1)
#define VIDEO_IN_CTRL_REG (0xff20306c)
#define VIDEO_IN_EDGE_REG (0xff203070)
#define FRAME_X_LEN (320)
#define FRAME_Y_LEN (240)
#define HW_REGS_MASK ( HW_REGS_SPAN - 1 )

// Front buffer and back buffer.
// These two will be swapped once one is filled.
#define BUF0 (SDRAM_BASE)
#define BUF1 (SDRAM_BASE + PIXEL_BUFFER_SPAN)

#include "FringeContextBase.h"
#include "DE1SoCAddressMap.h"
#include "ZynqUtils.h"
#include <cstring>
#include <stdlib.h>
#include <fcntl.h>
#include <sys/mman.h>

// include for networking
#include <sys/types.h>
#include <sys/socket.h>
#include <netdb.h>
#include <netinet/in.h>

// for hostIO
#include "DE1SoC.h"

typedef unsigned long u32;

class FringeContextDE1SoC : public FringeContextBase<void> {

  const uint32_t burstSizeBytes = 16;
  int fd = 0;

  volatile unsigned int* fringeScalarBase = 0;
	volatile int* videoInPtr = NULL;
  volatile void* pixelBufPtr = NULL;
  volatile void* buffPtr = NULL;
  volatile void* bufbPtr = NULL;
  volatile void* disp = NULL;

	const u32 commandReg = 0;
	const u32 statusReg = 1;

  // control registers for video IP core
  volatile int *frontBufReg = NULL;
  volatile int *backBufReg = NULL;
  volatile int *camStatusReg = NULL;

public:
  uint32_t numArgInsId = 0;
  uint32_t numArgOuts = 0;
  uint32_t numArgIOs = 0;
  uint32_t numArgIOsId = 0;
  uint32_t numArgIns = 0;
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
    void* virtual_base = mmap(NULL, HW_REGS_SPAN, (PROT_READ | PROT_WRITE), MAP_SHARED, fd, HW_REGS_BASE);
    fringeScalarBase = (volatile unsigned int*) (virtual_base + (ARG_IN_BASE & HW_REGS_MASK)); 

    // Initialize pointers to memories
    pixelBufPtr = mmap(NULL, PIXEL_BUFFER_SPAN, (PROT_READ | PROT_WRITE), MAP_SHARED, fd, FPGA_ONCHIP_BASE);
    buffPtr = mmap(NULL, PIXEL_BUFFER_SPAN, (PROT_READ | PROT_WRITE), MAP_SHARED, fd, BUF1);
    bufbPtr = mmap(NULL, PIXEL_BUFFER_SPAN, (PROT_READ | PROT_WRITE), MAP_SHARED, fd, BUF0);

    // Initialize pointers to video IP core
		videoInPtr = (int *)(virtual_base + ((VIDEO_IN_BASE - LEDR_BASE) & (HW_REGS_MASK)));
    frontBufReg = (int *)(virtual_base + ((PIXEL_BUF_CTRL_BASE - LEDR_BASE) & (HW_REGS_MASK))); 
    backBufReg = (int *)(virtual_base + ((PIXEL_BUF_CTRL_BASE - LEDR_BASE + 4) & (HW_REGS_MASK))); 
    camStatusReg = (int *)(virtual_base + ((PIXEL_BUF_CTRL_BASE - LEDR_BASE + 12) & (HW_REGS_MASK))); 
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

  virtual void setNumArgIns(uint32_t number) {
    numArgIns = number;
  }
  
  virtual void setNumArgIOs(uint32_t number) {
    numArgIOs = number;
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

  virtual void start()
  {
    writeReg(statusReg, 0);
    writeReg(commandReg, 1);
  }

  virtual bool isDone()
  {
    uint32_t status = 0;
    return (status == 1);
  }

  // camera APIs
  virtual void enableCamera()
  {
//    int video_in = VIDEO_IN_ENABLE;
//    *(videoInPtr + 3)	= video_in;

    *backBufReg = FPGA_ONCHIP_BASE;
    *frontBufReg = 1;
    while((*camStatusReg & 0x01) != 0);
//   disp = pixelBufPtr;
  }

  virtual void disableCamera()
  {
//    int video_in = VIDEO_IN_ENABLE;
//    video_in ^= VIDEO_IN_ENABLE;
//    *(videoInPtr + 3) = video_in;
  }

  virtual void enablePixelBuffer()
  {
    *backBufReg = BUF1;
    *frontBufReg = 1;
//    while((*camStatusReg & 0x01) != 0);
    *backBufReg = BUF0;
    disp = bufbPtr;
  }

  virtual void disablePixelBuffer()
  {
//    disp = pixelBufPtr;
  }

  virtual void swapBuf()
  {
    *frontBufReg = 1;
    while((*camStatusReg & 0x01) != 0);

    if (*backBufReg == BUF0)
    {
      *backBufReg = BUF1;
      disp = buffPtr;
    }
    else
    {
      *backBufReg = BUF0;
      disp = bufbPtr;
    }
  }

  // Currently swapping should happen for both frontBuffer and backBuffer cases
  virtual void writePixel2FrontBuffer(uint32_t row, uint32_t col, short pixel, bool swap)
  {
    writePixel2BackBuffer(row, col, pixel, swap);
  }

  virtual void writePixel2BackBuffer(uint32_t row, uint32_t col, short pixel, bool swap)
  {
    short *pixelLineStart = (short *)((char *)disp + (row << 10));  
    *(pixelLineStart + col) = pixel;
    if (swap) swapBuf();
  }

  virtual void writeRow2FrontBuffer(uint32_t row, short* buf, bool swap)
  {
    writeRow2BackBuffer(row, buf, swap);
  }

  virtual void writeRow2BackBuffer(uint32_t row, short* buf, bool swap)
  {
    // check if camera is enabled. If it is, we should 
    // printf("front buffer points to %x\n", *frontBufReg);
    short *pixelLineStart = (short *)((char *)disp + (row << 10));  
    std::memcpy(pixelLineStart, buf, FRAME_X_LEN * 2);
    if (swap) swapBuf();
  }

  virtual short readPixelFromCameraBuffer(uint32_t row, uint32_t col)
  {
    short *pixelLineStart = (short *)((char *)buffPtr + (row << 10));  
    return *(pixelLineStart + col);
  }

  virtual void readRowFromCameraBuffer(uint32_t row, short* buf)
  {
    short *pixelLineStart = (short *)((char *)buffPtr + (row << 10));  
    std::memcpy(buf, pixelLineStart, FRAME_X_LEN * 2);
  }

  virtual void run() {
     // Current assumption is that the design sets arguments individually
    uint32_t status = 0;

    // Implement 4-way handshake
    writeReg(statusReg, 0);
    writeReg(commandReg, 1);

    fprintf(stderr, "Running design..\n");
    double startTime = getTime();
    while (status == 0) {
      status = readReg(statusReg);
    }
    double endTime = getTime();
    fprintf(stderr, "Design done, ran for %lf secs\n", endTime - startTime);
    writeReg(commandReg, 0);
  }


  virtual void setArg(uint32_t arg, uint64_t data, bool isIO) {
    writeReg(arg+2, data);
    numArgInsId++;
    if (isIO) numArgIOsId++;
  }

  virtual uint64_t getArg(uint32_t arg, bool isIO) {
    numArgOuts++;
    if (isIO) {
      return readReg(2+arg);
    } else {
      if (numArgIns == 0) {
        return readReg(1-numArgIOs+2+arg);
      } else {
        return readReg(numArgIns-numArgIOs+2+arg);
      }

    }
  }

  virtual uint64_t getArgIn(uint32_t arg, bool isIO) {
    return readReg(2+arg);
  }

  // virtual void setArg(uint32_t arg, uint64_t data, bool isIO) {
  //   writeReg(arg+2, data);
  //   numArgIns++;
  //   if (isIO) numArgIOs++;
  // }

  // virtual uint64_t getArg(uint32_t arg, bool isIO) {
  //   numArgOuts++;
  //   if (isIO) {
  //     return readReg(2+arg);
  //   } else {
  //     return readReg(numArgIns-numArgIOs+2+arg);  
  //   }
  // }

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
