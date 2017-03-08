package spatial.codegen.pirgen

import argon.codegen.pirgen.PIRCodegen
import spatial.api.SRAMExp
import spatial.SpatialConfig
import spatial.SpatialExp

trait PIRGenDRAM extends PIRCodegen with PIRGenController {
  val IR: SpatialExp with PIRCommonExp
  import IR._

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case rhs if isFringe(lhs) & cus.contains(lhs) => 
      cus(lhs).flatten.foreach{cu => emitCU(lhs, cu)}
    case _ => super.emitNode(lhs, rhs)
  }

}
