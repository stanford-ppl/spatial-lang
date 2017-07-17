package spatial.interpreter

import argon.core._
import spatial.nodes._
import argon.interpreter.{Interpreter => AInterpreter}

trait Regs extends AInterpreter {

  class IReg(var v: Any) {
    override def toString = {
      val vs = AInterpreter.stringify(v)
      s"Reg($vs)"
    }
  }

  object EReg {
    def unapply(x: Exp[_]) = Some(eval[IReg](x))
  }
  
  override def matchNode(lhs: Sym[_])  = super.matchNode(lhs).orElse {

    case RegWrite(EReg(reg), EAny(v), EBoolean(cond)) =>
      if (cond) {
        reg.v = v
      }

    case RegRead(EReg(reg)) =>
      reg.v

    case RegNew(EAny(init)) =>
      variables
        .get(lhs)
        .getOrElse(new IReg(init))

  }

}


