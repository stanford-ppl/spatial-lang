package spatial.lang.static

import argon.core._
import forge._
import spatial.metadata._

trait ParametersApi {

  /**
    * Creates a parameter with default value of `c`.
    * The range for this parameter is left to be determined by the compiler.
    */
  @api def param(c: Int): Index = FixPt(FixPt.intParam(c))

  implicit class ParamCreate(default: Int) {
    /**
      * Creates a parameter with this value as the default, and the given range with a stride of 1.
      *
      * ``1 (1 -> 5)``
      * creates a parameter with a default of 1 with a range [1,5].
      */
    @api def apply(range: (Int, Int))(implicit ov1: Overload0): Index = createParam(default, range._1, 1, range._2)
    /**
      * Creates a parameter with this value as the default, and the given strided range.
      *
      * ``1 (1 -> 2 -> 8)``
      * creates a parameter with a default of 1 with a range in {1,2,4,8}.
      */
    @api def apply(range: ((Int, Int), Int))(implicit ov2: Overload2): Index = createParam(default, range._1._1, range._1._2, range._2)


    @api def UNIFORM(range: (Int,Int))(implicit ov0: Overload0): Index = createParam(default, range._1, 1, range._2, Uniform)
    @api def UNIFORM(range: ((Int, Int), Int))(implicit ov1: Overload1): Index = createParam(default, range._1._1, range._1._2, range._2, Uniform)

    @api def GAUSSIAN(range: (Int,Int))(implicit ov0: Overload0): Index = createParam(default, range._1, 1, range._2, Gaussian)
    @api def GAUSSIAN(range: ((Int, Int), Int))(implicit ov1: Overload1): Index = createParam(default, range._1._1, range._1._2, range._2, Gaussian)

    @api def EXP(range: (Int,Int))(implicit ov0: Overload0): Index = createParam(default, range._1, 1, range._2, Exponential)
    @api def EXP(range: ((Int, Int), Int))(implicit ov1: Overload1): Index = createParam(default, range._1._1, range._1._2, range._2, Exponential)

    @api def DECAY(range: (Int,Int))(implicit ov0: Overload0): Index = createParam(default, range._1, 1, range._2, Decay)
    @api def DECAY(range: ((Int, Int), Int))(implicit ov1: Overload1): Index = createParam(default, range._1._1, range._1._2, range._2, Decay)
  }



  @internal def createParam(default: Int, start: Int, stride: Int, end: Int, prior: Prior = Uniform): Index = {
    val p = FixPt.intParam(default)
    domainOf(p) = (start, stride, end)
    priorOf(p) = prior
    wrap(p)
  }
}
