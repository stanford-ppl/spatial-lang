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
    case op@FIFONew(size)   => emitMem(lhs, src"$lhs = new scala.collection.mutable.Queue[${op.mT}] // size: $size")
    case FIFOEnq(fifo,v,en) => emit(src"val $lhs = if ($en) $fifo.enqueue($v)")
    case FIFOEmpty(fifo)  => emit(src"val $lhs = $fifo.isEmpty")

    case FIFOAlmostEmpty(fifo)  =>
      // Find rPar
      val rPar = readersOf(fifo).map{ r => r.node match {
        case Def(ParFIFODeq(_,ens)) => ens.length
        case _ => 1
      }}.head
      emit(src"val $lhs = $fifo.size === $rPar")

    case FIFOAlmostFull(fifo) =>
      // Find wPar
      val wPar = writersOf(fifo).map{ r => r.node match {
        case Def(ParFIFOEnq(_,ens,_)) => ens.length
        case _ => 1
      }}.head
      emit(src"val $lhs = $fifo.size === ${stagedSizeOf(fifo)} - $wPar")

    case op@FIFOPeek(fifo) => emit(src"val $lhs = if (${fifo}.nonEmpty) ${fifo}.head else ${invalid(op.mT)}")
    case FIFONumel(fifo) => emit(src"val $lhs = $fifo.size")
    case FIFOFull(fifo)  => emit(src"val $lhs = $fifo.size >= ${stagedSizeOf(fifo)} ")
    case op@FIFODeq(fifo,en) => emit(src"val $lhs = if ($en && $fifo.nonEmpty) $fifo.dequeue() else ${invalid(op.mT)}")

    case op@ParFIFODeq(fifo, ens) =>
      open(src"val $lhs = {")
      ens.zipWithIndex.foreach{case (en,i) =>
        emit(src"val a$i = if ($en && $fifo.nonEmpty) $fifo.dequeue() else ${invalid(op.mT)}")
      }
      emit(src"Array[${op.mT}](" + ens.indices.map{i => src"a$i"}.mkString(", ") + ")")
      close("}")

    case ParFIFOEnq(fifo, data, ens) =>
      open(src"val $lhs = {")
      ens.zipWithIndex.foreach{case (en,i) =>
        emit(src"if ($en) $fifo.enqueue(${data(i)})")
      }
      close("}")

    case _ => super.emitNode(lhs, rhs)
  }
}
