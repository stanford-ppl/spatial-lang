package spatial.codegen.scalagen

import argon.core._
import spatial.compiler._
import spatial.nodes._

trait ScalaGenLineBuffer extends ScalaGenMemories {
  dependencies ::= FileDep("scalagen", "LineBuffer.scala")

  override protected def remap(tp: Type[_]): String = tp match {
    case tp: LineBufferType[_] => src"LineBuffer[${tp.child}]"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op@LineBufferNew(rows, cols) => emitMem(lhs, src"$lhs = LineBuffer[${op.mT}]($rows, $cols, ${invalid(op.mT)})")
    case op@LineBufferRowSlice(lb,row,len,col) =>
      open(src"val $lhs = Array.tabulate($len){i => ")
        oobApply(op.mT, lb, lhs, Seq(row,col)){ emit(src"$lb.apply($row+i,$col)") }
      close("}")
    case op@LineBufferColSlice(lb,row,col,len) =>
      open(src"val $lhs = Array.tabulate($len){i =>")
        oobApply(op.mT, lb, lhs, Seq(row,col)){ emit(src"$lb.apply($row,$col+i)") }
      close("}")

    case op@LineBufferLoad(lb,row,col,en) =>
      open(src"val $lhs = {")
        oobApply(op.mT, lb, lhs, Seq(row,col)){ emit(src"if ($en) $lb.apply($row, $col) else ${invalid(op.mT)}") }
      close("}")

    case op@LineBufferEnq(lb,data,en) =>
      open(src"val $lhs = {")
        oobUpdate(op.mT, lb, lhs, Nil){ emit(src"if ($en) $lb.enq($data)") }
      close("}")

    case op@ParLineBufferEnq(buffer,data,ens) =>
      open(src"val $lhs = {")
        ens.zipWithIndex.foreach{case (en,i) =>
          oobUpdate(op.mT, buffer,lhs, Nil){ emit(src"if ($en) $buffer.enq(${data(i)})") }
        }
      close("}")

    case op@ParLineBufferLoad(buffer,rows,cols,ens) =>
      open(src"val $lhs = {")
        ens.zipWithIndex.foreach{case (en,i) =>
          open(src"val a$i = {")
            oobApply(op.mT, buffer, lhs, List(rows(i),cols(i))){ emit(src"if ($en) $buffer.apply(${rows(i)},${cols(i)}) else ${invalid(op.mT)}") }
          close("}")
        }
        emit(src"Array[${op.mT}](" + ens.indices.map{i => src"a$i"}.mkString(", ") + ")")
      close("}")

    case _ => super.emitNode(lhs, rhs)
  }

}
