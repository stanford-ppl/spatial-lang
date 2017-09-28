package spatial.lang

import argon.core._
import forge._
import spatial.nodes._

// N by B
// N par P
// N by B par P
// M until N
// M until N par P
// M until N by B
// M until N by B par P

// valid to use "[Range] by B" unless range is already strided
// may require a Range which is not a StridedRange?
// :: this distinction is silly, only using one type
case class Wildcard()

case class Range(start: Option[Index], end: Index, step: Option[Index], p: Option[Index], isUnit: CBoolean) {
  /** Creates a Range with this Range's start and end but with the given `step` size. **/
  @api def by(step: Index): Range = Range(start, end, Some(step), p, isUnit = false)
  /** Creates a Range with this Range's start, end, and stride, but with the given `par` parallelization factor. **/
  @api def par(p: Index): Range = Range(start, end, step, Some(p), isUnit = false)

  /**
    * Creates a Range with this Range's end, with the previous start as the step size, and the given start.
    * Note that this operator is right-associative.
    */
  @api def ::(start2: Index): Range = Range(Some(start2), end, start, p, isUnit = false)

  /**
    * Iterates over all integers in this range, calling `func` on each.
    *
    * `NOTE`: This method is unsynthesizable, and can be used only on the CPU or in simulation.
    */
  @api def foreach(func: Index => MUnit): MUnit = {
    val begin  = start.map(_.s).getOrElse(int32s(0))
    val stride = step.map(_.s).getOrElse(int32s(1))
    MUnit(Range.foreach(begin, end.s, stride, {i: Exp[Index] => func(wrap(i)).s }, fresh[Index]))
  }

  /** Returns the length of this Range. **/
  @api def length: Index = (start, end, step) match {
    case (None, e, None) => e
    case (Some(s), e, None) => e - s
    case (None, e, Some(st)) => (e + st - 1) / st
    case (Some(s), e, Some(st)) => (e - s + st - 1) / st
  }


}
object Range {
  /** Constructors **/
  @internal def foreach(start: Exp[Index], end: Exp[Index], step: Exp[Index], func: Exp[Index] => Exp[MUnit], i: Bound[Index]) = {
    val fBlk = stageLambda1(i){ func(i) }
    val effects = fBlk.effects
    stageEffectful(RangeForeach(start, end, step, fBlk, i), effects.star)(ctx)
  }

  @internal def fromIndex(x: Index)(implicit ctx: SrcCtx) = alloc(Some(x), x + 1, None, None, isUnit = true)

  @internal def alloc(start: Option[Index], end: Index, stride: Option[Index], par: Option[Index], isUnit: Boolean = false) = {
    Range(start,end,stride,par,isUnit)
  }

}

case class Range64(start: Option[Int64], end: Int64, step: Option[Index], p: Option[Index], isUnit: Boolean) {
  @api def by(step: Index): Range64 = Range64(start, end, Some(step), p, isUnit = false)
  @api def par(p: Index): Range64 = Range64(start, end, step, Some(p), isUnit = false)

  // @api def ::(start2: Int64): Range64 = Range64(Some(start2), end, start, p, isUnit = false)

  // @api def foreach(func: Index => MUnit): MUnit = {
  //   val i = fresh[Index]
  //   val fBlk = () => func(wrap(i)).s
  //   val begin  = start.map(_.s).getOrElse(int64(0))
  //   val stride = step.map(_.s).getOrElse(int32(1))
  //   MUnit(range_foreach(begin, end.s, stride, fBlk(), i))
  // }

  // @api def length: Int64 = (start, end, step) match {
  //   case (None, e, None) => e
  //   case (Some(s), e, None) => e - s
  //   case (None, e, Some(st)) => (e + st.to[Int64] - 1) / st.to[Int64]
  //   case (Some(s), e, Some(st)) => (e - s + st.to[Int64] - 1) / st.to[Int64]
  // }
}
object Range64 {
  @internal def fromInt64(x: Int64) = alloc(Some(x), x + 1, None, None, isUnit = true)

  @internal def alloc(start: Option[Int64], end: Int64, stride: Option[Index], par: Option[Index], isUnit: Boolean = false) = {
    Range64(start,end,stride,par,isUnit)
  }
}
