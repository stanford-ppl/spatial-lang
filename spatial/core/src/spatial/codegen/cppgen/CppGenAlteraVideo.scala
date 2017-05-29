package spatial.codegen.cppgen

import argon.codegen.cppgen.CppCodegen
import spatial.lang.{ControllerExp, CounterExp, UnrolledExp}
import spatial.SpatialConfig
import spatial.analysis.SpatialMetadataExp
import spatial.SpatialExp

trait CppGenAlteraVideo extends CppCodegen {
  val IR: SpatialExp
  import IR._


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
