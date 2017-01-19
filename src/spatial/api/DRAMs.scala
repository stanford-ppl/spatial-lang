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


trait DRAMExp extends DRAMOps with SRAMExp with FIFOExp with RangeExp with SpatialExceptions { this: SpatialExp =>
  /** API **/
  case class DRAM[T:Bits](s: Exp[DRAM[T]]) extends DRAMOps[T] {
    def apply(ranges: Range*)(implicit ctx: SrcCtx): DRAMDenseTile[T] = DRAMDenseTile(this.s, ranges)

    def apply(addrs: SRAM[Index])(implicit ctx: SrcCtx): DRAMSparseTile[T] = {
      if (rankOf(addrs) > 1) new SparseAddressDimensionError(s, rankOf(addrs))(ctx)
      DRAMSparseTile(this.s, addrs)
    }
  }

  def DRAM[T:Bits](dimA: Index, dimsB: Index*)(implicit ctx: SrcCtx): DRAM[T] = {
    DRAM(dram_alloc[T](unwrap(dimA +: dimsB)))
  }

  case class DRAMDenseTile[T:Bits](dram: Exp[DRAM[T]], ranges: Seq[Range]) extends DRAMDenseTileOps[T] {
    def store(sram: SRAM[T])(implicit ctx: SrcCtx): Void = copy_burst(this, sram, isLoad = false)
    def store(fifo: FIFO[T])(implicit ctx: SrcCtx): Void = copy_burst(this, fifo, isLoad = false)
  }
  case class DRAMSparseTile[T:Bits](dram: Exp[DRAM[T]], addrs: SRAM[Index]) extends DRAMSparseTileOps[T] {
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
  }

