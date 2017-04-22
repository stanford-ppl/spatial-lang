package spatial.api

import spatial._
import forge._
import org.virtualized._

trait DRAMTransferApi extends DRAMTransferExp { this: SpatialApi =>

  /** Internals **/
  // Expansion rule for CoarseBurst -  Use coarse_burst(tile,onchip,isLoad) for anything in the frontend
  @internal def copy_dense[T:Meta:Bits,C[T]](
    offchip: Exp[DRAM[T]],
    onchip:  Exp[C[T]],
    ofs:     Seq[Exp[Index]],
    lens:    Seq[Exp[Index]],
    units:   Seq[scala.Boolean],
    par:     Const[Index],
    isLoad:  scala.Boolean
  )(implicit mem: Mem[T,C], mC: Meta[C[T]], mD: Meta[DRAM[T]], ctx: SrcCtx): Void = {

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
        val indices = is :+ 0.to[Index]

        val offchipAddr = () => flatIndex( offchipOffsets.zip(indices).map{case (a,b) => a + b}, wrap(stagedDimsOf(offchip)))

        val onchipOfs   = indices.zip(unitDims).flatMap{case (i,isUnitDim) => if (!isUnitDim) Some(i) else None}
        val onchipAddr  = {i: Index => onchipOfs.take(onchipOfs.length - 1) :+ (onchipOfs.last + i)}

        if (isLoad) load(offchipAddr(), onchipAddr)
        else        store(offchipAddr(), onchipAddr)
      }
    }
    else {
      Stream {
        def offchipAddr = () => flatIndex(offchipOffsets, wrap(stagedDimsOf(offchip)))
        if (isLoad) load(offchipAddr(), {i => List(i) })
        else        store(offchipAddr(), {i => List(i)})
      }
    }

    // NOTE: Results of register reads are allowed to be used to specialize for aligned load/stores,
    // as long as the value of the register read is known to be exactly some value.
    // FIXME: We should also be checking if the start address is aligned...
    def store(offchipAddr: => Index, onchipAddr: Index => Seq[Index]): Void = requestLength.s match {
      case Exact(c: BigInt) if (c*bits[T].length) % target.burstSize == 0 => alignedStore(offchipAddr, onchipAddr)
      case x =>
        //error(c"Unaligned store! Burst length: ${str(x)}")
        unalignedStore(offchipAddr, onchipAddr)
    }
    def load(offchipAddr: => Index, onchipAddr: Index => Seq[Index]): Void = requestLength.s match {
      case Exact(c: BigInt) if (c*bits[T].length) % target.burstSize == 0 => alignedLoad(offchipAddr, onchipAddr)
      case _ => unalignedLoad(offchipAddr, onchipAddr)
    }

    def alignedStore(offchipAddr: => Index, onchipAddr: Index => Seq[Index]): Void = {
      val cmdStream  = StreamOut[BurstCmd](BurstCmdBus)
      // val issueQueue = FIFO[Index](16)  // TODO: Size of issued queue?
      val dataStream = StreamOut[Tup2[T,Bool]](BurstFullDataBus[T]())
      val ackStream  = StreamIn[Bool](BurstAckBus)

      // Command generator
      Pipe {
        Pipe {
          val addr_bytes = offchipAddr * bytesPerWord + dram.address
          val size = requestLength
          val size_bytes = size * bytesPerWord
          cmdStream := BurstCmd(addr_bytes, size_bytes, false)
          // issueQueue.enq(size)
        }
        // Data loading
        Foreach(requestLength par p){i =>
          val data = mem.load(local, onchipAddr(i), true)
          dataStream := pack(data,true)
        }
      }
      // Fringe
      fringe_dense_store(offchip, cmdStream.s, dataStream.s, ackStream.s)
      // Ack receiver
      // TODO: Assumes one ack per command
      // Pipe {
        // val size = Reg[Index]
        // Pipe{size := issueQueue.deq()}
        Foreach(requestLength by target.burstSize/bits[T].length) {i => // TODO: Can we use by instead of par?
          val ack  = ackStream.value()
        }
      // }
    }

    case class AlignmentData(start: Index, end: Index, size: Index, addr_bytes: Index, size_bytes: Index)

    @virtualize
    def alignmentCalc(offchipAddr: => Index) = {
      val elementsPerBurst = (target.burstSize/bits[T].length).to[Index]
      val bytesPerBurst = target.burstSize/8

      val maddr_bytes  = offchipAddr * bytesPerWord     // Raw address in bytes
      val start_bytes  = maddr_bytes % bytesPerBurst    // Number of bytes offset from previous burst aligned address
      val length_bytes = requestLength * bytesPerWord   // Raw length in bytes
      val offset_bytes = maddr_bytes - start_bytes      // Burst-aligned start address, in bytes
      val raw_end      = maddr_bytes + length_bytes     // Raw end, in bytes, with burst-aligned start

      val end_bytes = mux(raw_end % bytesPerBurst == 0,  0.to[Index], bytesPerBurst - raw_end % bytesPerBurst) // Extra useless bytes at end

      // FIXME: What to do for bursts which split individual words?
      val start = start_bytes / bytesPerWord                   // Number of WHOLE elements to ignore at start
      val end   = raw_end / bytesPerWord                       // Index of WHOLE elements to start ignoring at again
      val extra = end_bytes / bytesPerWord                     // Number of WHOLE elements that will be ignored at end
      val size  = requestLength + start + extra                // Total number of WHOLE elements to expect

      val size_bytes = length_bytes + start_bytes + end_bytes  // Burst aligned length
      val addr_bytes = offset_bytes + dram.address             // Burst-aligned offchip byte address

      AlignmentData(start, end, size, addr_bytes, size_bytes)
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
          val aligned = alignmentCalc(offchipAddr)

          cmdStream := BurstCmd(aligned.addr_bytes, aligned.size_bytes, false)
          issueQueue.enq(aligned.size)
          startBound := aligned.start
          endBound := aligned.end
          length := aligned.size
        }
        Foreach(length par p){i =>
          val en = i >= startBound && i < endBound
          val data = mux(en, mem.load(local,onchipAddr(i - startBound), en), zero[T])
          dataStream := pack(data,en)
        }
      }
      // Fringe
      fringe_dense_store(offchip, cmdStream.s, dataStream.s, ackStream.s)
      // Ack receive
      // TODO: Assumes one ack per command
      Pipe {
        val size = Reg[Index]
        Pipe{size := issueQueue.deq()}
        Foreach(size.value by target.burstSize/bits[T].length) {i => // TODO: Can we use by instead of par?
          val ack  = ackStream.value()
        }
      }
    }

    def alignedLoad(offchipAddr: => Index, onchipAddr: Index => Seq[Index]): Void = {
      val cmdStream  = StreamOut[BurstCmd](BurstCmdBus)
      // val issueQueue = FIFO[Index](16)  // TODO: Size of issued queue?
      val dataStream = StreamIn[T](BurstDataBus[T]())

      // Command generator
      Pipe {
        val addr = offchipAddr * bytesPerWord + dram.address
        val size = requestLength

        val addr_bytes = addr
        val size_bytes = size * bytesPerWord

        cmdStream := BurstCmd(addr_bytes, size_bytes, true)
        // issueQueue.enq( size )
      }
      // Fringe
      fringe_dense_load(offchip, cmdStream.s, dataStream.s)
      // Data receiver
      // Pipe {
        // Pipe { val size = issueQueue.deq() }
        Foreach(requestLength par p){i =>
          val data = dataStream.value()
          val addr = onchipAddr(i)
          mem.store(local, addr, data, true)
        }
      // }
    }
    @virtualize
    def unalignedLoad(offchipAddr: => Index, onchipAddr: Index => Seq[Index]): Void = {
      val cmdStream  = StreamOut[BurstCmd](BurstCmdBus)
      val issueQueue = FIFO[IssuedCmd](16)  // TODO: Size of issued queue?
      val dataStream = StreamIn[T](BurstDataBus[T]())

      // Command
      Pipe {
        val aligned = alignmentCalc(offchipAddr)

        cmdStream := BurstCmd(aligned.addr_bytes, aligned.size_bytes, true)
        issueQueue.enq( IssuedCmd(aligned.size, aligned.start, aligned.end) )
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
          val data = dataStream.value()
          mem.store(local, addr, data, en)
        }
      }
    }
  }


  @internal def copy_sparse[T:Meta:Bits](
    offchip:   Exp[DRAM[T]],
    onchip:    Exp[SRAM1[T]],
    addresses: Exp[SRAM1[Index]],
    size:      Exp[Index],
    par:       Const[Index],
    isLoad:    scala.Boolean
  )(implicit mD: Meta[DRAM[T]], ctx: SrcCtx): Void = {
    val local = SRAM1(onchip)
    val addrs = SRAM1(addresses)
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
          addrBus := addr_bytes
        }
        // Fringe
        fringe_sparse_load(offchip, addrBus.s, dataBus.s)
        // Receive
        Foreach(requestLength par p){i =>
          val data = dataBus.value()
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

          cmdBus := pack(data,addr_bytes)
        }
        // Fringe
        fringe_sparse_store(offchip, cmdBus.s, ackBus.s)
        // Receive
        // TODO: Assumes one ack per address
        Foreach(requestLength par p){i =>
          val ack = ackBus.value()
        }
      }
    }

  }


}

