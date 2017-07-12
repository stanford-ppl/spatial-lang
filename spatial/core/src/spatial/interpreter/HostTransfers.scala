package spatial.interpreter

import argon.core._
import spatial.nodes._
import argon.interpreter.{Interpreter => AInterpreter}
import scala.math.BigDecimal

trait HostTransfers extends AInterpreter {
  this: Regs =>

  override def matchNode(lhs: Sym[_])  = super.matchNode(lhs).orElse {

    case ArgInNew(EAny(arg)) =>
      IReg(arg)

    case ArgOutNew(EAny(arg)) =>
      IReg(arg)
    
    case SetArg(arg: Sym[_], EAny(value)) =>
      updateVar(arg, IReg(value))

    case GetArg(EAny(arg)) =>
      arg.asInstanceOf[IReg].v

  }

}


