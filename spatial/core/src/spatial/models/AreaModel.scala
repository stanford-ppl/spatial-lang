package spatial.models

import argon.core._
import argon.nodes._
import forge._
import spatial.aliases._
import spatial.metadata._

abstract class AreaModel[A:AreaMetric] extends AreaMetricOps {
  lazy val NoArea: A = noArea[A]

  def init(): Unit

  @stateful def nDups(e: Exp[_]): Int = duplicatesOf(e).length
  @stateful def nStages(e: Exp[_]): Int = childrenOf((e,-1)).length

  @stateful def apply(e: Exp[_], inHwScope: Boolean, inReduce: Boolean): A = getDef(e) match {
    case Some(d) => areaOf(e, d, inHwScope, inReduce)
    case None => NoArea
  }
  @stateful def areaOf(e: Exp[_], d: Def, inHwScope: Boolean, inReduce: Boolean): A

  @stateful def areaOfDelayLine(length: Int, bits: Int, par: Int): A

  @stateful def summarize(area: A): AreaSummary
}
