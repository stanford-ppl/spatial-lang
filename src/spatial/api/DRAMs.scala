package spatial.api

import argon.core.Staging
import spatial.SpatialExp
import forge._

trait DRAMApi extends DRAMExp with DRAMTransferApi {
  this: SpatialExp =>

  type Tile[T] = DRAMDenseTile[T]
  type SparseTile[T] = DRAMSparseTile[T]

  @api def DRAM[T:Type:Bits](dimA: Index, dimsB: Index*): DRAM[T] = {
    DRAM(dram_alloc[T](unwrap(dimA +: dimsB)))
  }

}

trait DRAMExp extends Staging with SRAMExp with FIFOExp with RangeExp with SpatialExceptions with DRAMTransferExp {
  this: SpatialExp =>

  /** Infix methods **/
  case class DRAM[T:Meta:Bits](s: Exp[DRAM[T]]) extends Template[DRAM[T]] {
    @api def address: Index = wrap(get_dram_addr(s))

    @api def apply(ranges: Range*): DRAMDenseTile[T] = DRAMDenseTile(this.s, ranges)

    @api def apply(addrs: SRAM1[Index]): DRAMSparseTile[T] = this.apply(addrs, wrap(stagedDimsOf(addrs.s).head))
    @api def apply(addrs: SRAM1[Index], len: Index): DRAMSparseTile[T] = DRAMSparseTile(this.s, addrs, len)

    @api def toTile: DRAMDenseTile[T] = {
      val ranges = dimsOf(s).map{d => range_alloc(None, wrap(d),None,None) }
      DRAMDenseTile(this.s, ranges)
    }

    @api def store(sram: SRAM[T]): Void = dense_transfer(this.toTile, sram, isLoad = false)
    @api def store(fifo: FIFO[T]): Void = dense_transfer(this.toTile, fifo, isLoad = false)
    @api def store(regs: RegFile[T]): Void = dense_transfer(this.toTile, regs, isLoad = false)
  }

  case class DRAMDenseTile[T:Meta:Bits](dram: Exp[DRAM[T]], ranges: Seq[Range]) {
    @api def store(sram: SRAM[T]): Void = dense_transfer(this, sram, isLoad = false)
    @api def store(fifo: FIFO[T]): Void = dense_transfer(this, fifo, isLoad = false)
    @api def store(regs: RegFile[T]): Void = dense_transfer(this, regs, isLoad = false)
  }

  case class DRAMSparseTile[T:Meta:Bits](dram: Exp[DRAM[T]], addrs: SRAM[Index], len: Index) {
    @api def scatter(sram: SRAM1[T]): Void = sparse_transfer(this, sram, isLoad = false)
  }


  /** Staged Type **/
  case class DRAMType[T:Bits](child: Meta[T]) extends Meta[DRAM[T]] {
    override def wrapped(x: Exp[DRAM[T]]) = DRAM(x)(child,bits[T])
    override def typeArguments = List(child)
    override def stagedClass = classOf[DRAM[T]]
    override def isPrimitive = false
  }
  implicit def dramType[T:Meta:Bits]: Type[DRAM[T]] = DRAMType[T](meta[T])


  /** IR Nodes **/
  case class DRAMNew[T:Type:Bits](dims: Seq[Exp[Index]]) extends Op2[T,DRAM[T]] {
    def mirror(f:Tx) = dram_alloc[T](f(dims))
    val bT = bits[T]
    def zero: Exp[T] = bT.zero.s
  }

  case class GetDRAMAddress[T:Type:Bits](dram: Exp[DRAM[T]]) extends Op[Index] {
    def mirror(f:Tx) = get_dram_addr(f(dram))
  }

  /** Constructors **/
  def dram_alloc[T:Type:Bits](dims: Seq[Exp[Index]])(implicit ctx: SrcCtx): Exp[DRAM[T]] = {
    stageMutable( DRAMNew[T](dims) )(ctx)
  }
  def get_dram_addr[T:Type:Bits](dram: Exp[DRAM[T]])(implicit ctx: SrcCtx): Exp[Index] = {
    stage( GetDRAMAddress(dram) )(ctx)
  }

  /** Internals **/
  def dimsOf[T](x: Exp[DRAM[T]])(implicit ctx: SrcCtx): Seq[Exp[Index]] = x match {
    case Op(DRAMNew(dims)) => dims
    case _ => throw new UndefinedDimensionsError(x, None)
  }
}