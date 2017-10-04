package spatial.interpreter

import argon.core._
import argon.nodes._
import argon.interpreter.{Interpreter => AInterpreter}


trait Structs extends AInterpreter {

  override def matchNode(lhs: Sym[_])  = super.matchNode(lhs).orElse {

    case FieldApply(struct, f) =>
      val x = eval[Seq[(String, Exp[_])]](struct)
      if (x != null) {
        val m = x.toMap
        eval[Any](m(f))
      }
      else
        null


    case SimpleStruct(ab) =>
      ab.map(x => (x._1, new Const(x._2.tp)(eval[Any](x._2))))
  }

}


