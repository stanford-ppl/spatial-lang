package spatial.interpreter

import argon.core._
import spatial.nodes._
import argon.interpreter.{Interpreter => AInterpreter}

trait Debugging extends AInterpreter {

  override def matchNode  = super.matchNode.orElse {
    case PrintlnIf(a, b) =>
      if (eval[Boolean](a))
        println(eval[Any](b).toString)
  }

}


