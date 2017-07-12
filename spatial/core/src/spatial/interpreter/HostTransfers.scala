package spatial.interpreter

import argon.core._
import spatial.nodes._
import argon.interpreter.{Interpreter => AInterpreter}
import scala.math.BigDecimal

trait HostTransfers extends AInterpreter {

  override def matchNode(lhs: Sym[_])  = super.matchNode(lhs).orElse {

    case ArgInNew(EAny(arg)) =>
      arg

    case ArgOutNew(EAny(arg)) =>
      arg
    
    case SetArg(arg: Sym[_], EAny(value)) =>
      updateVar(arg, value)

    case GetArg(EAny(arg)) =>
      arg

  }

}


