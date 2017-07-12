package spatial.interpreter

import argon.core._
import spatial.nodes._
import argon.interpreter.{Interpreter => AInterpreter}

trait Regs extends AInterpreter {

  case class IReg(v: Any)

  override def matchNode(lhs: Sym[_])  = super.matchNode(lhs).orElse {

    case RegWrite(sym: Sym[_], EAny(value), EBoolean(cond)) =>
      if (cond) {
        updateVar(sym, IReg(value))
      }

    case RegRead(sym: Sym[_]) =>
      variables(sym).asInstanceOf[IReg].v

    case RegNew(EAny(init)) =>
      variables.get(lhs).getOrElse(IReg(init))

  }

}