trait DRAMTransferExp { this: SpatialExp =>
  /** Specialized buses **/
  @struct case class BurstCmd(offset: Index, size: Index, isLoad: Bool)
  @struct case class IssuedCmd(size: Index, start: Index, end: Index)

  abstract class DRAMBus[T:Type:Bits] extends Bus { def length = bits[T].length }

  case object BurstCmdBus extends DRAMBus[BurstCmd]
  case object BurstAckBus extends DRAMBus[Bool]
  case class BurstDataBus[T:Type:Bits]() extends DRAMBus[T]
  case class BurstFullDataBus[T:Type:Bits]() extends DRAMBus[Tup2[T,Bool]]

  case object GatherAddrBus extends DRAMBus[Index]
  case class GatherDataBus[T:Type:Bits]() extends DRAMBus[T]

  case class ScatterCmdBus[T:Type:Bits]() extends DRAMBus[Tup2[T, Index]]
  case object ScatterAckBus extends DRAMBus[Bool]

  /** Internal **/

  @internal def dense_transfer[T:Meta:Bits,C[_]](
    tile:   DRAMDenseTile[T],
    local:  C[T],
    isLoad: Boolean
  )(implicit mem: Mem[T,C], mC: Meta[C[T]]): Void = {
    implicit val mD: Meta[DRAM[T]] = tile.dram.tp

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

    val localRank = mem.iterators(local).length // TODO: Replace with something else here (this creates counters)

    val iters = List.tabulate(localRank){_ => fresh[Index]}

    Void(op_dense_transfer(dram,local.s,ofs,lens,units,p,isLoad,iters))
  }

