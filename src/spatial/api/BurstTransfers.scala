package spatial.api

import argon.core.Staging
import argon.ops.CastApi
import org.virtualized.virtualize
import spatial.SpatialExp
import spatial.SpatialConfig

trait BurstTransferApi extends BurstTransferExp with ControllerApi with FIFOApi with CastApi with RangeApi with PinApi {
  this: SpatialExp =>

  /** Internals **/
  // Expansion rule for CoarseBurst -  Use coarse_burst(tile,onchip,isLoad) for anything in the frontend
  private[spatial] def copy_burst[T:Staged:Bits,C[T]](
    offchip: Exp[DRAM[T]],
    local:   Exp[C[T]],
    ofs:     Seq[Exp[Index]],
    lens:    Seq[Exp[Index]],
    units:   Seq[Boolean],
    par:     Const[Index],
    isLoad:  Boolean
  )(implicit mem: Mem[T,C], mC: Staged[C[T]], ctx: SrcCtx): Void = {

    val unitDims = units
    val offchipOffsets = wrap(ofs)
    val tileDims = wrap(lens)
    val onchip = wrap(local)

    // Last counter is used as counter for load/store
    // Other counters (if any) are used to iterate over higher dimensions
    val counters = tileDims.map{d => () => Counter(start = 0, end = d, step = 1, par = 1) }

    val burstLength = tileDims.last
    val p = wrap(par)

    val fifo = FIFO[T](96000) // TODO: What should the size of this actually be?

    // Metaprogrammed (unstaged) if-then-else
    if (counters.length > 1) {
      Foreach(counters.dropRight(1).map{ctr => ctr()}){ is =>
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

    // NOTE: Results of register reads are allowed to be used to specialize for aligned load/stores,
    // as long as the value of the register read is known to be exactly some value.
    def store(offchipAddr: => Index, onchipAddr: Index => Seq[Index]): Void = burstLength.s match {
      case Exact(c: BigInt) if (c*bits[T].length) % target.burstSize == 0 => alignedStore(offchipAddr, onchipAddr)
      case x =>
        error(c"Unaligned store! Burst length: ${str(x)}")
        unalignedStore(offchipAddr, onchipAddr)
    }
    def load(offchipAddr: => Index, onchipAddr: Index => Seq[Index]): Void = burstLength.s match {
      case Exact(c: BigInt) if (c*bits[T].length) % target.burstSize == 0 => alignedLoad(offchipAddr, onchipAddr)
      case _ => unalignedLoad(offchipAddr, onchipAddr)
    }

    def alignedStore(offchipAddr: => Index, onchipAddr: Index => Seq[Index]): Void = {
      val maddr = Reg[Index]
      Pipe { maddr := offchipAddr }
      Foreach(burstLength par p){i => fifo.enq( mem.load(onchip, onchipAddr(i), true)) }
      Void(burst_store(offchip, fifo.s, maddr.value.s, counters.last().s, fresh[Index]))
    }
    // UNSUPPORTED: Unaligned store
    def unalignedStore(offchipAddr: => Index, onchipAddr: Index => Seq[Index]): Void = {
      new UnsupportedUnalignedTileStore()(ctx)
      alignedStore(offchipAddr, onchipAddr)
    }

    def alignedLoad(offchipAddr: => Index, onchipAddr: Index => Seq[Index]): Void = {
      val maddr = Reg[Index]
      Pipe { maddr := offchipAddr }
      burst_load(offchip, fifo.s, maddr.value.s, counters.last().s, fresh[Index])
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
        val elementsPerBurst = (target.burstSize/bits[T].length).as[Index]
        startBound := maddr % elementsPerBurst          // Number of elements to ignore at beginning
        memAddrDowncast := maddr - startBound.value     // Burst-aligned address
        endBound  := startBound.value + burstLength     // Index to begin ignoring again
        val offset = mux(endBound.value % elementsPerBurst != 0, elementsPerBurst, 0.as[Index]) // Number of elements aligned to nearest burst length
        lenUpcast := (endBound.value - (endBound.value %  elementsPerBurst)) + offset
      }
      // Deliberately make 2 copies of the same counter
      val innerCtr1 = range2counter(lenUpcast.value by p)
      val innerCtr2 = range2counter(lenUpcast.value by p)

      burst_load(offchip, fifo.s, memAddrDowncast.value.s, innerCtr1.s, fresh[Index])

      Foreach(innerCtr2){i =>
        val en = i >= startBound.value && i < endBound.value
        mem.store(onchip, onchipAddr(i - startBound.value), fifo.deq(), en)
      }
    }

  }

}

trait BurstTransferExp extends Staging { this: SpatialExp =>

