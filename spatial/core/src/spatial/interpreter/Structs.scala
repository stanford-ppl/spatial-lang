package spatial.interpreter

import argon.core._
import argon.nodes._
import argon.lang.Struct
import spatial.nodes._
import argon.interpreter.{Interpreter => AInterpreter}
import spatial.SpatialConfig


trait Structs extends AInterpreter {


  override def matchNode(lhs: Sym[_])  = super.matchNode(lhs).orElse {

    case FieldApply(struct, f) =>
      val x = eval[Seq[(String, Exp[_])]](struct)
      val m = x.toMap
      m(f)


    case SimpleStruct(ab) =>
      ab.map(x => (x._1, eval[Any](x._2)))
  }

}


