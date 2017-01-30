package spatial.codegen.scalagen

import argon.codegen.scalagen.ScalaCodegen
import spatial.api.FIFOExp

trait ScalaGenFIFO extends ScalaCodegen {
  val IR: FIFOExp
  import IR._

  override protected def remap(tp: Staged[_]): String = tp match {
    case tp: FIFOType[_] => src"scala.collection.mutable.Queue[${tp.bits}]"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op@FIFONew(size)   => emit(src"val $lhs = new scala.collection.mutable.Queue[${op.bT}]($size})")
    case FIFOEnq(fifo,v,en) => emit(src"val $lhs = if ($en) $fifo.enqueue($v)")
    case FIFODeq(fifo,en,z) => emit(src"val $lhs = if ($en) $fifo.dequeue() else $z")
    case _ => super.emitNode(lhs, rhs)
  }
}
