package spatial.spec

import argon.ops._

trait MathOps extends NumOps with FixPtOps with FltPtOps {
  /** Absolute value **/
  def abs[S:BOOL,I:INT,F:INT](x: FixPt[S,I,F])(implicit ctx: SrcCtx): FixPt[S,I,F]

  /** Absolute value **/
  def abs[G:INT,E:INT](x: FltPt[G,E])(implicit ctx: SrcCtx): FltPt[G,E]
  /** Natural logarithm **/
  def log[G:INT,E:INT](x: FltPt[G,E])(implicit ctx: SrcCtx): FltPt[G,E]
  /** Natural exponential (Euler's number, e, raised to the given exponent) **/
  def exp[G:INT,E:INT](x: FltPt[G,E])(implicit ctx: SrcCtx): FltPt[G,E]
  /** Square root **/
  def sqrt[G:INT,E:INT](x: FltPt[G,E])(implicit ctx: SrcCtx): FltPt[G,E]

  def mux[T:Bits](select: Bool, a: T, b: T)(implicit ctx: SrcCtx): T
  def min[T:Order](a: T, b: T)(implicit ctx: SrcCtx): T
  def max[T:Order](a: T, b: T)(implicit ctx: SrcCtx): T
}
trait MathApi extends MathOps with NumApi with FixPtApi with FltPtApi


trait MathExp extends MathOps with NumExp with FixPtExp with FltPtExp with SpatialExceptions {
  /** API **/
  def abs[S:BOOL,I:INT,F:INT](x: FixPt[S,I,F])(implicit ctx: SrcCtx): FixPt[S,I,F] = FixPt(fix_abs(x.s))

  def abs[G:INT,E:INT](x: FltPt[G,E])(implicit ctx: SrcCtx): FltPt[G,E] = FltPt(flt_abs(x.s))
  def log[G:INT,E:INT](x: FltPt[G,E])(implicit ctx: SrcCtx): FltPt[G,E] = FltPt(flt_log(x.s))
  def exp[G:INT,E:INT](x: FltPt[G,E])(implicit ctx: SrcCtx): FltPt[G,E] = FltPt(flt_exp(x.s))
  def sqrt[G:INT,E:INT](x: FltPt[G,E])(implicit ctx: SrcCtx): FltPt[G,E] = FltPt(flt_sqrt(x.s))

  def mux[T:Bits](select: Bool, a: T, b: T)(implicit ctx: SrcCtx): T = {
    wrap( math_mux(select.s, a.s, b.s) )
  }
  def min[T:Order](a: T, b: T)(implicit ctx: SrcCtx): T = wrap( math_min(a.s, b.s) )
  def max[T:Order](a: T, b: T)(implicit ctx: SrcCtx): T = wrap( math_max(a.s, b.s) )

  /** IR Nodes **/
  case class FixAbs[S:BOOL,I:INT,F:INT](x: Exp[FixPt[S,I,F]]) extends FixPtOp[S,I,F] { def mirror(f:Tx) = fix_abs(f(x)) }

  case class FltAbs [G:INT,E:INT](x: Exp[FltPt[G,E]]) extends FltPtOp[G,E] { def mirror(f:Tx) = flt_abs(f(x)) }
  case class FltLog [G:INT,E:INT](x: Exp[FltPt[G,E]]) extends FltPtOp[G,E] { def mirror(f:Tx) = flt_log(f(x)) }
  case class FltExp [G:INT,E:INT](x: Exp[FltPt[G,E]]) extends FltPtOp[G,E] { def mirror(f:Tx) = flt_exp(f(x)) }
  case class FltSqrt[G:INT,E:INT](x: Exp[FltPt[G,E]]) extends FltPtOp[G,E] { def mirror(f:Tx) = flt_sqrt(f(x)) }

  case class Mux[T:Bits](select: Exp[Bool], a: Exp[T], b: Exp[T]) extends Op[T] { def mirror(f:Tx) = math_mux(f(select),f(a),f(b)) }
  case class Min[T:Order](a: Exp[T], b: Exp[T]) extends Op[T] { def mirror(f:Tx) = math_min(f(a),f(b)) }
  case class Max[T:Order](a: Exp[T], b: Exp[T]) extends Op[T] { def mirror(f:Tx) = math_max(f(a),f(b)) }

