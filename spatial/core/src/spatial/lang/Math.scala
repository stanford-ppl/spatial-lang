package spatial.lang

import argon.core._
import forge._
import argon.nodes._
import spatial.nodes._

object Math {
  /** Absolute value **/
  @api def abs[S:BOOL,I:INT,F:INT](x: FixPt[S,I,F]): FixPt[S,I,F] = FixPt(fix_abs(x.s))

  /** Returns the absolute value of `x`. **/
  @api def abs[G:INT,E:INT](x: FltPt[G,E]): FltPt[G,E] = FltPt(flt_abs(x.s))
  /** Returns the natural logarithm of `x`. **/
  @api def log[G:INT,E:INT](x: FltPt[G,E]): FltPt[G,E] = FltPt(flt_log(x.s))
  /** Natural exponential (Euler's number, e, raised to the given exponent) **/
  @api def exp[G:INT,E:INT](x: FltPt[G,E]): FltPt[G,E] = FltPt(flt_exp(x.s))
  /** Natural exponential computed with Taylor Expansion **/
  // @api def exp_taylor[S:BOOL,I:INT,F:INT](x: FixPt[S,I,F], center: Int, degree: Int): FixPt[S,I,F] = {
  //   val ans = reg_new(0.to(0 until degree+1).map{ n => scala.math.exp(center) * (0 until n).map{(x.s - center.s)}.reduce{_*_} / scala.math.fac(n)}
  //   FixPt(x.s)
  // }

  /** Returns the largest (closest to positive infinity) integer value less than or equal to `x`. **/
  @api def floor[S:BOOL,I:INT,F:INT](x: FixPt[S,I,F]): FixPt[S,I,F] = FixPt[S,I,F](fix_floor(x.s))
  /** Returns the smallest (closest to negative infinity) integer value greater than or equal to `x`. **/
  @api def ceil[S:BOOL,I:INT,F:INT](x: FixPt[S,I,F]): FixPt[S,I,F] = FixPt[S,I,F](fix_ceil(x.s))

  // TODO: These should probably be added to Num instead
  /** Returns the absolute value of the supplied numeric `value`. **/
  @api def abs[T:Type:Num](value: T): T = typ[T] match {
    case t: FixPtType[s,i,f] =>
      implicit val mS = t.mS.asInstanceOf[BOOL[s]]
      implicit val mI = t.mI.asInstanceOf[INT[i]]
      implicit val mF = t.mF.asInstanceOf[INT[f]]
      abs[s,i,f](value.asInstanceOf[FixPt[s,i,f]]).asInstanceOf[T]
    case t: FltPtType[g,e] =>
      implicit val bG = t.mG.asInstanceOf[INT[g]]
      implicit val bE = t.mE.asInstanceOf[INT[e]]
      abs[g,e](value.asInstanceOf[FltPt[g,e]]).asInstanceOf[T]
  }

  /** Returns the natural exponentiation of `x` (e raised to the exponent `x`). **/
  @api def exp[T:Type:Num](x: T)(implicit ctx: SrcCtx): T = typ[T] match {
    case t: FixPtType[s,i,f] =>
      error(ctx, "Exponentiation of fixed point types is not yet implemented.")
      error(ctx)
      wrap(fresh[T])

    case t: FltPtType[g,e] =>
      implicit val bG = t.mG.asInstanceOf[INT[g]]
      implicit val bE = t.mE.asInstanceOf[INT[e]]
      exp[g,e](x.asInstanceOf[FltPt[g,e]]).asInstanceOf[T]
  }

  /** Creates a multiplexer that returns `a` when `select` is true, `b` otherwise. **/
  @api def mux[T:Type:Bits](select: Bit, a: T, b: T): T = wrap( math_mux(select.s, a.s, b.s) )
  /** Returns the minimum of the numeric values `a` and `b`. **/
  @api def min[T:Type:Bits:Order](a: T, b: T): T = wrap( math_min(a.s, b.s) )
  /** Returns the maximum of the numeric values `a` and `b`. **/
  @api def max[T:Type:Bits:Order](a: T, b: T): T = wrap( math_max(a.s, b.s) )

