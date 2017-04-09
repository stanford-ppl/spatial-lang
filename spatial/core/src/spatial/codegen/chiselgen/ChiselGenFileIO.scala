package spatial.codegen.chiselgen

import argon.codegen.chiselgen.ChiselCodegen
import spatial.api.FileIOExp
import spatial.SpatialConfig

trait ChiselGenFileIO extends ChiselCodegen  {
  val IR: FileIOExp
  import IR._

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    // case LoadCSV(filename, size) => 
    case _ => super.emitNode(lhs, rhs)
  }



}
