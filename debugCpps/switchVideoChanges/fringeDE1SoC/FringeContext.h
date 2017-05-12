#ifndef __FRINGE_CONTEXT_H__
#define __FRINGE_CONTEXT_H__

/**
 * Top-level target-specific Fringe Context API
 */

#ifdef SIM
#include "FringeContextSim.h"
typedef FringeContextSim FringeContext;

#elif defined DE1SoC
#include "FringeContextDE1SoC.h"
typedef FringeContextDE1SoC FringeContext;
#endif

#endif
