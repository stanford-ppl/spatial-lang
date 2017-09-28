package spatial.lang.static

import argon.core._
import forge._
import spatial.metadata._

trait ParametersApi {

  /**
    * Creates a parameter with default value of `c`.
    * The range for this parameter is left to be determined by the compiler.
    */
  @api def param(c: Int): Int32 = FixPt(FixPt.intParam(c))

  implicit class ParamCreate(default: Int) {
    /**
      * Creates a parameter with this value as the default, and the given range with a stride of 1.
      *
      * ``1 (1 -> 5)``
      * creates a parameter with a default of 1 with a range [1,5].
      */
    @api def apply(range: (Int, Int))(implicit ov1: Overload0): Int32 = createParam(default, range._1, 1, range._2)
    /**
      * Creates a parameter with this value as the default, and the given strided range.
      *
      * ``1 (1 -> 2 -> 8)``
      * creates a parameter with a default of 1 with a range in {1,2,4,8}.
      */
    @api def apply(range: ((Int, Int), Int))(implicit ov2: Overload2): Int32 = createParam(default, range._1._1, range._1._2, range._2)
  }



  @internal def createParam(default: Int, start: Int, stride: Int, end: Int): Int32 = {
    val p = FixPt.intParam(default)
    domainOf(p) = (start, stride, end)
    wrap(p)
  }
}
