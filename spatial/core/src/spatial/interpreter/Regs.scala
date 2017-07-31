package spatial.interpreter

import argon.core._
import spatial.nodes._
import argon.interpreter.{Interpreter => AInterpreter}

trait Regs extends AInterpreter {

  class IReg(val r: Any) {
    var v: Any = r
    override def toString = {
      val vs = AInterpreter.stringify(v)
      s"Reg($vs)"
    }

    def reset =
      v = r

  }

  object EReg {
    def unapply(x: Exp[_]) = Some(eval[IReg](x))
  }
  
  override def matchNode(lhs: Sym[_])  = super.matchNode(lhs).orElse {

    case RegNew(EAny(init)) =>
      variables
        .get(lhs)
        .getOrElse(new IReg(init))

    case RegRead(EReg(reg)) =>
      reg.v

    case RegReset(EReg(reg), EBoolean(en)) =>
      if (en)
        reg.reset
      
    case RegWrite(EReg(reg), EAny(v), EBoolean(cond)) =>
      if (cond) {
        reg.v = v
      }

    case VarRegNew(_) =>
      variables
        .get(lhs)
        .getOrElse(new IReg(null))
      
    case VarRegRead(EReg(reg)) =>
      reg.v
      
    case VarRegWrite(EReg(reg), EAny(v), EBoolean(cond)) =>
      if (cond) {
        reg.v = v
      }

  }

}


