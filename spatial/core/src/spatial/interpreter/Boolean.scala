package spatial.interpreter

import argon.core._
import argon.nodes._
import spatial.nodes._
import argon.interpreter.{Interpreter => AInterpreter}
import scala.math.BigDecimal

trait IBoolean extends AInterpreter {

  override def matchNode  = super.matchNode.orElse {
    case Not(EBoolean(b)) =>
      !b
  }

}
