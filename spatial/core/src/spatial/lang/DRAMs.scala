package spatial.lang

import forge._
import spatial.nodes._
import spatial.utils._

trait DRAM[T] { this: Template[_] =>
  def s: Exp[DRAM[T]]

  @api def address: Int64
}
object DRAM {
  @api def apply[T:Type:Bits](d1: Index): DRAM1[T] = DRAM1(alloc[T,DRAM1](d1.s))
  @api def apply[T:Type:Bits](d1: Index, d2: Index): DRAM2[T] = DRAM2(alloc[T,DRAM2](d1.s,d2.s))
  @api def apply[T:Type:Bits](d1: Index, d2: Index, d3: Index): DRAM3[T] = DRAM3(alloc[T,DRAM3](d1.s,d2.s,d3.s))
  @api def apply[T:Type:Bits](d1: Index, d2: Index, d3: Index, d4: Index): DRAM4[T] = DRAM4(alloc[T,DRAM4](d1.s,d2.s,d3.s,d4.s))
  @api def apply[T:Type:Bits](d1: Index, d2: Index, d3: Index, d4: Index, d5: Index): DRAM5[T] = DRAM5(alloc[T,DRAM5](d1.s,d2.s,d3.s,d4.s,d5.s))

  /** Constructors **/
  @internal def alloc[T:Type:Bits,C[_]<:DRAM[_]](dims: Exp[Index]*)(implicit cT: Type[C[T]]): Exp[C[T]] = {
    stageMutable( DRAMNew[T,C](dims, implicitly[Bits[T]].zero.s) )(ctx)
  }
  @internal def addr[T:Type:Bits](dram: Exp[DRAM[T]]): Exp[Int64] = {
    stage( GetDRAMAddress(dram) )(ctx)
  }
}

case class DRAM1[T:Type:Bits](s: Exp[DRAM1[T]]) extends Template[DRAM1[T]] with DRAM[T] {
  @api def toTile(ranges: Seq[Range]): DRAMDenseTile1[T] = DRAMDenseTile1(s, ranges)
  @api def apply(range: Range): DRAMDenseTile1[T] = DRAMDenseTile1(this.s, Seq(range))

  @api def apply(addrs: SRAM1[Index]): DRAMSparseTile[T] = this.apply(addrs, wrap(stagedDimsOf(addrs.s).head))
  @api def apply(addrs: SRAM1[Index], len: Index): DRAMSparseTile[T] = DRAMSparseTile(this.s, addrs, len)

  @api def store(sram: SRAM1[T]): MUnit = dense_transfer(this.toTile(sram.ranges), sram, isLoad = false)
  @api def store(fifo: FIFO[T]): MUnit = dense_transfer(this.toTile(fifo.ranges), fifo, isLoad = false)
  @api def store(filo: FILO[T]): MUnit = dense_transfer(this.toTile(filo.ranges), filo, isLoad = false)
  @api def store(regs: RegFile1[T]): MUnit = dense_transfer(this.toTile(regs.ranges), regs, isLoad = false)
  @api def address: Int64 = wrap(get_dram_addr(this.s))
}
object DRAM1 {
  implicit def dram1Type[T:Type:Bits]: Type[DRAM1[T]] = DRAM1Type(typ[T])
}

case class DRAM2[T:Type:Bits](s: Exp[DRAM2[T]]) extends Template[DRAM2[T]] with DRAM[T] {
  @api def toTile(ranges: Seq[Range]): DRAMDenseTile2[T] = DRAMDenseTile2(this.s, ranges)
  @api def apply(rows: Index, cols: Range) = DRAMDenseTile1(this.s, Seq(rows.toRange, cols))
  @api def apply(rows: Range, cols: Index) = DRAMDenseTile1(this.s, Seq(rows, cols.toRange))
  @api def apply(rows: Range, cols: Range) = DRAMDenseTile2(this.s, Seq(rows, cols))

  @api def store(sram: SRAM2[T]): MUnit = dense_transfer(this.toTile(sram.ranges), sram, isLoad = false)
  @api def store(regs: RegFile2[T]): MUnit = dense_transfer(this.toTile(regs.ranges), regs, isLoad = false)
  @api def address: Int64 = wrap(DRAM.addr(this.s))
}
object DRAM2 {
  implicit def dram2Type[T:Type:Bits]: Type[DRAM2[T]] = DRAM2Type(typ[T])
}

