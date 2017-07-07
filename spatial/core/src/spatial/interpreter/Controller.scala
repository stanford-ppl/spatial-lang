package spatial.interpreter

import argon.core._
import spatial.nodes._
import argon.interpreter.{Interpreter => AInterpreter}

trait Controller extends AInterpreter {

  override def matchNode  = super.matchNode.orElse {  
    case Hwblock(block,isForever) =>
      interpretBlock(block)
  }

}
