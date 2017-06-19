package spatial.codegen.scalagen

import argon.core._
import argon.codegen.scalagen.ScalaGenStructs
import argon.nodes._
import spatial.aliases._

trait ScalaGenSpatialStruct extends ScalaGenStructs with ScalaGenBits {

  override def invalid(tp: Type[_]): String = tp match {
    case struct: StructType[_] => src"""$struct(${struct.fields.map(_._2).map(invalid).mkString(", ")})"""
    case _ => super.invalid(tp)
  }

}
