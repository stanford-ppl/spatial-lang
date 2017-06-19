package spatial.codegen.dotgen

import argon.core._
import argon.codegen.dotgen.DotCodegen
import argon.codegen.FileDependencies
import spatial.aliases._
import spatial.nodes._

trait DotGenAlteraVideo extends DotCodegen with FileDependencies {

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case AxiMSNew() =>
    case DecoderTemplateNew(popFrom, pushTo) => 
    case DMATemplateNew(popFrom, loadIn) => 
    case _ => super.emitNode(lhs, rhs)
  }

}
