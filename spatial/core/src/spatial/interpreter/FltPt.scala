package spatial.interpreter

import argon.core._
import argon.nodes._
import spatial.nodes._
import argon.interpreter.{Interpreter => AInterpreter}
import scala.math.BigDecimal

trait FltPt extends AInterpreter {

  override def matchNode  = super.matchNode.orElse {
    
    case StringToFixPt(EString(str)) =>
      BigDecimal(str)
      
    case FltAdd(EBigDecimal(a), EBigDecimal(b)) =>
      a + b

  }

}
