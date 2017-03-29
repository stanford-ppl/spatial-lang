package spatial.codegen.scalagen

import argon.codegen.scalagen.ScalaCodegen
import spatial.api.VectorExp

trait ScalaGenVector extends ScalaGenBits {
  val IR: VectorExp
  import IR._

  override protected def remap(tp: Type[_]): String = tp match {
    case tp: VectorType[_,_] => src"Array[${tp.child}]"
    case _ => super.remap(tp)
  }

  override def invalid(tp: Type[_]): String = tp match {
    case tp: VectorType[_,_] => src"""Array.fill(${tp.width}(${invalid(tp.child)})"""
    case _ => super.invalid(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case ListVector(elems)      => emit(src"val $lhs = Array(" + elems.map(quote).mkString(",") + ")")
    case VectorApply(vector, i) => emit(src"val $lhs = $vector.apply($i)")
    case VectorSlice(vector, start, end) => emit(src"val $lhs = $vector.slice($start, $end)")
    case _ => super.emitNode(lhs, rhs)
  }
}