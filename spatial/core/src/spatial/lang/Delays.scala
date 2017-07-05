package spatial.lang

import argon.core._
import forge._
import spatial.nodes._

object Delays {
  @internal def delayLine[T:Type:Bits](size: Int, data: Exp[T]) = stageUnique( DelayLine[T](size, data) )(ctx)
}
