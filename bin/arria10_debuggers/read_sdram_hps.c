#include <sys/mman.h>
#include <stdio.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <stdlib.h>

#define HPS_SDRAM_BASE ( 0x00000000 )
#define PAGE_SIZE ( 0x00001000 )

int main(int argc, char ** argv)
{
  int fd = open("/dev/mem", (O_RDWR | O_SYNC));
  if (fd < 1)
  {
    perror("error opening /dev/mem\n");
    close(fd);
    return -1;
  }

  printf("/dev/mem opened successfully!\n");
  void *virtualBase = (void *)mmap(NULL, PAGE_SIZE, (PROT_READ | PROT_WRITE), MAP_SHARED, fd, HPS_SDRAM_BASE);

  printf("virtualBase = 0x%x\n", (unsigned int)virtualBase);

  // read back 64 locations
  int *memIntPtr = (int *)(virtualBase + 0x4340);
  int i;
  int range = 32;
  for (i = 0; i < range; i ++)
  {
    int *currPtr = memIntPtr + i;
    printf("read val = %d\n", *currPtr);
  }

  return 0;
}
