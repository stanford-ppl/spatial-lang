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
  cppDeliteArraystring* x0 = args;
  string x1 = x0->apply(0);
  int32_t x2 = atoi(x1.c_str());
  vector<int32_t>* x1212 = new vector<int32_t>(x2);
  for (int b3 = 0; b3 < x2; b3++) {
    int32_t x1211_fixrnd = rand() % 100;
    // results in x1211_fixrnd
    (*x1212)[b3] = x1211_fixrnd;
  }
  vector<int32_t>* x1214 = new vector<int32_t>(x2);
  for (int b6 = 0; b6 < x2; b6++) {
    int32_t x1213_fixrnd = rand() % 100;
    // results in x1213_fixrnd
    (*x1214)[b6] = x1213_fixrnd;
  }
  //int32_t* x1215_argin = new int32_t {0}; // Initialize cpp argin ???
  int32_t x1216 = (*x1212).size();
  c1->setArg(0, x1216); // x1217_set1215
  int32_t x1215_argin = x1216;
  int32_t x1218_readx1215 = x1215_argin;
  uint64_t x1219_a = c1->malloc(sizeof(int32_t) * x1218_readx1215);
  c1->setArg(1, x1219_a); // (memstream in: 0, out: -1)
  printf("Allocate mem of size x1218_readx1215 at %x\n", x1219_a);
  int32_t x1220_readx1215 = x1215_argin;
  uint64_t x1221_b = c1->malloc(sizeof(int32_t) * x1220_readx1215);
  c1->setArg(2, x1221_b); // (memstream in: 1, out: -1)
  printf("Allocate mem of size x1220_readx1215 at %x\n", x1221_b);
  //int32_t* x1222_argout = new int32_t {0}; // Initialize cpp argout ???
  c1->memcpy(x1219_a, &(*x1212)[0], (*x1212).size() * sizeof(int32_t));
  c1->memcpy(x1221_b, &(*x1214)[0], (*x1214).size() * sizeof(int32_t));
  time_t tstart = time(0);
  c1->run();
  time_t tend = time(0);
  double elapsed = difftime(tend, tstart);
  std::cout << "Kernel done, test run time = " << elapsed << " ms" << std::endl;
  int32_t x1328_get1222 = (int32_t) c1->getArg(0);
  vector<int32_t>* x1332 = new vector<int32_t>((*x1212).size());
  for (int b58 = 0; b58 < (*x1212).size(); b58++) { 
    int32_t x1329 = (*x1212)[b58];
    int32_t x1330 = (*x1214)[b58];
    int32_t x1331 = x1329 * x1330;
    // results in x1331
    (*x1332)[b58] = x1331;
  }
  int32_t x1334;
  if ((*x1332).size() > 0) { // Hack to handle reductions on things of length 0
    x1334 = (*x1332)[0];
  } else {
    x1334 = 0;
  }
  for (int b63 = 1; b63 < (*x1332).size(); b63++) {
    int32_t b64 = (*x1332)[b63];
    int32_t b65 = x1334;
    int32_t x67_sumunk_unk = b64 + b65;
    // results in x67_sumunk_unk
    x1334 = x67_sumunk_unk;
  }
  string x1335 = std::to_string(x1334);
  string x1336 = string_plus("expected: ", x1335);
  if (true) { std::cout << x1336 << std::endl; }
  string x1338 = std::to_string(x1328_get1222);
  string x1339 = string_plus("result: ", x1338);
  if (true) { std::cout << x1339 << std::endl; }
  bool x1341 = x1334 == x1328_get1222;
  string x1342 = std::to_string(x1341);
  string x1343 = string_plus("PASS: ", x1342);
  string x1344 = string_plus(x1343, " (DotProduct)");
  if (true) { std::cout << x1344 << std::endl; }
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

