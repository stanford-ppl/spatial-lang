#include <unistd.h>
#include <errno.h>
#include <cstring>
#include <string>
#include <cstdlib>
#include <stdio.h>
#include <vector>
#include <queue>
#include <map>
#include <poll.h>
#include <fcntl.h>
#include <signal.h>
#include <sys/mman.h>
#include <sys/prctl.h>
using namespace std;

#include "vc_hdrs.h"
#include "svdpi_src.h"

class InputStream {
public:
  string filename;
  int fd;

  InputStream(string filename) : filename(filename) {

  }

  // Poll fd for new bytes
  // - New data exists: Read and pass to writeStream
  // - No new data: Return
  void send() {
    static uint32_t data = 0;
    static uint32_t tag = 0xF00DCAFE;
    uint32_t last = 0;
    writeStream(data++, tag, last);
  }

  ~InputStream() {
    close(fd);
  }
};


class OutputStream {
public:
  string filename;
  int fd;

  OutputStream(string filename) : filename(filename) {

  }

  // Callback function called from SV -> sim.cpp
  // Just write data into a file
  // [TODO] In current scheme, output stream is always ready. Model backpressure more accurately.
  void recv(uint32_t udata, uint32_t utag, bool blast) {
    // Currently just print read data out to console
    EPRINTF("[readOutputStream] data = %08x, tag = %08x, last = %x\n", udata, utag, blast);
  }

  ~OutputStream() {
    close(fd);
  }
};
