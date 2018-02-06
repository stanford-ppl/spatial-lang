#include <fcntl.h>
#include <sys/mman.h>
#include <stdio.h>
#include <sys/types.h>
#include <unistd.h>

#define FPGA_BASE ( 0xff200000 )
#define PAGE_SIZE ( 0x00001000 )
#define PR_SRAM_OFFSET ( 0x00000A00 )
#define PR_ID_OFFSET ( 0x100000a00 )


int main()
{
  int fd = open("/dev/mem", (O_RDWR | O_SYNC));
  if (fd < 1)
  {
    perror("error opening /dev/mem\n");
    return -1;
  }

  printf("/dev/mem opened successfully!\n");
  int *virtualBase = (int *)mmap(NULL, PAGE_SIZE, (PROT_READ | PROT_WRITE), MAP_SHARED, fd, PR_ID_OFFSET);
  printf("virtualBase = 0x%x\n", (unsigned int)virtualBase);


  int *sramStart = (int *)(virtualBase);

  printf("reading from PR ID:\n");
  int result = *sramStart;
  printf("result = %x\n", result);

  munmap(virtualBase, PAGE_SIZE);
  close(fd);

  return 0;
}
