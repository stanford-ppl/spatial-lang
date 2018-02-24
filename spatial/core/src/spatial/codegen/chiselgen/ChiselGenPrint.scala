package argon.codegen.chiselgen

import argon.core._
import argon.nodes._

trait ChiselGenPrint extends ChiselCodegen {

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]) = rhs match {
    // case Print(x)   => emit(src"val $lhs = System.out.print($x)")
    // case Println(x) => emit(src"val $lhs = System.out.println($x)")
    case _ => super.emitNode(lhs, rhs)
  }
}
