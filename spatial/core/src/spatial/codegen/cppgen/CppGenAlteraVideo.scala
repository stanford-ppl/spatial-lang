package spatial.codegen.cppgen

import argon.codegen.cppgen.CppCodegen
import spatial.compiler._
import spatial.nodes._

trait CppGenAlteraVideo extends CppCodegen {

  // override def quote(s: Exp[_]): String = {
  //   s match {
  //     case b: Bound[_] => super.quote(s)
  //     case _ => super.quote(s)

  //   }
  // }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case AxiMSNew() =>
      emit(src"""// axi_master_slave""")

    case _ => super.emitNode(lhs, rhs)
  }
}
