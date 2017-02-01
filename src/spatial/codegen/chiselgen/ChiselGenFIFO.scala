package spatial.codegen.chiselgen

import argon.codegen.chiselgen.ChiselCodegen
import spatial.api.FIFOExp

trait ChiselGenFIFO extends ChiselCodegen {
  val IR: FIFOExp
  import IR._

  override def quote(s: Exp[_]): String = {
    // val Def(rhs) = s 
    s match {
      case lhs: Sym[_] =>
        val Op(rhs) = lhs
        rhs match {
          case e: FIFONew[_] =>
            s"x${lhs.id}_Fifo"
          case FIFOEnq(fifo:Sym[_],_,_) =>
            s"x${lhs.id}_enqTo${fifo.id}"
          case FIFODeq(fifo:Sym[_],_,_) =>
            s"x${lhs.id}_deqFrom${fifo.id}"
          case _ =>
            super.quote(s)
        }
      case _ =>
        super.quote(s)
    }
  } 

  override protected def remap(tp: Staged[_]): String = tp match {
    case tp: FIFOType[_] => src"chisel.collection.mutable.Queue[${tp.bits}]"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op@FIFONew(size)   => emit(src"val $lhs = new chisel.collection.mutable.Queue[${op.bT}]($size})")
    case FIFOEnq(fifo,v,en) => emit(src"val $lhs = if ($en) $fifo.enqueue($v)")
    case FIFODeq(fifo,en,z) => emit(src"val $lhs = if ($en) $fifo.dequeue() else $z")
    case _ => super.emitNode(lhs, rhs)
  }
}