  /** Trigonometric functions **/
  /** Returns the trigonometric sine of `x`. **/
  @api def sin[G:INT,E:INT](x: FltPt[G,E]): FltPt[G,E] = wrap( math_sin(x.s) )
  /** Returns the trigonometric cosine of `x`. **/
  @api def cos[G:INT,E:INT](x: FltPt[G,E]): FltPt[G,E] = wrap( math_cos(x.s) )
  /** Returns the trigonometric tangent of `x`. **/
  @api def tan[G:INT,E:INT](x: FltPt[G,E]): FltPt[G,E] = wrap( math_tan(x.s) )
  /** Returns the hyperbolic sine of `x`. **/
  @api def sinh[G:INT,E:INT](x: FltPt[G,E]): FltPt[G,E] = wrap( math_sinh(x.s) )
  /** Returns the hyperbolic cosine of `x`. **/
  @api def cosh[G:INT,E:INT](x: FltPt[G,E]): FltPt[G,E] = wrap( math_cosh(x.s) )
  /** Returns the hyperbolic tangent of `x`. **/
  @api def tanh[G:INT,E:INT](x: FltPt[G,E]): FltPt[G,E] = wrap( math_tanh(x.s) )
  /** Returns the arc sine of `x`. **/
  @api def asin[G:INT,E:INT](x: FltPt[G,E]): FltPt[G,E] = wrap( math_asin(x.s) )
  /** Returns the arc cosine of `x`. **/
  @api def acos[G:INT,E:INT](x: FltPt[G,E]): FltPt[G,E] = wrap( math_acos(x.s) )
  /** Returns the arc tangent of `x`. **/
  @api def atan[G:INT,E:INT](x: FltPt[G,E]): FltPt[G,E] = wrap( math_atan(x.s) )
  val PI = java.lang.Math.PI

  /** Returns `base` raised to the power of `exp`. **/
  @api def pow[G:INT,E:INT](base: FltPt[G,E], exp:FltPt[G,E]): FltPt[G,E] = wrap( math_pow(base.s, exp.s) )

  @api def pow[T:Type:Num](x: T, exp: Int)(implicit ctx: SrcCtx): T = {
    if (exp >= 0) productTree(List.fill(exp)(x))
    else {
      error(ctx, "Exponentiation on negative integers is currently unsupported")
      error(ctx)
      wrap(fresh[T])
    }
  }

  @api def reduceTree[T](xs: Seq[T])(reduce: (T,T) => T): T = reduceTreeLevel(xs, reduce).head

  @api def productTree[T:Num](xs: Seq[T]): T = {
    if (xs.isEmpty) implicitly[Num[T]].one else reduceTree(xs){(a,b) => num[T].times(a,b) }
  }

  @api def sumTree[T:Num](xs: Seq[T]): T = {
    if (xs.isEmpty) implicitly[Num[T]].zero else reduceTree(xs){(a,b) => num[T].plus(a,b) }
  }

  /** Constructors **/
  @internal def fix_mac[S:BOOL,I:INT,F:INT](m1: Exp[FixPt[S,I,F]], m2: Exp[FixPt[S,I,F]], add: Exp[FixPt[S,I,F]]): Exp[FixPt[S,I,F]] = {
    stage(FixMAC(m1,m2,add))(ctx)
  }

  @internal def fix_abs[S:BOOL,I:INT,F:INT](x: Exp[FixPt[S,I,F]]): Exp[FixPt[S,I,F]] = x match {
    case Literal(c) => FixPt.const[S,I,F](c.abs)
    case _ => stage(FixAbs(x))(ctx)
  }

  @internal def fix_floor[S:BOOL,I:INT,F:INT](x: Exp[FixPt[S,I,F]]): Exp[FixPt[S,I,F]] = x match {
    case Literal(c) => FixPt.const[S,I,F](c.floor)
    case _ => stage(FixFloor(x))(ctx)
  }

  @internal def fix_ceil[S:BOOL,I:INT,F:INT](x: Exp[FixPt[S,I,F]]): Exp[FixPt[S,I,F]] = x match {
    case Literal(c) => FixPt.const[S,I,F](c.ceil)
    case _ => stage(FixCeil(x))(ctx)
  }

