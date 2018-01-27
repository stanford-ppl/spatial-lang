#ifndef __FRINGE_CONTEXT_H__
#define __FRINGE_CONTEXT_H__

/**
 * Top-level target-specific Fringe Context API
 */

#ifdef SIM
#include "FringeContextSim.h"
typedef FringeContextSim FringeContext;

#elif defined ARRIA10 
#include "FringeContextArria10.h"
typedef FringeContextArria10 FringeContext;
#endif

#endif
