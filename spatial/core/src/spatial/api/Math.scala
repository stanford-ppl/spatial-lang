package spatial.api

import argon.core.Staging
import spatial.{SpatialApi, SpatialExp}
import argon.ops.{FixPtExp, FltPtExp}
import forge._

trait MathApi extends MathExp {
  this: SpatialApi =>

  /** Absolute value **/
  @api def abs[S:BOOL,I:INT,F:INT](x: FixPt[S,I,F]): FixPt[S,I,F] = FixPt(fix_abs(x.s))

  /** Absolute value **/
  @api def abs[G:INT,E:INT](x: FltPt[G,E]): FltPt[G,E] = FltPt(flt_abs(x.s))
  /** Natural logarithm **/
  @api def log[G:INT,E:INT](x: FltPt[G,E]): FltPt[G,E] = FltPt(flt_log(x.s))
  /** Natural exponential (Euler's number, e, raised to the given exponent) **/
  @api def exp[G:INT,E:INT](x: FltPt[G,E]): FltPt[G,E] = FltPt(flt_exp(x.s))
  /** Square root **/
  @api def sqrt[G:INT,E:INT](x: FltPt[G,E]): FltPt[G,E] = FltPt(flt_sqrt(x.s))

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


trait MathExp {
  this: SpatialExp =>

  @api def mux[T:Meta:Bits](select: Bool, a: T, b: T): T = wrap( math_mux(select.s, a.s, b.s) )
  @api def min[T:Meta:Bits:Order](a: T, b: T): T = wrap( math_min(a.s, b.s) )
  @api def max[T:Meta:Bits:Order](a: T, b: T): T = wrap( math_max(a.s, b.s) )

  implicit class MathInfixOps[T:Type:Num](x: T) {
    @api def **(exp: Int): T = pow(x, exp)
  }

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

  case class FltAbs [G:INT,E:INT](x: Exp[FltPt[G,E]]) extends FltPtOp[G,E] { def mirror(f:Tx) = flt_abs(f(x)) }
  case class FltLog [G:INT,E:INT](x: Exp[FltPt[G,E]]) extends FltPtOp[G,E] { def mirror(f:Tx) = flt_log(f(x)) }
  case class FltExp [G:INT,E:INT](x: Exp[FltPt[G,E]]) extends FltPtOp[G,E] { def mirror(f:Tx) = flt_exp(f(x)) }
  case class FltSqrt[G:INT,E:INT](x: Exp[FltPt[G,E]]) extends FltPtOp[G,E] { def mirror(f:Tx) = flt_sqrt(f(x)) }

  case class Mux[T:Type:Bits](select: Exp[Bool], a: Exp[T], b: Exp[T]) extends Op[T] { def mirror(f:Tx) = math_mux(f(select),f(a),f(b)) }
  case class Min[T:Type:Bits:Order](a: Exp[T], b: Exp[T]) extends Op[T] { def mirror(f:Tx) = math_min(f(a),f(b)) }
  case class Max[T:Type:Bits:Order](a: Exp[T], b: Exp[T]) extends Op[T] { def mirror(f:Tx) = math_max(f(a),f(b)) }

  /** Constructors **/
  @internal def fix_abs[S:BOOL,I:INT,F:INT](x: Exp[FixPt[S,I,F]]): Exp[FixPt[S,I,F]] = x match {
    case Const(c: BigDecimal) => fixpt[S,I,F](c.abs)
    case _ => stage(FixAbs(x))(ctx)
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


  /** Internals **/
  @internal def reduceTreeLevel[T](xs: Seq[T], reduce: (T,T) => T): Seq[T] = xs.length match {
    case 0 => throw new EmptyReductionTreeLevelException()(ctx)
    case 1 => xs
    case len if len % 2 == 0 => reduceTreeLevel(List.tabulate(len/2){i => reduce( xs(2*i), xs(2*i+1)) }, reduce)
    case len => reduceTreeLevel(List.tabulate(len/2){i => reduce( xs(2*i), xs(2*i+1)) } :+ xs.last, reduce)
  }
}
