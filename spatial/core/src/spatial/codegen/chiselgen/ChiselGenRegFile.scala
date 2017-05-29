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
          emitGlobalModule(s"""val ${quote(lhs)}_$i = Module(new templates.ShiftRegFile(List(${dims.mkString(",")}), 1, ${par}, false, $width))""")
          emitGlobalModule(s"${quote(lhs)}_$i.io.reset := reset")
          emitGlobalModule(s"${quote(lhs)}_$i.io.dump_en := false.B")
        } else {
          nbufs = nbufs :+ (lhs.asInstanceOf[Sym[SRAM[_]]], i)
          emitGlobalModule(s"""val ${quote(lhs)}_$i = Module(new templates.NBufShiftRegFile(List(${dims.mkString(",")}), 1, $depth, ${par}, $width))""")
          emitGlobalModule(s"${quote(lhs)}_$i.io.reset := reset")          
        }
      }
      
    case op@RegFileLoad(rf,inds,en) =>
      val dispatch = dispatchOf(lhs, rf).toList.head
      val port = portsOf(lhs, rf, dispatch).toList.head
      val addr = inds.map{i => src"${i}.r"}.mkString(",")
      emit(src"""val ${lhs} = Wire(${newWire(lhs.tp)})""")
      emit(src"""${lhs}.r := ${rf}_${dispatch}.readValue(List($addr), $port)""")

    case op@RegFileStore(rf,inds,data,en) =>
      duplicatesOf(rf).zipWithIndex.foreach{ case (mem, i) => 
        val width = bitWidth(rf.tp.typeArguments.head)
        val port = portsOf(lhs, rf, i)
        val parent = writersOf(rf).find{_.node == lhs}.get.ctrlNode
        val enable = src"""${parent}_datapath_en & ~${parent}_inhibitor"""
        emit(s"""// Assemble multidimW vector""")
        emit(src"""val ${lhs}_wVec = Wire(Vec(1, new multidimRegW(${inds.length}, ${width}))) """)
        emit(src"""${lhs}_wVec(0).data := ${data}.r""")
        emit(src"""${lhs}_wVec(0).en := ${en} & ${enable}.D(${symDelay(lhs)})""")
        inds.zipWithIndex.foreach{ case(ind,j) => 
          emit(src"""${lhs}_wVec(0).addr($j) := ${ind}.r // Assume always an int""")
        }
        emit(src"""${lhs}_wVec(0).shiftEn := false.B""")
        duplicatesOf(rf).zipWithIndex.foreach{ case (mem, i) => 
          val p = portsOf(lhs, rf, i).mkString(",")
          emit(src"""${rf}_$i.connectWPort(${lhs}_wVec, List(${p})) """)
        }

      }

    case RegFileShiftIn(rf,inds,d,data,en)    => 
      duplicatesOf(rf).zipWithIndex.foreach{ case (mem, i) => 
        val width = bitWidth(rf.tp.typeArguments.head)
        val port = portsOf(lhs, rf, i)
        val parent = writersOf(rf).find{_.node == lhs}.get.ctrlNode
        val enable = src"""${parent}_datapath_en & ~${parent}_inhibitor"""
        emit(s"""// Assemble multidimW vector""")
        emit(src"""val ${lhs}_wVec = Wire(Vec(1, new multidimRegW(${inds.length}, ${width}))) """)
        emit(src"""${lhs}_wVec(0).data := ${data}.r""")
        emit(src"""${lhs}_wVec(0).shiftEn := ${en} & ${enable}.D(${symDelay(lhs)})""")
        inds.zipWithIndex.foreach{ case(ind,j) => 
          emit(src"""${lhs}_wVec(0).addr($j) := ${ind}.r // Assume always an int""")
        }
        emit(src"""${lhs}_wVec(0).en := false.B""")
        duplicatesOf(rf).zipWithIndex.foreach{ case (mem, i) => 
          val p = portsOf(lhs, rf, i).mkString(",")
          emit(src"""${rf}_$i.connectShiftPort(${lhs}_wVec, List(${p})) """)
        }

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
      emit(src"""val ${lhs}_id = ${lut}_${dispatch}.connectRPort(List(${inds.map{a => src"${a}.r"}.mkString(",")}), $en & ${parent}_datapath_en.D(${symDelay(lhs)}))""")
      emit(src"""${lhs}.raw := ${lut}_${dispatch}.io.data_out(${lhs}_id).raw""")

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
