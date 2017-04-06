#ifndef __FRINGE_CONTEXT_VCS_H__
#define __FRINGE_CONTEXT_VCS_H__

#include <spawn.h>
#include <poll.h>
#include <sys/wait.h>
#include <iostream>
#include <fstream>
#include <vector>
#include <cstdlib>
#include <cstring>
#include <fcntl.h>
#include <errno.h>
#include <stdio.h>
#include <unistd.h>

#include "FringeContextBase.h"
#include "simDefs.h"
#include "channel.h"

//Source: http://stackoverflow.com/questions/13893085/posix-spawnp-and-piping-child-output-to-a-string
class FringeContextVCS : public FringeContextBase<void> {

  pid_t sim_pid;
  Channel *cmdChannel;
  Channel *respChannel;
  uint64_t numCycles = 0;
  uint32_t numArgIns = 0;
  uint32_t numArgOuts = 0;

  const uint32_t burstSizeBytes = 64;
  const uint32_t commandReg = 0;
  const uint32_t statusReg = 1;
  const uint64_t maxCycles = 500000;

  posix_spawn_file_actions_t action;
  int globalID = 1;

  int sendCmd(SIM_CMD cmd) {
    simCmd simCmd;
    simCmd.id = globalID++;
    simCmd.cmd = cmd;

    switch (cmd) {
      case RESET:
        simCmd.size = 0;
        break;
      case START:
        simCmd.size = 0;
        break;
      case STEP:
        simCmd.size = 0;
        break;
      case FIN:
        simCmd.size = 0;
        break;
      case READY:
        simCmd.size = 0;
        break;
      default:
        EPRINTF("Command %d not supported!\n", cmd);
        exit(-1);
    }

    cmdChannel->send(&simCmd);
    return simCmd.id;
  }

  simCmd* recvResp() {
    return (simCmd*) respChannel->recv();
  }

public:
  void step() {
    sendCmd(STEP);
    numCycles++;
    if ((numCycles % 10000) == 0) {
      EPRINTF("\t%lu cycles elapsed\n", numCycles);
    }
  }

  void finish() {
    sendCmd(FIN);
  }

  void reset() {
    sendCmd(RESET);
  }

  void start() {
    sendCmd(START);
  }

  virtual void writeReg(uint32_t reg, uint64_t data) {
    simCmd cmd;
    cmd.id = globalID++;
    cmd.cmd = WRITE_REG;
    std::memcpy(cmd.data, &reg, sizeof(uint32_t));
    std::memcpy(cmd.data+sizeof(uint32_t), &data, sizeof(uint64_t));
    cmd.size = sizeof(uint64_t);
    cmdChannel->send(&cmd);
  }

  virtual uint64_t readReg(uint32_t reg) {
    simCmd cmd;
    simCmd *resp = NULL;
    cmd.id = globalID++;
    cmd.cmd = READ_REG;
    cmd.size = 0;
    std::memcpy(cmd.data, &reg, sizeof(uint32_t));
    cmdChannel->send(&cmd);
    resp = recvResp();
    ASSERT(resp->cmd == READ_REG, "Response from Sim is not READ_REG");
    uint64_t rdata = *(uint64_t*)resp->data;
    return rdata;
  }

  virtual uint64_t malloc(size_t bytes) {
    simCmd cmd;
    cmd.id = globalID++;
    cmd.cmd = MALLOC;
    std::memcpy(cmd.data, &bytes, sizeof(size_t));
    cmd.size = sizeof(size_t);
    cmdChannel->send(&cmd);
    simCmd *resp = recvResp();
    ASSERT(cmd.id == resp->id, "malloc resp->id does not match cmd.id!");
    ASSERT(cmd.cmd == resp->cmd, "malloc resp->cmd does not match cmd.cmd!");
    return (uint64_t)(*(uint64_t*)resp->data);
  }

  virtual void free(uint64_t buf) {
    simCmd cmd;
    cmd.id = globalID++;
    cmd.cmd = FREE;
    std::memcpy(cmd.data, &buf, sizeof(uint64_t));
    cmd.size = sizeof(uint64_t);
    cmdChannel->send(&cmd);
  }

  virtual void memcpy(uint64_t dst, void *src, size_t bytes) {
    simCmd cmd;
    cmd.id = globalID++;
    cmd.cmd = MEMCPY_H2D;
    uint64_t *data = (uint64_t*)cmd.data;
    data[0] = dst;
    data[1] = bytes;
    cmd.size = 2* sizeof(uint64_t);
    cmdChannel->send(&cmd);

    // Now send fixed 'bytes' from src
    cmdChannel->sendFixedBytes(src, bytes);

    // Wait for ack
    simCmd *resp = recvResp();
    ASSERT(cmd.id == resp->id, "memcpy resp->id does not match cmd.id!");
    ASSERT(cmd.cmd == resp->cmd, "memcpy resp->cmd does not match cmd.cmd!");
  }

