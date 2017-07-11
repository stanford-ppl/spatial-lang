package spatial.interpreter

import argon.core._
import argon.nodes._
import spatial.nodes._
import argon.interpreter.{Interpreter => AInterpreter}
import scala.math.BigDecimal

trait FixPt extends AInterpreter {

  override def matchNode  = super.matchNode.orElse {
    
    case StringToFixPt(EString(str)) =>
      BigDecimal(str)
      
    case FixAdd(EBigDecimal(a), EBigDecimal(b)) =>
      a + b

    case FixPtToFltPt(fixp) =>
      eval[Float](fixp)

    case FixEql(EAny(a), EAny(b)) =>
      a == b
  }

}
