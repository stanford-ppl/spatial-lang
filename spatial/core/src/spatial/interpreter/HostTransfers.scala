package spatial.interpreter

import argon.core._
import spatial.nodes._
import argon.interpreter.{Interpreter => AInterpreter}
import scala.math.BigDecimal

trait HostTransfers extends AInterpreter {
  this: Regs =>

  override def matchNode(lhs: Sym[_])  = super.matchNode(lhs).orElse {

    case ArgInNew(EAny(arg)) =>
      new IReg(arg)

    case ArgOutNew(EAny(arg)) =>
      new IReg(arg)
    
    case SetArg(EReg(reg), EAny(v)) =>
      reg.v = v

    case GetArg(EReg(reg)) =>
      reg.v

  }

}