  def coarse_burst[T:Staged:Bits,C[T]](
    tile:   DRAMDenseTile[T],
    onchip: C[T],
    isLoad: Boolean
  )(implicit mem:Mem[T,C], mC: Staged[C[T]], ctx: SrcCtx): Void = {

    // Extract range lengths early to avoid unit pipe insertion eliminating rewrite opportunities
    val dram    = tile.dram
    val ofs     = tile.ranges.map(_.start.map(_.s).getOrElse(int32(0)))
    val lens    = tile.ranges.map(_.length.s)
    val strides = tile.ranges.map(_.step.map(_.s).getOrElse(int32(1)))
    val units   = tile.ranges.map(_.isUnit)
    val p       = extractParFactor(tile.ranges.last.p)

    // UNSUPPORTED: Strided ranges for DRAM in burst load/store
    if (strides.exists{case Const(1) => false ; case _ => true})
      new UnsupportedStridedDRAMError(isLoad)(ctx)

    val onchipRank = mem.iterators(onchip).length

    val iters = List.tabulate(onchipRank){_ => fresh[Index]}

    Void(op_coarse_burst(dram,onchip.s,ofs,lens,units,p,isLoad,iters))
  }

  case class CoarseBurst[T,C[T]](
    dram:   Exp[DRAM[T]],
    onchip: Exp[C[T]],
    ofs:    Seq[Exp[Index]],
    lens:   Seq[Exp[Index]],
    units:  Seq[Boolean],
    p:      Const[Index],
    isLoad: Boolean,
    iters:  List[Bound[Index]]
  )(implicit val mem: Mem[T,C], val mT: Staged[T], val bT: Bits[T], val mC: Staged[C[T]]) extends Op[Void] {

    def mirror(f:Tx): Exp[Void] = op_coarse_burst(f(dram),f(onchip),f(ofs),f(lens),units,p,isLoad,iters)

    override def inputs = syms(dram, onchip) ++ syms(ofs) ++ syms(lens)
    override def binds  = iters
    override def aliases = Nil

    // Experimental - call within a transformer to replace this abstract node with its implementation
    def expand(f:Tx)(implicit ctx: SrcCtx): Exp[Void] = {
      copy_burst(f(dram),f(onchip),f(ofs),f(lens),units,p,isLoad)(mT,bT,mem,mC,ctx).s
    }
  }

  private def op_coarse_burst[T:Staged:Bits,C[T]](
    dram:   Exp[DRAM[T]],
    onchip: Exp[C[T]],
    ofs:    Seq[Exp[Index]],
    lens:   Seq[Exp[Index]],
    units:  Seq[Boolean],
    p:      Const[Index],
    isLoad: Boolean,
    iters:  List[Bound[Index]]
  )(implicit mem: Mem[T,C], mC: Staged[C[T]], ctx: SrcCtx): Exp[Void] = {
    val out = if (isLoad) stageWrite(onchip)( CoarseBurst(dram,onchip,ofs,lens,units,p,isLoad,iters) )(ctx)
              else        stageWrite(dram)( CoarseBurst(dram,onchip,ofs,lens,units,p,isLoad,iters) )(ctx)
    styleOf(out) = InnerPipe
    out
  }


  // Filled in API
  private[spatial] def copy_burst[T:Staged:Bits,C[T]](
    offchip: Exp[DRAM[T]],
    local:   Exp[C[T]],
    ofs:     Seq[Exp[Index]],
    lens:    Seq[Exp[Index]],
    units:   Seq[Boolean],
    par:     Const[Index],
    isLoad:  Boolean
  )(implicit mem: Mem[T,C], mC: Staged[C[T]], ctx: SrcCtx): Void

  // TODO: May make more sense to change these to output StreamIn / StreamOut later
  case class BurstLoad[T:Staged:Bits](
    dram: Exp[DRAM[T]],
    fifo: Exp[FIFO[T]],
    ofs:  Exp[Index],
    ctr:  Exp[Counter],
    i:    Bound[Index]
  ) extends Op[Void] {
    def mirror(f:Tx) = burst_load(f(dram),f(fifo),f(ofs),f(ctr),i)

    override def inputs = syms(dram, fifo, ofs, ctr)
    override def binds = List(i)

    override def aliases = Nil
  }
  case class BurstStore[T:Staged:Bits](
    dram: Exp[DRAM[T]],
    fifo: Exp[FIFO[T]],
    ofs:  Exp[Index],
    ctr:  Exp[Counter],
    i:    Bound[Index]
  ) extends Op[Void] {
    def mirror(f:Tx) = burst_store(f(dram),f(fifo),f(ofs),f(ctr),i)

    override def inputs = syms(dram, fifo, ofs, ctr)
    override def binds = List(i)
    override def aliases = Nil
  }


  private[spatial] def burst_store[T:Staged:Bits](
    dram: Exp[DRAM[T]],
    fifo: Exp[FIFO[T]],
    ofs:  Exp[Index],
    ctr:  Exp[Counter],
    i:    Bound[Index]
  )(implicit ctx: SrcCtx): Exp[Void] = {
    val store = stageWrite(fifo)(BurstStore(dram, fifo, ofs, ctr, i))(ctx)
    styleOf(store) = InnerPipe
    store
  }
  private[spatial] def burst_load[T:Staged:Bits](
    dram: Exp[DRAM[T]],
    fifo: Exp[FIFO[T]],
    ofs:  Exp[Index],
    ctr:  Exp[Counter],
    i:    Bound[Index]
  )(implicit ctx: SrcCtx): Exp[Void] = {
    val load = stageWrite(fifo)(BurstLoad(dram, fifo, ofs, ctr, i))(ctx)
    styleOf(load) = InnerPipe
    load
  }


}