case class DRAM3[T:Type:Bits](s: Exp[DRAM3[T]]) extends Template[DRAM3[T]] with DRAM[T] {
  @api def toTile(ranges: Seq[Range]): DRAMDenseTile3[T] = DRAMDenseTile3(this.s, ranges)
  @api def apply(p: Index, r: Index, c: Range) = DRAMDenseTile1(this.s, Seq(p.toRange, r.toRange, c))
  @api def apply(p: Index, r: Range, c: Index) = DRAMDenseTile1(this.s, Seq(p.toRange, r, c.toRange))
  @api def apply(p: Index, r: Range, c: Range) = DRAMDenseTile2(this.s, Seq(p.toRange, r, c))
  @api def apply(p: Range, r: Index, c: Index) = DRAMDenseTile1(this.s, Seq(p, r.toRange, c.toRange))
  @api def apply(p: Range, r: Index, c: Range) = DRAMDenseTile2(this.s, Seq(p, r.toRange, c))
  @api def apply(p: Range, r: Range, c: Index) = DRAMDenseTile2(this.s, Seq(p, r, c.toRange))
  @api def apply(p: Range, r: Range, c: Range) = DRAMDenseTile3(this.s, Seq(p, r, c))

  @api def store(sram: SRAM3[T]): MUnit = dense_transfer(this.toTile(sram.ranges), sram, isLoad = false)
  @api def address: Int64 = wrap(DRAM.addr(this.s))
}
object DRAM3 {
  implicit def dram3Type[T:Type:Bits]: Type[DRAM3[T]] = DRAM3Type(typ[T])
}

case class DRAM4[T:Type:Bits](s: Exp[DRAM4[T]]) extends Template[DRAM4[T]] with DRAM[T] {
  @api def toTile(ranges: Seq[Range]): DRAMDenseTile4[T] = DRAMDenseTile4(this.s, ranges)
  @api def apply(q: Index, p: Index, r: Index, c: Range) = DRAMDenseTile1(this.s, Seq(q.toRange, p.toRange, r.toRange, c))
  @api def apply(q: Index, p: Index, r: Range, c: Index) = DRAMDenseTile1(this.s, Seq(q.toRange, p.toRange, r, c.toRange))
  @api def apply(q: Index, p: Index, r: Range, c: Range) = DRAMDenseTile2(this.s, Seq(q.toRange, p.toRange, r, c))
  @api def apply(q: Index, p: Range, r: Index, c: Index) = DRAMDenseTile1(this.s, Seq(q.toRange, p, r.toRange, c.toRange))
  @api def apply(q: Index, p: Range, r: Index, c: Range) = DRAMDenseTile2(this.s, Seq(q.toRange, p, r.toRange, c))
  @api def apply(q: Index, p: Range, r: Range, c: Index) = DRAMDenseTile2(this.s, Seq(q.toRange, p, r, c.toRange))
  @api def apply(q: Index, p: Range, r: Range, c: Range) = DRAMDenseTile3(this.s, Seq(q.toRange, p, r, c))
  @api def apply(q: Range, p: Index, r: Index, c: Index) = DRAMDenseTile1(this.s, Seq(q, p.toRange, r.toRange, c.toRange))
  @api def apply(q: Range, p: Index, r: Index, c: Range) = DRAMDenseTile2(this.s, Seq(q, p.toRange, r.toRange, c))
  @api def apply(q: Range, p: Index, r: Range, c: Index) = DRAMDenseTile2(this.s, Seq(q, p.toRange, r, c.toRange))
  @api def apply(q: Range, p: Index, r: Range, c: Range) = DRAMDenseTile3(this.s, Seq(q, p.toRange, r, c))
  @api def apply(q: Range, p: Range, r: Index, c: Index) = DRAMDenseTile2(this.s, Seq(q, p, r.toRange, c.toRange))
  @api def apply(q: Range, p: Range, r: Index, c: Range) = DRAMDenseTile3(this.s, Seq(q, p, r.toRange, c))
  @api def apply(q: Range, p: Range, r: Range, c: Index) = DRAMDenseTile3(this.s, Seq(q, p, r, c.toRange))
  @api def apply(q: Range, p: Range, r: Range, c: Range) = DRAMDenseTile4(this.s, Seq(q, p, r, c))

  @api def store(sram: SRAM4[T]): MUnit = dense_transfer(this.toTile(sram.ranges), sram, isLoad = false)
  @api def address: Int64 = wrap(DRAM.addr(this.s))
}
object DRAM4 {
  implicit def dram4Type[T:Type:Bits]: Type[DRAM4[T]] = DRAM4Type(typ[T])
}

