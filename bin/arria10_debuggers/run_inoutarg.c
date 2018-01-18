#include <sys/mman.h>
#include <stdio.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <stdlib.h>


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
  printf("%d, %s\n", argc, argv[1]);
  *(fringeAddrPtr + 2) = argc > 1 ? atoi(argv[1]) : 100;
  *(fringeAddrPtr) = 1;
  while (1) {
    int done = *(fringeAddrPtr + 1);
    printf("done = %d\n", done);
    if (done)
      break;
  }

  int result = *(fringeAddrPtr + 3);
  printf("result = %d\n", result);

  munmap(virtualBase, PAGE_SIZE);
  close(fd);

  return 0;
}
