package spatial.interpreter

import argon.core._
import spatial.nodes._
import argon.interpreter.{Interpreter => AInterpreter}

trait Debugging extends AInterpreter {

  override def matchNode  = super.matchNode.orElse {
    case PrintlnIf(EBoolean(cond), EString(str)) =>
      if (cond)
        println(str)
  }

}


