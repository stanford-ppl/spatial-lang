package spatial.codegen.cppgen

import argon.codegen.cppgen.CppCodegen
import spatial.lang.RangeExp
import spatial.SpatialConfig
import spatial.SpatialExp

trait CppGenRange extends CppCodegen {
  val IR: SpatialExp
  import IR._


  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case RangeForeach(start, end, step, func, i) =>
      open(src"for (int $i = $start; $i < $end; $i = $i + $step) {")
      emitBlock(func)
      close("}")

    case _ =>
    	super.emitNode(lhs,rhs)
  }
}