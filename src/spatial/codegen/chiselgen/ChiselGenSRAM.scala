package spatial.codegen.chiselgen

import argon.codegen.chiselgen.ChiselCodegen
import spatial.api.SRAMExp
import spatial.SpatialConfig
import spatial.SpatialExp


trait ChiselGenSRAM extends ChiselCodegen {
  val IR: SpatialExp
  import IR._

  private var nbufs: List[(Sym[SRAMNew[_]], Int)]  = List()

  override protected def remap(tp: Staged[_]): String = tp match {
    case tp: SRAMType[_] => src"Array[${tp.child}]"
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
            case BankedMemory(dims, depth, isAccum) =>
              val strides = s"""List(${dims.map(_.banks).mkString(",")})"""
              val numWriters = writersOf(lhs).filter{ write => dispatchOf(write, lhs) contains i }.distinct.length
              val numReaders = readersOf(lhs).filter{ read => dispatchOf(read, lhs) contains i }.distinct.length
              val width = bitWidth(lhs.tp.typeArguments.head)
              if (depth == 1) {
                open(src"""val ${lhs}_$i = Module(new SRAM(List(${dimensions.mkString(",")}), ${width}, """)
                emit(src"""List(${dims.map(_.banks).mkString(",")}), $strides,""")
                emit(src"""$numWriters, $numReaders, """)
                emit(src"""${dims.map(_.banks).reduce{_*_}}, ${dims.map(_.banks).reduce{_*_}}, "BankedMemory" // TODO: Be more precise with parallelizations """)
                close("))")
              } else {
                nbufs = nbufs :+ (lhs.asInstanceOf[Sym[SRAMNew[_]]], i)
                open(src"""val ${lhs}_$i = Module(new NBufSRAM(List(${dimensions.mkString(",")}), $depth, ${width},""")
                emit(src"""List(${dims.map(_.banks).mkString(",")}), $strides,""")
                emit(src"""$numWriters, $numReaders, """)
                emit(src"""${dims.map(_.banks).reduce{_*_}}, ${dims.map(_.banks).reduce{_*_}}, "BankedMemory" // TODO: Be more precise with parallelizations """)
                close("))")
              }
            case DiagonalMemory(strides, banks, depth, isAccum) =>
              throw new UnsupportedBankingType("Diagonal", lhs)
          }
        }
      }
    
    case SRAMLoad(sram, dims, is, ofs) =>
      val dispatch = dispatchOf(lhs, sram)
      val rPar = 1 // Because this is SRAMLoad node    
      val width = bitWidth(sram.tp.typeArguments.head)
      emit(s"""// Assemble multidimR vector""")
      dispatch.foreach{ i => 
        val parent = readersOf(sram).find{_.node == lhs}.get.ctrlNode
        val enable = src"""${parent}_en"""
        emit(src"""val ${lhs}_rVec = Wire(Vec(${rPar}, new multidimR(${dims.length}, ${width})))""")
        emit(src"""${lhs}_rVec(0).en := $enable""")
        is.zipWithIndex.foreach{ case(ind,j) => 
          emit(src"""${lhs}_rVec(0).addr($j) := ${ind}""")
        }
        val p = portsOf(lhs, sram, i).head
        emit(src"""${sram}_$i.connectRPort(Vec(${lhs}_rVec.toArray), $p)""")
        sram.tp.typeArguments.head match { 
          case FixPtType(s,d,f) => if (hasFracBits(sram.tp.typeArguments.head)) {
              emit(s"""val ${quote(lhs)} = Utils.FixedPoint($s,$d,$f, ${quote(sram)}_$i.io.output.data(${rPar}*$p))""")
            } else {
              emit(src"""val $lhs = ${sram}_$i.io.output.data(${rPar}*$p)""")
            }
          case _ => emit(src"""val $lhs = ${sram}_$i.io.output.data(${rPar}*$p)""")
        }
      }

    case SRAMStore(sram, dims, is, ofs, v, en) =>
      val width = bitWidth(sram.tp.typeArguments.head)
      emit(s"""// Assemble multidimW vector""")
      emit(src"""val ${lhs}_wVec = Wire(Vec(1, new multidimW(${dims.length}, ${width}))) """)
      sram.tp.typeArguments.head match { 
        case FixPtType(s,d,f) => if (hasFracBits(sram.tp.typeArguments.head)) {
            emit(src"""${lhs}_wVec(0).data := ${v}.number""")
          } else {
            emit(src"""${lhs}_wVec(0).data := ${v}""")
          }
        case _ => emit(src"""${lhs}_wVec(0).data := ${v}""")
      }
      emit(src"""${lhs}_wVec(0).en := ${en}""")
      is.zipWithIndex.foreach{ case(ind,j) => 
        emit(src"""${lhs}_wVec(0).addr($j) := ${ind}""")
      }
      duplicatesOf(sram).zipWithIndex.foreach{ case (mem, i) => 
        val p = portsOf(lhs, sram, i).mkString(",")
        val parent = writersOf(sram).find{_.node == lhs}.get.ctrlNode
        val enable = src"""${parent}_datapath_en"""
        emit(src"""${sram}_$i.connectWPort(${lhs}_wVec, ${enable}, List(${p})) """)
      }

    case _ => super.emitNode(lhs, rhs)
  }


  override protected def emitFileFooter() {
    withStream(getStream("BufferControlCxns")) {
      nbufs.foreach{ case (mem, i) => 

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
          emit(src"""${mem}_${i}.connectStageCtrl(${quote(node)}_done, ${quote(node)}_en, List(${port})) /*$rd $wr $empty*/""")
        }

      }
    }

    super.emitFileFooter()
  }
    
}
