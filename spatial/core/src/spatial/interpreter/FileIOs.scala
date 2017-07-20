package spatial.interpreter

import argon.core._
import spatial.nodes._
import argon.interpreter.{Interpreter => AInterpreter}

trait FileIOs extends AInterpreter {

  override def matchNode(lhs: Sym[_])  = super.matchNode(lhs).orElse {  
      case OpenFile(EString(file), _) =>
        io.Source.fromFile(file)

      case ReadTokens(fileS, delim) =>
      eval[io.Source](fileS).getLines
    }
}


