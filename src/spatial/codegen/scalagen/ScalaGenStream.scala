package spatial.codegen.scalagen

import spatial.api.StreamExp

trait ScalaGenStream extends ScalaGenMemories {
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
    case op@StreamEnq(strm, data, en) => emit(src"val $lhs = if ($en) $strm.enqueue($data)")
    case op@StreamDeq(strm, en, zero) => emit(src"val $lhs = if ($en && $strm.nonEmpty) $strm.dequeue() else ${invalid(op.mT)}")
    case _ => super.emitNode(lhs, rhs)
  }

}
