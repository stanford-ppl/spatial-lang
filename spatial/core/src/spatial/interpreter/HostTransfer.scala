package spatial.interpreter

import argon.core._
import spatial.nodes._
import argon.interpreter.{Interpreter => AInterpreter}
import scala.math.BigDecimal

trait HostTransfer extends AInterpreter {

  override def matchNode  = super.matchNode.orElse {

    case ArgInNew(a) =>
      eval[BigDecimal](a)

    case ArgOutNew(a) =>
      eval[BigDecimal](a)
    
    case SetArg(a: Sym[_], b) =>
      val x = eval[Any](b)
      updateVar(a, x)

    case GetArg(a) =>
      eval[Any](a)

  }

}


