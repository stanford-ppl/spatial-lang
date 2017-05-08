package spatial.api

import spatial._
import forge._

trait DRAMApi extends DRAMExp { this: SpatialApi =>

  type Tile[T] = DRAMDenseTile[T]
  type SparseTile[T] = DRAMSparseTile[T]

  @api def DRAM[T:Type:Bits](d1: Index): DRAM1[T] = DRAM1(dram_alloc[T,DRAM1](d1.s))
  @api def DRAM[T:Type:Bits](d1: Index, d2: Index): DRAM2[T] = DRAM2(dram_alloc[T,DRAM2](d1.s,d2.s))
  @api def DRAM[T:Type:Bits](d1: Index, d2: Index, d3: Index): DRAM3[T] = DRAM3(dram_alloc[T,DRAM3](d1.s,d2.s))
  @api def DRAM[T:Type:Bits](d1: Index, d2: Index, d3: Index, d4: Index): DRAM4[T] = DRAM4(dram_alloc[T,DRAM4](d1.s,d2.s,d3.s,d4.s))
  @api def DRAM[T:Type:Bits](d1: Index, d2: Index, d3: Index, d4: Index, d5: Index): DRAM5[T] = DRAM5(dram_alloc[T,DRAM5](d1.s,d2.s,d3.s,d4.s,d5.s))
}

trait DRAMExp { this: SpatialExp =>

  /** Infix methods **/
  trait DRAM[T] { this: Template[_] =>
    def s: Exp[DRAM[T]]

    @api def address: Int64
  }

  case class DRAM1[T:Meta:Bits](s: Exp[DRAM1[T]]) extends Template[DRAM1[T]] with DRAM[T] {
    @api def toTile(ranges: Seq[Range]): DRAMDenseTile1[T] = DRAMDenseTile1(s, ranges)
    @api def apply(range: Range): DRAMDenseTile1[T] = DRAMDenseTile1(this.s, Seq(range))

    @api def apply(addrs: SRAM1[Index]): DRAMSparseTile[T] = this.apply(addrs, wrap(stagedDimsOf(addrs.s).head))
    @api def apply(addrs: SRAM1[Index], len: Index): DRAMSparseTile[T] = DRAMSparseTile(this.s, addrs, len)

    @api def store(sram: SRAM1[T]): Void = dense_transfer(this.toTile(sram.ranges), sram, isLoad = false)
    @api def store(fifo: FIFO[T]): Void = dense_transfer(this.toTile(fifo.ranges), fifo, isLoad = false)
    @api def store(filo: FILO[T]): Void = dense_transfer(this.toTile(filo.ranges), filo, isLoad = false)
    @api def store(regs: RegFile1[T]): Void = dense_transfer(this.toTile(regs.ranges), regs, isLoad = false)
    @api def address: Int64 = wrap(get_dram_addr(this.s))
  }

  case class DRAM2[T:Meta:Bits](s: Exp[DRAM2[T]]) extends Template[DRAM2[T]] with DRAM[T] {
    @api def toTile(ranges: Seq[Range]): DRAMDenseTile2[T] = DRAMDenseTile2(this.s, ranges)
    @api def apply(rows: Index, cols: Range) = DRAMDenseTile1(this.s, Seq(rows.toRange, cols))
    @api def apply(rows: Range, cols: Index) = DRAMDenseTile1(this.s, Seq(rows, cols.toRange))
    @api def apply(rows: Range, cols: Range) = DRAMDenseTile2(this.s, Seq(rows, cols))

    @api def store(sram: SRAM2[T]): Void = dense_transfer(this.toTile(sram.ranges), sram, isLoad = false)
    @api def store(regs: RegFile2[T]): Void = dense_transfer(this.toTile(regs.ranges), regs, isLoad = false)
    @api def address: Int64 = wrap(get_dram_addr(this.s))
  }

