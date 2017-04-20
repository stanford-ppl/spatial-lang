package spatial.api

import spatial._
import forge._


trait ParameterApi extends ParameterExp { this: SpatialApi =>

  implicit class ParamCreate(x: scala.Int) {
    // 1 (1 -> 5)
    @api def apply(range: (scala.Int, scala.Int))(implicit ov1: Overload0): Int32 = createParam(x, range._1, 1, range._2)
    // 1 (1 -> 3 -> 5)
    @api def apply(range: ((scala.Int, scala.Int), scala.Int))(implicit ov2: Overload2): Int32 = createParam(x, range._1._1, range._1._2, range._2)
  }
}


trait ParameterExp { this: SpatialExp =>

  @internal def createParam(default: Int, start: Int, stride: Int, end: Int): Int32 = {
    val p = intParam(default)
    domainOf(p) = (start, stride, end)
    FixPt(p)
  }

  @api def param(c: Int)(implicit ctx: SrcCtx): Int32 = FixPt(intParam(c))
}
