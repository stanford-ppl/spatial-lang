package spatial.interpreter

import argon.core._
import argon.nodes._
import spatial.nodes._
import argon.interpreter.{Interpreter => AInterpreter}
import scala.math.BigDecimal

trait FltPts extends AInterpreter {

  override def matchNode(lhs: Sym[_]) = super.matchNode(lhs).orElse {
    
    case StringToFixPt(EString(str)) =>
      BigDecimal(str)
      
    case FltAdd(EBigDecimal(a), EBigDecimal(b)) =>
      a + b

    case FltRandom(maxo) =>
      maxo match {
        case Some(EBigDecimal(max)) =>
          util.Random.nextDouble()*max
        case None =>
          BigDecimal(util.Random.nextDouble())
      }
  }

}
