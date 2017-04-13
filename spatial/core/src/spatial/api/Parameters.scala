package spatial.api

import argon.core.Staging
import spatial.SpatialExp
import argon.ops.OverloadHack
import forge._


trait ParameterApi extends ParameterExp with OverloadHack {
  this: SpatialExp =>

  implicit class ParamCreate(x: Int) {
    // 1 (1 -> 5)
    @api def apply(range: (Int,Int))(implicit ov1: Overload1): Int32 = createParam(x, range._1, 1, range._2)
    // 1 (1 -> 3 -> 5)
    @api def apply(range: ((Int,Int), Int))(implicit ov2: Overload2): Int32 = createParam(x, range._1._1, range._1._2, range._2)
  }
}


trait ParameterExp extends Staging {
  this: SpatialExp =>

  @internal def createParam(default: Int, start: Int, stride: Int, end: Int): Int32 = {
    val p = intParam(default)
    domainOf(p) = (start, stride, end)
    FixPt(p)
  }

  @api def param(c: Int)(implicit ctx: SrcCtx): Int32 = FixPt(intParam(c))
}
