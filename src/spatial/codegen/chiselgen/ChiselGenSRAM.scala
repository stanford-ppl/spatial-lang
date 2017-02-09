package spatial.codegen.chiselgen

import argon.codegen.chiselgen.ChiselCodegen
import spatial.api.SRAMExp
import spatial.SpatialConfig
import spatial.SpatialExp


trait ChiselGenSRAM extends ChiselCodegen {
  val IR: SRAMExp with SpatialExp
  import IR._

  private var nbufs: List[(Sym[SRAMNew[_]], Int)]  = List()

  override protected def remap(tp: Staged[_]): String = tp match {
    case tp: SRAMType[_] => src"Array[${tp.bits}]"
    case _ => super.remap(tp)
  }

  override def quote(s: Exp[_]): String = {
    if (SpatialConfig.enableNaming) {
      s match {
        case lhs: Sym[_] =>
          val Op(rhs) = lhs
          rhs match {
            case SRAMNew(dims)=> 
              s"x${lhs.id}_sram"
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

  def flattenAddress(dims: Seq[Exp[Index]], indices: Seq[Exp[Index]], ofs: Option[Exp[Index]]): String = {
    val strides = List.tabulate(dims.length){i => (dims.drop(i+1).map(quote) :+ "1").mkString("*") }
    indices.zip(strides).map{case (i,s) => src"$i*$s" }.mkString(" + ") + ofs.map{o => src" + $o"}.getOrElse("")
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op@SRAMNew(dimensions) => 
      withStream(getStream("GlobalWires")) {
        duplicatesOf(lhs).zipWithIndex.foreach{ case (mem, i) => 
          mem match {
            case BankedMemory(dims, depth) =>
              val strides = s"""List(${dims.map{ d => d match {
                case StridedBanking(_, s) => s
                case _ => 1
              }}.mkString(",")})"""
              val numWriters = writersOf(lhs).map{access => portsOf(access, lhs, i)}.distinct.length // Count writers accessing this port
              val numReaders = readersOf(lhs).map{access => portsOf(access, lhs, i)}.distinct.length // Count writers accessing this port
              if (depth == 1) {
                open(src"""val ${lhs}_$i = Module(new SRAM(List(${dimensions.mkString(",")}), 32, """)
                emit(src"""List(${dims.map(_.banks).mkString(",")}), $strides,""")
                emit(src"""$numWriters, $numReaders, """)
                emit(src"""${dims.map(_.banks).reduce{_*_}}, ${dims.map(_.banks).reduce{_*_}}, "BankedMemory" // TODO: Be more precise with parallelizations """)
                close("))")
              } else {
                nbufs = nbufs :+ (lhs.asInstanceOf[Sym[SRAMNew[_]]], i)
                open(src"""val ${lhs}_$i = Module(new NBufSRAM(List(${dimensions.mkString(",")}), $depth, 32,""")
                emit(src"""List(${dims.map(_.banks).mkString(",")}), $strides,""")
                emit(src"""$numWriters, $numReaders, """)
                emit(src"""${dims.map(_.banks).reduce{_*_}}, ${dims.map(_.banks).reduce{_*_}}, "BankedMemory" // TODO: Be more precise with parallelizations """)
                close("))")
              }
            case DiagonalMemory(strides, banks, depth) => 
              Console.println(s"NOT SUPPORTED, MAKE EXCEPTION FOR THIS!")
          }
        }
      }
    
    case SRAMLoad(sram, dims, is, ofs) =>
      val dispatch = dispatchOf(lhs, sram)
      val rPar = 1 // Because this is SRAMLoad node    
      emit(s"""// Assemble multidimR vector""")
      dispatch.foreach{ i => 
        val parent = readersOf(sram).find{_.node == lhs}.get.ctrlNode
        val enable = src"""${parent}_en"""
        emit(src"""val ${lhs}_rVec = Wire(Vec(${rPar}, new multidimR(${dims.length}, 32)))""")
        emit(src"""${lhs}_rVec(0).en := $enable""")
        is.zipWithIndex.foreach{ case(ind,j) => 
          emit(src"""${lhs}_rVec(0).addr($j) := ${ind}""")
        }
        val p = portsOf(lhs, sram, i).head
        emit(src"""${sram}_$i.connectRPort(Vec(${lhs}_rVec.toArray), $p)""")
        emit(src"""val $lhs = ${sram}_$i.io.output.data(${rPar}*$p)""")
      }

    case SRAMStore(sram, dims, is, ofs, v, en) =>
      emit(s"""// Assemble multidimW vector""")
      emit(src"""val ${lhs}_wVec = Wire(Vec(1, new multidimW(${dims.length}, 32))) """)
      emit(src"""${lhs}_wVec(0).data := ${v}""")
      emit(src"""${lhs}_wVec(0).en := ${en}""")
      is.zipWithIndex.foreach{ case(ind,j) => 
        emit(src"""${lhs}_wVec(0).addr($j) := ${ind}""")
      }
      duplicatesOf(sram).zipWithIndex.foreach{ case (mem, i) => 
        val p = portsOf(lhs, sram, i).mkString(",")
        val parent = writersOf(sram).find{_.node == lhs}.get.ctrlNode
        val enable = src"""${parent}_en"""
        emit(src"""${sram}_$i.connectWPort(${lhs}_wVec, ${enable}, List(${p})) """)
      }

    case _ => super.emitNode(lhs, rhs)
  }


  override protected def emitFileFooter() {
    withStream(getStream("BufferControlCxns")) {
      nbufs.foreach{ case (mem, i) => 
        // TODO: Does david figure out which controllers' signals connect to which ports on the nbuf already? This is kind of complicated
        val readers = readersOf(mem)
        val writers = writersOf(mem)
        val readPorts = readers.filter{reader => dispatchOf(reader, mem).contains(i) }.groupBy{a => portsOf(a, mem, i) }
        val writePorts = writers.filter{writer => dispatchOf(writer, mem).contains(i) }.groupBy{a => portsOf(a, mem, i) }
        val allSiblings = childrenOf(parentOf(readPorts.map{case (_, readers) => readers.flatMap{a => topControllerOf(a,mem,i)}.head}.head.node).get)
        val readSiblings = readPorts.map{case (_,r) => r.flatMap{ a => topControllerOf(a, mem, i)}}.filter{case l => l.length > 0}.map{case all => all.head.node}
        val writeSiblings = writePorts.map{case (_,r) => r.flatMap{ a => topControllerOf(a, mem, i)}}.filter{case l => l.length > 0}.map{case all => all.head.node}
        val writePortsNumbers = writeSiblings.map{ sw => allSiblings.indexOf(sw) }
        val readPortsNumbers = readSiblings.map{ sr => allSiblings.indexOf(sr) }
        val firstActivePort = math.min( readPortsNumbers.min, writePortsNumbers.min )
        val lastActivePort = math.max( readPortsNumbers.max, writePortsNumbers.max )
        val numStagesInbetween = lastActivePort - firstActivePort

        (0 to numStagesInbetween).foreach { port =>
          val ctrlId = port + firstActivePort
          val node = allSiblings(ctrlId)
          val rd = if (readPortsNumbers.toList.contains(ctrlId)) {"read"} else ""
          val wr = if (writePortsNumbers.toList.contains(ctrlId)) {"write"} else ""
          val empty = if (rd == "" & wr == "") "empty" else ""
          emit(src"""${mem}_$i.connectStageCtrl(${quote(node)}_done, ${quote(node)}_en, List(${port})) /*$rd $wr $empty*/""")
        }
      }
    }
    
    super.emitFileFooter()
  }
    
}
