package spatial.codegen.scalagen

import argon.codegen.scalagen.ScalaGenStructs
import argon.ops.StructExp
import spatial.SpatialExp

trait ScalaGenSpatialStruct extends ScalaGenStructs with ScalaGenBits {
  val IR: SpatialExp
  import IR._

  override def invalid(tp: Type[_]): String = tp match {
    case struct: StructType[_] => src"""$struct(${struct.fields.map(_._2).map(invalid).mkString(", ")})"""

    case _ => super.invalid(tp)
  }

}
