package spatial.interpreter

import argon.core._
import spatial.nodes._
import argon.interpreter.{Interpreter => AInterpreter}

trait RegFiles extends AInterpreter {

  class IRegFile(val dims: Seq[Int], val v: Array[Any]) {
    override def toString = {
      val vs = AInterpreter.stringify(v)
      s"RegFile($dims, $vs)"
    }
    def index(ind: Seq[Int]) = {
      val strides = List.tabulate(dims.length)(i =>
        dims.drop(i+1).fold(1)(_*_)
      )
      val posMult = ind.zip(strides).map { case (a,b) => a*b }
      posMult.sum
    }
    
  }

  object ERegFile {
    def unapply(x: Exp[_]) = Some(eval[IRegFile](x))
  }
  
  override def matchNode(lhs: Sym[_])  = super.matchNode(lhs).orElse {

    case RegFileNew(SeqEI(size)) => 
      variables.get(lhs).getOrElse {
        new IRegFile(size, Array.fill[Any](size.product)(null))
      }

    case RegFileStore(ERegFile(regf), SeqEI(is), EAny(v), EBoolean(en)) =>
      if (en) {
        val i = regf.index(is)
        regf.v(i) = v
      }

    case RegFileLoad(ERegFile(regf), SeqEI(is), EBoolean(en)) =>
      if (en) {
        val i = regf.index(is)
        regf.v(i)
      }

    case ParRegFileLoad(ERegFile(regf), inds, SeqEB(ens)) =>
      inds.zipWithIndex.map { case (ind, i: Int)  => {
        if (ens(i)) {
          val indV = regf.index(SeqEI.unapply(ind).get)
          regf.v(indV) 
        }
        else
          null
      }}.toSeq

    case ParRegFileStore(ERegFile(regf), inds, SeqE(datas), SeqEB(ens)) =>
      inds.zipWithIndex.foreach { case (ind, i: Int)  => {
        if (ens(i)) {
          val indV = regf.index(SeqEI.unapply(ind).get)
          regf.v(indV) = datas(i)
        }
      }}
      


  }

}


