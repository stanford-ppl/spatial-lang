package spatial.interpreter

import argon.core._
import spatial.nodes._
import argon.interpreter.{Interpreter => AInterpreter}

trait Reg extends AInterpreter {

  override def matchNode  = super.matchNode.orElse {

    case RegWrite(sym: Sym[_], EAny(value), EBoolean(cond)) =>
      if (cond) {
        updateVar(sym, value)
      }

    case RegRead(sym: Sym[_]) =>
      variables(sym)

    case RegNew(EAny(init)) =>
      init

  }

}


