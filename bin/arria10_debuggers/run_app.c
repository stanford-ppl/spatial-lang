#include <sys/mman.h>
#include <stdio.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <stdlib.h>
#include <unistd.h>
#include "generated_debugRegs.h"


#define FPGA_BASE ( 0xff200000 )
#define FREEZE_BRIDGE_OFFSET ( 0x00000800 )
#define PR_SID_OFFSET ( 0x00000200 )
#define PR_DEFAULT_SID_OFFSET ( 0x00000000 )
#define PR_SRAM_OFFSET ( 0x00000000 )
#define PR_SRAM_SPAN ( 0x00000200 )
#define PAGE_SIZE ( 0x00001000 )

int main(int argc, char** argv)
{
  int fd = open("/dev/mem", (O_RDWR | O_SYNC));
  if (fd < 1)
  {
    perror("error opening /dev/mem\n");
    close(fd);
    return -1;
  }

  printf("/dev/mem opened successfully!\n");
  void* virtualBase = (void *)mmap(NULL, PAGE_SIZE, (PROT_READ | PROT_WRITE), MAP_SHARED, fd, FPGA_BASE);
  printf("virtualBase = 0x%x\n", (unsigned int)virtualBase);

  // read sys id
  printf("read sys id:\n")  ;
  int *sysIDPtr =  (int *)(virtualBase + FREEZE_BRIDGE_OFFSET + PR_SID_OFFSET);
  printf("0x%x\n", *sysIDPtr);

  // the argin/out registers are mapped at 0x0000 to 0x01ff
  int *fringeAddrPtr = (int *)(virtualBase + FREEZE_BRIDGE_OFFSET);
  // printf("%d, %s\n", argc, argv[1]);
  // *(fringeAddrPtr + 2) = argc > 1 ? atoi(argv[1]) : 100;
  printf("before writing app, the stats are: \n");
  printf("command = %d", *(fringeAddrPtr));
  printf("status = %d", *(fringeAddrPtr + 1));
  printf("start executing\n");

  int debugRegStart = 4;
  int totalRegs = 4 + NUM_DEBUG_SIGNALS;
  *(fringeAddrPtr) = 1;
  while (1) {
    int done = *(fringeAddrPtr + 1);
    printf("done = %d\n", done);
//    printf("<<<<<<<<<<\n");
//    printf("memlocs: \n");
//    int ii;
//    for (ii = 0; ii < 16; ii ++) {
//       printf("mem at %d = %x\n", ii, *(fringeAddrPtr + ii));
//    }

    int i;
    for (i = debugRegStart; i < totalRegs; i ++) {
      int value = *(fringeAddrPtr + i);
      printf("\tR%d (%s): %08x (%08u)\n", i, signalLabels[i - debugRegStart], value, value);
    }

    if (done)
      break;

    sleep(2);
  }

  munmap(virtualBase, PAGE_SIZE);
  close(fd);

  return 0;
}
