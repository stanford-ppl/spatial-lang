package spatial.codegen.scalagen

import argon.core._
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

trait ScalaGenLineBuffer extends ScalaGenMemories with ScalaGenControl {
  dependencies ::= FileDep("scalagen", "LineBuffer.scala")

  override protected def remap(tp: Type[_]): String = tp match {
    case tp: LineBufferType[_] => src"LineBuffer[${tp.child}]"
    case _ => super.remap(tp)
  }

  override protected def emitControlDone(ctrl: Exp[_]): Unit = {
    super.emitControlDone(ctrl)

    val written = localMems.filter{mem => writersOf(mem).exists{wr =>
      topControllerOf(wr.node,mem,0).exists(_.node == ctrl) && (wr.node match {
        case Def(_:LineBufferEnq[_]) | Def(_:ParLineBufferEnq[_]) => true
        //case Def(_:LineBufferRotateEnq[_]) | Def(_:ParLineBufferRotateEnq[_]) => true
        case _ => false
      })
    }}
    val lineBuffers = written.filter(isLineBuffer)
    if (lineBuffers.nonEmpty) {
      emit("/** Rotating LineBuffers from Ctrl Done**/")
      lineBuffers.foreach{lb => 
        //val stride = lb match {case Def(LineBufferNew(_,_,Exact(c: BigInt))) => c.toInt } // See bug #227
        //if (stride == 1)
        emit(src"$lb.rotate()")
      }
      emit("/***********************/")
    }
  }

  override protected def emitControlIncrement(ctrl: Exp[_], iter: Seq[Bound[Index]]): Unit = {
    super.emitControlIncrement(ctrl, iter)

    val lineBuffers = localMems.filter{mem => isLineBuffer(mem) && writersOf(mem).map(_.node).exists{
      case Def(LineBufferRotateEnq(_,row,_,_)) => iter.contains(delayLineTrace(row))
      case Def(ParLineBufferRotateEnq(_,row,_,_)) => iter.contains(delayLineTrace(row))
      case _ => false
    }}
    if (lineBuffers.nonEmpty) {
      emit("/** Rotating LineBuffers from Ctrl Increment**/")
      lineBuffers.foreach{lb => emit(src"$lb.rotate()") }
      emit("/**************************/")
    }
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op@LineBufferNew(rows, cols,stride) => emitMem(lhs, src"$lhs = LineBuffer[${op.mT}]($rows, $cols, $stride, ${invalid(op.mT)})")
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

    case op@ParLineBufferLoad(buffer,rows,cols,ens) =>
      open(src"val $lhs = {")
      ens.zipWithIndex.foreach{case (en,i) =>
        open(src"val a$i = {")
        oobApply(op.mT, buffer, lhs, List(rows(i),cols(i))){ emit(src"if ($en) $buffer.apply(${rows(i)},${cols(i)}) else ${invalid(op.mT)}") }
        close("}")
      }
      emit(src"Array[${op.mT}](" + ens.indices.map{i => src"a$i"}.mkString(", ") + ")")
      close("}")

    case op@LineBufferEnq(lb,data,en) =>
      open(src"val $lhs = {")
        oobUpdate(op.mT, lb, lhs, Nil){ emit(src"if ($en) $lb.enq($data)") }
      close("}")

    case op@ParLineBufferEnq(lb,data,ens) =>
      open(src"val $lhs = {")
        ens.zipWithIndex.foreach{case (en,i) =>
          oobUpdate(op.mT, lb,lhs, Nil){ emit(src"if ($en) $lb.enq(${data(i)})") }
        }
      close("}")

    case op@LineBufferRotateEnq(lb,row,data,en) =>
      open(src"val $lhs = {")
        oobUpdate(op.mT, lb, lhs, Nil){ emit(src"if ($en) $lb.enq($data)") }
      close("}")

    case op@ParLineBufferRotateEnq(lb,row,data,ens) =>
      open(src"val $lhs = {")
        ens.zipWithIndex.foreach{case (en,i) =>
          oobUpdate(op.mT,lb,lhs,Nil){ emit(src"if ($en) $lb.enq(${data(i)})") }
        }
      close("}")

    case _ => super.emitNode(lhs, rhs)
  }

}
