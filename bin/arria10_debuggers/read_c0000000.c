#include <sys/mman.h>
#include <stdio.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>

#define FPGA_BASE ( 0xc0000000 )
#define PAGE_SIZE ( 0x00001000 )

int main()
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
  int i;
  for (i = 0; i < 32; i ++) {
    int *currPtr = (int *)virtualBase + i;
    printf("read val = %d\n", *currPtr);
  }

  munmap(virtualBase, PAGE_SIZE);
  close(fd);

  return 0;
}
