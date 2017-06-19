package spatial.lang.static

import argon.core._
import forge._

trait RangeLowPriorityImplicits { this: SpatialApi =>

  // Have to make this a lower priority, otherwise seems to prefer this + Range infix op over the implicit class on Index
  @api implicit def index2range(x: Index)(implicit ctx: SrcCtx): MRange = MRange.fromIndex(x)
}

trait RangeApi extends RangeLowPriorityImplicits { this: SpatialApi =>

  def * = Wildcard()

  implicit class IndexRangeOps(x: Index) {
    @internal private def lft(x: Int) = lift[Int,Index](x)
    @api def by(step: Int): MRange = MRange.alloc(None, x, Some(lft(step)), None)
    @api def par(p: Int): MRange = MRange.alloc(None, x, None, Some(lft(p)))
    @api def until(end: Int): MRange = MRange.alloc(Some(x), lft(end), None, None)

    @api def by(step: Index): MRange = MRange.alloc(None, x, Some(step), None)
    @api def par(p: Index): MRange = MRange.alloc(None, x, None, Some(p))
    @api def until(end: Index): MRange = MRange.alloc(Some(x), end, None, None)

    @api def ::(start: Index): MRange = MRange.alloc(Some(start), x, None, None)
  }

  implicit class intWrapper(x: Int) {
    @internal private def lft(x: Int) = lift[Int,Index](x)
    @api def until(end: Index): MRange = MRange.alloc(Some(lft(x)), end, None, None)
    @api def by(step: Index): MRange = MRange.alloc(None, lft(x), Some(step), None)
    @api def par(p: Index): MRange = MRange.alloc(None, lft(x), None, Some(p))

    @api def until(end: Int): MRange = MRange.alloc(Some(lft(x)), lft(end), None, None)
    @api def by(step: Int): MRange = MRange.alloc(None, lft(x), Some(lft(step)), None)
    @api def par(p: Int): MRange = MRange.alloc(None, lft(x), None, Some(lft(p)))

    @api def ::(start: Index): MRange = MRange.alloc(Some(start), lft(x), None, None)
    @api def ::(start: Int): MRange = MRange.alloc(Some(lft(start)), lft(x), None, None)

    @api def to[B:Type](implicit cast: Cast[Int,B]): B = cast(x)
  }

  // Implicitly get value of register to use in counter definitions
  @api implicit def regToIndexRange(x: Reg[Index])(implicit ctx: SrcCtx): IndexRangeOps = IndexRangeOps(x.value)
}

