#ifndef __CHANNEL_H
#define __CHANNEL_H

#include <unistd.h>
#include <errno.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <assert.h>
#include "commonDefs.h"

#define READ 0
#define WRITE 1

class Channel {
  int pipeFd[2];
  uint8_t *buf;

public:
  Channel() {
    if (pipe(pipeFd)) {
      EPRINTF("Failed to create pipe, error = %s\n", strerror(errno));
      exit(-1);
    }

    buf = (uint8_t*) malloc(sizeof(simCmd));
    memset(buf, 0, sizeof(simCmd));
  }

  Channel(int readfd, int writefd) {
    pipeFd[READ] = readfd;
    pipeFd[WRITE] = writefd;

    buf = (uint8_t*) malloc(sizeof(simCmd));
    memset(buf, 0, sizeof(simCmd));
  }

  int writeFd() {
    return pipeFd[WRITE];
  }

  int readFd() {
    return pipeFd[READ];
  }

  void printPkt(simCmd *cmd) {
    EPRINTF("----- printPkt -----\n");
    EPRINTF("ID   : %d\n", cmd->id);
    EPRINTF("CMD  : %d\n", cmd->cmd);
    EPRINTF("SIZE : %lu\n", cmd->size);
    EPRINTF("----- End printPkt -----\n");
  }

  void send(simCmd *cmd) {
    int bytes = write(pipeFd[WRITE], cmd, sizeof(simCmd));
    if (bytes < 0) {
      EPRINTF("Error sending cmd, error = %s\n", strerror(errno));
      exit(-1);
    }
  }

	simCmd* recv() {
    memset(buf, 0, sizeof(simCmd));

    std::vector<pollfd> plist = { {pipeFd[READ], POLLIN} };

    for (int rval; (rval=poll(&plist[0], plist.size(), /*timeout*/-1)) > 0; ) {
      if (plist[0].revents & POLLIN) {
        int bytesRead = read(pipeFd[READ], buf, sizeof(simCmd));
        if (bytesRead > 0) {
          break;
        }
      } else {
        break; // nothing left to read
      }
    }
    return (simCmd*)buf;
	}
};

#endif // __CHANNEL_H
