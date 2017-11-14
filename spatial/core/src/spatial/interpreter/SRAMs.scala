package spatial.interpreter

import argon.core._
import argon.nodes._
import spatial.nodes._
import spatial.utils._
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
      variables.get(lhs).getOrElse{
        new ISRAM(size, Array.fill[Any](size.product)(null))
      }

    case SRAMStore(mem@ESRAM(sram), EAny(data), SeqEI(addr), EBoolean(en)) =>
      if (en) {
        val dims = constDimsOf(mem)
        val i = sram.index(dims, addr, 0)
        sram.v(i) = data
      }

    case BankedSRAMStore(ESRAM(sram), SeqE(data), bank, ofs, SeqEB(ens)) =>
      bank.zipWithIndex.foreach { case (ind, i: Int)  => {
        if (ens(i)) {
          val indV = sram.index(sram.dims, SeqEI.unapply(ind).get, 0)
          sram.v(indV) = data(i)
        }
      }}
 
    case BankedSRAMLoad(ESRAM(sram), bank, ofs, SeqEB(ens)) =>
      bank.zipWithIndex.map { case (ind, i: Int)  =>
        if (ens(i)) {
          val indV = sram.index(sram.dims, SeqEI.unapply(ind).get, 0)
          sram.v(indV) 
        }
        else {
          null
        }
      }
     
      

    case SRAMLoad(mem@ESRAM(sram), SeqEI(addr), EBoolean(en)) =>
      if (en) {
        val dims = constDimsOf(mem)
        val i = sram.index(dims, addr, 0)
        sram.v(i)
      }


  }

}


