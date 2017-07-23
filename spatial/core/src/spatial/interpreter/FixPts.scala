package spatial.interpreter

import argon.core._
import argon.nodes._
import spatial.nodes._
import argon.interpreter.{Interpreter => AInterpreter}
import scala.math.BigDecimal

trait FixPts extends AInterpreter {

  override def matchNode(lhs: Sym[_])  = super.matchNode(lhs).orElse {
    
    case StringToFixPt(EString(str)) =>
      BigDecimal(str)
      
    case FixAdd(EBigDecimal(a), EBigDecimal(b)) =>
      a + b

    case FixSub(EBigDecimal(a), EBigDecimal(b)) =>
      a - b

    case FixMul(EBigDecimal(a), EBigDecimal(b)) =>
      a * b
      
    case FixNeg(EBigDecimal(a)) =>
      -a
      

    case FixPtToFltPt(fixp) =>
      eval[Float](fixp)

    case FixEql(EBigDecimal(a), EBigDecimal(b)) =>
      a == b

    case FixNeq(EBigDecimal(a), EBigDecimal(b)) =>
      a != b

    case FixConvert(EBigDecimal(a)) =>
      a
      
//    case FixLog(EBigDecimal(a)) =>
//      BigDecimal(Math.log(a.toDouble)) 

//    case FixExp(EBigDecimal(a)) =>
//      BigDecimal(Math.exp(a.toDouble)) 
      
//    case FixSqrt(EBigDecimal(a)) =>
//      BigDecimal(Math.sqrt(a.toDouble))

//    case FixAcos(EBigDecimal(a)) =>
//      BigDecimal(Math.acos(a.toDouble))
      
//    case FixSin(EBigDecimal(a)) =>
//      BigDecimal(Math.sin(a.toDouble))

//    case FixCos(EBigDecimal(a)) =>
//      BigDecimal(Math.cos(a.toDouble))


    case FixLt(EBigDecimal(a), EBigDecimal(b)) =>
      if (a != null && b != null)
        a < b
      else
        null

    case FixLeq(EBigDecimal(a), EBigDecimal(b)) =>
      if (a != null && b != null)
        a <= b
      else
        null
      
    case FixDiv(EBigDecimal(a), EBigDecimal(b)) =>
      a / b

    case FixMod(EBigDecimal(a), EBigDecimal(b)) =>
      a % b

      
    case FltMul(EBigDecimal(a), EBigDecimal(b)) =>
      a * b      
      
    case FixRandom(maxo) =>
      maxo match {
        case Some(EBigDecimal(max)) =>
          (util.Random.nextDouble()*max)
        case None =>
          BigDecimal(util.Random.nextDouble*Double.MaxValue)
      }
      
  }

}
