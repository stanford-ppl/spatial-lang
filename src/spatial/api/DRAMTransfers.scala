package spatial.api

import argon.core.Staging
import argon.ops.CastApi
import macros.struct
import org.virtualized._
import spatial.SpatialExp

trait DRAMTransferApi extends DRAMTransferExp with ControllerApi with FIFOApi with CastApi with RangeApi with PinApi {
  this: SpatialExp =>

  /** Internals **/
  // Expansion rule for CoarseBurst -  Use coarse_burst(tile,onchip,isLoad) for anything in the frontend
  private[spatial] def copy_dense[T:Staged:Bits,C[T]](
    offchip: Exp[DRAM[T]],
    onchip:  Exp[C[T]],
    ofs:     Seq[Exp[Index]],
    lens:    Seq[Exp[Index]],
    units:   Seq[Boolean],
    par:     Const[Index],
    isLoad:  Boolean
  )(implicit mem: Mem[T,C], mC: Staged[C[T]], ctx: SrcCtx): Void = {

    val unitDims = units
    val offchipOffsets = wrap(ofs)
    val tileDims = wrap(lens)
    val local = wrap(onchip)
    val dram  = wrap(offchip)

    // Last counter is used as counter for load/store
    // Other counters (if any) are used to iterate over higher dimensions
    val counters = tileDims.map{d => () => Counter(start = 0, end = d, step = 1, par = 1) }

    val requestLength = tileDims.last
    val p = wrap(par)

    val bytesPerWord = bits[T].length / 8 + (if (bits[T].length % 8 != 0) 1 else 0)


    // Metaprogrammed (unstaged) if-then-else
    if (counters.length > 1) {
      Stream.Foreach(counters.dropRight(1).map{ctr => ctr()}){ is =>
        val indices = is :+ 0.as[Index]

        val offchipAddr = () => flatIndex( offchipOffsets.zip(indices).map{case (a,b) => a + b}, wrap(dimsOf(offchip)))

        val onchipOfs   = indices.zip(unitDims).flatMap{case (i,isUnitDim) => if (!isUnitDim) Some(i) else None}
        val onchipAddr  = {i: Index => onchipOfs.take(onchipOfs.length - 1) :+ (onchipOfs.last + i)}

        if (isLoad) load(offchipAddr(), onchipAddr)
        else        store(offchipAddr(), onchipAddr)
      }
    }
    else {
      Stream {
        def offchipAddr = () => flatIndex(offchipOffsets, wrap(dimsOf(offchip)))
        if (isLoad) load(offchipAddr(), {i => List(i) })
        else        store(offchipAddr(), {i => List(i)})
      }
    }

    // NOTE: Results of register reads are allowed to be used to specialize for aligned load/stores,
    // as long as the value of the register read is known to be exactly some value.
    // TODO: We should also be checking if the start address is aligned...
    def store(offchipAddr: => Index, onchipAddr: Index => Seq[Index]): Void = requestLength.s match {
      case Exact(c: BigInt) if (c*bits[T].length) % target.burstSize == 0 => alignedStore(offchipAddr, onchipAddr)
      case x =>
        error(c"Unaligned store! Burst length: ${str(x)}")
        unalignedStore(offchipAddr, onchipAddr)
    }
    def load(offchipAddr: => Index, onchipAddr: Index => Seq[Index]): Void = requestLength.s match {
      case Exact(c: BigInt) if (c*bits[T].length) % target.burstSize == 0 => alignedLoad(offchipAddr, onchipAddr)
      case _ => unalignedLoad(offchipAddr, onchipAddr)
    }

    def alignedStore(offchipAddr: => Index, onchipAddr: Index => Seq[Index]): Void = {
      val cmdStream  = StreamOut[BurstCmd](BurstCmdBus)
      val issueQueue = FIFO[Index](16)  // TODO: Size of issued queue?
      val dataStream = StreamOut[Tup2[T,Bool]](BurstFullDataBus[T]())
      val ackStream  = StreamIn[Bool](BurstAckBus)

      // Command generator
      Pipe {
        Pipe {
          val addr_bytes = offchipAddr * bytesPerWord + dram.address
          val size = requestLength
          val size_bytes = size * bytesPerWord
          cmdStream.enq(BurstCmd(addr_bytes, size_bytes, false))
          issueQueue.enq(size)
        }
        // Data loading
        Foreach(requestLength par p){i =>
          val data = mem.load(local, onchipAddr(i), true)
          dataStream.enq( pack(data,true) )
        }
      }
      // Fringe
      fringe_dense_store(offchip, cmdStream.s, dataStream.s, ackStream.s)
      // Ack receiver
      // TODO: Assumes one ack per command
      Pipe {
        val size = issueQueue.deq()
        val ack  = ackStream.deq()
        ()
      }
    }

    @virtualize
    def unalignedStore(offchipAddr: => Index, onchipAddr: Index => Seq[Index]): Void = {
      val cmdStream  = StreamOut[BurstCmd](BurstCmdBus)
      val issueQueue = FIFO[Index](16)  // TODO: Size of issued queue?
      val dataStream = StreamOut[Tup2[T,Bool]](BurstFullDataBus[T]())
      val ackStream  = StreamIn[Bool](BurstAckBus)

      // Command generator
      Pipe {
        val startBound = Reg[Index]
        val endBound   = Reg[Index]
        val length     = Reg[Index]
        Pipe {
          val elementsPerBurst = (target.burstSize / bits[T].length).as[Index]
          val maddr = offchipAddr * bytesPerWord
          val start = maddr % elementsPerBurst    // Number of elements to ignore at beginning
          val end = start + requestLength         // Index to begin ignoring again
          val addr = maddr + dram.address - start // Burst-aligned offchip address

          val extra = elementsPerBurst - (requestLength % elementsPerBurst)     // Number of extra elements needed
          val size = requestLength + mux(requestLength % elementsPerBurst == 0, 0.as[Index], extra) // Burst aligned length

          val addr_bytes = addr
          val size_bytes = size * bytesPerWord

          cmdStream.enq(BurstCmd(addr_bytes, size_bytes, false))
          issueQueue.enq(size)
          startBound := start
          endBound := end
          length := size
        }
        Foreach(length par p){i =>
          val en = i >= startBound && i < endBound
          val data = mux(en, mem.load(local,onchipAddr(i - startBound), en), zero[T])
          dataStream.enq( pack(data,en) )
        }
      }
      // Fringe
      fringe_dense_store(offchip, cmdStream.s, dataStream.s, ackStream.s)
      // Ack receive
      // TODO: Assumes one ack per command
      Pipe {
        val size = issueQueue.deq()
        val ack  = ackStream.deq()
        ()
      }
    }

    def alignedLoad(offchipAddr: => Index, onchipAddr: Index => Seq[Index]): Void = {
      val cmdStream  = StreamOut[BurstCmd](BurstCmdBus)
      val issueQueue = FIFO[Index](16)  // TODO: Size of issued queue?
      val dataStream = StreamIn[T](BurstDataBus[T]())

      // Command generator
      Pipe {
        val addr = offchipAddr * bytesPerWord + dram.address
        val size = requestLength

        val addr_bytes = addr
        val size_bytes = size * bytesPerWord

        cmdStream.enq( BurstCmd(addr_bytes, size_bytes, true) )
        issueQueue.enq( size )
      }
      // Fringe
      fringe_dense_load(offchip, cmdStream.s, dataStream.s)
      // Data receiver
      Pipe {
        Pipe { val size = issueQueue.deq() }
        Foreach(requestLength par p){i =>
          val data = dataStream.deq()
          val addr = onchipAddr(i)
          mem.store(local, addr, data, true)
        }
      }
    }
    @virtualize
    def unalignedLoad(offchipAddr: => Index, onchipAddr: Index => Seq[Index]): Void = {
      val cmdStream  = StreamOut[BurstCmd](BurstCmdBus)
      val issueQueue = FIFO[IssuedCmd](16)  // TODO: Size of issued queue?
      val dataStream = StreamIn[T](BurstDataBus[T]())

      // Command
      Pipe {
        val elementsPerBurst = (target.burstSize/bits[T].length).as[Index]

        val maddr = offchipAddr * bytesPerWord
        val start = maddr % elementsPerBurst              // Number of elements to ignore at beginning
        val end   = start + requestLength                 // Index to begin ignoring again
        val addr  = maddr + dram.address - start          // Burst-aligned offchip address

        val extra = elementsPerBurst - (requestLength % elementsPerBurst)     // Number of extra elements needed
        val size  = requestLength + mux(requestLength % elementsPerBurst == 0, 0.as[Index], extra) // Burst aligned length

        val addr_bytes = addr
        val size_bytes = size * bytesPerWord

        cmdStream.enq( BurstCmd(addr_bytes, size_bytes, true) )
        issueQueue.enq( IssuedCmd(size, start, end) )
      }

      // Fringe
      fringe_dense_load(offchip, cmdStream.s, dataStream.s)

      // Receive
      Pipe {
        // TODO: Should also try Reg[IssuedCmd] here
        val start = Reg[Index]
        val end   = Reg[Index]
        val size  = Reg[Index]
        Pipe {
          val cmd = issueQueue.deq()
          start := cmd.start
          end := cmd.end
          size := cmd.size
        }
        Foreach(size par p){i =>
          val en = i >= start && i < end
          val addr = onchipAddr(i - start)
          val data = dataStream.deq()
          mem.store(local, addr, data, en)
        }
      }
    }
  }


