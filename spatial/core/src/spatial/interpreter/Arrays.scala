package spatial.interpreter

import argon.core._
import argon.nodes._
import spatial.aliases._
import spatial.nodes._
import argon.interpreter.{Interpreter => AInterpreter}

trait Arrays extends AInterpreter {

  override def matchNode(lhs: Sym[_])  = super.matchNode(lhs).orElse {

    case ArrayApply(EArray(array), EInt(i)) =>
      array(i.toInt)

    case InputArguments() => spatialConfig.inputs

    case MapIndices(_, _, _) =>
      ???
      

  }

}