  case class DRAM3[T:Meta:Bits](s: Exp[DRAM3[T]]) extends Template[DRAM3[T]] with DRAM[T] {
    @api def toTile(ranges: Seq[Range]): DRAMDenseTile3[T] = DRAMDenseTile3(this.s, ranges)
    @api def apply(p: Index, r: Index, c: Range) = DRAMDenseTile1(this.s, Seq(p.toRange, r.toRange, c))
    @api def apply(p: Index, r: Range, c: Index) = DRAMDenseTile1(this.s, Seq(p.toRange, r, c.toRange))
    @api def apply(p: Index, r: Range, c: Range) = DRAMDenseTile2(this.s, Seq(p.toRange, r, c))
    @api def apply(p: Range, r: Index, c: Index) = DRAMDenseTile1(this.s, Seq(p, r.toRange, c.toRange))
    @api def apply(p: Range, r: Index, c: Range) = DRAMDenseTile2(this.s, Seq(p, r.toRange, c))
    @api def apply(p: Range, r: Range, c: Index) = DRAMDenseTile2(this.s, Seq(p, r, c.toRange))
    @api def apply(p: Range, r: Range, c: Range) = DRAMDenseTile3(this.s, Seq(p, r, c))

    @api def store(sram: SRAM3[T]): Void = dense_transfer(this.toTile(sram.ranges), sram, isLoad = false)
    @api def address: Int64 = wrap(get_dram_addr(this.s))
  }
  case class DRAM4[T:Meta:Bits](s: Exp[DRAM4[T]]) extends Template[DRAM4[T]] with DRAM[T] {
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

    @api def store(sram: SRAM4[T]): Void = dense_transfer(this.toTile(sram.ranges), sram, isLoad = false)
    @api def address: Int64 = wrap(get_dram_addr(this.s))
  }

  case class DRAM5[T:Meta:Bits](s: Exp[DRAM5[T]]) extends Template[DRAM5[T]] with DRAM[T] {
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

    @api def store(sram: SRAM4[T]): Void = dense_transfer(this.toTile(sram.ranges), sram, isLoad = false)
    @api def address: Int64 = wrap(get_dram_addr(this.s))
  }


  /** Dense Tiles **/
  trait DRAMDenseTile[T] {
    def dram: Exp[DRAM[T]]
    def ranges: Seq[Range]
  }

  case class DRAMDenseTile1[T:Meta:Bits](dram: Exp[DRAM[T]], ranges: Seq[Range]) extends DRAMDenseTile[T] {
    @api def store(sram: SRAM1[T]): Void    = dense_transfer(this, sram, isLoad = false)
    @api def store(fifo: FIFO[T]): Void     = dense_transfer(this, fifo, isLoad = false)
    @api def store(filo: FILO[T]): Void     = dense_transfer(this, filo, isLoad = false)
    @api def store(regs: RegFile1[T]): Void = dense_transfer(this, regs, isLoad = false)
  }
  case class DRAMDenseTile2[T:Meta:Bits](dram: Exp[DRAM[T]], ranges: Seq[Range]) extends DRAMDenseTile[T] {
    @api def store(sram: SRAM2[T]): Void    = dense_transfer(this, sram, isLoad = false)
    @api def store(regs: RegFile2[T]): Void = dense_transfer(this, regs, isLoad = false)
  }
  case class DRAMDenseTile3[T:Meta:Bits](dram: Exp[DRAM[T]], ranges: Seq[Range]) extends DRAMDenseTile[T] {
    @api def store(sram: SRAM3[T]): Void   = dense_transfer(this, sram, isLoad = false)
  }
  case class DRAMDenseTile4[T:Meta:Bits](dram: Exp[DRAM[T]], ranges: Seq[Range]) extends DRAMDenseTile[T] {
    @api def store(sram: SRAM4[T]): Void   = dense_transfer(this, sram, isLoad = false)
  }
  case class DRAMDenseTile5[T:Meta:Bits](dram: Exp[DRAM[T]], ranges: Seq[Range]) extends DRAMDenseTile[T] {
    @api def store(sram: SRAM2[T]): Void   = dense_transfer(this, sram, isLoad = false)
  }

