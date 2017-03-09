package spatial.codegen.dotgen

import argon.codegen.dotgen.DotCodegen
import spatial.api.SRAMExp
import spatial.SpatialConfig
import spatial.SpatialExp


trait DotGenSRAM extends DotCodegen {
  val IR: SpatialExp
  import IR._

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op@SRAMNew(dimensions) => 
    
    case SRAMLoad(sram, dims, is, ofs) =>
    case SRAMStore(sram, dims, is, ofs, v, en) =>
    case _ => super.emitNode(lhs, rhs)
  }

  override protected def emitFileFooter() {
    super.emitFileFooter()
  }
    
}
