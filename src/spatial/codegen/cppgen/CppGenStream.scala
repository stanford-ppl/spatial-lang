package spatial.codegen.cppgen

import argon.codegen.cppgen.CppCodegen
import spatial.api.RegExp
import spatial.SpatialConfig
import spatial.analysis.SpatialMetadataExp
import spatial.SpatialExp

trait CppGenStream extends CppCodegen {
  val IR: SpatialExp
  import IR._

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case StreamInNew(bus) => emit(s"$lhs = $bus // TODO: No idea what to connect this bus to, should expose periphal pins to something...")
    case StreamOutNew(bus) =>
      s"$bus" match {
        case "BurstCmdBus" => 
        case _ =>
          emit(src"// New stream out $lhs")
      }
    case _ => super.emitNode(lhs, rhs)
  }

  

  override protected def emitFileFooter() {
    super.emitFileFooter()
  }
}
