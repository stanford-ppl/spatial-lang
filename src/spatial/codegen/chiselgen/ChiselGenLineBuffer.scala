package spatial.codegen.chiselgen

import argon.codegen.chiselgen.ChiselCodegen
import spatial.api.LineBufferExp
import spatial.SpatialConfig
import spatial.SpatialExp

trait ChiselGenLineBuffer extends ChiselCodegen {
  val IR: SpatialExp
  import IR._

  private var linebufs: List[Sym[LineBufferNew[_]]]  = List()

  override def quote(s: Exp[_]): String = {
    if (SpatialConfig.enableNaming) {
      s match {
        case lhs: Sym[_] =>
          lhs match {
            case Def(e: LineBufferNew[_]) =>
              s"""x${lhs.id}_${nameOf(lhs).getOrElse("linebuf")}"""
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
    case tp: LineBufferType[_] => src"LineBuffer[${tp.child}]"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op@LineBufferNew(rows, cols) =>
      emitGlobal(s"val ${quote(lhs)} = Module(new templates.LineBuffer($rows, $cols, /* extra_rows_to_buffer = */ 1, 1, $rows))  // Data type: ${remap(op.mT)}")
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
      emit(src"$lb.io.col_addr := ${col}.number")
      emit(s"val ${quote(lhs)} = ${quote(lb)}.io.data_out(${row}.number)")

    case op@LineBufferEnq(lb,data,en) =>
      val parent = writersOf(reg).find{_.node == lhs}.get.ctrlNode
      emit(src"$lb.io.data_in := ${data}.number")
      emit(src"$lb.io.w_en := $en & ${parent}_datapath_en")
      
    case _ => super.emitNode(lhs, rhs)
  }

  override protected def emitFileFooter() {
    withStream(getStream("BufferControlCxns")) {
      linebufs.foreach{ mem => 
        val readers = readersOf(mem)
        val writers = writersOf(mem)
        // Console.println(s"working on ${quote(mem)} $readers $writers")
  //       // Console.println(s"innermost ${readers.map{case (_, readers) => readers.flatMap{a => topControllerOf(a,mem,i)}.head}.head.node}")
  //       // Console.println(s"middle ${parentOf(readers.map{case (_, readers) => readers.flatMap{a => topControllerOf(a,mem,i)}.head}.head.node).get}")
  //       // Console.println(s"outermost ${childrenOf(parentOf(readers.map{case (_, readers) => readers.flatMap{a => topControllerOf(a,mem,i)}.head}.head.node).get)}")
        val readPorts = readers.filter{reader => dispatchOf(reader, mem).contains(0) }.groupBy{a => portsOf(a, mem, 0) }
        val writePorts = writers.filter{writer => dispatchOf(writer, mem).contains(0) }.groupBy{a => portsOf(a, mem, 0) }
        val allSiblings = childrenOf(parentOf(readPorts.map{case (_, readers) => readers.flatMap{a => topControllerOf(a,mem,0)}.head}.head.node).get)
        val readSiblings = readPorts.map{case (_,r) => r.flatMap{ a => topControllerOf(a, mem, 0)}}.filter{case l => l.length > 0}.map{case all => all.head.node}
        val writeSiblings = writePorts.map{case (_,r) => r.flatMap{ a => topControllerOf(a, mem, 0)}}.filter{case l => l.length > 0}.map{case all => all.head.node}
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
          emit(src"""${mem}.connectStageCtrl(${quote(node)}_done, ${quote(node)}_en, List(${port})) /*$rd $wr $empty*/""")
        }

      }
    }

    super.emitFileFooter()
  }

}
