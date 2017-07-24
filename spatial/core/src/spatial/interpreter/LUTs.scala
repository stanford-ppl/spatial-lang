package spatial.interpreter

import argon.core._
import argon.nodes._
import spatial.nodes._
import argon.interpreter.{Interpreter => AInterpreter}

trait LUTs extends AInterpreter {

  class ILUT(val dims: Seq[Int], val v: Array[Any]) {
    override def toString = {
      val vs = AInterpreter.stringify(v)
      s"LUT($dims, $vs)"
    }
    def index(ind: Seq[Int]) = {
      val strides = List.tabulate(dims.length)(i =>
        dims.drop(i+1).fold(1)(_*_)
      )
      val posMult = ind.zip(strides).map { case (a,b) => a*b }
      posMult.sum
    }
    
  }


  object ELUT {
    def unapply(x: Exp[_]) = Some(eval[ILUT](x))
  }
  
  override def matchNode(lhs: Sym[_])  = super.matchNode(lhs).orElse {
    case LUTNew(size, SeqE(data)) =>
      variables.get(lhs).getOrElse {
        new ILUT(size, Array.tabulate[Any](size.product)(i => data(i)))
      }

    case LUTLoad(ELUT(lut), SeqEI(inds), EBoolean(en)) =>
      if (en)
        lut.v(lut.index(inds))
      else
        null

  }
}
