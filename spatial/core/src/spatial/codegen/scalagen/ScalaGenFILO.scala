package spatial.codegen.scalagen

import argon.core._
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._
import org.virtualized.SourceContext

trait ScalaGenFILO extends ScalaGenMemories {

  override protected def remap(tp: Type[_]): String = tp match {
    case tp: FILOType[_] => src"scala.collection.mutable.Stack[${tp.child}]"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op@FILONew(size)    => emitMem(lhs, src"$lhs = new scala.collection.mutable.Stack[${op.mT}] // size: $size")
    case FILOPush(filo,v,en) => emit(src"val $lhs = if ($en) $filo.push($v)")
    case FILOEmpty(filo)     => emit(src"val $lhs = $filo.isEmpty")
    case FILOAlmostEmpty(filo)  => 
      // Find rPar
      val rPar = readersOf(filo).map{ r => r.node match {
        case Def(ParFILOPop(_,ens)) => ens.length
        case _ => 1
      }}.head
      emit(src"val $lhs = $filo.size === $rPar") 
    case FILOAlmostFull(filo)  =>
      // Find wPar
      val wPar = writersOf(filo).map{ r => r.node match {
        case Def(ParFILOPush(_,ens,_)) => ens.length
        case _ => 1
      }}.head
      emit(src"val $lhs = $filo.size === ${stagedSizeOf(filo)} - $wPar")

    case op@FILOPeek(fifo) => emit(src"val $lhs = if (${fifo}.nonEmpty) ${fifo}.head else ${invalid(op.mT)}")
    case FILONumel(filo) => emit(src"val $lhs = $filo.size")
    case FILOFull(filo)  => emit(src"val $lhs = $filo.size >= ${stagedSizeOf(filo)} ")
    case op@FILOPop(filo,en) => emit(src"val $lhs = if ($en && $filo.nonEmpty) $filo.pop() else ${invalid(op.mT)}")

    case op@ParFILOPop(filo, ens) =>
      open(src"val $lhs = {")
        ens.zipWithIndex.foreach{case (en,i) =>
          emit(src"val a$i = if ($en && $filo.nonEmpty) $filo.pop() else ${invalid(op.mT)}")
        }
        emit(src"Array[${op.mT}](" + ens.indices.map{i => src"a$i"}.mkString(", ") + ")")
      close("}")

    case ParFILOPush(filo, data, ens) =>
      open(src"val $lhs = {")
        ens.zipWithIndex.foreach{case (en,i) =>
          emit(src"if ($en) $filo.push(${data(i)})")
        }
      close("}")

    case _ => super.emitNode(lhs, rhs)
  }
}
