package spatial.api

import spatial._
import spatial.analysis.SpatialMetadataExp
import argon.ops._

trait ParameterOps extends FixPtOps {
  this: SpatialOps =>

  def param(c: Int)(implicit ctx: SrcCtx): Int32

  implicit class ParamCreate(x: Int) {
    // 1 (1 -> 5)
    def apply(range: (Int,Int))(implicit ctx: SrcCtx, ov1: Overload1): Int32 = createParam(x, range._1, 1, range._2)
    // 1 (1 -> 3 -> 5)
    def apply(range: ((Int,Int), Int))(implicit ctx: SrcCtx, ov2: Overload2): Int32 = createParam(x, range._1._1, range._1._2, range._2)
  }
  private[spatial] def createParam(default: Int, start: Int, stride: Int, end: Int)(implicit ctx: SrcCtx): Int32
}

trait ParameterApi extends ParameterOps with FixPtApi { this: SpatialApi => }

trait ParameterExp extends ParameterOps with FixPtExp with SpatialMetadataExp { this: SpatialExp =>
  private[spatial] def createParam(default: Int, start: Int, stride: Int, end: Int)(implicit ctx: SrcCtx): Int32 = {
    val p = intParam(default)
    domainOf(p) = (start, stride, end)
    FixPt(p)
  }

  def param(c: Int)(implicit ctx: SrcCtx): Int32 = FixPt(intParam(c))
}
