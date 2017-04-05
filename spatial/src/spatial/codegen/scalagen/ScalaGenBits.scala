package spatial.codegen.scalagen

import argon.codegen.scalagen.ScalaCodegen
import argon.core.Staging
import spatial.SpatialConfig

trait ScalaGenBits extends ScalaCodegen {
  val IR: Staging
  import IR._

  dependencies ::= AlwaysDep("scalagen", "Data.scala")

  def invalid(tp: Type[_]): String = tp match {
    case _ => throw new Exception(u"Don't know how to generate invalid for type $tp")
  }
  override def emitFileHeader() = {
    emit(src"import DataImplicits._")
    super.emitFileHeader()
  }
}
