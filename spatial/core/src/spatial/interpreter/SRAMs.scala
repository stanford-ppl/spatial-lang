package spatial.interpreter

import argon.core._
import argon.nodes._
import spatial.nodes._
import argon.interpreter.{Interpreter => AInterpreter}


trait SRAMs extends AInterpreter {

  class ISRAM(val dims: Seq[Int], val v: Array[Any]) {
    def index(dims: Seq[Int], ind: Seq[Int], ofs: Int) = {
      val strides = List.tabulate(dims.length)(i =>
        dims.drop(i+1).fold(1)(_*_)
      )
      val posMult = ind.zip(strides).map { case (a,b) => a*b }
      posMult.sum + ofs
    }
    override def toString = {
      val vs = AInterpreter.stringify(v)
      s"SRAM($dims, $vs)"
    }
    
  }


  object ESRAM {
    def unapply(x: Exp[_]) = Some(eval[ISRAM](x))
  }
  
  override def matchNode(lhs: Sym[_])  = super.matchNode(lhs).orElse {
    case SRAMNew(SeqEI(size)) =>
      variables.get(lhs).getOrElse {
        new ISRAM(size, Array.fill[Any](size.product)(null))
      }

    case SRAMStore(ESRAM(sram), SeqEI(dims), SeqEI(is), EInt(ofs), EAny(v), EBoolean(en)) =>
      if (en) {
        val i = sram.index(dims, is, ofs)
        sram.v(i) = v
      }

    case ParSRAMStore(ESRAM(sram), inds, SeqE(data), SeqEB(ens)) =>
      inds.zipWithIndex.foreach { case (ind, i: Int)  => {
        if (ens(i)) {
          val indV = sram.index(sram.dims, SeqEI.unapply(ind).get, 0)
          sram.v(indV) = data(i)
        }
      }}
 
    case ParSRAMLoad(ESRAM(sram), inds, SeqEB(ens)) =>
      inds.zipWithIndex.map { case (ind, i: Int)  => {
        if (ens(i)) {
          val indV = sram.index(sram.dims, SeqEI.unapply(ind).get, 0)
          sram.v(indV) 
        }
        else {
          null
        }
      }}.toSeq
     
      

    case SRAMLoad(ESRAM(sram), SeqEI(dims), SeqEI(is), EInt(ofs), EBoolean(en)) =>
      if (en) {
        val i = sram.index(dims, is, ofs)
        sram.v(i)
      }


  }

}


