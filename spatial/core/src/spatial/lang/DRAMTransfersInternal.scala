package spatial.lang

import control._
import FringeTransfers._

import argon.core._
import forge._
import org.virtualized._
import spatial.metadata._
import spatial.utils._

// object ShiftInternal {
//   target = spatialConfig.target

//   @internal def expandLsh
// }
object DRAMTransfersInternal {
  @stateful def target = spatialConfig.target

  /** Internals **/
  // Expansion rule for CoarseBurst -  Use coarse_burst(tile,onchip,isLoad) for anything in the frontend
  @internal def copy_dense[T:Type:Bits,C[T]](
    offchip: Exp[DRAM[T]],
    onchip:  Exp[C[T]],
    ofs:     Seq[Exp[Index]],
    lens:    Seq[Exp[Index]],
    strides: Seq[Exp[Index]],
    units:   Seq[Boolean],
    par:     Const[Index],
    isLoad:  Boolean,
    isAlign: Boolean
  )(implicit mem: Mem[T,C], mC: Type[C[T]], mD: Type[DRAM[T]], ctx: SrcCtx): MUnit = {

    val unitDims = units
    val offchipOffsets = wrap(ofs)
    val tileDims = wrap(lens)
    val strideNums = wrap(strides)
    val local = wrap(onchip)
    val dram  = wrap(offchip)

    // Last counter is used as counter for load/store
    // Other counters (if any) are used to iterate over higher dimensions
    val counters = tileDims.zip(strideNums).map{case(d,st) => () => Counter(start = 0, end = d, step = 1, par = 1) }

    val requestLength = tileDims.last
    val p = wrap(par)

    val bytesPerWord = bits[T].length / 8 + (if (bits[T].length % 8 != 0) 1 else 0)


    // Metaprogrammed (unstaged) if-then-else
    if (counters.length > 1) {
      Stream.Foreach(counters.dropRight(1).map{ctr => ctr()}){ is =>
        val indices = is :+ 0.to[Index]

        val offchipAddr = () => flatIndex( offchipOffsets.zip(indices.zip(strideNums)).map{case (a,(b,st)) => a + b*st}, wrap(stagedDimsOf(offchip)))

        val onchipOfs   = indices.zip(unitDims).collect{case (i,isUnitDim) if !isUnitDim => i }
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
      case Exact(c: BigInt) if (c.toInt*bits[T].length) % target.burstSize == 0 | spatialConfig.enablePIR | isAlign => //TODO: Hack for pir
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
      case Exact(c: BigInt) if (c.toInt*bits[T].length) % target.burstSize == 0 | spatialConfig.enablePIR | isAlign => //TODO: Hack for pir
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
      isAligned(cmdStream.s) = true
      // val issueQueue = FIFO[Index](16)  // TODO: Size of issued queue?
      val dataStream = StreamOut[MTuple2[T,Bit]](BurstFullDataBus[T]())
      val ackStream  = StreamIn[Bit](BurstAckBus)

      // Command generator
      // if (spatialConfig.enablePIR) { // On plasticine the sequential around data and address generation is inefficient
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
      // } else {
      //   Pipe {
      //     Pipe {
      //       val addr_bytes = (offchipAddr * bytesPerWord).to[Int64] + dram.address
      //       val size = requestLength
      //       val size_bytes = size * bytesPerWord
      //       cmdStream := BurstCmd(addr_bytes.to[Int64], size_bytes, false)
      //       // issueQueue.enq(size)
      //     }
      //     // Data loading
      //     Foreach(requestLength par p){i =>
      //       val data = mem.load(local, onchipAddr(i), true)
      //       dataStream := pack(data,true)
      //     }
      //   }
      // }
      // Fringe
      fringe_dense_store(offchip, cmdStream.s, dataStream.s, ackStream.s)
      // Ack receiver
      // TODO: Assumes one ack per command
      Pipe {
        // val size = Reg[Index]
        // Pipe{size := issueQueue.deq()}
        val ack  = ackStream.value()
        ()
//        Foreach(requestLength by target.burstSize/bits[T].length) {i =>
//          val ack  = ackStream.value()
//        }
      }
    }

    case class AlignmentData(start: Index, end: Index, size: Index, addr_bytes: Int64, size_bytes: Index)

    @virtualize
    def alignmentCalc(offchipAddr: => Index) = {
      /*
              ←--------------------------- size ----------------→
                               ←  (one burst) →
                     _______________________________
              |-----:_________|________________|____:------------|
              0
          start ----⬏
            end -----------------------------------⬏
                                                   extra --------⬏

      */
      val elementsPerBurst = (target.burstSize/bits[T].length).to[Index]
      val bytesPerBurst = target.burstSize/8

      val maddr_bytes  = offchipAddr * bytesPerWord     // Raw address in bytes
      val start_bytes  = maddr_bytes % bytesPerBurst    // Number of bytes offset from previous burst aligned address
      val length_bytes = requestLength * bytesPerWord   // Raw length in bytes
      val offset_bytes = maddr_bytes - start_bytes      // Burst-aligned start address, in bytes
      val raw_end      = maddr_bytes + length_bytes     // Raw end, in bytes, with burst-aligned start

      val end_bytes = Math.mux(raw_end % bytesPerBurst === 0.to[Index],  0.to[Index], bytesPerBurst - raw_end % bytesPerBurst) // Extra useless bytes at end

      // FIXME: What to do for bursts which split individual words?
      val start = start_bytes / bytesPerWord                   // Number of WHOLE elements to ignore at start
      val extra = end_bytes / bytesPerWord                     // Number of WHOLE elements that will be ignored at end
      val end   = start + requestLength                       // Index of WHOLE elements to start ignoring at again
      val size  = requestLength + start + extra                // Total number of WHOLE elements to expect

      val size_bytes = length_bytes + start_bytes + end_bytes  // Burst aligned length
      val addr_bytes = offset_bytes.to[Int64] + dram.address             // Burst-aligned offchip byte address

      AlignmentData(start, end, size, addr_bytes, size_bytes)
    }

    @virtualize
    def unalignedStore(offchipAddr: => Index, onchipAddr: Index => Seq[Index]): MUnit = {
      val cmdStream  = StreamOut[BurstCmd](BurstCmdBus)
      isAligned(cmdStream.s) = false
//      val issueQueue = FIFO[Index](16)  // TODO: Size of issued queue?
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
//          issueQueue.enq(aligned.size)
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
//        val size = Reg[Index]
//        Pipe{size := issueQueue.deq()}
        val ack  = ackStream.value()
        ()
//        Foreach(size.value by size.value) {i => // TODO: Can we use by instead of par?
//          val ack  = ackStream.value()
//        }
      }
    }

    def alignedLoad(offchipAddr: => Index, onchipAddr: Index => Seq[Index]): MUnit = {
      val cmdStream  = StreamOut[BurstCmd](BurstCmdBus)
      isAligned(cmdStream.s) = true
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
      isAligned(cmdStream.s) = false
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
    isLoad:    Boolean
  )(implicit mD: Type[DRAM[T]], ctx: SrcCtx): MUnit = {
    val local = new SRAM1(onchip)
    val addrs = new SRAM1(addresses)
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

  @internal def copy_sparse_mem[T,C[T],A[_]](
    offchip:   Exp[DRAM[T]],
    onchip:    Exp[C[T]],
    addresses: Exp[A[Index]],
    size:      Exp[Index],
    par:       Const[Index],
    isLoad:    Boolean
  )(implicit mT:   Type[T],
             bT:   Bits[T],
             memC: Mem[T,C],
             mC:   Type[C[T]],
             memA: Mem[Index,A],
             mA:   Type[A[Index]],
             mD:   Type[DRAM[T]],
             ctx:  SrcCtx
  ): MUnit = {
    val local = wrap(onchip)
    val addrs = wrap(addresses)
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
          val addr = ifThenElse(i >= requestLength, dram.address.to[Int64], (memA.load(addrs,Seq(i),true) * bytesPerWord).to[Int64] + dram.address )
          val addr_bytes = addr
          addrBus := addr_bytes
          // addrBus := addr_bytes
        }
        // Fringe
        fringe_sparse_load(offchip, addrBus.s, dataBus.s)
        // Receive
        Foreach(iters par p){i =>
          val data = dataBus.value()
          memC.store(local, Seq(i), data, i < requestLength)
        }
      }
      // Scatter
      else {
        val cmdBus = StreamOut[MTuple2[T,Int64]](ScatterCmdBus[T]())
        val ackBus = StreamIn[Bit](ScatterAckBus)

        // Send
        Foreach(iters par p){i =>
          val pad_addr = Math.max(requestLength - 1, 0.to[Index])
          // Using ifThenElse instead of syntax sugar out of convenience (sugar needs T <: MetaAny[T] evidence...)
          //val curAddr  = if (i >= requestLength) memA.load(addrs, Seq(pad_addr), true) else memA.load(addrs, Seq(i), true)
          //val data     = if (i >= requestLength) memC.load(local, Seq(pad_addr), true) else memC.load(local, Seq(i), true)
          val curAddr  = ifThenElse(i >= requestLength, memA.load(addrs, Seq(pad_addr), true), memA.load(addrs, Seq(i), true))
          val data     = ifThenElse(i >= requestLength, memC.load(local, Seq(pad_addr), true), memC.load(local, Seq(i), true))

          val addr     = (curAddr * bytesPerWord).to[Int64] + dram.address
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

}
