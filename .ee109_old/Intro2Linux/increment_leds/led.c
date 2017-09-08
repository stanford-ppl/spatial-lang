#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <string.h>
#include <signal.h>
#include <unistd.h>
#include <sys/types.h>

#define HW_REGS_BASE 0xff200000
#define HW_REGS_SPAN 0x00200000
#define HW_REGS_MASK HW_REGS_SPAN - 1
#define LED_PIO_BASE 0x0

void *virtual_base;
int fd;

void ctrlCHandler(int);

void ctrlCHandler(int dummy)
{
	printf("CTRL+C detected...\n");
	// TODO: You may want to implement a signal handler in case that 
	// your user pressed ctrl+c during running the program. In this 
	// case you will still want to clean your memory before exiting
	// the program.
	exit(0);
}

int main(void)
{
	signal(SIGINT, ctrlCHandler);
	// This is the address where the ARM2FPGA registers will be mapped to. 
  volatile unsigned int *h2p_lw_led_addr=NULL;

	// Open the /dev/mem and keep the file descriptor
	if ((fd = open("/dev/mem", (O_RDWR | O_SYNC))) == -1)
	{
		printf("ERROR: could not open \"/dev/mem\"...\n");
		return(1);
	}
   
	// TODO: Please memory-map the ARM2FPGA bridge by using: 
	// mmap(addr, length, prot, flags, fd, offset)
	// Here is a link to the function description: http://man7.org/linux/man-pages/man2/mmap.2.html
	// Here we want to memory-map the whole HW_REGS_SPAN, with PROT_READ and PROT_WRITE access. 
	// The start of the physcial address is at HW_REGS_BASE.
	// Please don't forget to report an error if the memory-mapping step fails.

  // TODO: Add 1 to the PIO register.
  *h2p_lw_led_addr = *h2p_lw_led_addr + 1;
   
	// TODO: You will need to unmap the device here.

	// TODO: You will need to close the file descriptor here.
	return 0;
}
