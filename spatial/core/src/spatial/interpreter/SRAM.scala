package spatial.interpreter

import argon.core._
import argon.nodes._
import spatial.nodes._
import argon.interpreter.{Interpreter => AInterpreter}


trait ISRAM extends AInterpreter {


  override def matchNode  = super.matchNode.orElse {
    case SRAMNew(size) =>
      val ab = size.map(x => eval[BigDecimal](x).toInt)
      ab.size match {
        case 1 =>
          Array.fill[Any](ab(0))(null)
        case 2 =>
          Array.fill[Any](ab(0), ab(1))(null)
        case _ =>
          ???
      }

  }

}


