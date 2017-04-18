package spatial.codegen.simgen


import argon.ops.PrintExp
import spatial.SpatialExp

trait SimGenPrint extends SimCodegen {
  val IR: SpatialExp with PrintExp
  import IR._

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]) = rhs match {
    case Print(x)   => emit(src"val $lhs = System.out.print($x)")
    case Println(x) => emit(src"val $lhs = System.out.println($x)")
    case _ => super.emitNode(lhs, rhs)
  }
}
