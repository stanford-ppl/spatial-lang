package spatial.interpreter

import argon.core._
import argon.nodes._
import spatial.nodes._
import argon.interpreter.{Interpreter => AInterpreter}

trait IString extends AInterpreter {

  override def matchNode  = super.matchNode.orElse {

    case StringConcat(a, b) =>
      eval[String](a) + eval[String](b)

    case ToString(a) =>
      eval[Any](a).toString

  }

}