case class DRAM5[T:Type:Bits](s: Exp[DRAM5[T]]) extends Template[DRAM5[T]] with DRAM[T] {
  @api def toTile(ranges: Seq[Range]): DRAMDenseTile5[T] = DRAMDenseTile5(this.s, ranges)
  // I'm not getting carried away, you're getting carried away! By the amazingness of this code!
  @api def apply(x: Index, q: Index, p: Index, r: Index, c: Range) = DRAMDenseTile1(this.s, Seq(x.toRange, q.toRange, p.toRange, r.toRange, c))
  @api def apply(x: Index, q: Index, p: Index, r: Range, c: Index) = DRAMDenseTile1(this.s, Seq(x.toRange, q.toRange, p.toRange, r, c.toRange))
  @api def apply(x: Index, q: Index, p: Index, r: Range, c: Range) = DRAMDenseTile2(this.s, Seq(x.toRange, q.toRange, p.toRange, r, c))
  @api def apply(x: Index, q: Index, p: Range, r: Index, c: Index) = DRAMDenseTile1(this.s, Seq(x.toRange, q.toRange, p, r.toRange, c.toRange))
  @api def apply(x: Index, q: Index, p: Range, r: Index, c: Range) = DRAMDenseTile2(this.s, Seq(x.toRange, q.toRange, p, r.toRange, c))
  @api def apply(x: Index, q: Index, p: Range, r: Range, c: Index) = DRAMDenseTile2(this.s, Seq(x.toRange, q.toRange, p, r, c.toRange))
  @api def apply(x: Index, q: Index, p: Range, r: Range, c: Range) = DRAMDenseTile3(this.s, Seq(x.toRange, q.toRange, p, r, c))
  @api def apply(x: Index, q: Range, p: Index, r: Index, c: Index) = DRAMDenseTile1(this.s, Seq(x.toRange, q, p.toRange, r.toRange, c.toRange))
  @api def apply(x: Index, q: Range, p: Index, r: Index, c: Range) = DRAMDenseTile2(this.s, Seq(x.toRange, q, p.toRange, r.toRange, c))
  @api def apply(x: Index, q: Range, p: Index, r: Range, c: Index) = DRAMDenseTile2(this.s, Seq(x.toRange, q, p.toRange, r, c.toRange))
  @api def apply(x: Index, q: Range, p: Index, r: Range, c: Range) = DRAMDenseTile3(this.s, Seq(x.toRange, q, p.toRange, r, c))
  @api def apply(x: Index, q: Range, p: Range, r: Index, c: Index) = DRAMDenseTile2(this.s, Seq(x.toRange, q, p, r.toRange, c.toRange))
  @api def apply(x: Index, q: Range, p: Range, r: Index, c: Range) = DRAMDenseTile3(this.s, Seq(x.toRange, q, p, r.toRange, c))
  @api def apply(x: Index, q: Range, p: Range, r: Range, c: Index) = DRAMDenseTile3(this.s, Seq(x.toRange, q, p, r, c.toRange))
  @api def apply(x: Index, q: Range, p: Range, r: Range, c: Range) = DRAMDenseTile4(this.s, Seq(x.toRange, q, p, r, c))
  @api def apply(x: Range, q: Index, p: Index, r: Index, c: Index) = DRAMDenseTile1(this.s, Seq(x, q.toRange, p.toRange, r.toRange, c.toRange))
  @api def apply(x: Range, q: Index, p: Index, r: Index, c: Range) = DRAMDenseTile2(this.s, Seq(x, q.toRange, p.toRange, r.toRange, c))
  @api def apply(x: Range, q: Index, p: Index, r: Range, c: Index) = DRAMDenseTile2(this.s, Seq(x, q.toRange, p.toRange, r, c.toRange))
  @api def apply(x: Range, q: Index, p: Index, r: Range, c: Range) = DRAMDenseTile3(this.s, Seq(x, q.toRange, p.toRange, r, c))
  @api def apply(x: Range, q: Index, p: Range, r: Index, c: Index) = DRAMDenseTile2(this.s, Seq(x, q.toRange, p, r.toRange, c.toRange))
  @api def apply(x: Range, q: Index, p: Range, r: Index, c: Range) = DRAMDenseTile3(this.s, Seq(x, q.toRange, p, r.toRange, c))
  @api def apply(x: Range, q: Index, p: Range, r: Range, c: Index) = DRAMDenseTile3(this.s, Seq(x, q.toRange, p, r, c.toRange))
  @api def apply(x: Range, q: Index, p: Range, r: Range, c: Range) = DRAMDenseTile4(this.s, Seq(x, q.toRange, p, r, c))
  @api def apply(x: Range, q: Range, p: Index, r: Index, c: Index) = DRAMDenseTile2(this.s, Seq(x, q, p.toRange, r.toRange, c.toRange))
  @api def apply(x: Range, q: Range, p: Index, r: Index, c: Range) = DRAMDenseTile3(this.s, Seq(x, q, p.toRange, r.toRange, c))
  @api def apply(x: Range, q: Range, p: Index, r: Range, c: Index) = DRAMDenseTile3(this.s, Seq(x, q, p.toRange, r, c.toRange))
  @api def apply(x: Range, q: Range, p: Index, r: Range, c: Range) = DRAMDenseTile4(this.s, Seq(x, q, p.toRange, r, c))
  @api def apply(x: Range, q: Range, p: Range, r: Index, c: Index) = DRAMDenseTile3(this.s, Seq(x, q, p, r.toRange, c.toRange))
  @api def apply(x: Range, q: Range, p: Range, r: Index, c: Range) = DRAMDenseTile4(this.s, Seq(x, q, p, r.toRange, c))
  @api def apply(x: Range, q: Range, p: Range, r: Range, c: Index) = DRAMDenseTile4(this.s, Seq(x, q, p, r, c.toRange))
  @api def apply(x: Range, q: Range, p: Range, r: Range, c: Range) = DRAMDenseTile5(this.s, Seq(x, q, p, r, c))

  @api def store(sram: SRAM5[T]): MUnit = dense_transfer(this.toTile(sram.ranges), sram, isLoad = false)
  @api def address: Int64 = wrap(DRAM.addr(this.s))
}
object DRAM5 {
  implicit def dram5Type[T:Type:Bits]: Type[DRAM5[T]] = DRAM5Type(typ[T])
}

