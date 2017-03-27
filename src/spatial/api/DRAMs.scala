package spatial.api

import argon.core.Staging
import spatial.SpatialExp

trait DRAMApi extends DRAMExp with DRAMTransferApi {
  this: SpatialExp =>

  type Tile[T] = DRAMDenseTile[T]
  type SparseTile[T] = DRAMSparseTile[T]

  def DRAM[T:Type:Bits](dimA: Index, dimsB: Index*)(implicit ctx: SrcCtx): DRAM[T] = {
    DRAM(dram_alloc[T](unwrap(dimA +: dimsB)))
  }

}

trait DRAMExp extends Staging with SRAMExp with FIFOExp with RangeExp with SpatialExceptions with DRAMTransferExp {
  this: SpatialExp =>

  /** Infix methods **/
  case class DRAM[T:Meta:Bits](s: Exp[DRAM[T]]) extends Template[DRAM[T]] {
    def address(implicit ctx: SrcCtx): Index = wrap(get_dram_addr(s))

    def apply(ranges: Range*)(implicit ctx: SrcCtx): DRAMDenseTile[T] = DRAMDenseTile(this.s, ranges)

    def apply(addrs: SRAM[Index])(implicit ctx: SrcCtx): DRAMSparseTile[T] = {
      this.apply(addrs, wrap(stagedDimsOf(addrs.s).head))
    }
    def apply(addrs: SRAM[Index], len: Index)(implicit ctx: SrcCtx): DRAMSparseTile[T] = {
      if (rankOf(addrs) > 1) new SparseAddressDimensionError(s, rankOf(addrs))(ctx)
      DRAMSparseTile(this.s, addrs, len)
    }

    def toTile(implicit ctx: SrcCtx): DRAMDenseTile[T] = {
      val ranges = dimsOf(s).map{d => range_alloc(None, wrap(d),None,None) }
      DRAMDenseTile(this.s, ranges)
    }

    def store(sram: SRAM[T])(implicit ctx: SrcCtx): Void = dense_transfer(this.toTile, sram, isLoad = false)
    def store(fifo: FIFO[T])(implicit ctx: SrcCtx): Void = dense_transfer(this.toTile, fifo, isLoad = false)
    def store(regs: RegFile[T])(implicit ctx: SrcCtx): Void = dense_transfer(this.toTile, regs, isLoad = false)
  }

  case class DRAMDenseTile[T:Meta:Bits](dram: Exp[DRAM[T]], ranges: Seq[Range]) {
    def store(sram: SRAM[T])(implicit ctx: SrcCtx): Void = dense_transfer(this, sram, isLoad = false)
    def store(fifo: FIFO[T])(implicit ctx: SrcCtx): Void = dense_transfer(this, fifo, isLoad = false)
    def store(regs: RegFile[T])(implicit ctx: SrcCtx): Void = dense_transfer(this, regs, isLoad = false)
  }

  case class DRAMSparseTile[T:Meta:Bits](dram: Exp[DRAM[T]], addrs: SRAM[Index], len: Index) {
    def scatter(sram: SRAM[T])(implicit ctx: SrcCtx): Void = sparse_transfer(this, sram, isLoad = false)
  }


  /** Staged Type **/
  case class DRAMType[T:Bits](child: Meta[T]) extends Meta[DRAM[T]] {
    override def unwrapped(x: DRAM[T]) = x.s
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