  @internal def sparse_transfer[T:Meta:Bits](
    tile:   DRAMSparseTile[T],
    local:  SRAM1[T],
    isLoad: Boolean
  ): Void = {
    implicit val mD: Meta[DRAM[T]] = tile.dram.tp

    val p = extractParFactor(local.p)
    val size = stagedDimsOf(local.s).head
    val i = fresh[Index]
    Void(op_sparse_transfer(tile.dram, local.s, tile.addrs.s, size, p, isLoad, i))
  }

  // Defined in API
  @internal def copy_dense[T:Meta:Bits,C[T]](
    offchip: Exp[DRAM[T]],
    onchip:  Exp[C[T]],
    ofs:     Seq[Exp[Index]],
    lens:    Seq[Exp[Index]],
    units:   Seq[Boolean],
    par:     Const[Index],
    isLoad:  Boolean
  )(implicit mem: Mem[T,C], mC: Type[C[T]], mD: Meta[DRAM[T]], ctx: SrcCtx): Void

  @internal def copy_sparse[T:Meta:Bits](
    offchip:   Exp[DRAM[T]],
    onchip:    Exp[SRAM1[T]],
    addresses: Exp[SRAM1[Index]],
    size:      Exp[Index],
    par:       Const[Index],
    isLoad:    Boolean
  )(implicit mD: Meta[DRAM[T]], ctx: SrcCtx): Void

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
  )(implicit val mem: Mem[T,C], val mT: Meta[T], val bT: Bits[T], val mC: Meta[C[T]], mD: Meta[DRAM[T]]) extends Op[Void] {

    def isStore = !isLoad

    def mirror(f:Tx): Exp[Void] = op_dense_transfer(f(dram),f(local),f(ofs),f(lens),units,p,isLoad,iters)

    override def inputs = dyns(dram, local) ++ dyns(ofs) ++ dyns(lens)
    override def binds  = iters
    override def aliases = Nil

    def expand(f:Tx)(implicit ctx: SrcCtx): Exp[Void] = {
      copy_dense(f(dram),f(local),f(ofs),f(lens),units,p,isLoad)(mT,bT,mem,mC,mD,ctx).s
    }
  }

  case class SparseTransfer[T:Meta:Bits](
    dram:   Exp[DRAM[T]],
    local:  Exp[SRAM1[T]],
    addrs:  Exp[SRAM1[Index]],
    size:   Exp[Index],
    p:      Const[Index],
    isLoad: Boolean,
    i:      Bound[Index]
  )(implicit mD: Meta[DRAM[T]]) extends Op[Void] {
    def isStore = !isLoad

    def mirror(f:Tx) = op_sparse_transfer(f(dram),f(local),f(addrs),f(size),p,isLoad,i)

    override def inputs = dyns(dram, local, addrs, size, p)
    override def binds = List(i)
    override def aliases = Nil
    val mT = meta[T]
    val bT = bits[T]

    def expand(f:Tx)(implicit ctx: SrcCtx): Exp[Void] = {
      copy_sparse(f(dram),f(local),f(addrs),f(size),p,isLoad)(mT,bT,mD,ctx).s
    }
  }

  /** Fringe IR Nodes **/
  case class FringeDenseLoad[T:Meta:Bits](
    dram:       Exp[DRAM[T]],
    cmdStream:  Exp[StreamOut[BurstCmd]],
    dataStream: Exp[StreamIn[T]]
  ) extends Op[Void] {
    def mirror(f:Tx) = fringe_dense_load(f(dram),f(cmdStream),f(dataStream))
    val bT = bits[T]
    val mT = meta[T]
  }

  case class FringeDenseStore[T:Meta:Bits](
    dram:       Exp[DRAM[T]],
    cmdStream:  Exp[StreamOut[BurstCmd]],
    dataStream: Exp[StreamOut[Tup2[T,Bool]]],
    ackStream:  Exp[StreamIn[Bool]]
  ) extends Op[Void] {
    def mirror(f:Tx) = fringe_dense_store(f(dram),f(cmdStream),f(dataStream),f(ackStream))
    val bT = bits[T]
    val mT = meta[T]
  }

  case class FringeSparseLoad[T:Meta:Bits](
    dram:       Exp[DRAM[T]],
    addrStream: Exp[StreamOut[Index]],
    dataStream: Exp[StreamIn[T]]
  ) extends Op[Void] {
    def mirror(f:Tx) = fringe_sparse_load(f(dram),f(addrStream),f(dataStream))
    val bT = bits[T]
    val mT = meta[T]
  }

  case class FringeSparseStore[T:Meta:Bits](
    dram:       Exp[DRAM[T]],
    cmdStream: Exp[StreamOut[Tup2[T,Index]]],
    ackStream: Exp[StreamIn[Bool]]
  ) extends Op[Void] {
    def mirror(f:Tx) = fringe_sparse_store(f(dram),f(cmdStream),f(ackStream))
    val bT = bits[T]
    val mT = meta[T]
  }


  /** Constructors **/

  private def op_dense_transfer[T:Meta:Bits,C[T]](
    dram:   Exp[DRAM[T]],
    local:  Exp[C[T]],
    ofs:    Seq[Exp[Index]],
    lens:   Seq[Exp[Index]],
    units:  Seq[Boolean],
    p:      Const[Index],
    isLoad: Boolean,
    iters:  List[Bound[Index]]
  )(implicit mem: Mem[T,C], mC: Meta[C[T]], mD: Meta[DRAM[T]], ctx: SrcCtx): Exp[Void] = {

    val node = DenseTransfer(dram,local,ofs,lens,units,p,isLoad,iters)

    val out = if (isLoad) stageWrite(local)(node)(ctx) else stageWrite(dram)(node)(ctx)
    styleOf(out) = InnerPipe
    out
  }

  private def op_sparse_transfer[T:Meta:Bits](
    dram:   Exp[DRAM[T]],
    local:  Exp[SRAM1[T]],
    addrs:  Exp[SRAM1[Index]],
    size:   Exp[Index],
    p:      Const[Index],
    isLoad: Boolean,
    i:      Bound[Index]
  )(implicit mD: Meta[DRAM[T]], ctx: SrcCtx): Exp[Void] = {

    val node = SparseTransfer(dram,local,addrs,size,p,isLoad,i)

    val out = if (isLoad) stageWrite(local)(node)(ctx) else stageWrite(dram)(node)(ctx)
    styleOf(out) = InnerPipe
    out
  }

  @internal def fringe_dense_load[T:Meta:Bits](
    dram:       Exp[DRAM[T]],
    cmdStream:  Exp[StreamOut[BurstCmd]],
    dataStream: Exp[StreamIn[T]]
  ): Exp[Void] = {
    stageCold(FringeDenseLoad(dram,cmdStream,dataStream))(ctx)
  }

  @internal def fringe_dense_store[T:Meta:Bits](
    dram:       Exp[DRAM[T]],
    cmdStream:  Exp[StreamOut[BurstCmd]],
    dataStream: Exp[StreamOut[Tup2[T,Bool]]],
    ackStream:  Exp[StreamIn[Bool]]
  ): Exp[Void] = {
    stageCold(FringeDenseStore(dram,cmdStream,dataStream,ackStream))(ctx)
  }

  @internal def fringe_sparse_load[T:Meta:Bits](
    dram:       Exp[DRAM[T]],
    addrStream: Exp[StreamOut[Index]],
    dataStream: Exp[StreamIn[T]]
  ): Exp[Void] = {
    stageCold(FringeSparseLoad(dram,addrStream,dataStream))(ctx)
  }

  @internal def fringe_sparse_store[T:Meta:Bits](
    dram:       Exp[DRAM[T]],
    cmdStream: Exp[StreamOut[Tup2[T,Index]]],
    ackStream: Exp[StreamIn[Bool]]
  ): Exp[Void] = {
    stageCold(FringeSparseStore(dram,cmdStream,ackStream))(ctx)
  }

}
