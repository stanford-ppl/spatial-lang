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

    case FltSub(EBigDecimal(a), EBigDecimal(b)) =>
      a - b

    case FltNeg(EBigDecimal(a)) =>
      -a

    case FltSqrt(EBigDecimal(a)) =>
      BigDecimal(Math.sqrt(a.toDouble))

    case FltAcos(EBigDecimal(a)) =>
      BigDecimal(Math.acos(a.toDouble))
      
    case FltSin(EBigDecimal(a)) =>
      BigDecimal(Math.sin(a.toDouble))

    case FltCos(EBigDecimal(a)) =>
      BigDecimal(Math.cos(a.toDouble))
      
    case FltMul(EBigDecimal(a), EBigDecimal(b)) =>
      a * b
      
    case FltDiv(EBigDecimal(a), EBigDecimal(b)) =>
      a / b
      
    case FltLog(EBigDecimal(a)) =>
      BigDecimal(Math.log(a.toDouble)) 

    case FltExp(EBigDecimal(a)) =>
      BigDecimal(Math.exp(a.toDouble)) 
      

    case FltLt(EBigDecimal(a), EBigDecimal(b)) =>
      if (a != null && b != null)
        a < b
      else
        null

    case FltLeq(EBigDecimal(a), EBigDecimal(b)) =>
      if (a != null && b != null)
        a <= b
      else
        null
      
    case FltRandom(maxo) =>
      maxo match {
        case Some(EBigDecimal(max)) =>
          util.Random.nextDouble()*max
        case None =>
          BigDecimal(util.Random.nextDouble())
      }

    case FltPtToFixPt(EBigDecimal(a)) =>
        a
  }

}
