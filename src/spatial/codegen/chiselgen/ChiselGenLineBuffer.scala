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
      emit(src"$lb.io.col_addr := $col")
      emit(s"val ${quote(lhs)} = ${quote(lb)}.io.data_out($row)")

    case op@LineBufferEnq(lb,data,en) =>
      emit(src"$lb.io.data_in := $data")
      emit(src"$lb.io.w_en := $en")
      
    case _ => super.emitNode(lhs, rhs)
  }

  // override protected def emitFileFooter() {
  //   withStream(getStream("BufferControlCxns")) {
  //     linebufs.foreach{ mem => 
  //       val readers = readersOf(mem)
  //       val writers = writersOf(mem)
  //       // Console.println(s"working on $mem $i $readers $readers $writers $writers")
  //       // Console.println(s"${readers.map{case (_, readers) => readers}}")
  //       // Console.println(s"innermost ${readers.map{case (_, readers) => readers.flatMap{a => topControllerOf(a,mem,i)}.head}.head.node}")
  //       // Console.println(s"middle ${parentOf(readers.map{case (_, readers) => readers.flatMap{a => topControllerOf(a,mem,i)}.head}.head.node).get}")
  //       // Console.println(s"outermost ${childrenOf(parentOf(readers.map{case (_, readers) => readers.flatMap{a => topControllerOf(a,mem,i)}.head}.head.node).get)}")
  //       val allSiblings = childrenOf(parentOf(readers.map{case (_, readers) => readers.flatMap{a => topControllerOf(a,mem,i)}.head}.head.node).get)
  //       val readSiblings = readers.map{case (_,r) => r.flatMap{ a => topControllerOf(a, mem, i)}}.filter{case l => l.length > 0}.map{case all => all.head.node}
  //       val writeSiblings = writers.map{case (_,r) => r.flatMap{ a => topControllerOf(a, mem, i)}}.filter{case l => l.length > 0}.map{case all => all.head.node}
  //       val writersNumbers = writeSiblings.map{ sw => allSiblings.indexOf(sw) }
  //       val readersNumbers = readSiblings.map{ sr => allSiblings.indexOf(sr) }
  //       val firstActivePort = math.min( readersNumbers.min, writersNumbers.min )
  //       val lastActivePort = math.max( readersNumbers.max, writersNumbers.max )
  //       val numStagesInbetween = lastActivePort - firstActivePort

  //       // map port on memory to child of parent
  //       (0 to numStagesInbetween).foreach { port =>
  //         val ctrlId = port + firstActivePort
  //         val node = allSiblings(ctrlId)
  //         val rd = if (readersNumbers.toList.contains(ctrlId)) {"read"} else ""
  //         val wr = if (writersNumbers.toList.contains(ctrlId)) {"write"} else ""
  //         val empty = if (rd == "" & wr == "") "empty" else ""
  //         emit(src"""${mem}.connectStageCtrl(${quote(node)}_done, ${quote(node)}_en, List(${port})) /*$rd $wr $empty*/""")
  //       }

  //     }
  //   }

  //   super.emitFileFooter()
  // }

}
