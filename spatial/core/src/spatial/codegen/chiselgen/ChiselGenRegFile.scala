package spatial.codegen.chiselgen

import argon.core._
import argon.nodes._
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._
import spatial.SpatialConfig

trait ChiselGenRegFile extends ChiselGenSRAM {
  private var nbufs: List[(Sym[SRAM[_]], Int)]  = List()

  override def quote(s: Exp[_]): String = {
    if (SpatialConfig.enableNaming) {
      s match {
        case lhs: Sym[_] =>
          lhs match {
            case Def(e: RegFileNew[_,_]) =>
              s"""x${lhs.id}_${lhs.name.getOrElse("regfile")}"""
            case Def(e: LUTNew[_,_]) =>
              s"""x${lhs.id}_${lhs.name.getOrElse("lut")}"""
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
    case op@RegFileNew(dims, inits) =>
      val initVals = if (inits.isDefined) {
        getConstValues(inits.get).toList.map{a => src"${a}d"}.mkString(",")
      } else { "None"}
      
      val initString = if (inits.isDefined) src"Some(List(${initVals}))" else "None"
      val f = lhs.tp.typeArguments.head match {
        case a: FixPtType[_,_,_] => a.fracBits
        case _ => 0
      }
      val width = bitWidth(lhs.tp.typeArguments.head)
      duplicatesOf(lhs).zipWithIndex.foreach{ case (mem, i) => 
        val writerInfo = writersOf(lhs).zipWithIndex.map{ case (w,ii) => 
          val port = portsOf(w, lhs, i).head
          w.node match {
            case Def(_:RegFileStore[_]) => (port, 1, 1)
            case Def(_:RegFileShiftIn[_]) => (port, 1, 1)
            case Def(_@ParRegFileShiftIn(_,inds,d,data,en)) => (port, inds.length, data.tp.asInstanceOf[VectorType[_]].width) // Get stride
            case Def(_@ParRegFileStore(_,_,_,en)) => (port, en.length, 1)
          }
        }
        val parInfo = writerInfo.groupBy(_._1).map{case (k,v) => src"($k -> ${v.map{_._2}.reduce{_+_}})"}
        val stride = writerInfo.map(_._3).max
        val depth = mem match {
          case BankedMemory(dims, d, isAccum) => d
          case _ => 1
        }
        if (depth == 1) {
          emitGlobalModule(src"""val ${lhs}_$i = Module(new templates.ShiftRegFile(List(${getConstValues(dims)}), $initString, $stride, ${writerInfo.map{_._2}.reduce{_+_}}, false, $width, $f))""")
          emitGlobalModule(src"${lhs}_$i.io.dump_en := false.B")
        } else {
          nbufs = nbufs :+ (lhs.asInstanceOf[Sym[SRAM[_]]], i)
          emitGlobalModule(src"""val ${lhs}_$i = Module(new NBufShiftRegFile(List(${getConstValues(dims)}), $initString, $stride, $depth, Map(${parInfo.mkString(",")}), $width, $f))""")
        }
        resettersOf(lhs).indices.foreach{ ii => emitGlobalWire(src"""val ${lhs}_${i}_manual_reset_$ii = Wire(Bool())""")}
        if (resettersOf(lhs).length > 0) {
          emitGlobalModule(src"""val ${lhs}_${i}_manual_reset = ${resettersOf(lhs).indices.map{ii => src"${lhs}_${i}_manual_reset_$ii"}.mkString(" | ")}""")
          emitGlobalModule(src"""${lhs}_$i.io.reset := ${lhs}_${i}_manual_reset | reset.toBool""")
        } else {emitGlobalModule(src"${lhs}_$i.io.reset := reset.toBool")}

      }

    case RegFileReset(rf,en) => 
      val parent = parentOf(lhs).get
      val id = resettersOf(rf).map{_._1}.indexOf(lhs)
      duplicatesOf(rf).indices.foreach{i => emit(src"${rf}_${i}_manual_reset_$id := $en & ${swap(parent, DatapathEn)}.D(${symDelay(lhs)}) ")}
      
    case op@RegFileLoad(rf,inds,en) =>
      val dispatch = dispatchOf(lhs, rf).toList.head
      val port = portsOf(lhs, rf, dispatch).toList.head
      val addr = inds.map{i => src"${i}.r"}.mkString(",")
      emit(src"""val ${lhs} = Wire(${newWire(lhs.tp)})""")
      emit(src"""${lhs}.r := ${rf}_${dispatch}.readValue(List($addr), $port)""")

    case op@RegFileStore(rf,inds,data,en) =>
      val width = bitWidth(rf.tp.typeArguments.head)
      val parent = writersOf(rf).find{_.node == lhs}.get.ctrlNode
      val enable = src"""${swap(parent, DatapathEn)} & ~${parent}_inhibitor & ${swap(parent, IIDone)}"""
      emit(s"""// Assemble multidimW vector""")
      emit(src"""val ${lhs}_wVec = Wire(Vec(1, new multidimRegW(${inds.length}, List(${constDimsOf(rf)}), ${width}))) """)
      emit(src"""${lhs}_wVec(0).data := ${data}.r""")
      emit(src"""${lhs}_wVec(0).en := ${en} & (${enable}).D(${symDelay(lhs)})""")
      inds.zipWithIndex.foreach{ case(ind,j) => 
        emit(src"""${lhs}_wVec(0).addr($j) := ${ind}.r // Assume always an int""")
      }
      emit(src"""${lhs}_wVec(0).shiftEn := false.B""")
      duplicatesOf(rf).zipWithIndex.foreach{ case (mem, i) =>
        emit(src"""${rf}_$i.connectWPort(${lhs}_wVec, List(${portsOf(lhs, rf, i)})) """)
      }


    case RegFileShiftIn(rf,inds,d,data,en)    => 
      val width = bitWidth(rf.tp.typeArguments.head)
      val parent = writersOf(rf).find{_.node == lhs}.get.ctrlNode
      val enable = src"""${swap(parent, DatapathEn)} & ~${parent}_inhibitor"""
      emit(s"""// Assemble multidimW vector""")
      emit(src"""val ${lhs}_wVec = Wire(Vec(1, new multidimRegW(${inds.length}, List(${constDimsOf(rf)}), ${width}))) """)
      emit(src"""${lhs}_wVec(0).data := ${data}.r""")
      emit(src"""${lhs}_wVec(0).shiftEn := ${en} & (${enable}).D(${symDelay(lhs)})""")
      inds.zipWithIndex.foreach{ case(ind,j) => 
        emit(src"""${lhs}_wVec(0).addr($j) := ${ind}.r // Assume always an int""")
      }
      emit(src"""${lhs}_wVec(0).en := false.B""")
      duplicatesOf(rf).zipWithIndex.foreach{ case (mem, i) =>
        emit(src"""${rf}_$i.connectShiftPort(${lhs}_wVec, List(${portsOf(lhs, rf, i)})) """)
      }

    case ParRegFileShiftIn(rf,inds,d,data,en) => 
      val width = bitWidth(rf.tp.typeArguments.head)
      val parent = writersOf(rf).find{_.node == lhs}.get.ctrlNode
      val enable = src"""${swap(parent, DatapathEn)} & ~${parent}_inhibitor"""
      emit(s"""// Assemble multidimW vectors""")
      emit(src"""val ${lhs}_wVec = Wire(Vec(${inds.length}, new multidimRegW(${inds.length}, List(${constDimsOf(rf)}), ${width}))) """)
      open(src"""for (${lhs}_i <- 0 until ${data}.length) {""")
        emit(src"""${lhs}_wVec(${lhs}_i).data := ${data}(${lhs}_i).r""")
        emit(src"""${lhs}_wVec(${lhs}_i).shiftEn := ${en} & (${enable}).D(${symDelay(lhs)})""")
        inds.zipWithIndex.foreach{ case(ind,j) => 
          emit(src"""${lhs}_wVec(${lhs}_i).addr($j) := ${ind}.r // Assume always an int""")
        }
        emit(src"""${lhs}_wVec(${lhs}_i).en := false.B""")
      close(src"}")
      duplicatesOf(rf).zipWithIndex.foreach{ case (mem, i) =>
        emit(src"""${rf}_$i.connectShiftPort(${lhs}_wVec, List(${portsOf(lhs, rf, i)})) """)
      }

    case op@LUTNew(dims, init) =>
      val width = bitWidth(lhs.tp.typeArguments.head)
      val f = lhs.tp.typeArguments.head match {
        case a: FixPtType[_,_,_] => a.fracBits
        case _ => 0
      }
      val lut_consts = getConstValues(init).toList.map{a => src"${a}d"}.mkString(",")
      duplicatesOf(lhs).zipWithIndex.foreach{ case (mem, i) => 
        val numReaders = readersOf(lhs).filter{read => dispatchOf(read, lhs) contains i}.length
        emitGlobalModule(src"""val ${lhs}_$i = Module(new LUT(List($dims), List(${lut_consts}), ${numReaders}, $width, $f))""")
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
      emit(src"""val ${lhs}_id = ${lut}_${dispatch}.connectRPort(List(${inds.map{a => src"${a}.r"}}), $en & ${swap(parent, DatapathEn)}.D(${symDelay(lhs)}))""")
      emit(src"""${lhs}.raw := ${lut}_${dispatch}.io.data_out(${lhs}_id).raw""")

    case op@VarRegNew(init)    => 
    case VarRegRead(reg)       => 
    case VarRegWrite(reg,v,en) => 
    case Print(x)   => 
    case Println(x) => 
    case PrintlnIf(_,_) =>
    case BreakpointIf(_)       => ()
    case ExitIf(_)       => ()            

    case _ => super.emitNode(lhs, rhs)
  }


  override protected def emitFileFooter() {
    withStream(getStream("BufferControlCxns")) {
      nbufs.foreach{ case (mem, i) => 
        val info = bufferControlInfo(mem, i)
        info.zipWithIndex.foreach{ case (inf, port) => 
          emit(src"""${mem}_${i}.connectStageCtrl(${swap(quote(inf._1), Done)}.D(1,rr), ${swap(quote(inf._1), BaseEn)}, List(${port})) ${inf._2}""")
        }

      }
    }

    super.emitFileFooter()
  }

}
