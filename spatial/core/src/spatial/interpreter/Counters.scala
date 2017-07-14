package spatial.interpreter

import argon.core._
import argon.nodes._
import spatial.nodes._
import argon.interpreter.{Interpreter => AInterpreter}
import math.BigDecimal

trait Counters extends AInterpreter {


  override def matchNode(lhs: Sym[_])  = super.matchNode(lhs).orElse {
    case CounterNew(EInt(from), EInt(to), EInt(by), EInt(param)) =>
      Counter(from, to, by, param)
      //      System.exit(0)

    case CounterChainNew(lc) =>
      lc.map(eval[Counterlike])

  }

}

abstract class Counterlike {
  def foreach(moreData: () => Boolean, func: (Array[BigDecimal],Array[Boolean]) => Unit): Unit
  
}

case class Counter(start: Int, end: Int, step: Int, par: Int) extends Counterlike {
  private val parStep = par
  private val fullStep = parStep * step
  private val vecOffsets = Array.tabulate(par){p => p * step}

  def foreach(moreData: () => Boolean, func: (Array[BigDecimal],Array[scala.Boolean]) => Unit): Unit = {
    var i = start
    while (moreData() && {if (step > 0) {i < end} else {i > end}}) {
      val vec = vecOffsets.map{ofs => ofs + i } // Create current vector
      val valids = vec.map{ix => if (step > 0) {ix < end} else {ix > end} }        // Valid bits
      func(vec.map(x => BigDecimal(x)), valids)
      i += fullStep
    }
  }
}

case class ForeverC() extends Counterlike {
  def foreach(moreData: () => Boolean, func: (Array[BigDecimal],Array[Boolean]) => Unit): Unit = {
    val vec = Array.tabulate(1){ofs => BigDecimal(ofs) }  // Create current vector
    val valids = vec.map{ix => true }                 // Valid bits

    while (moreData()) {
      func(vec, valids)
    }
  }
}

