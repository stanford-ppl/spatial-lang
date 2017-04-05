package spatial.api

import argon.core.Staging
import spatial.SpatialExp
import spatial.analysis.SpatialMetadataExp
import argon.ops.{FixPtExp, OverloadHack}

trait ParameterApi extends ParameterExp with OverloadHack {
  this: SpatialExp =>

  def param(c: Int)(implicit ctx: SrcCtx): Int32

  implicit class ParamCreate(x: Int) {
    // 1 (1 -> 5)
    def apply(range: (Int,Int))(implicit ctx: SrcCtx, ov1: Overload1): Int32 = createParam(x, range._1, 1, range._2)
    // 1 (1 -> 3 -> 5)
    def apply(range: ((Int,Int), Int))(implicit ctx: SrcCtx, ov2: Overload2): Int32 = createParam(x, range._1._1, range._1._2, range._2)
  }
  private[spatial] def createParam(default: Int, start: Int, stride: Int, end: Int)(implicit ctx: SrcCtx): Int32
}


trait ParameterExp extends Staging with FixPtExp with SpatialMetadataExp {
  this: SpatialExp =>

  private[spatial] def createParam(default: Int, start: Int, stride: Int, end: Int)(implicit ctx: SrcCtx): Int32 = {
    val p = intParam(default)
    domainOf(p) = (start, stride, end)
    FixPt(p)
  }

  def param(c: Int)(implicit ctx: SrcCtx): Int32 = FixPt(intParam(c))
}
