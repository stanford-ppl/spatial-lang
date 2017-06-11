package spatial.api

import spatial._
import forge._

trait MathApi extends MathExp { this: SpatialApi =>

  /** Absolute value **/
  @api def abs[S:BOOL,I:INT,F:INT](x: FixPt[S,I,F]): FixPt[S,I,F] = FixPt(fix_abs(x.s))

  /** Absolute value **/
  @api def abs[G:INT,E:INT](x: FltPt[G,E]): FltPt[G,E] = FltPt(flt_abs(x.s))
  /** Natural logarithm **/
  @api def log[G:INT,E:INT](x: FltPt[G,E]): FltPt[G,E] = FltPt(flt_log(x.s))
  /** Natural exponential (Euler's number, e, raised to the given exponent) **/
  @api def exp[G:INT,E:INT](x: FltPt[G,E]): FltPt[G,E] = FltPt(flt_exp(x.s))
  /** Natural exponential computed with Taylor Expansion **/
  // @api def exp_taylor[S:BOOL,I:INT,F:INT](x: FixPt[S,I,F], center: Int, degree: Int): FixPt[S,I,F] = { 
  //   val ans = reg_new(0.to(0 until degree+1).map{ n => scala.math.exp(center) * (0 until n).map{(x.s - center.s)}.reduce{_*_} / scala.math.fac(n)}
  //   FixPt(x.s)
  // }
  /** Taylor expansion for natural exponential to third degree **/
  @api def exp_taylor[S:BOOL,I:INT,F:INT](x: FixPt[S,I,F]): FixPt[S,I,F] = { 
    val ans = mux(x < -3.5.to[FixPt[S,I,F]], 0.to[FixPt[S,I,F]], mux(x < -1.2.to[FixPt[S,I,F]], x*0.1.to[FixPt[S,I,F]] + 0.35.to[FixPt[S,I,F]], 1 + x + x*x/2 + x*x*x/6 + x*x*x*x/24 + x*x*x*x*x/120))
    FixPt(ans.s)
  }
  /** Square root **/
  @api def sqrt[G:INT,E:INT](x: FltPt[G,E]): FltPt[G,E] = FltPt(flt_sqrt(x.s))
  @api def sqrt_approx[S:BOOL,I:INT,F:INT](x: FixPt[S,I,F]): FixPt[S,I,F] = {
    // I don't care how inefficient this is, it is just a placeholder for backprop until we implement floats
    val ans = mux(x < 2.to[FixPt[S,I,F]], 1 + (x-1)/2 -(x-1)*(x-1)/8+(x-1)*(x-1)*(x-1)/16, // 3rd order taylor for values up to 2
                  mux(x < 10.to[FixPt[S,I,F]], x*0.22.to[FixPt[S,I,F]] + 1, // Linearize
                    mux( x < 100.to[FixPt[S,I,F]], x*0.08.to[FixPt[S,I,F]] + 2.5.to[FixPt[S,I,F]], // Linearize
                      mux( x < 1000.to[FixPt[S,I,F]], x*0.028.to[FixPt[S,I,F]] + 8, // Linearize
                        mux( x < 10000.to[FixPt[S,I,F]], x*0.008.to[FixPt[S,I,F]] + 20, // Linearize
                          mux( x < 100000.to[FixPt[S,I,F]], x*0.003.to[FixPt[S,I,F]] + 60, x*0.0002.to[FixPt[S,I,F]] + 300))))))
    FixPt(ans.s)
  }
  @api def floor[S:BOOL,I:INT,F:INT](x: FixPt[S,I,F]): FixPt[S,I,F] = FixPt[S,I,F](fix_floor(x.s))
  @api def ceil[S:BOOL,I:INT,F:INT](x: FixPt[S,I,F]): FixPt[S,I,F] = FixPt[S,I,F](fix_ceil(x.s))

  // TODO: These should probably be added to Num instead
  @api def abs[T:Meta:Num](x: T): T = typ[T] match {
    case t: FixPtType[s,i,f] =>
      implicit val mS = t.mS.asInstanceOf[BOOL[s]]
      implicit val mI = t.mI.asInstanceOf[INT[i]]
      implicit val mF = t.mF.asInstanceOf[INT[f]]
      abs[s,i,f](x.asInstanceOf[FixPt[s,i,f]]).asInstanceOf[T]
    case t: FltPtType[g,e] =>
      implicit val bG = t.mG.asInstanceOf[INT[g]]
      implicit val bE = t.mE.asInstanceOf[INT[e]]
      abs[g,e](x.asInstanceOf[FltPt[g,e]]).asInstanceOf[T]
  }

