package spatial.api

import org.virtualized.virtualize
import spatial.{SpatialApi, SpatialExp, SpatialOps}

trait DRAMOps extends SRAMOps with FIFOOps with RangeOps { this: SpatialOps =>
  type DRAM[T] <: DRAMOps[T]
  type DRAMDenseTile[T] <: DRAMDenseTileOps[T]
  type DRAMSparseTile[T] <: DRAMSparseTileOps[T]

  protected trait DRAMOps[T] {
    def apply(ranges: Range*)(implicit ctx: SrcCtx): DRAMDenseTile[T]
    def apply(addrs: SRAM[Index])(implicit ctx: SrcCtx): DRAMSparseTile[T]
    def apply(addrs: SRAM[Index], len: Index)(implicit ctx: SrcCtx): DRAMSparseTile[T]
  }

  protected trait DRAMDenseTileOps[T] {
    // Dense tile store
    def store(sram: SRAM[T])(implicit ctx: SrcCtx): Void
    def store(fifo: FIFO[T])(implicit ctx: SrcCtx): Void
  }
  protected trait DRAMSparseTileOps[T] {
    // Scatter: dram(addrs) scatter sram(0::N)
    def scatter(sram: SRAM[T])(implicit ctx: SrcCtx): Void
  }

  // DRAM allocate
  def DRAM[T:Bits](dimA: Index, dimsB: Index*)(implicit ctx: SrcCtx): DRAM[T]

  implicit def dramType[T:Bits]: Staged[DRAM[T]]
}
trait DRAMApi extends DRAMOps with SRAMApi with FIFOApi with RangeApi { this: SpatialApi => }


trait DRAMExp extends DRAMOps with SRAMExp with FIFOExp with RangeExp with SpatialExceptions with BurstTransfers {
  this: SpatialExp =>

  /** API **/
  case class DRAM[T:Bits](s: Exp[DRAM[T]]) extends DRAMOps[T] {
    def apply(ranges: Range*)(implicit ctx: SrcCtx): DRAMDenseTile[T] = DRAMDenseTile(this.s, ranges)

    def apply(addrs: SRAM[Index])(implicit ctx: SrcCtx): DRAMSparseTile[T] = {
      this.apply(addrs, wrap(stagedDimsOf(addrs.s).head))
    }
    def apply(addrs: SRAM[Index], len: Index)(implicit ctx: SrcCtx): DRAMSparseTile[T] = {
      if (rankOf(addrs) > 1) new SparseAddressDimensionError(s, rankOf(addrs))(ctx)
      DRAMSparseTile(this.s, addrs, len)
    }
  }

  def DRAM[T:Bits](dimA: Index, dimsB: Index*)(implicit ctx: SrcCtx): DRAM[T] = {
    DRAM(dram_alloc[T](unwrap(dimA +: dimsB)))
  }

  case class DRAMDenseTile[T:Bits](dram: Exp[DRAM[T]], ranges: Seq[Range]) extends DRAMDenseTileOps[T] {
    def store(sram: SRAM[T])(implicit ctx: SrcCtx): Void = coarse_burst(this, sram, isLoad = false)
    def store(fifo: FIFO[T])(implicit ctx: SrcCtx): Void = coarse_burst(this, fifo, isLoad = false)
  }

  case class DRAMSparseTile[T:Bits](dram: Exp[DRAM[T]], addrs: SRAM[Index], len: Index) extends DRAMSparseTileOps[T] {
    def scatter(sram: SRAM[T])(implicit ctx: SrcCtx): Void = copy_sparse(this, sram, isLoad = false)
  }


  /** Staged Type **/
  case class DRAMType[T](child: Bits[T]) extends Staged[DRAM[T]] {
    override def unwrapped(x: DRAM[T]) = x.s
    override def wrapped(x: Exp[DRAM[T]]) = DRAM(x)(child)
    override def typeArguments = List(child)
    override def stagedClass = classOf[DRAM[T]]
    override def isPrimitive = false
  }
  implicit def dramType[T:Bits]: Staged[DRAM[T]] = DRAMType[T](bits[T])


  /** IR Nodes **/
  case class DRAMNew[T:Bits](dims: Seq[Exp[Index]]) extends Op2[T,DRAM[T]] { def mirror(f:Tx) = dram_alloc[T](f(dims)) }

  case class Gather[T:Bits](
    dram:  Exp[DRAM[T]],
    local: Exp[SRAM[T]],
    addrs: Exp[SRAM[Index]],
    ctr:   Exp[Counter],
    i:     Bound[Index]
  ) extends Op[Void] {
    def mirror(f:Tx) = gather(f(dram),f(local),f(addrs),f(ctr),i)

    override def aliases = Nil
    val bT = bits[T]
  }
  case class Scatter[T:Bits](
    dram:  Exp[DRAM[T]],
    local: Exp[SRAM[T]],
    addrs: Exp[SRAM[Index]],
    ctr:   Exp[Counter],
    i:     Bound[Index]
  ) extends Op[Void] {
    def mirror(f:Tx) = scatter(f(dram),f(local),f(addrs),f(ctr),i)

    override def aliases = Nil
    val bT = bits[T]
  }

  /** Constructors **/
  def dram_alloc[T:Bits](dims: Seq[Exp[Index]])(implicit ctx: SrcCtx): Exp[DRAM[T]] = {
    stageMutable( DRAMNew[T](dims) )(ctx)
  }
  def gather[T:Bits](mem: Exp[DRAM[T]],local: Exp[SRAM[T]],addrs: Exp[SRAM[Index]], ctr: Exp[Counter], i: Bound[Index])(implicit ctx: SrcCtx): Exp[Void] = {
    stageWrite(mem)(Gather(mem, local, addrs, ctr, i))(ctx)
  }
  def scatter[T:Bits](mem: Exp[DRAM[T]],local: Exp[SRAM[T]],addrs: Exp[SRAM[Index]], ctr: Exp[Counter], i: Bound[Index])(implicit ctx: SrcCtx): Exp[Void] = {
    stageWrite(mem)(Scatter(mem, local, addrs, ctr, i))(ctx)
  }

  def copy_sparse[T:Bits](offchip: DRAMSparseTile[T], local: SRAM[T], isLoad: Boolean)(implicit ctx: SrcCtx): Void = {
    if (rankOf(local) > 1) new SparseDataDimensionError(isLoad, rankOf(local))

    val p = local.par
    val ctr = Counter(0, wrap(stagedDimsOf(local.s)).head, 1, p).s //range2counter(0 until wrap(stagedDimsOf(local).head)).s
    val i = fresh[Index]
    if (isLoad) Void(gather(offchip.dram, local.s, offchip.addrs.s, ctr, i))
    else        Void(scatter(offchip.dram, local.s, offchip.addrs.s, ctr, i))
  }

  def dimsOf[T](x: Exp[DRAM[T]])(implicit ctx: SrcCtx): Seq[Exp[Index]] = x match {
    case Op(DRAMNew(dims)) => dims
    case _ => throw new UndefinedDimensionsError(x, None)
  }
}