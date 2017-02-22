package spatial.codegen.chiselgen

import argon.codegen.chiselgen.ChiselCodegen
import argon.codegen.FileDependencies
import spatial.SpatialConfig
import spatial.analysis.SpatialMetadataExp
import spatial.SpatialExp

trait ChiselGenAlteraVideo extends ChiselCodegen with FileDependencies {
  val IR: SpatialExp
  import IR._


  override def quote(s: Exp[_]): String = {
    s match {
      case b: Bound[_] => super.quote(s)
      case _ => super.quote(s)

    }
  } 


  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case AxiMSNew() =>
      dependencies ::= AlwaysDep(s"${SpatialConfig.HOME}/src/spatial/codegen/chiselgen/resources/altera-goodies/address_map_arm.h", outputPath="altera/")
      dependencies ::= AlwaysDep(s"${SpatialConfig.HOME}/src/spatial/codegen/chiselgen/resources/altera-goodies/interfaces.v", outputPath="altera/")
      dependencies ::= AlwaysDep(s"${SpatialConfig.HOME}/src/spatial/codegen/chiselgen/resources/altera-goodies/StreamPixBuffer2ARM.scala", outputPath="altera/")
    case DecoderTemplateNew() => 
      dependencies ::= AlwaysDep(s"${SpatialConfig.HOME}/src/spatial/codegen/chiselgen/resources/altera-goodies/altera_up_avalon_video_decoder", outputPath="altera/")
    case DMATemplateNew() => 
      dependencies ::= AlwaysDep(s"${SpatialConfig.HOME}/src/spatial/codegen/chiselgen/resources/altera-goodies/altera_up_avalon_video_dma_controller", outputPath="altera/")
    case _ => super.emitNode(lhs, rhs)
  }
}
