package spatial.codegen.chiselgen

import argon.codegen.chiselgen.ChiselCodegen
import spatial.api.FileIOExp
import spatial.SpatialConfig

trait ChiselGenFileIO extends ChiselCodegen  {
  val IR: FileIOExp
  import IR._

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case OpenFile(filename, isWr) => 
    case CloseFile(file) =>
    case ReadTokens(file, delim) =>
    case WriteTokens(file, delim, len, token, i) =>

    case _ => super.emitNode(lhs, rhs)
  }



}
