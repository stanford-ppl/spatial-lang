package spatial.codegen.dotgen

import argon.codegen.dotgen.DotCodegen
import spatial.api.RegExp
import spatial.SpatialConfig
import spatial.SpatialExp

trait DotGenReg extends DotCodegen {
  val IR: RegExp with SpatialExp
  import IR._

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case ArgInNew(init)  => 
    case ArgOutNew(init) => 
    case RegNew(init)    => 
    case RegRead(reg)    => 
    case RegWrite(reg,v,en) => 
    case _ => super.emitNode(lhs, rhs)
  }

  override protected def emitFileFooter() {
    super.emitFileFooter()
  }
}
