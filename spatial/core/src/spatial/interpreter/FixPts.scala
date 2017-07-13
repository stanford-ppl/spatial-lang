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

    case FixPtToFltPt(fixp) =>
      eval[Float](fixp)

    case FixEql(EBigDecimal(a), EBigDecimal(b)) =>
      a == b

    case FixLt(EBigDecimal(a), EBigDecimal(b)) =>
      a < b
      
    case FixRandom(maxo) =>
      maxo match {
        case Some(EBigDecimal(max)) =>
          (util.Random.nextDouble()*max)
        case None =>
          BigDecimal(util.Random.nextDouble*Double.MaxValue)
      }
      
  }

}
