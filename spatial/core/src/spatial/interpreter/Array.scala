package spatial.interpreter

import argon.core._
import argon.nodes._
import spatial.nodes._
import argon.interpreter.{Interpreter => AInterpreter}
import spatial.SpatialConfig

trait IArray extends AInterpreter {

  override def matchNode  = super.matchNode.orElse {

    case ArrayApply(EArray(array), EBigDecimal(i)) =>
      array(i.toInt)

    case InputArguments() =>
      SpatialConfig.inputs
      

  }

}


