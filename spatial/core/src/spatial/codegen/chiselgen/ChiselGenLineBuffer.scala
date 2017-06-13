package spatial.codegen.chiselgen

import spatial.compiler._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._
import spatial.SpatialConfig


trait ChiselGenLineBuffer extends ChiselGenController {
  private var linebufs: List[Sym[LineBufferNew[_]]]  = List()

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
    case op@LineBufferNew(rows, cols) =>
      val row_rPar = s"$rows" // TODO: Do correct analysis here!
      val accessors = bufferControlInfo(lhs, 0).length
      val row_wPar = 1 // TODO: Do correct analysis here!
      val col_rPar = 1 // TODO: Do correct analysis here!
      val col_wPar = 1 // TODO: Do correct analysis here!
      emitGlobalModule(s"""val ${quote(lhs)} = Module(new templates.LineBuffer($rows, $cols, 1, 
        ${col_wPar}, ${col_rPar}, 
        ${row_wPar}, ${row_rPar}, $accessors))  // Data type: ${remap(op.mT)}""")
      emitGlobalModule(src"$lhs.io.reset := reset")
      linebufs = linebufs :+ lhs.asInstanceOf[Sym[LineBufferNew[_]]]
      
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
      // TODO: Multiple cycles
      // Copied from ScalaGen:
      // open(src"val $lhs = Array.tabulate($len){i =>")
        // oobApply(op.mT, lb, lhs, Seq(row,col)){ emit(src"$lb.apply($row,$col+i)") }
      // close("}")
      
    case op@LineBufferLoad(lb,row,col,en) => 
      emit(src"$lb.io.col_addr(0) := ${col}.raw")
      val rowtext = row match {
        case c: Const[_] => "0.U"
        case _ => src"${row}.r"
      }
      emit(s"val ${quote(lhs)} = ${quote(lb)}.readRow(${rowtext})")

    case op@LineBufferEnq(lb,data,en) =>
      val parent = writersOf(lb).find{_.node == lhs}.get.ctrlNode
      emit(src"$lb.io.data_in(0) := ${data}.raw")
      emit(src"$lb.io.w_en := $en & ShiftRegister(${parent}_datapath_en & ~${parent}_inhibitor, ${symDelay(lhs)})")

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
      linebufs.foreach{ mem => 
        val info = bufferControlInfo(mem, 0)
        info.zipWithIndex.foreach{ case (inf, port) => 
          emit(src"""${mem}.connectStageCtrl(${quote(inf._1)}_done, ${quote(inf._1)}_base_en, List(${port})) ${inf._2}""")
        }


        // emit(src"""${mem}.lockUnusedCtrl() // Specific method for linebufs, since there is one ctrl port per line, but possibly not an access per line """)


      }
    }

    super.emitFileFooter()
  }

}
