package spatial.codegen.chiselgen

import spatial.SpatialConfig
import spatial.SpatialExp
import scala.collection.mutable.HashMap

trait ChiselGenDRAM extends ChiselGenSRAM {
  val IR: SpatialExp
  import IR._

  var dramMap = HashMap[String, (String, String)]() // Map for tracking defs of nodes and if they get redeffed anywhere, we map it to a suffix

  override def quote(s: Exp[_]): String = {
    if (SpatialConfig.enableNaming) {
      s match {
        case lhs: Sym[_] =>
          lhs match {
              case Def(e: DRAMNew[_]) => s"x${lhs.id}_dram"
            case _ =>
              super.quote(s)
          }
        case _ =>
          super.quote(s)
      }
    } else {
      super.quote(s)
    }
  } 

  override protected def remap(tp: Staged[_]): String = tp match {
    case tp: DRAMType[_] => src"Array[${tp.child}]"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op@DRAMNew(dims) => 
      // Register first dram
      val length = dims.map{i => src"${i}"}.mkString("*")
      if (dramMap.size == 0)  {
        dramMap += (src"$lhs" -> ("0", length))
      } else if (!dramMap.contains(src"$lhs")) {
        val start = dramMap.values.map{ _._2 }.mkString{" + "}
        dramMap += (src"$lhs" -> (start, length))
      } else {
        log(s"dram $lhs used multiple times")
      }

    case GetDRAMAddress(dram) =>
      val id = argMapping(dram)._1
      emit(src"""val $lhs = io.argIns($id)""")

    case FringeDenseLoad(dram,cmdStream,dataStream) =>
      val id = argMapping(dram)._2
      emitGlobal(src"""val ${childrenOf(childrenOf(parentOf(lhs).get).apply(1)).apply(1)}_enq = io.memStreams(${id}).rdata.valid""")
      emit(src"""// Connect streams to ports on mem controller""")
      emit("// HACK: Assume load is par=16")
      val allData = (0 until 16).map{ i => src"io.memStreams($id).rdata.bits($i)" }.mkString(",")
      emit(src"""val ${dataStream}_data = Vec(List($allData))""")
      emitGlobal(src"""val ${dataStream}_ready = io.memStreams($id).rdata.valid""")
      emit(src"io.memStreams($id).cmd.bits.addr(0) := ${cmdStream}_data(64, 33) // Bits 33 to 64 (AND BEYOND???) are addr")
      emit(src"io.memStreams($id).cmd.bits.size := ${cmdStream}_data(32,1) // Bits 1 to 32 are size command")
      emit(src"io.memStreams($id).cmd.valid :=  ${cmdStream}_valid// LSB is enable, instead of pulser?? Reg(UInt(1.W), pulser.io.out)")
      emit(src"io.memStreams($id).cmd.bits.isWr := ~${cmdStream}_data(0)")
      emitGlobal(src"val ${cmdStream}_ready = true.B // Assume cmd fifo will never fill up")


    case FringeDenseStore(dram,cmdStream,dataStream,ackStream) =>
      val id = argMapping(dram)._2
      // emitGlobal(src"""val ${childrenOf(childrenOf(parentOf(lhs).get).apply(1)).apply(1)}_enq = io.memStreams(${id}).rdata.valid""")
      emit(src"""// Connect streams to ports on mem controller""")
      emit("// HACK: Assume store is par=16")
      val allData = (0 until 16).map{ i => src"io.memStreams($id).rdata.bits($i)" }.mkString(",")
      emitGlobal(src"""val ${dataStream}_en = Wire(Bool())""")
      emit(src"""io.memStreams($id).wdata.bits.zip(${dataStream}_data).foreach{case (wport, wdata) => wport := wdata(31,1) /*LSB is status bit*/}""")
      emit(src"""io.memStreams($id).wdata.valid := ${dataStream}_en""")
      emit(src"io.memStreams($id).cmd.bits.addr(0) := ${cmdStream}_data(64, 33) // Bits 33 to 64 (AND BEYOND???) are addr")
      emit(src"io.memStreams($id).cmd.bits.size := ${cmdStream}_data(32,1) // Bits 1 to 32 are size command")
      emit(src"io.memStreams($id).cmd.valid :=  ${cmdStream}_valid// LSB is enable, instead of pulser?? Reg(UInt(1.W), pulser.io.out)")
      emit(src"io.memStreams($id).cmd.bits.isWr := ~${cmdStream}_data(0)")
      emitGlobal(src"val ${cmdStream}_ready = true.B // Assume cmd fifo will never fill up")
      emitGlobal(src"""val ${dataStream}_ready = true.B // Assume cmd fifo will never fill up""")
      emitGlobal(src"val ${ackStream}_data = 0.U // Definitely wrong signal")

    case FringeSparseLoad(dram,addrStream,dataStream) =>
      open(src"val $lhs = $addrStream.foreach{addr => ")
        emit(src"$dataStream.enqueue( $dram(addr) )")
      close("}")
      emit(src"$addrStream.clear()")

    case FringeSparseStore(dram,cmdStream,ackStream) =>
      open(src"val $lhs = $cmdStream.foreach{cmd => ")
        emit(src"$dram(cmd._2) = cmd._1 ")
        emit(src"$ackStream.enqueue(true)")
      close("}")
      emit(src"$cmdStream.clear()")

    /*case Gather(dram, local, addrs, ctr, i)  =>


    case BurstLoad(dram, fifo, ofs, ctr, i)  =>
      val (start,stop,stride,p) = ctr match { case Def(CounterNew(s1,s2,s3,par)) => (s1,s2,s3,par); case _ => (1,1,1,1) }
      val streamId = offchipMems.length
      offchipMems = offchipMems :+ lhs.asInstanceOf[Sym[Any]]
      emitGlobal(src"""val ${lhs} = Module(new MemController(${p}))""".replace(".U",""))
      emitGlobal(src"""io.MemStreams.outPorts${streamId} := ${lhs}.io.CtrlToDRAM""")
      emitGlobal(src"""${lhs}.io.DRAMToCtrl := io.MemStreams.inPorts${streamId} """)
      alphaconv_register(src"$dram")
      emit(src"""// ---- Memory Controller (Load) ${lhs} ----
val ${dram} = 1024 * 1024 * ${streamId}
${lhs}_done := ${lhs}.io.CtrlToAccel.cmdIssued
${lhs}.io.AccelToCtrl.enLoad := ${lhs}_en
${lhs}.io.AccelToCtrl.offset := ${ofs}
${lhs}.io.AccelToCtrl.base := ${dram}.U
${lhs}.io.AccelToCtrl.pop := ${fifo}_writeEn
${fifo}_wdata.zip(${lhs}.io.CtrlToAccel.data).foreach { case (d, p) => d := p }""")

      emit(src"""${lhs}.io.AccelToCtrl.size := ($stop - $start) / $stride // TODO: Optimizie this if it is constant""")

      emit(src"""${fifo}_writeEn := ${lhs}.io.CtrlToAccel.valid;""")

    case BurstStore(dram, fifo, ofs, ctr, i) =>
      val (start,stop,stride,p) = ctr match { case Def(CounterNew(s1,s2,s3,par)) => (s1,s2,s3,par); case _ => (1,1,1,1) }
      val streamId = offchipMems.length
      offchipMems = offchipMems :+ lhs.asInstanceOf[Sym[Any]]
      emitGlobal(src"""val ${lhs} = Module(new MemController(${p}))""".replace(".U",""))
      emitGlobal(src"""io.MemStreams.outPorts${streamId} := ${lhs}.io.CtrlToDRAM""")
      emitGlobal(src"""${lhs}.io.DRAMToCtrl := io.MemStreams.inPorts${streamId} """)
      alphaconv_register(src"$dram")
      emit(src"""// ---- Memory Controller (Store) ${lhs} ----
val ${dram} = 1024 * 1024 * ${streamId}
${lhs}_done := ${lhs}.io.CtrlToAccel.valid
${lhs}.io.AccelToCtrl.enStore := ${lhs}_en
${lhs}.io.AccelToCtrl.offset := ${ofs}
${lhs}.io.AccelToCtrl.base := ${dram}.U
${lhs}.io.AccelToCtrl.data := ${fifo}_wdata
${lhs}.io.AccelToCtrl.push := ${fifo}_writeEn
${lhs}_done := ${lhs}.io.CtrlToAccel.doneStore
""")
      emit(src"""${lhs}.io.AccelToCtrl.size := ($stop - $start) / $stride // TODO: Optimizie this if it is constant""")
    */
    case _ => super.emitNode(lhs, rhs)
  }


