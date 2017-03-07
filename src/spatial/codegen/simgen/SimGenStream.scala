package spatial.codegen.simgen

import spatial.api.StreamExp

trait SimGenStream extends SimCodegen {
  val IR: StreamExp
  import IR._

  override protected def remap(tp: Staged[_]): String = tp match {
    case tp: StreamInType[_]  => src"scala.collection.mutable.Queue[${tp.child}]"
    case tp: StreamOutType[_] => src"scala.collection.mutable.Queue[${tp.child}]"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op: StreamInNew[_]  => emit(src"val $lhs = new scala.collection.mutable.Queue[${op.mT}]")
    case op: StreamOutNew[_] => emit(src"val $lhs = new scala.collection.mutable.Queue[${op.mT}]")
    case op@StreamEnq(stream, data, en) => emit(src"val $lhs = if ($en) $stream.enqueue($data)")
    case op@StreamDeq(stream, en, zero) => emit(src"val $lhs = if ($en) $stream.dequeue() else ${op.zero}")
    case _ => super.emitNode(lhs, rhs)
  }

}
