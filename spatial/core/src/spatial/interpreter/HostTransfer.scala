package spatial.interpreter

import argon.core._
import spatial.nodes._
import argon.interpreter.{Interpreter => AInterpreter}
import scala.math.BigDecimal

trait HostTransfer extends AInterpreter {

  override def matchNode  = super.matchNode.orElse {

    case ArgInNew(EBigDecimal(arg)) =>
      arg

    case ArgOutNew(EBigDecimal(arg)) =>
      arg
    
    case SetArg(arg: Sym[_], EAny(value)) =>
      updateVar(arg, value)

    case GetArg(EAny(arg)) =>
      arg

  }

}


