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
  vector<int32_t>* x4178 = new vector<int32_t>(x2);
  for (int b3 = 0; b3 < x2; b3++) {
    int32_t x4177_fixrnd = rand() % 2;
    // results in x4177_fixrnd
    (*x4178)[b3] = x4177_fixrnd;
  }
  vector<double>* x4180 = new vector<double>(x2);
  for (int b6 = 0; b6 < x2; b6++) {
    double x4179_fixrnd = rand() % 100;
    // results in x4179_fixrnd
    (*x4180)[b6] = x4179_fixrnd;
  }
  vector<double>* x4182 = new vector<double>(x2);
  for (int b9 = 0; b9 < x2; b9++) {
    double x4181_fixrnd = rand() % 100;
    // results in x4181_fixrnd
    (*x4182)[b9] = x4181_fixrnd;
  }
  vector<double>* x4184 = new vector<double>(x2);
  for (int b12 = 0; b12 < x2; b12++) {
    double x4183_fixrnd = rand() % 100;
    // results in x4183_fixrnd
    (*x4184)[b12] = x4183_fixrnd;
  }
  vector<double>* x4186 = new vector<double>(x2);
  for (int b15 = 0; b15 < x2; b15++) {
    double x4185_fixrnd = rand() % 100;
    // results in x4185_fixrnd
    (*x4186)[b15] = x4185_fixrnd;
  }
  vector<double>* x4188 = new vector<double>(x2);
  for (int b18 = 0; b18 < x2; b18++) {
    double x4187_fixrnd = rand() % 100;
    // results in x4187_fixrnd
    (*x4188)[b18] = x4187_fixrnd;
  }
  //int32_t* x4189_argin = new int32_t {0}; // Initialize cpp argin ???
  int32_t x4190 = (*x4178).size();
  c1->setArg(0, x4190); // x4191_set4189
  int32_t x4189_argin = x4190;
  int32_t x4192_readx4189 = x4189_argin;
  uint64_t x4193_types = c1->malloc(sizeof(int32_t) * x4192_readx4189);
  c1->setArg(7, x4193_types); // (memstream in: 6, out: -1)
  printf("Allocate mem of size x4192_readx4189 at %x\n", x4193_types);
  int32_t x4194_readx4189 = x4189_argin;
  uint64_t x4195_prices = c1->malloc(sizeof(int32_t) * x4194_readx4189);
  c1->setArg(4, x4195_prices); // (memstream in: 3, out: -1)
  printf("Allocate mem of size x4194_readx4189 at %x\n", x4195_prices);
  int32_t x4196_readx4189 = x4189_argin;
  uint64_t x4197_strike = c1->malloc(sizeof(int32_t) * x4196_readx4189);
  c1->setArg(6, x4197_strike); // (memstream in: 5, out: -1)
  printf("Allocate mem of size x4196_readx4189 at %x\n", x4197_strike);
  int32_t x4198_readx4189 = x4189_argin;
  uint64_t x4199_rate = c1->malloc(sizeof(int32_t) * x4198_readx4189);
  c1->setArg(3, x4199_rate); // (memstream in: 2, out: -1)
  printf("Allocate mem of size x4198_readx4189 at %x\n", x4199_rate);
  int32_t x4200_readx4189 = x4189_argin;
  uint64_t x4201_vol = c1->malloc(sizeof(int32_t) * x4200_readx4189);
  c1->setArg(1, x4201_vol); // (memstream in: 0, out: -1)
  printf("Allocate mem of size x4200_readx4189 at %x\n", x4201_vol);
  int32_t x4202_readx4189 = x4189_argin;
  uint64_t x4203_times = c1->malloc(sizeof(int32_t) * x4202_readx4189);
  c1->setArg(5, x4203_times); // (memstream in: 4, out: -1)
  printf("Allocate mem of size x4202_readx4189 at %x\n", x4203_times);
  int32_t x4204_readx4189 = x4189_argin;
  uint64_t x4205_optprice = c1->malloc(sizeof(int32_t) * x4204_readx4189);
  c1->setArg(2, x4205_optprice); // (memstream in: -1, out: 1)
  printf("Allocate mem of size x4204_readx4189 at %x\n", x4205_optprice);
  c1->memcpy(x4193_types, &(*x4178)[0], (*x4178).size() * sizeof(int32_t));
  vector<int32_t>* x4195_prices_rawified = new vector<int32_t>((*x4180).size());
  for (int x4195_prices_rawified_i = 0; x4195_prices_rawified_i < (*x4180).size(); x4195_prices_rawified_i++) {
    (*x4195_prices_rawified)[x4195_prices_rawified_i] = (int32_t) ((*x4180)[x4195_prices_rawified_i] * (1 << 16));
  }
  c1->memcpy(x4195_prices, &(*x4195_prices_rawified)[0], (*x4195_prices_rawified).size() * sizeof(int32_t));
  vector<int32_t>* x4197_strike_rawified = new vector<int32_t>((*x4182).size());
  for (int x4197_strike_rawified_i = 0; x4197_strike_rawified_i < (*x4182).size(); x4197_strike_rawified_i++) {
    (*x4197_strike_rawified)[x4197_strike_rawified_i] = (int32_t) ((*x4182)[x4197_strike_rawified_i] * (1 << 16));
  }
  c1->memcpy(x4197_strike, &(*x4197_strike_rawified)[0], (*x4197_strike_rawified).size() * sizeof(int32_t));
  vector<int32_t>* x4199_rate_rawified = new vector<int32_t>((*x4184).size());
  for (int x4199_rate_rawified_i = 0; x4199_rate_rawified_i < (*x4184).size(); x4199_rate_rawified_i++) {
    (*x4199_rate_rawified)[x4199_rate_rawified_i] = (int32_t) ((*x4184)[x4199_rate_rawified_i] * (1 << 16));
  }
  c1->memcpy(x4199_rate, &(*x4199_rate_rawified)[0], (*x4199_rate_rawified).size() * sizeof(int32_t));
  vector<int32_t>* x4201_vol_rawified = new vector<int32_t>((*x4186).size());
  for (int x4201_vol_rawified_i = 0; x4201_vol_rawified_i < (*x4186).size(); x4201_vol_rawified_i++) {
    (*x4201_vol_rawified)[x4201_vol_rawified_i] = (int32_t) ((*x4186)[x4201_vol_rawified_i] * (1 << 16));
  }
  c1->memcpy(x4201_vol, &(*x4201_vol_rawified)[0], (*x4201_vol_rawified).size() * sizeof(int32_t));
  vector<double>* x4212 = new vector<double>((*x4188).size());
  for (int b21 = 0; b21 < (*x4188).size(); b21++) { 
    double x4211 = (*x4188)[b21];
    // results in x4211
    (*x4212)[b21] = x4211;
  }
  vector<int32_t>* x4203_times_rawified = new vector<int32_t>((*x4212).size());
  for (int x4203_times_rawified_i = 0; x4203_times_rawified_i < (*x4212).size(); x4203_times_rawified_i++) {
    (*x4203_times_rawified)[x4203_times_rawified_i] = (int32_t) ((*x4212)[x4203_times_rawified_i] * (1 << 16));
  }
  c1->memcpy(x4203_times, &(*x4203_times_rawified)[0], (*x4203_times_rawified).size() * sizeof(int32_t));
  time_t tstart = time(0);
  c1->run();
  time_t tend = time(0);
  double elapsed = difftime(tend, tstart);
  std::cout << "Kernel done, test run time = " << elapsed << " ms" << std::endl;
  vector<double>* x4471 = new vector<double>(x4204_readx4189);
  vector<int32_t>* x4471_rawified = new vector<int32_t>((*x4471).size());
  c1->memcpy(&(*x4471_rawified)[0], x4205_optprice, (*x4471_rawified).size() * sizeof(int32_t));
  for (int x4471_i = 0; x4471_i < (*x4471).size(); x4471_i++) {
    (*x4471)[x4471_i] = (double) (*x4471_rawified)[x4471_i] / (1 << 16);
  }
  if (true) { std::cout << "gold: " << std::endl; }
  vector<double>* x4557 = new vector<double>(x2);
  for (int b173 = 0; b173 < x2; b173++) {
    double x4474 = (*x4180)[b173];
    double x4475 = (*x4182)[b173];
    double x4476 = x4474 / x4475;
    double x4477 = (*x4188)[b173];
    double x4478 = (*x4184)[b173];
    double x4479 = (*x4186)[b173];
    double x4480 = x4479 * x4479;
    double x4481 = x4480 * 0.5;
    double x4482_sumx4478_x4481 = x4478 + x4481;
    double x4483 = x4482_sumx4478_x4481 * x4477;
    double x4484_sumx4483_x4476 = x4483 + x4476;
    double x4485 = x4479 * x4477;
    double x4486 = x4485 * x4477;
    double x4487 = x4486 * x4486;
    double x4488 = x4484_sumx4483_x4476 / x4487;
    double x4489 = abs(x4488);
    double x4490 = x4489 * x4489;
    double x4491 = x4490 * -0.05;
    double x4492 = x4491 * 0.3989422804014327;
    double x4493 = x4489 * 0.2316419;
    double x4494_sumx4493_unk = x4493 + 1.0;
    double x4495 = 1 / x4494_sumx4493_unk;
    double x4496 = x4495 * 0.31938153;
    double x4497 = x4495 * x4495;
    double x4498 = x4497 * x4495;
    double x4499 = x4498 * x4495;
    double x4500 = x4499 * x4495;
    double x4501 = x4500 * 1.330274429;
    double x4502 = x4499 * -1.821255978;
    double x4503 = x4497 * -0.356563782;
    double x4504 = x4498 * 1.781477937;
    double x4505_sumx4503_x4504 = x4503 + x4504;
    double x4506_sumx4505_x4502 = x4505_sumx4503_x4504 + x4502;
    double x4507_sumx4506_x4501 = x4506_sumx4505_x4502 + x4501;
    double x4508_sumx4507_x4496 = x4507_sumx4506_x4501 + x4496;
    double x4509 = x4508_sumx4507_x4496 * x4492;
    double x4510_negx4509 = -x4509;
    double x4511_sumx4510_unk = x4510_negx4509 + 1.0;
    bool x4512 = x4488 < 0.0;
    double x4513;
    if (x4512){ x4513 = x4509; } else { x4513 = x4511_sumx4510_unk; }
    double x4514 = x4474 * x4513;
    double x4515 = x4488 - x4486;
    double x4516 = abs(x4515);
    double x4517 = x4516 * x4516;
    double x4518 = x4517 * -0.05;
    double x4519 = x4518 * 0.3989422804014327;
    double x4520 = x4516 * 0.2316419;
    double x4521_sumx4520_unk = x4520 + 1.0;
    double x4522 = 1 / x4521_sumx4520_unk;
    double x4523 = x4522 * 0.31938153;
    double x4524 = x4522 * x4522;
    double x4525 = x4524 * x4522;
    double x4526 = x4525 * x4522;
    double x4527 = x4526 * x4522;
    double x4528 = x4527 * 1.330274429;
    double x4529 = x4526 * -1.821255978;
    double x4530 = x4524 * -0.356563782;
    double x4531 = x4525 * 1.781477937;
    double x4532_sumx4530_x4531 = x4530 + x4531;
    double x4533_sumx4532_x4529 = x4532_sumx4530_x4531 + x4529;
    double x4534_sumx4533_x4528 = x4533_sumx4532_x4529 + x4528;
    double x4535_sumx4534_x4523 = x4534_sumx4533_x4528 + x4523;
    double x4536 = x4535_sumx4534_x4523 * x4519;
    double x4537_negx4536 = -x4536;
    double x4538_sumx4537_unk = x4537_negx4536 + 1.0;
    bool x4539 = x4515 < 0.0;
    double x4540;
    if (x4539){ x4540 = x4536; } else { x4540 = x4538_sumx4537_unk; }
    double x4541_negx4478 = -x4478;
    double x4542 = x4475 * x4541_negx4478;
    double x4543 = (double) x4477;  // should be fixpt double
    double x4544 = x4542 * x4543;
    double x4545 = x4544 * x4540;
    double x4546 = x4514 - x4545;
    double x4547_negx4540 = -x4540;
    double x4548_sumx4547_unk = x4547_negx4540 + 1.0;
    double x4549 = x4544 * x4548_sumx4547_unk;
    double x4550_negx4513 = -x4513;
    double x4551_sumx4550_unk = x4550_negx4513 + 1.0;
    double x4552 = x4474 * x4551_sumx4550_unk;
    double x4553 = x4549 - x4552;
    int32_t x4554 = (*x4178)[b173];
    bool x4555 = x4554 == 0;
    double x4556;
    if (x4555){ x4556 = x4553; } else { x4556 = x4546; }
    // results in x4556
    (*x4557)[b173] = x4556;
  }
  int32_t x4558 = (*x4557).size();
  for (int b260 = 0; b260 < x4558; b260 = b260 + 1) {
    double x4559 = (*x4557)[b260];
    string x4560 = std::to_string(x4559);
    string x4561 = string_plus(x4560, " ");
    if (true) { std::cout << x4561; }
    // results in x4562
  }
  if (true) { std::cout << "" << std::endl; }
  if (true) { std::cout << "result: " << std::endl; }
  int32_t x4566 = (*x4471).size();
  for (int b269 = 0; b269 < x4566; b269 = b269 + 1) {
    double x4567 = (*x4471)[b269];
    string x4568 = std::to_string(x4567);
    string x4569 = string_plus(x4568, " ");
    if (true) { std::cout << x4569; }
    // results in x4570
  }
  if (true) { std::cout << "" << std::endl; }
  vector<bool>* x4580 = new vector<bool>((*x4471).size());
  for (int b276 = 0; b276 < (*x4471).size(); b276++) { 
    double x4573 = (*x4471)[b276];
    double x4574 = (*x4557)[b276];
    double x4575_sumx4573_unk = x4573 + 0.5;
    bool x4576 = x4574 < x4575_sumx4573_unk;
    double x4577 = x4573 - 0.5;
    bool x4578 = x4577 < x4574;
    bool x4579 = x4576 && x4578;
    // results in x4579
    (*x4580)[b276] = x4579;
  }
  bool x4582;
  if ((*x4580).size() > 0) { // Hack to handle reductions on things of length 0
    x4582 = (*x4580)[0];
  } else {
    x4582 = 0;
  }
  for (int b285 = 1; b285 < (*x4580).size(); b285++) {
    bool b286 = (*x4580)[b285];
    bool b287 = x4582;
    bool x289 = b286 && b287;
    // results in x289
    x4582 = x289;
  }
  string x4583 = std::to_string(x4582);
  string x4584 = string_plus("PASS: ", x4583);
  string x4585 = string_plus(x4584, " (BlackScholes) * Remember to change the exp, square, and log hacks, which was a hack so we can used fix point numbers");
  if (true) { std::cout << x4585 << std::endl; }
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

