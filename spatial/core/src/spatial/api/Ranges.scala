package spatial.api

import spatial._
import forge._

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

trait RangeLowPriorityImplicits { this: SpatialExp =>

  // Have to make this a lower priority, otherwise seems to prefer this + Range infix op over the implicit class on Index
  implicit def index2range(x: Index)(implicit ctx: SrcCtx): Range = index_to_range(x)
}

trait RangeApi extends RangeExp with RangeLowPriorityImplicits { this: SpatialApi =>

  def * = Wildcard()

  implicit class IndexRangeOps(x: Index) {
    private def lft(x: scala.Int)(implicit ctx: SrcCtx) = lift[scala.Int,Index](x)
    @api def by(step: scala.Int): Range = range_alloc(None, x, Some(lft(step)), None)
    @api def par(p: scala.Int): Range = range_alloc(None, x, None, Some(lft(p)))
    @api def until(end: scala.Int): Range = range_alloc(Some(x), lft(end), None, None)

    @api def by(step: Index): Range = range_alloc(None, x, Some(step), None)
    @api def par(p: Index): Range = range_alloc(None, x, None, Some(p))
    @api def until(end: Index): Range = range_alloc(Some(x), end, None, None)

    @api def ::(start: Index): Range = range_alloc(Some(start), x, None, None)
  }

  implicit class intWrapper(x: scala.Int) {
    private def lft(x: Int)(implicit ctx: SrcCtx) = lift[Int,Index](x)
    @api def until(end: Index): Range = range_alloc(Some(lft(x)), end, None, None)
    @api def by(step: Index): Range = range_alloc(None, lft(x), Some(step), None)
    @api def par(p: Index): Range = range_alloc(None, lft(x), None, Some(p))

    @api def until(end: scala.Int): Range = range_alloc(Some(lft(x)), lft(end), None, None)
    @api def by(step: scala.Int): Range = range_alloc(None, lft(x), Some(lft(step)), None)
    @api def par(p: scala.Int): Range = range_alloc(None, lft(x), None, Some(lft(p)))

    @api def ::(start: Index): Range = range_alloc(Some(start), lft(x), None, None)
    @api def ::(start: scala.Int): Range = range_alloc(Some(lft(start)), lft(x), None, None)

    @api def to[B:Meta](implicit cast: Cast[scala.Int,B]): B = cast(x)
  }

  // Implicitly get value of register to use in counter definitions
  implicit def regToIndexRange(x: Reg[Index])(implicit ctx: SrcCtx): IndexRangeOps = IndexRangeOps(x.value)
}


trait RangeExp { this: SpatialExp =>

  implicit class IndexRangeInternalOps(x: Index) {
    def toRange(implicit ctx: SrcCtx): Range = index_to_range(x)
  }
  implicit class Int64RangeInternalOps(x: Int64) {
    def toRange64(implicit ctx: SrcCtx): Range64 = index_to_range64(x)
  }

  case class Wildcard()

  case class Range(start: Option[Index], end: Index, step: Option[Index], p: Option[Index], isUnit: Boolean) {
    @api def by(step: Index): Range = Range(start, end, Some(step), p, isUnit = false)
    @api def par(p: Index): Range = Range(start, end, step, Some(p), isUnit = false)

    @api def ::(start2: Index): Range = Range(Some(start2), end, start, p, isUnit = false)

    @api def foreach(func: Index => Void): Void = {
      val i = fresh[Index]
      val fBlk = () => func(wrap(i)).s
      val begin  = start.map(_.s).getOrElse(int32(0))
      val stride = step.map(_.s).getOrElse(int32(1))
      Void(range_foreach(begin, end.s, stride, fBlk(), i))
    }

    @api def length: Index = (start, end, step) match {
      case (None, e, None) => e
      case (Some(s), e, None) => e - s
      case (None, e, Some(st)) => (e + st - 1) / st
      case (Some(s), e, Some(st)) => (e - s + st - 1) / st
    }
  }

  case class Range64(start: Option[Int64], end: Int64, step: Option[Index], p: Option[Index], isUnit: Boolean) {
    @api def by(step: Index): Range64 = Range64(start, end, Some(step), p, isUnit = false)
    @api def par(p: Index): Range64 = Range64(start, end, step, Some(p), isUnit = false)

    // @api def ::(start2: Int64): Range64 = Range64(Some(start2), end, start, p, isUnit = false)

    // @api def foreach(func: Index => Void): Void = {
    //   val i = fresh[Index]
    //   val fBlk = () => func(wrap(i)).s
    //   val begin  = start.map(_.s).getOrElse(int64(0))
    //   val stride = step.map(_.s).getOrElse(int32(1))
    //   Void(range_foreach(begin, end.s, stride, fBlk(), i))
    // }

    // @api def length: Int64 = (start, end, step) match { 
    //   case (None, e, None) => e
    //   case (Some(s), e, None) => e - s
    //   case (None, e, Some(st)) => (e + st.to[Int64] - 1) / st.to[Int64]
    //   case (Some(s), e, Some(st)) => (e - s + st.to[Int64] - 1) / st.to[Int64]
    // }
  }

  protected def range_alloc(start: Option[Index], end: Index, stride: Option[Index], par: Option[Index], isUnit: Boolean = false) = {
    Range(start,end,stride,par,isUnit)
  }
  protected def range_alloc64(start: Option[Int64], end: Int64, stride: Option[Index], par: Option[Index], isUnit: Boolean = false) = {
    Range64(start,end,stride,par,isUnit)
  }
  protected def index_to_range(x: Index)(implicit ctx: SrcCtx) = range_alloc(Some(x), x + 1, None, None, isUnit = true)
  protected def index_to_range64(x: Int64)(implicit ctx: SrcCtx) = range_alloc64(Some(x), x + 1, None, None, isUnit = true)

  /** IR Nodes **/
  case class RangeForeach(
    start: Exp[Index],
    end:   Exp[Index],
    step:  Exp[Index],
    func:  Block[Void],
    i:     Bound[Index]
  ) extends Op[Void] {
    def mirror(f:Tx) = range_foreach(f(start),f(end),f(step),f(func),i)
    override def inputs = dyns(start,end,step) ++ dyns(func)
    override def freqs  = normal(start) ++ normal(end) ++ normal(step) ++ hot(func)
    override def binds  = i +: super.binds
  }

  /*case class RangeReduce[T](
    start:  Exp[Index],
    end:    Exp[Index],
    step:   Exp[Index],
    func:   Block[T],
    reduce: Block[T],
    rV:     (Bound[Index],Bound[Index]),
    i:      Bound[Index]
  )*/

  /** Constructors **/
  @internal def range_foreach(start: Exp[Index], end: Exp[Index], step: Exp[Index], func: => Exp[Void], i: Bound[Index]) = {
    val fBlk = stageBlock { func }
    val effects = fBlk.summary
    stageEffectful(RangeForeach(start, end, step, fBlk, i), effects)(ctx)
  }

}