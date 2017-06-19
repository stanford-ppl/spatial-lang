package spatial.lang.static

import argon.core._
import forge._
import spatial.metadata._

trait ParametersApi {

  implicit class ParamCreate(default: Int) {
    // 1 (1 -> 5)
    @api def apply(range: (Int, Int))(implicit ov1: Overload0): Int32 = createParam(default, range._1, 1, range._2)
    // 1 (1 -> 3 -> 5)
    @api def apply(range: ((Int, Int), Int))(implicit ov2: Overload2): Int32 = createParam(default, range._1._1, range._1._2, range._2)
  }

  @internal def createParam(default: Int, start: Int, stride: Int, end: Int): Int32 = {
    val p = FixPt.intParam(default)
    domainOf(p) = (start, stride, end)
    wrap(p)
  }

  @api def param(c: Int): Int32 = FixPt(FixPt.intParam(c))
}
