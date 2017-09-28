package spatial.interpreter

import argon.core._
import argon.nodes._
import spatial.nodes._
import argon.interpreter.{Interpreter => AInterpreter}

trait Booleans extends AInterpreter {

  override def matchNode(lhs: Sym[_])  = super.matchNode(lhs).orElse {
    
    case Not(EBoolean(b)) =>
      !b

    case And(EBoolean(a), EBoolean(b)) =>
      a && b

    case Or(EBoolean(a), EBoolean(b)) =>
      a || b      
      
  }

}
