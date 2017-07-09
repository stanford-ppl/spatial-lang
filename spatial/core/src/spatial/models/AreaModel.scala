package spatial.models

import argon.core._
import argon.nodes._
import forge._
import spatial.aliases._
import spatial.metadata._

abstract class AreaModel[A:AreaMetric] extends AreaMetricOps {
  lazy val NoArea: A = noArea[A]

  @stateful def nDups(e: Exp[_]): Int = duplicatesOf(e).length

  def nbits(e: Exp[_]): Int = e.tp match {case Bits(bT) => bT.length; case _ => 0 }

  @stateful def apply(e: Exp[_]): A = getDef(e) match {
    case Some(d) => areaOf(e, d)
    case None => NoArea
  }
  @stateful def areaOf(e: Exp[_], d: Def): A

}