  private[spatial] def copy_sparse[T:Staged:Bits](
    offchip:   Exp[DRAM[T]],
    onchip:    Exp[SRAM[T]],
    addresses: Exp[SRAM[Index]],
    size:      Exp[Index],
    par:       Const[Index],
    isLoad:    Boolean
  )(implicit ctx: SrcCtx): Void = {
    val local = wrap(onchip)
    val addrs = wrap(addresses)
    val dram  = wrap(offchip)
    val requestLength = wrap(size)
    val p = wrap(par)

    val bytesPerWord = bits[T].length / 8 + (if (bits[T].length % 8 != 0) 1 else 0)

    Stream {
      // Gather
      if (isLoad) {
        val addrBus = StreamOut[Index](GatherAddrBus)
        val dataBus = StreamIn[T](GatherDataBus[T]())

        // Send
        Foreach(requestLength par p){i =>
          val addr = addrs(i) * bytesPerWord + dram.address

          val addr_bytes = addr
          addrBus.enq(addr_bytes)
        }
        // Fringe
        fringe_sparse_load(offchip, addrBus.s, dataBus.s)
        // Receive
        Foreach(requestLength par p){i =>
          val data = dataBus.deq()
          local(i) = data
        }
      }
      // Scatter
      else {
        val cmdBus = StreamOut[Tup2[T,Index]](ScatterCmdBus[T]())
        val ackBus = StreamIn[Bool](ScatterAckBus)

        // Send
        Foreach(requestLength par p){i =>
          val addr = addrs(i) * bytesPerWord + dram.address
          val data = local(i)

          val addr_bytes = addr

          cmdBus.enq( pack(data,addr_bytes) )
        }
        // Fringe
        fringe_sparse_store(offchip, cmdBus.s, ackBus.s)
        // Receive
        // TODO: Assumes one ack per address
        Foreach(requestLength par p){i =>
          val ack = ackBus.deq()
        }
      }
    }

  }


}