  @internal def flt_abs[G:INT,E:INT](x: Exp[FltPt[G,E]]): Exp[FltPt[G,E]] = x match {
    case Literal(c) => FltPt.const[G,E](c.abs)
    case _ => stage(FltAbs(x))(ctx)
  }
  @internal def flt_log[G:INT,E:INT](x: Exp[FltPt[G,E]]): Exp[FltPt[G,E]] = x match {
    //case Const(c:BigDecimal) => fltpt[G,E](???) TODO: log of BigDecimal? Change representation?
    case _ => stage(FltLog(x))(ctx)
  }
  @internal def flt_exp[G:INT,E:INT](x: Exp[FltPt[G,E]]): Exp[FltPt[G,E]] = x match {
    //case Const(c:BigDecimal) => fltpt[G,E](???)
    case _ => stage(FltExp(x))(ctx)
  }
  @internal def flt_sqrt[G:INT,E:INT](x: Exp[FltPt[G,E]]): Exp[FltPt[G,E]] = x match {
    //case Const(c: BigDecimal) => fltpt[G,E](???)
    case _ => stage(FltSqrt(x))(ctx)
  }

  @internal def onehot_mux[T:Type:Bits](selects: Seq[Exp[Bit]], datas: Seq[Exp[T]]): Exp[T] = {
    stage(OneHotMux(selects,datas))(ctx)
  }

  @internal def math_mux[T:Type:Bits](select: Exp[Bit], a: Exp[T], b: Exp[T]): Exp[T] = select match {
    case Const(true) => a
    case Const(false) => b
    case _ => stage(Mux(select,a,b))(ctx)
  }
  @internal def math_min[T:Type:Bits:Order](a: Exp[T], b: Exp[T]): Exp[T] = (a,b) match {
    case (Const(_),Const(_)) => implicitly[Order[T]].lessThan(wrap(a),wrap(b)).s match {
      case Const(true) => a
      case Const(false) => b
      case _ => stage(Min(a, b))(ctx)
    }
    case _ => stage(Min(a, b))(ctx)
  }
  @internal def math_max[T:Type:Bits:Order](a: Exp[T], b: Exp[T]): Exp[T] = (a,b) match {
    case (Const(_),Const(_)) => implicitly[Order[T]].lessThan(wrap(b),wrap(a)).s match {
      case Const(true) => a
      case Const(false) => b
      case _ => stage(Max(a, b))(ctx)
    }
    case _ => stage(Max(a, b))(ctx)
  }

  @internal def math_pow[G:INT,E:INT](x: Exp[FltPt[G,E]], y: Exp[FltPt[G,E]]): Exp[FltPt[G,E]] = stage(FltPow(x, y))(ctx)
  @internal def math_sin[G:INT,E:INT](x: Exp[FltPt[G,E]]): Exp[FltPt[G,E]] = stage(FltSin(x))(ctx)
  @internal def math_cos[G:INT,E:INT](x: Exp[FltPt[G,E]]): Exp[FltPt[G,E]] = stage(FltCos(x))(ctx)
  @internal def math_tan[G:INT,E:INT](x: Exp[FltPt[G,E]]): Exp[FltPt[G,E]] = stage(FltTan(x))(ctx)
  @internal def math_sinh[G:INT,E:INT](x: Exp[FltPt[G,E]]): Exp[FltPt[G,E]] = stage(FltSinh(x))(ctx)
  @internal def math_cosh[G:INT,E:INT](x: Exp[FltPt[G,E]]): Exp[FltPt[G,E]] = stage(FltCosh(x))(ctx)
  @internal def math_tanh[G:INT,E:INT](x: Exp[FltPt[G,E]]): Exp[FltPt[G,E]] = stage(FltTanh(x))(ctx)
  @internal def math_asin[G:INT,E:INT](x: Exp[FltPt[G,E]]): Exp[FltPt[G,E]] = stage(FltAsin(x))(ctx)
  @internal def math_acos[G:INT,E:INT](x: Exp[FltPt[G,E]]): Exp[FltPt[G,E]] = stage(FltAcos(x))(ctx)
  @internal def math_atan[G:INT,E:INT](x: Exp[FltPt[G,E]]): Exp[FltPt[G,E]] = stage(FltAtan(x))(ctx)

  /** Internals **/
  @internal def reduceTreeLevel[T](xs: Seq[T], reduce: (T,T) => T): Seq[T] = xs.length match {
    case 0 => throw new spatial.EmptyReductionTreeLevelException()
    case 1 => xs
    case len if len % 2 == 0 => reduceTreeLevel(List.tabulate(len/2){i => reduce( xs(2*i), xs(2*i+1)) }, reduce)
    case len => reduceTreeLevel(List.tabulate(len/2){i => reduce( xs(2*i), xs(2*i+1)) } :+ xs.last, reduce)
  }
}
