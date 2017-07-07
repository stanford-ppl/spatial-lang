package spatial.interpreter

import argon.core._
import spatial.nodes._
import argon.interpreter.{Interpreter => AInterpreter}

trait Reg extends AInterpreter {

  override def matchNode  = super.matchNode.orElse {

    case RegWrite(a: Sym[_], b, c) =>
      if (eval[Boolean](c)) {
        val x = eval[Any](b)
        updateVar(a, x)
      }

    case RegRead(a: Sym[_]) =>
      variables(a)

  }

}