  // TODO: May make more sense to change these to output StreamIn / StreamOut later
  case class BurstLoad[T:Bits](
    dram: Exp[DRAM[T]],
    fifo: Exp[FIFO[T]],
    ofs:  Exp[Index],
    ctr:  Exp[Counter],
    i:    Bound[Index]
  ) extends Op[Void] {
    def mirror(f:Tx) = burst_load(f(dram),f(fifo),f(ofs),f(ctr),i)

    override def aliases = Nil
  }
  case class BurstStore[T:Bits](
    dram: Exp[DRAM[T]],
    fifo: Exp[FIFO[T]],
    ofs:  Exp[Index],
    ctr:  Exp[Counter],
    i:    Bound[Index]
  ) extends Op[Void] {
    def mirror(f:Tx) = burst_store(f(dram),f(fifo),f(ofs),f(ctr),i)

    override def aliases = Nil
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
  def burst_store[T:Bits](dram: Exp[DRAM[T]], fifo: Exp[FIFO[T]], ofs: Exp[Index], ctr: Exp[Counter], i: Bound[Index])(implicit ctx: SrcCtx): Exp[Void] = {
    stageWrite(fifo)(BurstStore(dram, fifo, ofs, ctr, i))(ctx)
  }
  def burst_load[T:Bits](dram: Exp[DRAM[T]], fifo: Exp[FIFO[T]], ofs: Exp[Index], ctr: Exp[Counter], i: Bound[Index])(implicit ctx: SrcCtx): Exp[Void] = {
    stageWrite(fifo)(BurstLoad(dram, fifo, ofs, ctr, i))(ctx)
  }



  /** Internals **/
  def copy_burst[T:Bits,C[T]](tile: DRAMDenseTile[T], onchip: C[T], isLoad: Boolean)(implicit mem:Mem[T,C], ctx: SrcCtx): Void = {
    val unitDims = tile.ranges.map(_.isUnit)

    val offchip  = tile.dram
    val offchipOffsets = tile.ranges.map(_.start.getOrElse(0.as[Index]))
    val offchipStrides = tile.ranges.map(_.step.getOrElse(1.as[Index]))

    // UNSUPPORTED: Strided ranges for DRAM in burst load/store
    if (unwrap(offchipStrides).exists{case Const(1) => false ; case _ => true})
      new UnsupportedStridedDRAMError(isLoad)(ctx)

    val tileDims = tile.ranges.map(_.length)
    val counters = tileDims.dropRight(1).map{d => Counter(start = 0, end = d, step = 0, par = 1) }

    val burstLength = tileDims.last
    val p = tile.ranges.last.p.getOrElse(wrap(param(1)))

    val fifo = FIFO[T](96000) // TODO: What should the size of this actually be?


    def alignedStore(offchipAddr: => Index, onchipAddr: Index => Seq[Index]): Void = {
      val maddr = Reg[Index]
      Pipe { maddr := offchipAddr }
      Foreach(burstLength par p){i => fifo.enq( mem.load(onchip, onchipAddr(i), true)) }
      Void(burst_store(offchip, fifo.s, maddr.value.s, counters.last.s, fresh[Index]))
    }
    // UNSUPPORTED: Unaligned store
    def unalignedStore(offchipAddr: => Index, onchipAddr: Index => Seq[Index]): Void = {
      new UnsupportedUnalignedTileStore()(ctx)
      alignedStore(offchipAddr, onchipAddr)
    }

    def alignedLoad(offchipAddr: => Index, onchipAddr: Index => Seq[Index]): Void = {
      val maddr = Reg[Index]
      Pipe { maddr := offchipAddr }
      burst_load(offchip, fifo.s, maddr.value.s, counters.last.s, fresh[Index])
      Foreach(burstLength par p){i => mem.store(onchip, onchipAddr(i), fifo.deq(), true) }
    }
    @virtualize
    def unalignedLoad(offchipAddr: => Index, onchipAddr: Index => Seq[Index]): Void = {
      val startBound = Reg[Index]
      val endBound = Reg[Index]
      val memAddrDowncast = Reg[Index]
      val lenUpcast = Reg[Index]

      Pipe {
        val maddr = offchipAddr
        val elementsPerBurst = 256*8/bits[T].length     // TODO: 256 should be target-dependent value
        startBound := maddr % elementsPerBurst          // Number of elements to ignore at beginning
        memAddrDowncast := maddr - startBound.value     // Burst-aligned address
        endBound  := startBound.value + burstLength     // Index to begin ignoring again
        lenUpcast := (endBound.value - (endBound.value % elementsPerBurst)) + mux(endBound.value % elementsPerBurst != 0, elementsPerBurst, 0) // Number of elements aligned to nearest burst length
      }
      val innerCtr = range2counter(lenUpcast.value by p)

      burst_load(offchip, fifo.s, memAddrDowncast.value.s, innerCtr.s, fresh[Index])

      Foreach(innerCtr){i =>
        val en = i >= startBound.value && i < endBound.value
        mem.store(onchip, onchipAddr(i - startBound.value), fifo.deq(), en)
      }
    }


    def store(offchipAddr: => Index, onchipAddr: Index => Seq[Index]): Void = burstLength.s match {
      case Const(c: BigInt) if c % (256*8/bits[T].length) == 0 => alignedStore(offchipAddr, onchipAddr)
      case _ => unalignedStore(offchipAddr, onchipAddr)
    }
    def load(offchipAddr: => Index, onchipAddr: Index => Seq[Index]): Void = burstLength.s match {
      case Const(c: BigInt) if c % (256*8/bits[T].length) == 0 => alignedLoad(offchipAddr, onchipAddr)  // TODO: 256
      case _ => unalignedLoad(offchipAddr, onchipAddr)
    }

    if (counters.length > 1) {
      Foreach(counters.dropRight(1)){ is =>
        val indices = is :+ 0.as[Index]
        val offchipAddr = () => flatIndex( offchipOffsets.zip(indices).map{case (a,b) => a + b}, wrap(dimsOf(offchip)))

        val onchipOfs   = indices.zip(unitDims).flatMap{case (i,isUnitDim) => if (!isUnitDim) Some(i) else None}
        val onchipAddr  = {i: Index => onchipOfs.take(onchipOfs.length - 1) :+ (onchipOfs.last + i)}

        if (isLoad) load(offchipAddr(), onchipAddr)
        else        store(offchipAddr(), onchipAddr)
      }
    }
    else {
      Pipe {
        def offchipAddr = () => flatIndex(offchipOffsets, wrap(dimsOf(offchip)))
        if (isLoad) load(offchipAddr(), {i => List(i) })
        else        store(offchipAddr(), {i => List(i)})
      }
    }
  }

  def copy_sparse[T:Bits](offchip: DRAMSparseTile[T], local: SRAM[T], isLoad: Boolean)(implicit ctx: SrcCtx): Void = {
    if (rankOf(local) > 1) new SparseDataDimensionError(isLoad, rankOf(local))

    val ctr = Counter(0, wrap(stagedDimsOf(local.s)).head, 1, 1).s //range2counter(0 until wrap(stagedDimsOf(local).head)).s
    val i = fresh[Index]
    if (isLoad) Void(gather(offchip.dram, local.s, offchip.addrs.s, ctr, i))
    else        Void(scatter(offchip.dram, local.s, offchip.addrs.s, ctr, i))
  }

  def dimsOf[T](x: Exp[DRAM[T]])(implicit ctx: SrcCtx): Seq[Exp[Index]] = x match {
    case Op(DRAMNew(dims)) => dims
    case _ => throw new UndefinedDimensionsError(x, None)
  }
}