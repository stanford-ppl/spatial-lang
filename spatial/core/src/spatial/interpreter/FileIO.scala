package spatial.interpreter

import argon.core._
import spatial.nodes._
import argon.interpreter.{Interpreter => AInterpreter}

trait FileIO extends AInterpreter {

  override def matchNode  = super.matchNode.orElse {  
      case OpenFile(a, b) =>
        val x = eval[String](a)
        val y = eval[Boolean](a)
        new java.io.File(x)

      case ReadTokens(file, delim) =>
        ()
    }
}


