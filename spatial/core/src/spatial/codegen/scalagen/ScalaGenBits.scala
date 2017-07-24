package spatial.codegen.scalagen

import argon.core._
import argon.codegen.scalagen.ScalaCodegen
import spatial.aliases._
import spatial.nodes._

trait ScalaGenBits extends ScalaCodegen {

  dependencies ::= FileDep("scalagen", "Bool.scala")
  dependencies ::= FileDep("scalagen", "FixedPoint.scala")
  dependencies ::= FileDep("scalagen", "FixedPointRange.scala")
  dependencies ::= FileDep("scalagen", "FloatPoint.scala")
  dependencies ::= FileDep("scalagen", "DataImplicits.scala")
  dependencies ::= FileDep("scalagen", "Number.scala")

  def invalid(tp: Type[_]): String = tp match {
    case _ => throw new Exception(u"Don't know how to generate invalid for type $tp")
  }
  override def emitFileHeader() = {
    emit(src"import DataImplicits._")
    super.emitFileHeader()
  }
}