  virtual void memcpy(void *dst, uint64_t src, size_t bytes) {
    simCmd cmd;
    cmd.id = globalID++;
    cmd.cmd = MEMCPY_D2H;
    uint64_t *data = (uint64_t*)cmd.data;
    data[0] = src;
    data[1] = bytes;
    cmd.size = 2* sizeof(uint64_t);
    cmdChannel->send(&cmd);

    // Now receive fixed 'bytes' from src
    respChannel->recvFixedBytes(dst, bytes);
  }

  void connect() {
    int id = sendCmd(READY);
    simCmd *cmd = recvResp();
    ASSERT(cmd->id == id, "Error: Received ID does not match sent ID\n");
    ASSERT(cmd->cmd == READY, "Error: Received cmd is not 'READY'\n");
    EPRINTF("Connection successful!\n");
  }

  FringeContextVCS(std::string path = "") : FringeContextBase(path) {
    cmdChannel = new Channel(sizeof(simCmd));
    respChannel = new Channel(sizeof(simCmd));

    posix_spawn_file_actions_init(&action);

    // Create cmdPipe (read) handle at SIM_CMD_FD, respPipe (write) handle at SIM_RESP_FD
    // Close old descriptors after dup2
    posix_spawn_file_actions_addclose(&action, cmdChannel->writeFd());
    posix_spawn_file_actions_addclose(&action, respChannel->readFd());
    posix_spawn_file_actions_adddup2(&action, cmdChannel->readFd(), SIM_CMD_FD);
    posix_spawn_file_actions_adddup2(&action, respChannel->writeFd(), SIM_RESP_FD);

    std::string argsmem[] = {path};
    char *args[] = {&argsmem[0][0],nullptr};

    // Pass required environment variables to simulator
    // LD_LIBRARY_PATH
    // DRAMSIM_HOME
    // ..(others)..
    char *ldPath = getenv("LD_LIBRARY_PATH");
    char *dramPath = getenv("DRAMSIM_HOME");
    ASSERT(ldPath != NULL, "ldPath is NULL");
    ASSERT(dramPath != NULL, "dramPath is NULL");

    std::string ldLib = "LD_LIBRARY_PATH=" + string(getenv("LD_LIBRARY_PATH"));
    std::string dramSimHome = "DRAMSIM_HOME=" + string(getenv("DRAMSIM_HOME"));
    std::string idealDram = "USE_IDEAL_DRAM=" + string(getenv("USE_IDEAL_DRAM"));
    std::string envstrings[] = {ldLib, dramSimHome, idealDram};
    char *envs[] = {&envstrings[0][0], &envstrings[1][0], &envstrings[2][0], nullptr};

    if(posix_spawnp(&sim_pid, args[0], &action, NULL, &args[0], &envs[0]) != 0) {
      EPRINTF("posix_spawnp failed, error = %s\n", strerror(errno));
      exit(-1);
    }

    // Close Sim side of pipes
    close(cmdChannel->readFd());
    close(respChannel->writeFd());

    // Connect with simulator
    connect();
  }

  virtual void load() {
    for (int i=0; i<5; i++) {
      reset();
    }
    start();
  }

  virtual void run() {
    // Current assumption is that the design sets arguments individually
    uint32_t status = 0;

    // Implement 4-way handshake
    writeReg(statusReg, 0);
    writeReg(commandReg, 1);
    numCycles = 0;  // restart cycle count (incremented with each step())
    while((status == 0) && (numCycles <= maxCycles)) {
      step();
      status = readReg(statusReg);
    }
    EPRINTF("Design ran for %lu cycles, status = %u\n", numCycles, status);
    if (status == 0) { // Design did not run to completion
      EPRINTF("=========================================\n");
      EPRINTF("ERROR: Simulation terminated after %lu cycles\n", numCycles);
      EPRINTF("=========================================\n");
    } else {  // Ran to completion, pull down command signal
      writeReg(commandReg, 0);
      while (status == 1) {
        step();
        status = readReg(statusReg);
      }
    }
  }

  virtual void setArg(uint32_t arg, uint64_t data) {
    writeReg(arg+2, data);
    numArgIns++;
  }

  virtual uint64_t getArg(uint32_t arg) {
    numArgOuts++;
    return readReg(numArgIns+2+arg);
  }

  ~FringeContextVCS() {
    finish();
  }
};

// Fringe Simulation APIs
void fringeInit(int argc, char **argv) {
}

#endif
