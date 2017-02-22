package spatial.codegen.chiselgen

import spatial.SpatialConfig
import spatial.SpatialExp


trait ChiselGenDRAM extends ChiselGenSRAM {
  val IR: SpatialExp
  import IR._

  var offchipMems: List[Sym[Any]] = List()

  override def quote(s: Exp[_]): String = {
    if (SpatialConfig.enableNaming) {
      s match {
        case lhs: Sym[_] =>
          val Op(rhs) = lhs
          rhs match {
            case e: Gather[_]=> 
              s"x${lhs.id}_gath"
            case e: Scatter[_] =>
              s"x${lhs.id}_scat"
            case e: BurstLoad[_] =>
              s"x${lhs.id}_load"
            case e: BurstStore[_] =>
              s"x${lhs.id}_store"
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
    case op@DRAMNew(dims) => emit(src"""val $lhs = new Array[${op.mA}](${dims.map(quote).mkString("*")})""")
    case Gather(dram, local, addrs, ctr, i)  =>
      open(src"val $lhs = {")
      open(src"$ctr.foreach{case (is,vs) => is.zip(vs).foreach{case ($i,v) => if (v) {")
      emit(src"$local.update($i, $dram.apply($addrs.apply($i)) )")
      close("}}}")
      close("}")
    case Scatter(dram, local, addrs, ctr, i) =>
      open(src"val $lhs = {")
      open(src"$ctr.foreach{case (is,vs) => is.zip(vs).foreach{case ($i,v) => if (v) {")
      emit(src"$dram.update($addrs.apply($i), $local.apply($i))")
      close("}}}")
      close("}")

    case BurstLoad(dram, fifo, ofs, ctr, i)  =>
      val (start,stop,stride,p) = ctr match { case Def(CounterNew(s1,s2,s3,par)) => (s1,s2,s3,par); case _ => (1,1,1,1) }
      val streamId = offchipMems.length
      offchipMems = offchipMems :+ lhs.asInstanceOf[Sym[Any]]
      emitGlobal(src"""val ${lhs} = Module(new MemController(${p}))""".replace(".U",""))
      emitGlobal(src"""io.MemStreams.outPorts${streamId} := ${lhs}.io.CtrlToDRAM""")
      emitGlobal(src"""${lhs}.io.DRAMToCtrl := io.MemStreams.inPorts${streamId} """)
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

    case _ => super.emitNode(lhs, rhs)
  }


  override protected def emitFileFooter() {

    withStream(getStream("IOModule")) {
      open(s"""class MemStreamsBundle() extends Bundle{""")
      offchipMems.zipWithIndex.foreach{ case (port,i) => 
        val info = port match {
          case Def(BurstLoad(dram,_,_,ctr,_)) => 
            emit(s"// Burst Load")
            val p = ctr match { case Def(CounterNew(_,_,_,par)) => par; case _ => 1 }
            (s"${p}", s"""${nameOf(dram).getOrElse("")}""")
          case Def(BurstStore(dram,_,_,ctr,_)) =>
            val p = ctr match { case Def(CounterNew(_,_,_,par)) => par; case _ => 1 }
            emit("// Burst Store")
            (s"${p}", s"""${nameOf(dram).getOrElse("")}""")
          case _ => 
            ("No match", s"No mem")
        }
        emit(s"""  val outPorts${i} = Output(new ToDRAM(${info._1}))
    val inPorts${i} = Input(new FromDRAM(${info._1}))""")
        emit(s"""    //  ${quote(port)} = ports$i (${info._2})
  """)
        // offchipMemsByName = offchipMemsByName :+ s"${quote(port)}"
      }

      close("}")
    }

//    withStream(getStream("GeneratedPoker")) {
//      offchipMems.zipWithIndex.foreach{case (port,i) => 
//        val interface = port match {
//          case Def(BurstLoad(mem,_,_,ctr,_)) => 
//            val p = ctr match { case Def(CounterNew(_,_,_,par)) => par; case _ => 1 }
//            ("receiveBurst", s"${p}", "BurstLoad",
//             s"""for (j <- 0 until size${i}) {
//          (0 until par${i}).foreach { k => 
//            val element = (addr${i}-base${i}+j*par${i}+k) % 256 // TODO: Should be loaded from CPU side
//            poke(c.io.MemStreams.inPorts${i}.data(k), element) 
//          }  
//          poke(c.io.MemStreams.inPorts${i}.valid, 1)
//          step(1)
//          }
//          poke(c.io.MemStreams.inPorts${i}.valid, 0)
//          step(1)""", s"""${nameOf(mem)}.getOrElse("")}""")
//          case Def(BurstStore(mem,_,_,ctr,_)) =>
//            val p = ctr match { case Def(CounterNew(_,_,_,par)) => par; case _ => 1 }
//            ("sendBurst", s"${p}", "BurstStore",
//             s"""for (j <- 0 until size${i}) {
//          poke(c.io.MemStreams.inPorts${i}.pop, 1)
//          (0 until par${i}).foreach { k => 
//            offchipMem = offchipMem :+ peek(c.io.MemStreams.outPorts${i}.data(k)) 
//          }  
//          step(1)
//          }
//        poke(c.io.MemStreams.inPorts${i}.pop, 0)
//        step(1)""", s"""${nameOf(mem)}.getOrElse("")}""")
//        }
//        emit(s"""
//      // ${interface._3} Poker -- ${quote(port)} <> ports${i} <> ${interface._5}
//      val req${i} = (peek(c.io.MemStreams.outPorts${i}.${interface._1}) == 1)
//      val size${i} = peek(c.io.MemStreams.outPorts${i}.size).toInt
//      val base${i} = peek(c.io.MemStreams.outPorts${i}.base).toInt
//      val addr${i} = peek(c.io.MemStreams.outPorts${i}.addr).toInt
//      val par${i} = ${interface._2}
//      if (req${i}) {
//        ${interface._4}
//      }
//
//  """)
//      }
//    }

    super.emitFileFooter()
  }

}