trait DRAMTransferExp extends Staging { this: SpatialExp =>

  /** Specialized busses **/
  @struct case class BurstCmd(offset: Index, size: Index, isLoad: Bool)
  @struct case class IssuedCmd(size: Index, start: Index, end: Index)

  abstract class DRAMBus[T:Staged:Bits] extends Bus { def length = bits[T].length }

  case object BurstCmdBus extends DRAMBus[BurstCmd]
  case object BurstAckBus extends DRAMBus[Bool]
  case class BurstDataBus[T:Staged:Bits]() extends DRAMBus[T]
  case class BurstFullDataBus[T:Staged:Bits]() extends DRAMBus[Tup2[T,Bool]]

  case object GatherAddrBus extends DRAMBus[Index]
  case class GatherDataBus[T:Staged:Bits]() extends DRAMBus[T]

  case class ScatterCmdBus[T:Staged:Bits]() extends DRAMBus[Tup2[T, Index]]
  case object ScatterAckBus extends DRAMBus[Bool]

  /** Internal **/

  def dense_transfer[T:Staged:Bits,C[T]](
    tile:   DRAMDenseTile[T],
    local:  C[T],
    isLoad: Boolean
  )(implicit mem: Mem[T,C], mC: Staged[C[T]], ctx: SrcCtx): Void = {

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

    val localRank = mem.iterators(local).length

    val iters = List.tabulate(localRank){_ => fresh[Index]}

    Void(op_dense_transfer(dram,local.s,ofs,lens,units,p,isLoad,iters))
  }

