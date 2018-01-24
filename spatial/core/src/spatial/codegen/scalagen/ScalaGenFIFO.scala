package spatial.codegen.scalagen

import argon.core._
import org.virtualized.SourceContext
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

trait ScalaGenFIFO extends ScalaGenMemories {

  override protected def remap(tp: Type[_]): String = tp match {
    case tp: FIFOType[_] => src"scala.collection.mutable.Queue[${tp.child}]"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op@FIFONew(size)   => emitMemObject(lhs){ emit(src"object $lhs extends scala.collection.mutable.Queue[${op.mT}]") }
    case FIFOEnq(fifo,v,en) => throw new Exception(s"Cannot generate unbanked FIFO enqueue\n${str(lhs)}")
    case FIFODeq(fifo,en)   => throw new Exception(s"Cannot generate unbanked FIFO dequeue\n${str(lhs)}")

    case FIFOEmpty(fifo)    => emit(src"val $lhs = $fifo.isEmpty")

    case FIFOAlmostEmpty(fifo)  =>
      val rPar = maxReadWidth(fifo)
      emit(src"val $lhs = $fifo.size === $rPar")

    case FIFOAlmostFull(fifo) =>
      val wPar = maxWriteWidth(fifo)
      emit(src"val $lhs = $fifo.size === ${stagedSizeOf(fifo)} - $wPar")

    case op@FIFOPeek(fifo) => emit(src"val $lhs = if ($fifo.nonEmpty) $fifo.head else ${invalid(op.mT)}")
    case FIFONumel(fifo) => emit(src"val $lhs = $fifo.size")
    case FIFOFull(fifo)  => emit(src"val $lhs = $fifo.size >= ${stagedSizeOf(fifo)} ")

    case op@BankedFIFODeq(fifo, ens) =>
      open(src"val $lhs = {")
      ens.zipWithIndex.foreach{case (en,i) =>
        emit(src"val a$i = if ($en && $fifo.nonEmpty) $fifo.dequeue() else ${invalid(op.mT)}")
      }
      emit(src"Array[${op.mT}](" + ens.indices.map{i => src"a$i"}.mkString(", ") + ")")
      close("}")

    case BankedFIFOEnq(fifo, data, ens) =>
      open(src"val $lhs = {")
      ens.zipWithIndex.foreach{case (en,i) =>
        emit(src"if ($en) $fifo.enqueue(${data(i)})")
      }
      close("}")

    case _ => super.emitNode(lhs, rhs)
  }
}
