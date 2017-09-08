#include <stdint.h>
#include <sys/time.h>
#include <iostream>
#include <fstream>
#include <string> 
#include <sstream> 
#include <stdarg.h>
#include <signal.h>
#include <sys/wait.h>
#include <pwd.h>
#include <unistd.h>
#include <stdlib.h>
#include <stdio.h>
#include <errno.h>
#include "DeliteCpp.h"
#include "cppDeliteArraystring.h"
#include "cppDeliteArrays.h"
#include "cppDeliteArraydouble.h"
#include "FringeContext.h"
#include "functions.h"
#include <vector>

using std::vector;


void Application(int numThreads, vector<string> * args) {
  // Create an execution context.
  FringeContext *c1 = new FringeContext("./verilog/accel.bit.bin");
  c1->load();
  int32_t x130 = 0; // Initialize cpp argout ???
  // x131 = SliderSwitch // TODO: No idea what to connect this bus to, should expose periphal pins to something...
  // x132 = Forever
  time_t tstart = time(0);
  // Set up network
  struct sockaddr_in myaddr;  /* our address */
  struct sockaddr_in remaddr; /* remote address */
  socklen_t addrlen = sizeof(remaddr);        /* length of addresses */
  int recvlen;            /* # bytes received */
  int fd;             /* our socket */
  unsigned char buf[BUFSIZE]; /* receive buffer */

  /* create a UDP socket */
  if ((fd = socket(AF_INET, SOCK_DGRAM, 0)) < 0) {
    perror("cannot create socket\n");
    exit(1);
  }

  /* bind the socket to any valid IP address and a specific port */
  memset((char *)&myaddr, 0, sizeof(myaddr));
  myaddr.sin_family = AF_INET;
  myaddr.sin_addr.s_addr = htonl(INADDR_ANY);
  myaddr.sin_port = htons(SERVICE_PORT);

  if (bind(fd, (struct sockaddr *)&myaddr, sizeof(myaddr)) < 0) {
    perror("bind failed");
    exit(1);
  }

  c1->start();
  c1->enablePixelBuffer();

  int cntr = 0;
  bool writeToPix = 1;
  while (1)
  {
    recvlen = recvfrom(fd, buf, BUFSIZE, 0, (struct sockaddr *)&remaddr, &addrlen);
  
    if (recvlen > 0)      
    {
      int row = *(int *)buf;  
      short *bufPtr = (short *)(buf + 4);
      short *dataLineStart = bufPtr + 1;
      bool swapBuf = (row == FRAME_Y_LEN - 1);
      if (swapBuf) cntr ++;
      volatile int switchVal = (int)c1->readReg(io1);
      if (switchVal > 0)  
      {
        c1->enableCamera();
        writeToPix = 0;
      }
      else
      {
        c1->enablePixelBuffer();
        writeToPix = 1;
      }

      if (writeToPix)
        c1->writeRow2BackBuffer(row, dataLineStart, swapBuf);
    }
  }

  time_t tend = time(0);
  double elapsed = difftime(tend, tstart);
  std::cout << "Kernel done, test run time = " << elapsed << " ms" << std::endl;
  int32_t x136 = (int32_t) c1->getArg(0, true);
  string x137 = std::to_string(x136);
  string x138 = string_plus("received: ", x137);
  if (true) { std::cout << x138 << std::endl; }
  // results in ()
  delete c1;
}

int main(int argc, char *argv[]) {
  vector<string> *args = new vector<string>(argc-1);
  for (int i=1; i<argc; i++) {
    (*args)[i-1] = std::string(argv[i]);
  }
  int numThreads = 1;
  char *env_threads = getenv("DELITE_NUM_THREADS");
  if (env_threads != NULL) {
    numThreads = atoi(env_threads);
  } else {
    fprintf(stderr, "[WARNING]: DELITE_NUM_THREADS undefined, defaulting to 1\n");
  }
  fprintf(stderr, "Executing with %d thread(s)\n", numThreads);
  Application(numThreads, args);
  return 0;
}

