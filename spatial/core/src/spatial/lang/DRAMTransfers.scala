package spatial.lang

import argon.internals._
import forge._
import org.virtualized._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._
import spatial.SpatialApi

/** Specialized buses **/
@struct case class BurstCmd(offset: Int64, size: Index, isLoad: Bit)
@struct case class IssuedCmd(size: Index, start: Index, end: Index)

abstract class DRAMBus[T:Type:Bits] extends Bus { def length = bits[T].length }

case object BurstCmdBus extends DRAMBus[BurstCmd]
case object BurstAckBus extends DRAMBus[Bit]
case class BurstDataBus[T:Type:Bits]() extends DRAMBus[T]
case class BurstFullDataBus[T:Type:Bits]() extends DRAMBus[MTuple2[T,Bit]]

case object GatherAddrBus extends DRAMBus[Int64]
case class GatherDataBus[T:Type:Bits]() extends DRAMBus[T]

case class ScatterCmdBus[T:Type:Bits]() extends DRAMBus[MTuple2[T, Int64]]
case object ScatterAckBus extends DRAMBus[Bit]



trait DRAMTransfersApi { self: SpatialApi =>

  /** Internals **/
  // Expansion rule for CoarseBurst -  Use coarse_burst(tile,onchip,isLoad) for anything in the frontend
  @internal def copy_dense[T:Type:Bits,C[T]](
    offchip: Exp[DRAM[T]],
    onchip:  Exp[C[T]],
    ofs:     Seq[Exp[Index]],
    lens:    Seq[Exp[Index]],
    units:   Seq[scala.Boolean],
    par:     Const[Index],
    isLoad:  scala.Boolean
  )(implicit mem: Mem[T,C], mC: Type[C[T]], mD: Type[DRAM[T]], ctx: SrcCtx): MUnit = {

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


    // Typeprogrammed (unstaged) if-then-else
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
    def store(offchipAddr: => Index, onchipAddr: Index => Seq[Index]): MUnit = requestLength.s match {
      case Exact(c: BigInt) if (c*bits[T].length) % target.burstSize == 0 =>
        dbg(u"$onchip => $offchip: Using aligned store ($c * ${bits[T].length} % ${target.burstSize} = ${c*bits[T].length % target.burstSize})")
        alignedStore(offchipAddr, onchipAddr)
      case Exact(c: BigInt) =>
        dbg(u"$onchip => $offchip: Using unaligned store ($c * ${bits[T].length} % ${target.burstSize} = ${c*bits[T].length % target.burstSize})")
        unalignedStore(offchipAddr, onchipAddr)
      case _ =>
        dbg(u"$onchip => $offchip: Using unaligned store (request length is statically unknown)")
        unalignedStore(offchipAddr, onchipAddr)
    }
    def load(offchipAddr: => Index, onchipAddr: Index => Seq[Index]): MUnit = requestLength.s match {
      case Exact(c: BigInt) if (c*bits[T].length) % target.burstSize == 0 =>
        dbg(u"$offchip => $onchip: Using aligned load ($c * ${bits[T].length} % ${target.burstSize} = ${c*bits[T].length % target.burstSize})")
        alignedLoad(offchipAddr, onchipAddr)
      case Exact(c: BigInt) =>
        dbg(u"$offchip => $onchip: Using unaligned load ($c * ${bits[T].length} % ${target.burstSize}* ${c*bits[T].length % target.burstSize})")
        unalignedLoad(offchipAddr, onchipAddr)
      case _ =>
        dbg(u"$offchip => $onchip: Using unaligned load (request length is statically unknown)")
        unalignedLoad(offchipAddr, onchipAddr)
    }

    def alignedStore(offchipAddr: => Index, onchipAddr: Index => Seq[Index]): MUnit = {
      val cmdStream  = StreamOut[BurstCmd](BurstCmdBus)
      // val issueQueue = FIFO[Index](16)  // TODO: Size of issued queue?
      val dataStream = StreamOut[MTuple2[T,Bit]](BurstFullDataBus[T]())
      val ackStream  = StreamIn[Bit](BurstAckBus)

      // Command generator
      Pipe {
        Pipe {
          val addr_bytes = (offchipAddr * bytesPerWord).to[Int64] + dram.address
          val size = requestLength
          val size_bytes = size * bytesPerWord
          cmdStream := BurstCmd(addr_bytes.to[Int64], size_bytes, false)
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
      Pipe {
        // val size = Reg[Index]
        // Pipe{size := issueQueue.deq()}
        Foreach(requestLength by target.burstSize/bits[T].length) {i =>
          val ack  = ackStream.value()
        }
      }
    }

    case class AlignmentData(start: Index, end: Index, size: Index, addr_bytes: Int64, size_bytes: Index)

    @virtualize
    def alignmentCalc(offchipAddr: => Index) = {
      val elementsPerBurst = (target.burstSize/bits[T].length).to[Index]
      val bytesPerBurst = target.burstSize/8

      val maddr_bytes  = offchipAddr * bytesPerWord     // Raw address in bytes
      val start_bytes  = maddr_bytes % bytesPerBurst    // Number of bytes offset from previous burst aligned address
      val length_bytes = requestLength * bytesPerWord   // Raw length in bytes
      val offset_bytes = maddr_bytes - start_bytes      // Burst-aligned start address, in bytes
      val raw_end      = maddr_bytes + length_bytes     // Raw end, in bytes, with burst-aligned start

      val end_bytes = Math.mux(raw_end % bytesPerBurst == 0,  0.to[Index], bytesPerBurst - raw_end % bytesPerBurst) // Extra useless bytes at end

      // FIXME: What to do for bursts which split individual words?
      val start = start_bytes / bytesPerWord                   // Number of WHOLE elements to ignore at start
      val end   = raw_end / bytesPerWord                       // Index of WHOLE elements to start ignoring at again
      val extra = end_bytes / bytesPerWord                     // Number of WHOLE elements that will be ignored at end
      val size  = requestLength + start + extra                // Total number of WHOLE elements to expect

      val size_bytes = length_bytes + start_bytes + end_bytes  // Burst aligned length
      val addr_bytes = offset_bytes.to[Int64] + dram.address             // Burst-aligned offchip byte address

      AlignmentData(start, end, size, addr_bytes, size_bytes)
    }

    @virtualize
    def unalignedStore(offchipAddr: => Index, onchipAddr: Index => Seq[Index]): MUnit = {
      val cmdStream  = StreamOut[BurstCmd](BurstCmdBus)
      val issueQueue = FIFO[Index](16)  // TODO: Size of issued queue?
      val dataStream = StreamOut[MTuple2[T,Bit]](BurstFullDataBus[T]())
      val ackStream  = StreamIn[Bit](BurstAckBus)

      // Command generator
      Pipe {
        val startBound = Reg[Index]
        val endBound   = Reg[Index]
        val length     = Reg[Index]
        Pipe {
          val aligned = alignmentCalc(offchipAddr)

          cmdStream := BurstCmd(aligned.addr_bytes.to[Int64], aligned.size_bytes, false)
          issueQueue.enq(aligned.size)
          startBound := aligned.start
          endBound := aligned.end
          length := aligned.size
        }
        Foreach(length par p){i =>
          val en = i >= startBound && i < endBound
          val data = Math.mux(en, mem.load(local,onchipAddr(i - startBound), en), implicitly[Bits[T]].zero)
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

    def alignedLoad(offchipAddr: => Index, onchipAddr: Index => Seq[Index]): MUnit = {
      val cmdStream  = StreamOut[BurstCmd](BurstCmdBus)
      // val issueQueue = FIFO[Index](16)  // TODO: Size of issued queue?
      val dataStream = StreamIn[T](BurstDataBus[T]())

      // Command generator
      Pipe {
        val addr = (offchipAddr * bytesPerWord).to[Int64] + dram.address
        val size = requestLength

        val addr_bytes = addr
        val size_bytes = size * bytesPerWord

        cmdStream := BurstCmd(addr_bytes.to[Int64], size_bytes, true)
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
    def unalignedLoad(offchipAddr: => Index, onchipAddr: Index => Seq[Index]): MUnit = {
      val cmdStream  = StreamOut[BurstCmd](BurstCmdBus)
      val issueQueue = FIFO[IssuedCmd](16)  // TODO: Size of issued queue?
      val dataStream = StreamIn[T](BurstDataBus[T]())

      // Command
      Pipe {
        val aligned = alignmentCalc(offchipAddr)

        cmdStream := BurstCmd(aligned.addr_bytes.to[Int64], aligned.size_bytes, true)
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


  @internal def copy_sparse[T:Type:Bits](
    offchip:   Exp[DRAM[T]],
    onchip:    Exp[SRAM1[T]],
    addresses: Exp[SRAM1[Index]],
    size:      Exp[Index],
    par:       Const[Index],
    isLoad:    scala.Boolean
  )(implicit mD: Type[DRAM[T]], ctx: SrcCtx): MUnit = {
    val local = SRAM1(onchip)
    val addrs = SRAM1(addresses)
    val dram  = wrap(offchip)
    val requestLength = wrap(size)
    val p = wrap(par)

    val bytesPerWord = bits[T].length / 8 + (if (bits[T].length % 8 != 0) 1 else 0)

    // FIXME: Bump up request to nearest multiple of 16 because of fringe
    val iters = Reg[Index](0)
    Pipe{
      iters := Math.mux(requestLength < 16.to[Index], 16.to[Index],
        Math.mux(requestLength % 16.to[Index] === 0.to[Index], requestLength, requestLength + 16.to[Index] - (requestLength % 16.to[Index]) ))
      // (requestLength + math_mux((requestLength % 16.to[Index] === 0.to[Index]).s, (0.to[Index]).s, (16.to[Index] - (requestLength % 16.to[Index])).s )).s
    }
    Stream {
      // Gather
      if (isLoad) {
        val addrBus = StreamOut[Int64](GatherAddrBus)
        val dataBus = StreamIn[T](GatherDataBus[T]())

        // Send
        Foreach(iters par p){i =>
          val addr = mux(i >= requestLength, dram.address.to[Int64], (addrs(i) * bytesPerWord).to[Int64] + dram.address)

          val addr_bytes = addr
          addrBus := addr_bytes
          // addrBus := addr_bytes
        }
        // Fringe
        fringe_sparse_load(offchip, addrBus.s, dataBus.s)
        // Receive
        Foreach(iters par p){i =>
          val data = dataBus.value()
          SRAM.store(local.s, stagedDimsOf(local.s), Seq(i.s), i.s/*notused*/, unwrap(data), (i < requestLength).s)
          ()
          // local(i) = data
        }
      }
      // Scatter
      else {
        val cmdBus = StreamOut[MTuple2[T,Int64]](ScatterCmdBus[T]())
        val ackBus = StreamIn[Bit](ScatterAckBus)

        // Send
        Foreach(iters par p){i =>
          val pad_addr = Math.max(requestLength - 1, 0.to[Index])
          val unique_addr = addrs(pad_addr)
          val addr = Math.mux(i >= requestLength,
            (unique_addr * bytesPerWord).to[Int64] + dram.address,
            (addrs(i) * bytesPerWord).to[Int64] + dram.address
          )
          val data = Math.mux(i >= requestLength, local(pad_addr), local(i))

          val addr_bytes = addr

          cmdBus := pack(data, addr_bytes)
        }
        // Fringe
        fringe_sparse_store(offchip, cmdBus.s, ackBus.s)
        // Receive
        // TODO: Assumes one ack per address
        Foreach(iters by target.burstSize/bits[T].length){i =>
          val ack = ackBus.value()
        }
      }
    }
  }

  @internal def fringe_dense_load[T:Type:Bits](
    dram:       Exp[DRAM[T]],
    cmdStream:  Exp[StreamOut[BurstCmd]],
    dataStream: Exp[StreamIn[T]]
  ): Exp[MUnit] = {
    stageUnique(FringeDenseLoad(dram,cmdStream,dataStream))(ctx)
  }

  @internal def fringe_dense_store[T:Type:Bits](
    dram:       Exp[DRAM[T]],
    cmdStream:  Exp[StreamOut[BurstCmd]],
    dataStream: Exp[StreamOut[MTuple2[T,Bit]]],
    ackStream:  Exp[StreamIn[Bit]]
  ): Exp[MUnit] = {
    stageUnique(FringeDenseStore(dram,cmdStream,dataStream,ackStream))(ctx)
  }

  @internal def fringe_sparse_load[T:Type:Bits](
    dram:       Exp[DRAM[T]],
    addrStream: Exp[StreamOut[Int64]],
    dataStream: Exp[StreamIn[T]]
  ): Exp[MUnit] = {
    stageUnique(FringeSparseLoad(dram,addrStream,dataStream))(ctx)
  }

  @internal def fringe_sparse_store[T:Type:Bits](
    dram:       Exp[DRAM[T]],
    cmdStream: Exp[StreamOut[MTuple2[T,Int64]]],
    ackStream: Exp[StreamIn[Bit]]
  ): Exp[MUnit] = {
    stageUnique(FringeSparseStore(dram,cmdStream,ackStream))(ctx)
  }

}


object DRAMTransfers {
  /** Internal **/
  @internal def dense_transfer[T:Type:Bits,C[_]](
    tile:   DRAMDenseTile[T],
    local:  C[T],
    isLoad: Boolean
  )(implicit mem: Mem[T,C], mC: Type[C[T]]): MUnit = {
    implicit val mD: Type[DRAM[T]] = tile.dram.tp

    // Extract range lengths early to avoid unit pipe insertion eliminating rewrite opportunities
    val dram    = tile.dram
    val ofs     = tile.ranges.map(_.start.map(_.s).getOrElse(int32(0)))
    val lens    = tile.ranges.map(_.length.s)
    val strides = tile.ranges.map(_.step.map(_.s).getOrElse(int32(1)))
    val units   = tile.ranges.map(_.isUnit)
    val p       = extractParFactor(tile.ranges.last.p)

    // UNSUPPORTED: Strided ranges for DRAM in burst load/store
    if (strides.exists{case Const(1) => false ; case _ => true}) {
      new spatial.UnsupportedStridedDRAMError(isLoad)(ctx, state)
    }

    val localRank = mem.iterators(local).length // TODO: Replace with something else here (this creates counters)

    val iters = List.tabulate(localRank){_ => fresh[Index]}

    MUnit(op_dense_transfer(dram,local.s,ofs,lens,units,p,isLoad,iters))
  }

  @internal def sparse_transfer[T:Type:Bits](
    tile:   DRAMSparseTile[T],
    local:  SRAM1[T],
    isLoad: Boolean
  ): MUnit = {
    implicit val mD: Type[DRAM[T]] = tile.dram.tp

    val p = extractParFactor(local.p)
    val size = tile.len.s //stagedDimsOf(local.s).head
    val i = fresh[Index]
    MUnit(op_sparse_transfer(tile.dram, local.s, tile.addrs.s, size, p, isLoad, i))
  }

  /** Constructors **/
  @internal def op_dense_transfer[T:Type:Bits,C[T]](
    dram:   Exp[DRAM[T]],
    local:  Exp[C[T]],
    ofs:    Seq[Exp[Index]],
    lens:   Seq[Exp[Index]],
    units:  Seq[Boolean],
    p:      Const[Index],
    isLoad: Boolean,
    iters:  List[Bound[Index]]
  )(implicit mem: Mem[T,C], mC: Type[C[T]], mD: Type[DRAM[T]], ctx: SrcCtx): Exp[MUnit] = {

    val node = DenseTransfer(dram,local,ofs,lens,units,p,isLoad,iters)

    val out = if (isLoad) stageWrite(local)(node)(ctx) else stageWrite(dram)(node)(ctx)
    styleOf(out) = InnerPipe
    out
  }

  @internal def op_sparse_transfer[T:Type:Bits](
    dram:   Exp[DRAM[T]],
    local:  Exp[SRAM1[T]],
    addrs:  Exp[SRAM1[Index]],
    size:   Exp[Index],
    p:      Const[Index],
    isLoad: Boolean,
    i:      Bound[Index]
  )(implicit mD: Type[DRAM[T]], ctx: SrcCtx): Exp[MUnit] = {

    val node = SparseTransfer(dram,local,addrs,size,p,isLoad,i)

    val out = if (isLoad) stageWrite(local)(node)(ctx) else stageWrite(dram)(node)(ctx)
    styleOf(out) = InnerPipe
    out
  }
}