trait DRAMDenseTile[T] {
  def dram: Exp[DRAM[T]]
  def ranges: Seq[Range]
}

case class DRAMDenseTile1[T:Type:Bits](dram: Exp[DRAM[T]], ranges: Seq[Range]) extends DRAMDenseTile[T] {
  @api def store(sram: SRAM1[T]): MUnit    = dense_transfer(this, sram, isLoad = false)
  @api def store(fifo: FIFO[T]): MUnit     = dense_transfer(this, fifo, isLoad = false)
  @api def store(filo: FILO[T]): MUnit     = dense_transfer(this, filo, isLoad = false)
  @api def store(regs: RegFile1[T]): MUnit = dense_transfer(this, regs, isLoad = false)
}
case class DRAMDenseTile2[T:Type:Bits](dram: Exp[DRAM[T]], ranges: Seq[Range]) extends DRAMDenseTile[T] {
  @api def store(sram: SRAM2[T]): MUnit    = dense_transfer(this, sram, isLoad = false)
  @api def store(regs: RegFile2[T]): MUnit = dense_transfer(this, regs, isLoad = false)
}
case class DRAMDenseTile3[T:Type:Bits](dram: Exp[DRAM[T]], ranges: Seq[Range]) extends DRAMDenseTile[T] {
  @api def store(sram: SRAM3[T]): MUnit   = dense_transfer(this, sram, isLoad = false)
}
case class DRAMDenseTile4[T:Type:Bits](dram: Exp[DRAM[T]], ranges: Seq[Range]) extends DRAMDenseTile[T] {
  @api def store(sram: SRAM4[T]): MUnit   = dense_transfer(this, sram, isLoad = false)
}
case class DRAMDenseTile5[T:Type:Bits](dram: Exp[DRAM[T]], ranges: Seq[Range]) extends DRAMDenseTile[T] {
  @api def store(sram: SRAM5[T]): MUnit   = dense_transfer(this, sram, isLoad = false)
}

/** Sparse Tiles are limited to 1D right now **/
case class DRAMSparseTile[T:Type:Bits](dram: Exp[DRAM[T]], addrs: SRAM1[Index], len: Index) {
  @api def scatter(sram: SRAM1[T]): MUnit = sparse_transfer(this, sram, isLoad = false)
}