  @api def exp[T:Meta:Num](x: T)(implicit ctx: SrcCtx): T = typ[T] match {
    case t: FixPtType[s,i,f] =>
      error(ctx, "Exponentiation of fixed point types is not yet implemented.")
      error(ctx)
      wrap(fresh[T])

    case t: FltPtType[g,e] =>
      implicit val bG = t.mG.asInstanceOf[INT[g]]
      implicit val bE = t.mE.asInstanceOf[INT[e]]
      exp[g,e](x.asInstanceOf[FltPt[g,e]]).asInstanceOf[T]
  }
}


trait MathExp { this: SpatialExp =>

  @api def mux[T:Meta:Bits](select: Bool, a: T, b: T): T = wrap( math_mux(select.s, a.s, b.s) )
  @api def min[T:Meta:Bits:Order](a: T, b: T): T = wrap( math_min(a.s, b.s) )
  @api def max[T:Meta:Bits:Order](a: T, b: T): T = wrap( math_max(a.s, b.s) )

  /** Trigonometric functions **/
  @api def sin[G:INT,E:INT](x: FltPt[G,E]): FltPt[G,E] = wrap( math_sin(x.s) )
  @api def cos[G:INT,E:INT](x: FltPt[G,E]): FltPt[G,E] = wrap( math_cos(x.s) )
  @api def tan[G:INT,E:INT](x: FltPt[G,E]): FltPt[G,E] = wrap( math_tan(x.s) )
  @api def sinh[G:INT,E:INT](x: FltPt[G,E]): FltPt[G,E] = wrap( math_sinh(x.s) )
  @api def cosh[G:INT,E:INT](x: FltPt[G,E]): FltPt[G,E] = wrap( math_cosh(x.s) )
  @api def tanh[G:INT,E:INT](x: FltPt[G,E]): FltPt[G,E] = wrap( math_tanh(x.s) )
  @api def asin[G:INT,E:INT](x: FltPt[G,E]): FltPt[G,E] = wrap( math_asin(x.s) )
  @api def acos[G:INT,E:INT](x: FltPt[G,E]): FltPt[G,E] = wrap( math_acos(x.s) )
  @api def atan[G:INT,E:INT](x: FltPt[G,E]): FltPt[G,E] = wrap( math_atan(x.s) )
  val PI = Math.PI


