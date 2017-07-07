package spatial.interpreter

import argon.core._
import argon.nodes._
import spatial.nodes._
import argon.interpreter.{Interpreter => AInterpreter}
import scala.math.BigDecimal

trait IArray extends AInterpreter {

  override def matchNode  = super.matchNode.orElse {

    case ArrayApply(a, i) =>
      eval[Array[_]](a)(eval[BigDecimal](i).toInt)


    case InputArguments() =>
      Array.tabulate(10)(x => (x + 1).toString)
      

  }

}


