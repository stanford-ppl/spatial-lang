package spatial.codegen.chiselgen

import argon.core._
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._
import spatial.SpatialConfig


trait ChiselGenLineBuffer extends ChiselGenController {
  private var linebufs: List[(Sym[LineBufferNew[_]], Int)]  = List()

  override def quote(s: Exp[_]): String = {
    if (SpatialConfig.enableNaming) {
      s match {
        case lhs: Sym[_] =>
          lhs match {
            case Def(e: LineBufferNew[_]) =>
              s"""x${lhs.id}_${lhs.name.getOrElse("linebuf")}"""
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
    case tp: LineBufferType[_] => src"LineBuffer[${tp.child}]"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    // TODO: Need to account for stride here
    case op@LineBufferNew(rows, cols, stride) =>
      duplicatesOf(lhs).zipWithIndex.foreach{ case (mem, i) => 
        val col_rPar = readersOf(lhs) // Currently assumes all readers have same par
          .filter{read => dispatchOf(read, lhs) contains i}
          .map { r => 
            val par = r.node match {
              case Def(_: LineBufferLoad[_]) => 1
              case Def(a@ParLineBufferLoad(_,rows,cols,ens)) => cols.distinct.length
              case Def(LineBufferColSlice(_,_,_,Exact(len))) => len.toInt
            }
            par
          }.head
        val col_wPar = writersOf(lhs) // Currently assumes all readers have same par
          .filter{write => dispatchOf(write, lhs) contains i}
          .filter{a => !isTransient(a.node.asInstanceOf[Exp[_]])}
          .map { w => 
            val par = w.node match {
              case Def(_: LineBufferEnq[_]) => 1
              case Def(a@ParLineBufferEnq(_,_,ens)) => ens.length
              case Def(_: LineBufferRotateEnq[_]) => 1
              case Def(op@ParLineBufferRotateEnq(lb,row,data,ens)) => ens.length
            }
            par
          }.head
        val transient_wPar = writersOf(lhs) // Currently assumes all readers have same par
          .filter{write => dispatchOf(write, lhs) contains i}
          .filter{a => isTransient(a.node.asInstanceOf[Exp[_]])}
          .map { w => 
            val par = w.node match {
              case Def(_: LineBufferEnq[_]) => 1
              case Def(a@ParLineBufferEnq(_,_,ens)) => ens.length
              case Def(_: LineBufferRotateEnq[_]) => 1
              case Def(op@ParLineBufferRotateEnq(lb,row,data,ens)) => ens.length
            }
            par
          }.sum // Assumes there is either 0 of these (returns 0) or 1 of these

        val col_banks = mem match { case BankedMemory(dims, depth, isAccum) => dims.last.banks; case _ => 1 }
        // rows to buffer is 1 + number of blank stages between the write and the read (i.e.- 1 + buffer_info - 2 )
        val empty_stages_to_buffer = bufferControlInfo(lhs, i).length - 1
        val row_rPar = mem match { case BankedMemory(dims, depth, isAccum) => dims.head.banks; case _ => 1 } // Could be wrong
        val accessors = bufferControlInfo(lhs, 0).length
        val row_wPar = 1 // TODO: Do correct analysis here!
        val width = bitWidth(lhs.tp.typeArguments.head)
        emitGlobalModule(src"""val ${lhs}_$i = Module(new templates.LineBuffer(${getConstValue(rows)}, ${getConstValue(cols)}, ${empty_stages_to_buffer}, ${getConstValue(stride)},
          ${col_wPar}, ${col_rPar}, ${col_banks},
          ${row_wPar}, ${row_rPar}, ${transient_wPar}, $accessors, $width))  // Data type: ${op.mT}""")
        emitGlobalModule(src"${lhs}_$i.io.reset := reset")
        linebufs = linebufs :+ (lhs.asInstanceOf[Sym[LineBufferNew[_]]], i)
      }

    case op@LineBufferRotateEnq(lb,row,data,en) =>
      throw new Exception(src"Non-parallelized LineBufferRotateEnq not implemented yet!  It isn't hard, just ask matt to do it")
      val dispatch = dispatchOf(lhs, lb).toList.distinct
      if (dispatch.length > 1) { throw new Exception(src"This is an example where lb dispatch > 1. Please use as test case! (node $lhs on lb $lb)") }
      val i = dispatch.head
      val parent = writersOf(lb).find{_.node == lhs}.get.ctrlNode
      emit(src"${lb}_$i.io.data_in(${row}) := ${data}.raw")
      emit(src"${lb}_$i.io.w_en(${row}) := $en & ShiftRegister(${parent}_datapath_en & ~${parent}_inhibitor, ${symDelay(lhs)})")


    case op@ParLineBufferRotateEnq(lb,row,data,ens) =>
      if (!isTransient(lhs)) {
        val dispatch = dispatchOf(lhs, lb).toList.distinct
        val stride = lb match {case Def(LineBufferNew(_,_,Exact(s))) => s.toInt}
        if (dispatch.length > 1) { throw new Exception(src"This is an example where lb dispatch > 1. Please use as test case! (node $lhs on lb $lb)") }
        val ii = dispatch.head
        val parent = writersOf(lb).find{_.node == lhs}.get.ctrlNode
        (0 until stride).foreach{r => 
          data.zipWithIndex.foreach { case (d, i) =>
            emit(src"${lb}_$ii.io.data_in(${r} * ${data.length} + $i) := ${d}.raw")
          }
          emit(src"""${lb}_$ii.io.w_en($r) := ${ens.map{en => src"$en"}.mkString("&")} & (${parent}_datapath_en & ~${parent}_inhibitor).D(${symDelay(lhs)}, rr) & ${row} === ${r}.U(${row}.getWidth.W)""")
        }        
      } else {
        val dispatch = dispatchOf(lhs, lb).toList.distinct
        if (dispatch.length > 1) { throw new Exception(src"This is an example where lb dispatch > 1. Please use as test case! (node $lhs on lb $lb)") }
        val ii = dispatch.head
        val parent = writersOf(lb).find{_.node == lhs}.get.ctrlNode
        emit(src"""val ${lb}_${ii}_transient_base = ${lb}_$ii.col_wPar*${lb}_$ii.rstride""")
        data.zipWithIndex.foreach { case (d, i) =>
          emit(src"${lb}_$ii.io.data_in(${lb}_${ii}_transient_base + $i) := ${d}.raw")
        }
        emit(src"""${lb}_$ii.io.w_en(${lb}_$ii.rstride) := ${ens.map{en => src"$en"}.mkString("&")} & (${parent}_datapath_en & ~${parent}_inhibitor).D(${symDelay(lhs)}, rr)""")
        emit(src"""${lb}_$ii.io.transientDone := ${parent}_done""")
        emit(src"""${lb}_$ii.io.transientSwap := ${parentOf(parent).get}_done""")
      }
        
    case op@LineBufferRowSlice(lb,row,len,col) =>
      // TODO: Multiple cycles
      // Copied from ScalaGen:
      // open(src"val $lhs = Array.tabulate($len){i => ")
        // oobApply(op.mT, lb, lhs, Seq(row,col)){ emit(src"$lb.apply($row+i,$col)") }
      // close("}")
      // Console.println(row)
      // Console.println(src"$row")
      // Console.println(s"$quote(row)")
      
    case op@LineBufferColSlice(lb,row,col,len) =>
      val dispatch = dispatchOf(lhs, lb).toList.distinct
      if (dispatch.length > 1) { throw new Exception(src"This is an example where lb dispatch > 1. Please use as test case! (node $lhs on lb $lb)") }
      val i = dispatch.head
      for ( k <- 0 until lenOf(lhs)) {
        emit(src"${lb}_$i.io.col_addr($k) := ${col}.r + ${k}.U")  
      }
      val rowtext = row match {
        case Const(cc) => s"${cc}.U"
        case _ => src"${row}.r"
      }
      emitGlobalWire(s"""val ${quote(lhs)} = Wire(Vec(${getConstValue(len)}, ${newWire(lhs.tp.typeArguments.head)}))""")
      for ( k <- 0 until lenOf(lhs)) {
        emit(src"${lhs}($k) := ${quote(lb)}_$i.readRowSlice(${rowtext}, ${k}.U).r")  
      }

      
    case op@LineBufferLoad(lb,row,col,en) => 
      val dispatch = dispatchOf(lhs, lb).toList.distinct
      if (dispatch.length > 1) { throw new Exception(src"This is an example where lb dispatch > 1. Please use as test case! (node $lhs on lb $lb)") }
      val i = dispatch.head
      emit(src"${lb}_$i.io.col_addr(0) := ${col}.raw")
      val rowtext = row match {
        case Const(cc) => s"$cc"
        case _ => src"${row}.r"
      }
      emit(s"val ${quote(lhs)} = ${quote(lb)}_$i.readRow(${rowtext})")

    case op@LineBufferEnq(lb,data,en) =>
      val dispatch = dispatchOf(lhs, lb).toList.distinct
      if (dispatch.length > 1) { throw new Exception(src"This is an example where lb dispatch > 1. Please use as test case! (node $lhs on lb $lb)") }
      val i = dispatch.head
      val parent = writersOf(lb).find{_.node == lhs}.get.ctrlNode
      emit(src"${lb}_$i.io.data_in(0) := ${data}.raw")
      emit(src"${lb}_$i.io.w_en(0) := $en & ShiftRegister(${parent}_datapath_en & ~${parent}_inhibitor, ${symDelay(lhs)})")

    case _ => super.emitNode(lhs, rhs)
  }

  def getStreamAdjustment(c: Exp[Any]): String = {
      // If we are inside a stream pipe, the following may be set
      if (childrenOf(c).length == 0) {
        getStreamEnablers(c)
      } else {
        childrenOf(c).map{getStreamEnablers(_)}.filter(_.trim().length > 0).mkString{" "}
      }

  }

  override protected def emitFileFooter() {
    withStream(getStream("BufferControlCxns")) {
      linebufs.foreach{ case (mem,i) => 
        val info = bufferControlInfo(mem, i)
        info.zipWithIndex.foreach{ case (inf, port) => 
          emit(src"""${mem}_$i.connectStageCtrl(${quote(inf._1)}_done.D(1,rr), ${quote(inf._1)}_base_en, List(${port})) ${inf._2}""")
        }


        // emit(src"""${mem}.lockUnusedCtrl() // Specific method for linebufs, since there is one ctrl port per line, but possibly not an access per line """)


      }
    }

    super.emitFileFooter()
  }

}
