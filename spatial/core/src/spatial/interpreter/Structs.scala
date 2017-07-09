package spatial.interpreter

import argon.core._
import argon.nodes._
import argon.lang.Struct
import spatial.nodes._
import argon.interpreter.{Interpreter => AInterpreter}
import spatial.SpatialConfig


trait IStruct extends AInterpreter {

  override def matchNode  = super.matchNode.orElse {

    case FieldApply(struct, f) =>
//      val x = eval[Struct[_]](struct)
//      println(struct, f, x)
      //      eval[Struct[_]](struct).fields(f)
      System.exit(0)

    case SimpleStruct(ab) =>
      ab
  }

}


