package spatial.codegen.simgen

import spatial.api.VectorExp

trait SimGenVector extends SimCodegen {
  val IR: VectorExp
  import IR._

  override protected def remap(tp: Staged[_]): String = tp match {
    case tp: VectorType[_] => src"Array[${tp.child}]"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case ListVector(elems)      => emit(src"val $lhs = Array(" + elems.map(quote).mkString(",") + ")")
    case VectorApply(vector, i) => emit(src"val $lhs = $vector.apply($i)")
    case VectorSlice(vector, start, end) => emit(src"val $lhs = $vector.slice($start, $end)")
    case _ => super.emitNode(lhs, rhs)
  }
}