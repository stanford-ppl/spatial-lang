package spatial.codegen.pirgen

import argon.codegen.pirgen.PIRCodegen
import spatial.api.SRAMExp
import spatial.SpatialConfig
import spatial.SpatialExp


trait PIRGenSRAM extends PIRCodegen with PIRGenController {
  val IR: SpatialExp with PIRCommonExp
  import IR._

  private var nbufs: List[(Sym[SRAMNew[_]], Int)]  = List()

  override protected def remap(tp: Staged[_]): String = tp match {
    case tp: SRAMType[_] => src"Array[${tp.child}]"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op@SRAMNew(dimensions) if cus.contains(lhs) => 
      cus(lhs).flatten.foreach{cu => emitCU(lhs, cu)}
    
    case SRAMLoad(sram, dims, is, ofs) =>

    case SRAMStore(sram, dims, is, ofs, v, en) =>

    case _ => super.emitNode(lhs, rhs)
  }

}
