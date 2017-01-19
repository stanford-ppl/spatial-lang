package spatial.api
import argon.ops._
import spatial.{SpatialApi, SpatialExp, SpatialOps}

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

trait RangeOps extends MemoryOps { this: SpatialOps =>
  type Range <: RangeOps

  protected trait RangeOps {
    def by(step: Index)(implicit ctx: SrcCtx): Range
    def par(p: Index)(implicit ctx: SrcCtx): Range

    def ::(x: Index)(implicit ctx: SrcCtx): Range
  }
  implicit class IndexRangeOps(x: Index) {
    def by(step: Int)(implicit ctx: SrcCtx): Range = range_alloc(None, x, Some(lift(step)), None)
    def par(p: Int)(implicit ctx: SrcCtx): Range = range_alloc(None, x, None, Some(lift(p)))
    def until(end: Int)(implicit ctx: SrcCtx): Range = range_alloc(Some(x), lift(end), None, None)

    def by(step: Index)(implicit ctx: SrcCtx): Range = range_alloc(None, x, Some(step), None)
    def par(p: Index)(implicit ctx: SrcCtx): Range = range_alloc(None, x, None, Some(p))
    def until(end: Index)(implicit ctx: SrcCtx): Range = range_alloc(Some(x), end, None, None)

    def ::(start: Index)(implicit ctx: SrcCtx): Range = range_alloc(Some(start), x, None, None)
  }

  private[spatial] def range_alloc(start: Option[Index], end: Index, stride: Option[Index], par: Option[Index]): Range

  def Range(start: Index, end: Index, stride: Index, par: Index): Range = {
    range_alloc(Some(start), end, Some(stride), Some(par))
  }

  implicit def index2range(x: Index)(implicit ctx: SrcCtx): Range
}
trait RangeApi extends RangeOps with MemoryApi {
  this: SpatialApi =>

  implicit class intWrapper(x: scala.Int) {
    def until(end: Index)(implicit ctx: SrcCtx): Range = range_alloc(Some(lift(x)), end, None, None)
    def by(step: Index)(implicit ctx: SrcCtx): Range = range_alloc(None, lift(x), Some(step), None)
    def par(p: Index)(implicit ctx: SrcCtx): Range = range_alloc(None, lift(x), None, Some(p))

    def until(end: scala.Int)(implicit ctx: SrcCtx): Range = range_alloc(Some(lift(x)), lift(end), None, None)
    def by(step: scala.Int)(implicit ctx: SrcCtx): Range = range_alloc(None, lift(x), Some(lift(step)), None)
    def par(p: scala.Int)(implicit ctx: SrcCtx): Range = range_alloc(None, lift(x), None, Some(lift(p)))

    def ::(start: Index)(implicit ctx: SrcCtx): Range = range_alloc(Some(start), lift(x), None, None)
    def ::(start: scala.Int)(implicit ctx: SrcCtx): Range = range_alloc(Some(lift(start)), lift(x), None, None)
  }
}


trait RangeExp extends RangeOps with MemoryExp {
  this: SpatialExp =>

  case class Range(start: Option[Index], end: Index, step: Option[Index], p: Option[Index], isUnit: Boolean) extends RangeOps {
    def by(step: Index)(implicit ctx: SrcCtx): Range = Range(start, end, Some(step), p, isUnit = false)
    def par(p: Index)(implicit ctx: SrcCtx): Range = Range(start, end, step, Some(p), isUnit = false)

    def ::(start2: Index)(implicit ctx: SrcCtx): Range = Range(Some(start2), end, start, p, isUnit = false)

    def length(implicit ctx: SrcCtx) = (start, end, step) match {
      case (None, e, None) => e
      case (Some(s), e, None) => e - s
      case (None, e, Some(st)) => (e + st - 1) / st
      case (Some(s), e, Some(st)) => (e - s + st - 1) / st
    }
  }

  private[spatial] def range_alloc(start: Option[Index], end: Index, stride: Option[Index], par: Option[Index]) = {
    Range(start,end,stride,par, isUnit = start.isDefined || stride.isDefined || par.isDefined)
  }

  implicit def index2range(x: Index)(implicit ctx: SrcCtx): Range = Range(Some(x), x + 1, None, None, isUnit = true)
}