  def sparse_transfer[T:Staged:Bits](
    tile: DRAMSparseTile[T],
    local: SRAM[T],
    isLoad: Boolean
  )(implicit ctx: SrcCtx): Void = {
    // UNSUPPORTED: Sparse transfer on multidimensional memories
    if (rankOf(local) > 1) new SparseDataDimensionError(isLoad, rankOf(local))

    val p = extractParFactor(local.p)
    val size = stagedDimsOf(local.s).head
    val i = fresh[Index]
    Void(op_sparse_transfer(tile.dram, local.s, tile.addrs.s, size, p, isLoad, i))
  }

  // Defined in API
  private[spatial] def copy_dense[T:Staged:Bits,C[T]](
    offchip: Exp[DRAM[T]],
    onchip:  Exp[C[T]],
    ofs:     Seq[Exp[Index]],
    lens:    Seq[Exp[Index]],
    units:   Seq[Boolean],
    par:     Const[Index],
    isLoad:  Boolean
  )(implicit mem: Mem[T,C], mC: Staged[C[T]], ctx: SrcCtx): Void

  private[spatial] def copy_sparse[T:Staged:Bits](
    offchip:   Exp[DRAM[T]],
    onchip:    Exp[SRAM[T]],
    addresses: Exp[SRAM[Index]],
    size:      Exp[Index],
    par:       Const[Index],
    isLoad:    Boolean
  )(implicit ctx: SrcCtx): Void

  /** Abstract IR Nodes **/

  case class DenseTransfer[T,C[T]](
    dram:   Exp[DRAM[T]],
    local:  Exp[C[T]],
    ofs:    Seq[Exp[Index]],
    lens:   Seq[Exp[Index]],
    units:  Seq[Boolean],
    p:      Const[Index],
    isLoad: Boolean,
    iters:  List[Bound[Index]]
  )(implicit val mem: Mem[T,C], val mT: Staged[T], val bT: Bits[T], val mC: Staged[C[T]]) extends Op[Void] {

    def isStore = !isLoad

    def mirror(f:Tx): Exp[Void] = op_dense_transfer(f(dram),f(local),f(ofs),f(lens),units,p,isLoad,iters)

    override def inputs = syms(dram, local) ++ syms(ofs) ++ syms(lens)
    override def binds  = iters
    override def aliases = Nil

    def expand(f:Tx)(implicit ctx: SrcCtx): Exp[Void] = {
      copy_dense(f(dram),f(local),f(ofs),f(lens),units,p,isLoad)(mT,bT,mem,mC,ctx).s
    }
  }

  case class SparseTransfer[T:Staged:Bits](
    dram:   Exp[DRAM[T]],
    local:  Exp[SRAM[T]],
    addrs:  Exp[SRAM[Index]],
    size:   Exp[Index],
    p:      Const[Index],
    isLoad: Boolean,
    i:      Bound[Index]
  ) extends Op[Void] {
    def isStore = !isLoad

    def mirror(f:Tx) = op_sparse_transfer(f(dram),f(local),f(addrs),f(size),p,isLoad,i)

    override def inputs = syms(dram, local, addrs, size, p)
    override def binds = List(i)
    override def aliases = Nil
    val mT = typ[T]
    val bT = bits[T]

    def expand(f:Tx)(implicit ctx: SrcCtx): Exp[Void] = {
      copy_sparse(f(dram),f(local),f(addrs),f(size),p,isLoad)(mT,bT,ctx).s
    }
  }

  /** Fringe IR Nodes **/
  case class FringeDenseLoad[T:Staged:Bits](
    dram:       Exp[DRAM[T]],
    cmdStream:  Exp[StreamOut[BurstCmd]],
    dataStream: Exp[StreamIn[T]]
  ) extends Op[Void] {
    def mirror(f:Tx) = fringe_dense_load(f(dram),f(cmdStream),f(dataStream))
    val bT = bits[T]
  }