  /** Constructors **/
  def fix_abs[S:BOOL,I:INT,F:INT](x: Exp[FixPt[S,I,F]])(implicit ctx: SrcCtx): Exp[FixPt[S,I,F]] = x match {
    case Const(c: BigInt) => fixpt[S,I,F](c.abs)
    case _ => stage(FixAbs(x))(ctx)
  }

  def flt_abs[G:INT,E:INT](x: Exp[FltPt[G,E]])(implicit ctx: SrcCtx): Exp[FltPt[G,E]] = x match {
    case Const(c:BigDecimal) => fltpt[G,E](c.abs)
    case _ => stage(FltAbs(x))(ctx)
  }
  def flt_log[G:INT,E:INT](x: Exp[FltPt[G,E]])(implicit ctx: SrcCtx): Exp[FltPt[G,E]] = x match {
    //case Const(c:BigDecimal) => fltpt[G,E](???) TODO: log of BigDecimal? Change representation?
    case _ => stage(FltLog(x))(ctx)
  }
  def flt_exp[G:INT,E:INT](x: Exp[FltPt[G,E]])(implicit ctx: SrcCtx): Exp[FltPt[G,E]] = x match {
    //case Const(c:BigDecimal) => fltpt[G,E](???)
    case _ => stage(FltExp(x))(ctx)
  }
  def flt_sqrt[G:INT,E:INT](x: Exp[FltPt[G,E]])(implicit ctx: SrcCtx): Exp[FltPt[G,E]] = x match {
    //case Const(c: BigDecimal) => fltpt[G,E](???)
    case _ => stage(FltSqrt(x))(ctx)
  }

  def math_mux[T:Bits](select: Exp[Bool], a: Exp[T], b: Exp[T])(implicit ctx: SrcCtx): Exp[T] = select match {
    case Const(true) => a
    case Const(false) => b
    case _ => stage(Mux(select,a,b))(ctx)
  }
  def math_min[T:Order](a: Exp[T], b: Exp[T])(implicit ctx: SrcCtx): Exp[T] = (a,b) match {
    case (Const(_),Const(_)) => implicitly[Order[T]].lessThan(wrap(a),wrap(b)).s match {
      case Const(true) => a
      case Const(false) => b
      case _ => stage(Min(a, b))(ctx)
    }
    case _ => stage(Min(a, b))(ctx)
  }
  def math_max[T:Order](a: Exp[T], b: Exp[T])(implicit ctx: SrcCtx): Exp[T] = (a,b) match {
    case (Const(_),Const(_)) => implicitly[Order[T]].lessThan(wrap(b),wrap(a)).s match {
      case Const(true) => a
      case Const(false) => b
      case _ => stage(Max(a, b))(ctx)
    }
    case _ => stage(Max(a, b))(ctx)
  }



  /** Internals **/
  def reduceTreeLevel[T:Bits](xs: Seq[T], reduce: (T,T) => T)(implicit ctx: SrcCtx): Seq[T] = xs.length match {
    case 0 => throw new EmptyReductionTreeLevelException()(ctx)
    case 1 => xs
    case len if len % 2 == 0 => reduceTreeLevel(List.tabulate(len/2){i => reduce( xs(2*i), xs(2*i+1)) }, reduce)
    case len => reduceTreeLevel(List.tabulate(len/2){i => reduce( xs(2*i), xs(2*i+1)) } :+ xs.last, reduce)
  }

  def reduceTree[T:Bits](xs: Seq[T])(reduce: (T,T) => T)(implicit ctx: SrcCtx): T = reduceTreeLevel(xs, reduce).head

  def productTree[T:Num](xs: Seq[T])(implicit ctx: SrcCtx): T = reduceTree(xs){(a,b) => num[T].times(a,b) }
  def sumTree[T:Num](xs: Seq[T])(implicit ctx: SrcCtx): T = reduceTree(xs){(a,b) => num[T].plus(a,b) }

}
