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
#include <vector>
using std::vector;


void Application(int numThreads, cppDeliteArraystring * args) {
  // Create an execution context.
  FringeContext *c1 = new FringeContext();
  //int32_t* x150_argin = new int32_t {0}; // Initialize cpp argin ???
  //int32_t* x151_argout = new int32_t {0}; // Initialize cpp argout ???
  cppDeliteArraystring* x2 = args;
  string x3 = x2->apply(0);
  int32_t x4 = atoi(x3.c_str());
  c1->setArg(0, x4); // x152_set150
  int32_t x150_argin = x4;
  time_t tstart = time(0);
  c1->run();
  time_t tend = time(0);
  double elapsed = difftime(tend, tstart);
  std::cout << "Kernel done, test run time = " << elapsed << " ms" << std::endl;
  int32_t x157_get151 = (int32_t) c1->getArg(0);
  int32_t x11_sumx4_unk = x4 + 4;
  string x12 = std::to_string(x11_sumx4_unk);
  string x13 = string_plus("expected: ", x12);
  if (true) { std::cout << x13 << std::endl; }
  string x159 = std::to_string(x157_get151);
  string x160 = string_plus("result: ", x159);
  if (true) { std::cout << x160 << std::endl; }
  // results in ()
}

int main(int argc, char *argv[]) {
  cppDeliteArraystring *args = new cppDeliteArraystring(argc-1);
  for (int i=1; i<argc; i++) {
    args->update(i-1, *(new string(argv[i])));
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

