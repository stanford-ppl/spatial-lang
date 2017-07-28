package spatial.interpreter

import argon.core._
import argon.nodes._
import spatial.nodes._
import argon.interpreter.{Interpreter => AInterpreter}


trait DRAMs extends AInterpreter {

  class IDRAM(val dims: Seq[Int], val v: Array[Any]) {
    def index(dims: Seq[Int], ind: Seq[Int], ofs: Int) = {
      val strides = List.tabulate(dims.length)(i =>
        dims.drop(i+1).fold(1)(_*_)
      )
      val posMult = ind.zip(strides).map { case (a,b) => a*b }
      posMult.sum + ofs
    }
    override def toString = {
      val vs = AInterpreter.stringify(v)
      s"DRAM($dims, $vs)"
    }
    
  }


  object EDRAM {
    def unapply(x: Exp[_]) = Some(eval[IDRAM](x))
  }
  
  override def matchNode(lhs: Sym[_])  = super.matchNode(lhs).orElse {
    case DRAMNew(SeqEI(size), EAny(zero)) =>
      variables.get(lhs).getOrElse {
        new IDRAM(size, Array.fill[Any](size.product)(zero))
      }
      
  }

}


