package spatial.interpreter

import argon.core._
import spatial.nodes._
import argon.interpreter.{Interpreter => AInterpreter}

trait Debuggings extends AInterpreter {

  override def matchNode(lhs: Sym[_])  = super.matchNode(lhs).orElse {
    case PrintlnIf(EBoolean(cond), EString(str)) =>
      if (cond)
        println(str)
  }

}