  implicit class MathInfixOps[T:Type:Num](x: T) {
    @api def **(exp: Int): T = pow(x, exp)
  }

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
    if (xs.isEmpty) one[T] else reduceTree(xs){(a,b) => num[T].times(a,b) }
  }

  @api def sumTree[T:Num](xs: Seq[T]): T = {
    if (xs.isEmpty) zero[T] else reduceTree(xs){(a,b) => num[T].plus(a,b) }
  }


  /** IR Nodes **/
  case class FixAbs[S:BOOL,I:INT,F:INT](x: Exp[FixPt[S,I,F]]) extends FixPtOp[S,I,F] { def mirror(f:Tx) = fix_abs(f(x)) }
  case class FixFloor[S:BOOL,I:INT,F:INT](x: Exp[FixPt[S,I,F]]) extends FixPtOp[S,I,F] { def mirror(f:Tx) = fix_floor(f(x)) }
  case class FixCeil[S:BOOL,I:INT,F:INT](x: Exp[FixPt[S,I,F]]) extends FixPtOp[S,I,F] { def mirror(f:Tx) = fix_ceil(f(x)) }

  case class FltAbs [G:INT,E:INT](x: Exp[FltPt[G,E]]) extends FltPtOp[G,E] { def mirror(f:Tx) = flt_abs(f(x)) }
  case class FltLog [G:INT,E:INT](x: Exp[FltPt[G,E]]) extends FltPtOp[G,E] { def mirror(f:Tx) = flt_log(f(x)) }
  case class FltExp [G:INT,E:INT](x: Exp[FltPt[G,E]]) extends FltPtOp[G,E] { def mirror(f:Tx) = flt_exp(f(x)) }
  case class FltSqrt[G:INT,E:INT](x: Exp[FltPt[G,E]]) extends FltPtOp[G,E] { def mirror(f:Tx) = flt_sqrt(f(x)) }

  case class FltPow[G:INT,E:INT](x: Exp[FltPt[G,E]], y: Exp[FltPt[G,E]]) extends FltPtOp[G,E] { def mirror(f:Tx) = math_pow(f(x), f(y)) }

  case class FltSin[G:INT,E:INT](x: Exp[FltPt[G,E]]) extends FltPtOp[G,E] { def mirror(f:Tx) = math_sin(f(x)) }
  case class FltCos[G:INT,E:INT](x: Exp[FltPt[G,E]]) extends FltPtOp[G,E] { def mirror(f:Tx) = math_cos(f(x)) }
  case class FltTan[G:INT,E:INT](x: Exp[FltPt[G,E]]) extends FltPtOp[G,E] { def mirror(f:Tx) = math_tan(f(x)) }
  case class FltSinh[G:INT,E:INT](x: Exp[FltPt[G,E]]) extends FltPtOp[G,E] { def mirror(f:Tx) = math_sinh(f(x)) }
  case class FltCosh[G:INT,E:INT](x: Exp[FltPt[G,E]]) extends FltPtOp[G,E] { def mirror(f:Tx) = math_cosh(f(x)) }
  case class FltTanh[G:INT,E:INT](x: Exp[FltPt[G,E]]) extends FltPtOp[G,E] { def mirror(f:Tx) = math_tanh(f(x)) }
  case class FltAsin[G:INT,E:INT](x: Exp[FltPt[G,E]]) extends FltPtOp[G,E] { def mirror(f:Tx) = math_asin(f(x)) }
  case class FltAcos[G:INT,E:INT](x: Exp[FltPt[G,E]]) extends FltPtOp[G,E] { def mirror(f:Tx) = math_acos(f(x)) }
  case class FltAtan[G:INT,E:INT](x: Exp[FltPt[G,E]]) extends FltPtOp[G,E] { def mirror(f:Tx) = math_atan(f(x)) }

  case class OneHotMux[T:Type:Bits](selects: Seq[Exp[Bool]], datas: Seq[Exp[T]]) extends Op[T] {
    def mirror(f:Tx) = onehot_mux(f(selects),f(datas))
    val mT = typ[T]
  }

  case class Mux[T:Type:Bits](select: Exp[Bool], a: Exp[T], b: Exp[T]) extends Op[T] { def mirror(f:Tx) = math_mux(f(select),f(a),f(b)) }
  case class Min[T:Type:Bits:Order](a: Exp[T], b: Exp[T]) extends Op[T] { def mirror(f:Tx) = math_min(f(a),f(b)) }
  case class Max[T:Type:Bits:Order](a: Exp[T], b: Exp[T]) extends Op[T] { def mirror(f:Tx) = math_max(f(a),f(b)) }

  /** Constructors **/
  @internal def fix_abs[S:BOOL,I:INT,F:INT](x: Exp[FixPt[S,I,F]]): Exp[FixPt[S,I,F]] = x match {
    case Const(c: BigDecimal) => fixpt[S,I,F](c.abs)
    case _ => stage(FixAbs(x))(ctx)
  }

  @internal def fix_floor[S:BOOL,I:INT,F:INT](x: Exp[FixPt[S,I,F]]): Exp[FixPt[S,I,F]] = x match {
    case Const(c: BigDecimal) => 
      val res = if (c % 1 == 0) c else BigDecimal(c.toInt)
      fixpt[S,I,F](res)
    case _ => stage(FixFloor(x))(ctx)
  }

  @internal def fix_ceil[S:BOOL,I:INT,F:INT](x: Exp[FixPt[S,I,F]]): Exp[FixPt[S,I,F]] = x match {
    case Const(c: BigDecimal) => 
      val res = if (c % 1 == 0) c else BigDecimal(c.toInt + 1)
      fixpt[S,I,F](res)
    case _ => stage(FixCeil(x))(ctx)
  }

  @internal def flt_abs[G:INT,E:INT](x: Exp[FltPt[G,E]]): Exp[FltPt[G,E]] = x match {
    case Const(c: BigDecimal) => fltpt[G,E](c.abs)
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

  @util def onehot_mux[T:Type:Bits](selects: Seq[Exp[Bool]], datas: Seq[Exp[T]]): Exp[T] = {
    stage(OneHotMux(selects,datas))(ctx)
  }

  @util def math_mux[T:Type:Bits](select: Exp[Bool], a: Exp[T], b: Exp[T]): Exp[T] = select match {
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
    case 0 => throw new EmptyReductionTreeLevelException()(ctx)
    case 1 => xs
    case len if len % 2 == 0 => reduceTreeLevel(List.tabulate(len/2){i => reduce( xs(2*i), xs(2*i+1)) }, reduce)
    case len => reduceTreeLevel(List.tabulate(len/2){i => reduce( xs(2*i), xs(2*i+1)) } :+ xs.last, reduce)
  }
}