  /** Sparse Tiles are limited to 1D right now **/
  case class DRAMSparseTile[T:Meta:Bits](dram: Exp[DRAM[T]], addrs: SRAM1[Index], len: Index) {
    @api def scatter(sram: SRAM1[T]): Void = sparse_transfer(this, sram, isLoad = false)
  }


  /** Staged Type **/
  trait DRAMType[T] {
    def child: Meta[T]
    def isPrimitive = false
  }
  case class DRAM1Type[T:Bits](child: Meta[T]) extends Meta[DRAM1[T]] with DRAMType[T] {
    override def wrapped(x: Exp[DRAM1[T]]) = DRAM1(x)(child,bits[T])
    override def typeArguments = List(child)
    override def stagedClass = classOf[DRAM1[T]]
  }
  case class DRAM2Type[T:Bits](child: Meta[T]) extends Meta[DRAM2[T]] with DRAMType[T] {
    override def wrapped(x: Exp[DRAM2[T]]) = DRAM2(x)(child,bits[T])
    override def typeArguments = List(child)
    override def stagedClass = classOf[DRAM2[T]]
  }
  case class DRAM3Type[T:Bits](child: Meta[T]) extends Meta[DRAM3[T]] with DRAMType[T] {
    override def wrapped(x: Exp[DRAM3[T]]) = DRAM3(x)(child,bits[T])
    override def typeArguments = List(child)
    override def stagedClass = classOf[DRAM3[T]]
  }
  case class DRAM4Type[T:Bits](child: Meta[T]) extends Meta[DRAM4[T]] with DRAMType[T] {
    override def wrapped(x: Exp[DRAM4[T]]) = DRAM4(x)(child,bits[T])
    override def typeArguments = List(child)
    override def stagedClass = classOf[DRAM4[T]]
  }
  case class DRAM5Type[T:Bits](child: Meta[T]) extends Meta[DRAM5[T]] with DRAMType[T] {
    override def wrapped(x: Exp[DRAM5[T]]) = DRAM5(x)(child,bits[T])
    override def typeArguments = List(child)
    override def stagedClass = classOf[DRAM5[T]]
  }

  implicit def dram1Type[T:Meta:Bits]: Meta[DRAM1[T]] = DRAM1Type(meta[T])
  implicit def dram2Type[T:Meta:Bits]: Meta[DRAM2[T]] = DRAM2Type(meta[T])
  implicit def dram3Type[T:Meta:Bits]: Meta[DRAM3[T]] = DRAM3Type(meta[T])
  implicit def dram4Type[T:Meta:Bits]: Meta[DRAM4[T]] = DRAM4Type(meta[T])
  implicit def dram5Type[T:Meta:Bits]: Meta[DRAM5[T]] = DRAM5Type(meta[T])


  /** IR Nodes **/
  case class DRAMNew[T:Type:Bits,C[_]<:DRAM[_]](dims: Seq[Exp[Index]])(implicit cT: Type[C[T]]) extends Op2[T,C[T]] {
    def mirror(f:Tx) = dram_alloc[T,C](f(dims):_*)
    val bT = bits[T]
    def zero: Exp[T] = bT.zero.s
  }

  case class GetDRAMAddress[T:Type:Bits](dram: Exp[DRAM[T]]) extends Op[Int64] {
    def mirror(f:Tx) = get_dram_addr(f(dram))
  }

  /** Constructors **/
  def dram_alloc[T:Type:Bits,C[_]<:DRAM[_]](dims: Exp[Index]*)(implicit ctx: SrcCtx, cT: Type[C[T]]): Exp[C[T]] = {
    stageMutable( DRAMNew[T,C](dims) )(ctx)
  }
  def get_dram_addr[T:Type:Bits](dram: Exp[DRAM[T]])(implicit ctx: SrcCtx): Exp[Int64] = {
    stage( GetDRAMAddress(dram) )(ctx)
  }
}