package spatial.codegen.scalagen

import argon.codegen.scalagen.ScalaCodegen
import spatial.compiler._

trait ScalaGenBits extends ScalaCodegen {

  dependencies ::= FileDep("scalagen", "Data.scala")

  def invalid(tp: Type[_]): String = tp match {
    case _ => throw new Exception(u"Don't know how to generate invalid for type $tp")
  }
  override def emitFileHeader() = {
    emit(src"import DataImplicits._")
    super.emitFileHeader()
  }
}
