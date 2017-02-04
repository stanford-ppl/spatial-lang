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
      offchipMems = offchipMems :+ lhs.asInstanceOf[Sym[Any]]
      open(src"val $lhs = {")
      open(src"$ctr.foreach{case (is,vs) => is.zip(vs).foreach{case ($i,v) => if (v) {")
      emit(src"$fifo.enqueue( $dram.apply($ofs + $i) )")
      close("}}}")
      close("}")

    case BurstStore(dram, fifo, ofs, ctr, i) =>
      offchipMems = offchipMems :+ lhs.asInstanceOf[Sym[Any]]
      open(src"val $lhs = {")
      open(src"$ctr.foreach{case (is,vs) => is.zip(vs).foreach{case ($i,v) => if (v) {")
      emit(src"$dram.update($ofs + $i, $fifo.dequeue() )")
      close("}}}")
      close("}")

    case _ => super.emitNode(lhs, rhs)
  }


  override protected def emitFileFooter() {

    withStream(getStream("IOModule")) {
      open(s"""  class MemStreamsBundle() extends Bundle{""")
      offchipMems.zipWithIndex.foreach{ case (port,i) => 
        val info = port match {
          case Def(BurstLoad(dram,_,_,_,p)) => 
            emit(s"// Burst Load")
            (s"${boundOf(p).toInt}", s"""${nameOf(dram).getOrElse("")}""")
          case Def(BurstStore(dram,_,_,_,p)) =>
            emit("// Burst Store")
            (s"${boundOf(p).toInt}", s"""${nameOf(dram).getOrElse("")}""")
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

    withStream(getStream("GeneratedPoker")) {
      offchipMems.zipWithIndex.foreach{case (port,i) => 
        val interface = port match {
          case Def(BurstLoad(mem,_,_,_,p)) => 
            ("receiveBurst", s"${boundOf(p).toInt}", "BurstLoad",
             s"""for (j <- 0 until size${i}) {
          (0 until par${i}).foreach { k => 
            val element = (addr${i}-base${i}+j*par${i}+k) % 256 // TODO: Should be loaded from CPU side
            poke(c.io.MemStreams.inPorts${i}.data(k), element) 
          }  
          poke(c.io.MemStreams.inPorts${i}.valid, 1)
          step(1)
          }
          poke(c.io.MemStreams.inPorts${i}.valid, 0)
          step(1)""", s"""${nameOf(mem)}.getOrElse("")}""")
          case Def(BurstStore(mem,_,_,_,p)) =>
            ("sendBurst", s"${boundOf(p).toInt}", "BurstStore",
             s"""for (j <- 0 until size${i}) {
          poke(c.io.MemStreams.inPorts${i}.pop, 1)
          (0 until par${i}).foreach { k => 
            offchipMem = offchipMem :+ peek(c.io.MemStreams.outPorts${i}.data(k)) 
          }  
          step(1)
          }
        poke(c.io.MemStreams.inPorts${i}.pop, 0)
        step(1)""", s"""${nameOf(mem)}.getOrElse("")}""")
        }
        emit(s"""
      // ${interface._3} Poker -- ${quote(port)} <> ports${i} <> ${interface._5}
      val req${i} = (peek(c.io.MemStreams.outPorts${i}.${interface._1}) == 1)
      val size${i} = peek(c.io.MemStreams.outPorts${i}.size).toInt
      val base${i} = peek(c.io.MemStreams.outPorts${i}.base).toInt
      val addr${i} = peek(c.io.MemStreams.outPorts${i}.addr).toInt
      val par${i} = ${interface._2}
      if (req${i}) {
        ${interface._4}
      }

  """)
      }
    }

    super.emitFileFooter()
  }

}