  case class FringeDenseStore[T:Staged:Bits](
    dram:       Exp[DRAM[T]],
    cmdStream:  Exp[StreamOut[BurstCmd]],
    dataStream: Exp[StreamOut[Tup2[T,Bool]]],
    ackStream:  Exp[StreamIn[Bool]]
  ) extends Op[Void] {
    def mirror(f:Tx) = fringe_dense_store(f(dram),f(cmdStream),f(dataStream),f(ackStream))
    val bT = bits[T]
  }

  case class FringeSparseLoad[T:Staged:Bits](
    dram:       Exp[DRAM[T]],
    addrStream: Exp[StreamOut[Index]],
    dataStream: Exp[StreamIn[T]]
  ) extends Op[Void] {
    def mirror(f:Tx) = fringe_sparse_load(f(dram),f(addrStream),f(dataStream))
    val bT = bits[T]
  }

  case class FringeSparseStore[T:Staged:Bits](
    dram:       Exp[DRAM[T]],
    cmdStream: Exp[StreamOut[Tup2[T,Index]]],
    ackStream: Exp[StreamIn[Bool]]
  ) extends Op[Void] {
    def mirror(f:Tx) = fringe_sparse_store(f(dram),f(cmdStream),f(ackStream))
    val bT = bits[T]
  }


  /** Constructors **/

  private def op_dense_transfer[T:Staged:Bits,C[T]](
    dram:   Exp[DRAM[T]],
    local:  Exp[C[T]],
    ofs:    Seq[Exp[Index]],
    lens:   Seq[Exp[Index]],
    units:  Seq[Boolean],
    p:      Const[Index],
    isLoad: Boolean,
    iters:  List[Bound[Index]]
  )(implicit mem: Mem[T,C], mC: Staged[C[T]], ctx: SrcCtx): Exp[Void] = {

    val node = DenseTransfer(dram,local,ofs,lens,units,p,isLoad,iters)

    val out = if (isLoad) stageWrite(local)(node)(ctx) else stageWrite(dram)(node)(ctx)
    styleOf(out) = InnerPipe
    out
  }

  private def op_sparse_transfer[T:Staged:Bits](
    dram:   Exp[DRAM[T]],
    local:  Exp[SRAM[T]],
    addrs:  Exp[SRAM[Index]],
    size:   Exp[Index],
    p:      Const[Index],
    isLoad: Boolean,
    i:      Bound[Index]
  )(implicit ctx: SrcCtx): Exp[Void] = {

    val node = SparseTransfer(dram,local,addrs,size,p,isLoad,i)

    val out = if (isLoad) stageWrite(local)(node)(ctx) else stageWrite(dram)(node)(ctx)
    styleOf(out) = InnerPipe
    out
  }

  private[spatial] def fringe_dense_load[T:Staged:Bits](
    dram:       Exp[DRAM[T]],
    cmdStream:  Exp[StreamOut[BurstCmd]],
    dataStream: Exp[StreamIn[T]]
  )(implicit ctx: SrcCtx): Exp[Void] = {
    stageCold(FringeDenseLoad(dram,cmdStream,dataStream))(ctx)
  }

  private[spatial] def fringe_dense_store[T:Staged:Bits](
    dram:       Exp[DRAM[T]],
    cmdStream:  Exp[StreamOut[BurstCmd]],
    dataStream: Exp[StreamOut[Tup2[T,Bool]]],
    ackStream:  Exp[StreamIn[Bool]]
  )(implicit ctx: SrcCtx): Exp[Void] = {
    stageCold(FringeDenseStore(dram,cmdStream,dataStream,ackStream))(ctx)
  }

  private[spatial] def fringe_sparse_load[T:Staged:Bits](
    dram:       Exp[DRAM[T]],
    addrStream: Exp[StreamOut[Index]],
    dataStream: Exp[StreamIn[T]]
  )(implicit ctx: SrcCtx): Exp[Void] = {
    stageCold(FringeSparseLoad(dram,addrStream,dataStream))(ctx)
  }

  private[spatial] def fringe_sparse_store[T:Staged:Bits](
    dram:       Exp[DRAM[T]],
    cmdStream: Exp[StreamOut[Tup2[T,Index]]],
    ackStream: Exp[StreamIn[Bool]]
  )(implicit ctx: SrcCtx): Exp[Void] = {
    stageCold(FringeSparseStore(dram,cmdStream,ackStream))(ctx)
  }

}
