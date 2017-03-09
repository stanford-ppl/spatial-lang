package spatial.codegen.dotgen

import argon.codegen.dotgen.DotCodegen
import argon.codegen.FileDependencies
import spatial.SpatialConfig
import spatial.analysis.SpatialMetadataExp
import spatial.SpatialExp

trait DotGenAlteraVideo extends DotCodegen with FileDependencies {
  val IR: SpatialExp
  import IR._

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case AxiMSNew() =>
    case DecoderTemplateNew() => 
    case DMATemplateNew() => 
    case _ => super.emitNode(lhs, rhs)
  }
}
