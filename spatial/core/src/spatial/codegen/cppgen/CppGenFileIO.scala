package spatial.codegen.cppgen

import argon.codegen.cppgen.CppCodegen
import spatial.api.FileIOExp
import spatial.SpatialConfig


trait CppGenFileIO extends CppCodegen  {
  val IR: FileIOExp
  import IR._

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case LoadCSV(filename, size) => 

    case _ => super.emitNode(lhs, rhs)
  }



}