  override protected def emitFileFooter() {

    withStream(getStream("Instantiator")) {
      emit("")
      emit(s"// Memory streams")
      emit(s"""val numMemoryStreams = ${dramMap.size}""")
      emit(s"""val numArgIns_mem = ${dramMap.size}""")
      emit(s"// Mapping:")
    }

    withStream(getStream("IOModule")) {
      emit("// Tile Load")
      emit(s"val io_numMemoryStreams = ${dramMap.size}")
      emit(s"val io_numArgIns_mem = ${dramMap.size}")

    }

  //   withStream(getStream("GeneratedPoker")) {
  //     offchipMems.zipWithIndex.foreach{case (port,i) =>
  //       val interface = port match {
  //         case Def(BurstLoad(mem,_,_,ctr,_)) =>
  //           val p = ctr match { case Def(CounterNew(_,_,_,par)) => par; case _ => 1 }
  //           ("receiveBurst", s"${p}", "BurstLoad",
  //            s"""for (j <- 0 until size${i}) {
  //         (0 until par${i}).foreach { k => 
  //           val element = (addr${i}-base${i}+j*par${i}+k) % 256 // TODO: Should be loaded from CPU side
  //           poke(c.io.MemStreams.inPorts${i}.data(k), element) 
  //         }  
  //         poke(c.io.MemStreams.inPorts${i}.valid, 1)
  //         step(1)
  //         }
  //         poke(c.io.MemStreams.inPorts${i}.valid, 0)
  //         step(1)""", s"""${nameOf(mem)}.getOrElse("")}""")
  //         case Def(BurstStore(mem,_,_,ctr,_)) =>
  //           val p = ctr match { case Def(CounterNew(_,_,_,par)) => par; case _ => 1 }
  //           ("sendBurst", s"${p}", "BurstStore",
  //            s"""for (j <- 0 until size${i}) {
  //         poke(c.io.MemStreams.inPorts${i}.pop, 1)
  //         (0 until par${i}).foreach { k => 
  //           offchipMem = offchipMem :+ peek(c.io.MemStreams.outPorts${i}.data(k)) 
  //         }  
  //         step(1)
  //         }
  //       poke(c.io.MemStreams.inPorts${i}.pop, 0)
  //       step(1)""", s"""${nameOf(mem)}.getOrElse("")}""")
  //       }
  //       emit(s"""
  //     // ${interface._3} Poker -- ${quote(port)} <> ports${i} <> ${interface._5}
  //     val req${i} = (peek(c.io.MemStreams.outPorts${i}.${interface._1}) == 1)
  //     val size${i} = peek(c.io.MemStreams.outPorts${i}.size).toInt
  //     val base${i} = peek(c.io.MemStreams.outPorts${i}.base).toInt
  //     val addr${i} = peek(c.io.MemStreams.outPorts${i}.addr).toInt
  //     val par${i} = ${interface._2}
  //     if (req${i}) {
  //       ${interface._4}
  //     }

  // """)
  //     }
  //   }

    super.emitFileFooter()
  }

}
