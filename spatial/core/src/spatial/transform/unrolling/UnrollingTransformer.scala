package spatial.transform.unrolling

import argon.core._

case class UnrollingTransformer(var IR: State) extends UnrollingBase
     with ControllerUnrolling
     with MemoryUnrolling
