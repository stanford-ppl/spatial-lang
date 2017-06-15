package spatial.codegen.dotgen

import argon.internals._
import argon.codegen.dotgen.DotCodegen
import argon.codegen.FileDependencies
import spatial.compiler._
import spatial.nodes._

trait DotGenAlteraVideo extends DotCodegen with FileDependencies {

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case AxiMSNew() =>
    case DecoderTemplateNew(popFrom, pushTo) => 
    case DMATemplateNew(popFrom, loadIn) => 
    case _ => super.emitNode(lhs, rhs)
  }

}
