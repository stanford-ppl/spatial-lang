package spatial.interpreter

import argon.core._
import spatial.nodes._
import argon.interpreter.{Interpreter => AInterpreter}

trait Maths extends AInterpreter {

  override def matchNode(lhs: Sym[_])  = super.matchNode(lhs).orElse {

    case Mux(EBoolean(sel), EAny(a), EAny(b)) =>
      if (sel) 
        a
      else
        b

    case Min(EBigDecimal(a), EBigDecimal(b)) =>
      if (a < b)
        a
      else
        b

    case Max(EBigDecimal(a), EBigDecimal(b)) =>
      if (a < b)
        b
      else
        a            

  }

}


