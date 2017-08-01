package spatial.interpreter

import argon.core._
import argon.nodes._
import spatial.nodes._
import argon.interpreter.{Interpreter => AInterpreter}
import spatial.SpatialConfig

trait Arrays extends AInterpreter {

  override def matchNode(lhs: Sym[_])  = super.matchNode(lhs).orElse {

    case ArrayApply(EArray(array), EInt(i)) =>
      array(i.toInt)

    case InputArguments() =>
      SpatialConfig.inputs

    case MapIndices(_, _, _) =>
      ???
      

  }

}


