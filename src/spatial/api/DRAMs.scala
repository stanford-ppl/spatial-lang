package spatial.api

import argon.core.Staging
import spatial.SpatialExp

trait DRAMApi extends DRAMExp with BurstTransferApi {
  this: SpatialExp =>

  type Tile[T] = DRAMDenseTile[T]
  type SparseTile[T] = DRAMSparseTile[T]

  def DRAM[T:Staged:Bits](dimA: Index, dimsB: Index*)(implicit ctx: SrcCtx): DRAM[T] = {
    DRAM(dram_alloc[T](unwrap(dimA +: dimsB)))
  }

}

trait DRAMExp extends Staging with SRAMExp with FIFOExp with RangeExp with SpatialExceptions with BurstTransferExp {
  this: SpatialExp =>

  /** Infix methods **/
  case class DRAM[T:Staged:Bits](s: Exp[DRAM[T]]) {
    def apply(ranges: Range*)(implicit ctx: SrcCtx): DRAMDenseTile[T] = DRAMDenseTile(this.s, ranges)

    def apply(addrs: SRAM[Index])(implicit ctx: SrcCtx): DRAMSparseTile[T] = {
      this.apply(addrs, wrap(stagedDimsOf(addrs.s).head))
    }
    def apply(addrs: SRAM[Index], len: Index)(implicit ctx: SrcCtx): DRAMSparseTile[T] = {
      if (rankOf(addrs) > 1) new SparseAddressDimensionError(s, rankOf(addrs))(ctx)
      DRAMSparseTile(this.s, addrs, len)
    }
  }

  case class DRAMDenseTile[T:Staged:Bits](dram: Exp[DRAM[T]], ranges: Seq[Range]) {
    def store(sram: SRAM[T])(implicit ctx: SrcCtx): Void = coarse_burst(this, sram, isLoad = false)
    def store(fifo: FIFO[T])(implicit ctx: SrcCtx): Void = coarse_burst(this, fifo, isLoad = false)
  }

  case class DRAMSparseTile[T:Staged:Bits](dram: Exp[DRAM[T]], addrs: SRAM[Index], len: Index) {
    def scatter(sram: SRAM[T])(implicit ctx: SrcCtx): Void = { copy_sparse(this, sram, isLoad = false); () }
  }


  /** Staged Type **/
  case class DRAMType[T:Bits](child: Staged[T]) extends Staged[DRAM[T]] {
    override def unwrapped(x: DRAM[T]) = x.s
    override def wrapped(x: Exp[DRAM[T]]) = DRAM(x)(child,bits[T])
    override def typeArguments = List(child)
    override def stagedClass = classOf[DRAM[T]]
    override def isPrimitive = false
  }
  implicit def dramType[T:Staged:Bits]: Staged[DRAM[T]] = DRAMType[T](typ[T])


  /** IR Nodes **/
  case class DRAMNew[T:Staged:Bits](dims: Seq[Exp[Index]]) extends Op2[T,DRAM[T]] {
    def mirror(f:Tx) = dram_alloc[T](f(dims))
  }

  case class Gather[T:Staged:Bits](
    dram:  Exp[DRAM[T]],
    local: Exp[SRAM[T]],
    addrs: Exp[SRAM[Index]],
    ctr:   Exp[Counter],
    i:     Bound[Index]
  ) extends Op[Controller] {
    def mirror(f:Tx) = gather(f(dram),f(local),f(addrs),f(ctr),i)

    override def inputs = syms(dram, local, addrs, ctr)
    override def binds = List(i)
    override def aliases = Nil
    val mT = typ[T]
    val bT = bits[T]
  }
  case class Scatter[T:Staged:Bits](
    dram:  Exp[DRAM[T]],
    local: Exp[SRAM[T]],
    addrs: Exp[SRAM[Index]],
    ctr:   Exp[Counter],
    i:     Bound[Index]
  ) extends Op[Controller] {
    def mirror(f:Tx) = scatter(f(dram),f(local),f(addrs),f(ctr),i)

    override def inputs = syms(dram, local, addrs, ctr)
    override def binds = List(i)
    override def aliases = Nil
    val mT = typ[T]
    val bT = bits[T]
  }

  /** Constructors **/
  def dram_alloc[T:Staged:Bits](dims: Seq[Exp[Index]])(implicit ctx: SrcCtx): Exp[DRAM[T]] = {
    stageMutable( DRAMNew[T](dims) )(ctx)
  }
  def gather[T:Staged:Bits](mem: Exp[DRAM[T]],local: Exp[SRAM[T]],addrs: Exp[SRAM[Index]], ctr: Exp[Counter], i: Bound[Index])(implicit ctx: SrcCtx) = {
    stageWrite(mem)(Gather(mem, local, addrs, ctr, i))(ctx)
  }
  def scatter[T:Staged:Bits](mem: Exp[DRAM[T]],local: Exp[SRAM[T]],addrs: Exp[SRAM[Index]], ctr: Exp[Counter], i: Bound[Index])(implicit ctx: SrcCtx) = {
    stageWrite(mem)(Scatter(mem, local, addrs, ctr, i))(ctx)
  }

  def copy_sparse[T:Staged:Bits](offchip: DRAMSparseTile[T], local: SRAM[T], isLoad: Boolean)(implicit ctx: SrcCtx) = {
    if (rankOf(local) > 1) new SparseDataDimensionError(isLoad, rankOf(local))

    val p = local.p
    val ctr = Counter(0, wrap(stagedDimsOf(local.s)).head, 1, p).s //range2counter(0 until wrap(stagedDimsOf(local).head)).s
    val i = fresh[Index]
    if (isLoad) Controller(gather(offchip.dram, local.s, offchip.addrs.s, ctr, i))
    else        Controller(scatter(offchip.dram, local.s, offchip.addrs.s, ctr, i))
  }

  /** Internals **/
  def dimsOf[T](x: Exp[DRAM[T]])(implicit ctx: SrcCtx): Seq[Exp[Index]] = x match {
    case Op(DRAMNew(dims)) => dims
    case _ => throw new UndefinedDimensionsError(x, None)
  }
}