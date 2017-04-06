package spatial.codegen.simgen

import spatial.api.FIFOExp

trait SimGenFIFO extends SimCodegen {
  val IR: FIFOExp
  import IR._

  override protected def remap(tp: Type[_]): String = tp match {
    case tp: FIFOType[_] => src"scala.collection.mutable.Queue[${tp.child}]"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op@FIFONew(size)   => emit(src"val $lhs = new scala.collection.mutable.Queue[${op.mT}] // size: $size")
    case FIFOEnq(fifo,v,en) => emit(src"val $lhs = if ($en) $fifo.enqueue($v)")
    case FIFODeq(fifo,en)   => //emit(src"val $lhs = if ($en) $fifo.dequeue() else ${z") TODO
    case _ => super.emitNode(lhs, rhs)
  }
}
