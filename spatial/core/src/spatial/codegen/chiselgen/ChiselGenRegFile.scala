package spatial.codegen.chiselgen

import argon.codegen.chiselgen.{ChiselCodegen}
import spatial.api.RegisterFileExp
import spatial.SpatialConfig
import spatial.SpatialExp

trait ChiselGenRegFile extends ChiselGenSRAM {
  val IR: SpatialExp
  import IR._

  private var nbufs: List[(Sym[SRAM[_]], Int)]  = List()

  override def quote(s: Exp[_]): String = {
    if (SpatialConfig.enableNaming) {
      s match {
        case lhs: Sym[_] =>
          lhs match {
            case Def(e: RegFileNew[_,_]) =>
              s"""x${lhs.id}_${nameOf(lhs).getOrElse("regfile")}"""
            case Def(e: LUTNew[_,_]) =>
              s"""x${lhs.id}_${nameOf(lhs).getOrElse("lut")}"""
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

  override protected def remap(tp: Type[_]): String = tp match {
    case tp: RegFileType[_] => src"Array[${tp.child}]"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op@RegFileNew(dims) =>
      val width = bitWidth(lhs.tp.typeArguments.head)
      val par = writersOf(lhs).length
      duplicatesOf(lhs).zipWithIndex.foreach{ case (mem, i) => 
        val depth = mem match {
          case BankedMemory(dims, d, isAccum) => d
          case _ => 1
        }
        if (depth == 1) {
          emitGlobalModule(s"val ${quote(lhs)}_$i = Module(new templates.ShiftRegFile(${dims(0)}, ${dims(1)}, 1, ${par}/${dims(0)}, false, $width))")
          emitGlobalModule(s"${quote(lhs)}_$i.io.reset := reset")
        } else {
          nbufs = nbufs :+ (lhs.asInstanceOf[Sym[SRAM[_]]], i)
          emitGlobalModule(s"val ${quote(lhs)}_$i = Module(new templates.NBufShiftRegFile(${dims(0)}, ${dims(1)}, 1, $depth, ${par}/${dims(0)}, $width))")
          emitGlobalModule(s"${quote(lhs)}_$i.io.reset := reset")          
        }
      }
      
    case op@RegFileLoad(rf,inds,en) =>
      val dispatch = dispatchOf(lhs, rf).toList.head
      val port = portsOf(lhs, rf, dispatch).toList.head
      emit(src"""val ${lhs} = Wire(${newWire(lhs.tp)})""")
      emit(src"""${lhs}.r := ${rf}_${dispatch}.readValue(${inds(0)}.raw, ${inds(1)}.raw, $port)""")

    case op@RegFileStore(rf,inds,data,en) =>
      duplicatesOf(rf).zipWithIndex.foreach{ case (mem, i) => 
        val port = portsOf(lhs, rf, i)
        val parent = writersOf(rf).find{_.node == lhs}.get.ctrlNode
        emit(s"""${quote(rf)}_${i}.connectWPort(${quote(data)}.raw, ${quote(inds(0))}.raw, ${quote(inds(1))}.raw, ${quote(en)} & (${quote(parent)}_datapath_en & ~${quote(parent)}_inhibitor).D(${symDelay(lhs)}), List(${port.toList.mkString(",")}))""")
      }

    case RegFileShiftIn(rf,inds,d,data,en)    => 
      duplicatesOf(rf).zipWithIndex.foreach{ case (mem, i) => 
        val port = portsOf(lhs, rf, i)
        val parent = writersOf(rf).find{_.node == lhs}.get.ctrlNode
        emit(s"""${quote(rf)}_${i}.connectShiftPort(${quote(data)}.raw, ${quote(inds(0))}.raw, ${quote(en)} & (${quote(parent)}_datapath_en & ~${quote(parent)}_inhibitor).D(${symDelay(lhs)}), List(${port.toList.mkString(",")}))""")
      }

    case ParRegFileShiftIn(rf,i,d,data,en) => 
      emit("ParRegFileShiftIn not implemented!")
      // (copied from ScalaGen) shiftIn(lhs, rf, i, d, data, isVec = true)

    case op@LUTNew(dims, init) =>
      val width = bitWidth(lhs.tp.typeArguments.head)
      val f = lhs.tp.typeArguments.head match {
        case a: FixPtType[_,_,_] => a.fracBits
        case _ => 0
      }
      duplicatesOf(lhs).zipWithIndex.foreach{ case (mem, i) => 
        val numReaders = readersOf(lhs).filter{read => dispatchOf(read, lhs) contains i}.length
        emitGlobalModule(s"""val ${quote(lhs)}_$i = Module(new LUT(List(${dims.mkString(",")}), List(${init.mkString(",")}), ${numReaders}, $width, $f))""")
      }
        // } else {
        //   nbufs = nbufs :+ (lhs.asInstanceOf[Sym[SRAM[_]]], i)
        //   emitGlobalModule(s"val ${quote(lhs)}_$i = Module(new templates.NBufShiftRegFile(${dims(0)}, ${dims(1)}, 1, $depth, ${par}/${dims(0)}, $width))")
        //   emitGlobalModule(s"${quote(lhs)}_$i.io.reset := reset")          
        // }

      
    case op@LUTLoad(lut,inds,en) =>
      val dispatch = dispatchOf(lhs, lut).toList.head
      emit(src"""val ${lhs} = Wire(${newWire(lhs.tp)})""")
      val parent = parentOf(lhs).get
      emit(src"""${lut}_${dispatch}.connectRPort(List(${inds.map{a => src"${a}.r"}.mkString(",")}), $en & ${parent}_datapath_en.D(${symDelay(lhs)}))""")
      emit(src"""${lhs}.raw := ${lut}_${dispatch}.io.data_out.raw""")

    case _ => super.emitNode(lhs, rhs)
  }


  override protected def emitFileFooter() {
    withStream(getStream("BufferControlCxns")) {
      nbufs.foreach{ case (mem, i) => 
        val info = bufferControlInfo(mem, i)
        info.zipWithIndex.foreach{ case (inf, port) => 
          emit(src"""${mem}_${i}.connectStageCtrl(${quote(inf._1)}_done, ${quote(inf._1)}_base_en, List(${port})) ${inf._2}""")
        }

      }
    }

    super.emitFileFooter()
  }

}
