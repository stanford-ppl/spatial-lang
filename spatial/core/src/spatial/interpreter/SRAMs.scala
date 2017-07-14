package spatial.interpreter

import argon.core._
import argon.nodes._
import spatial.nodes._
import argon.interpreter.{Interpreter => AInterpreter}


trait SRAMs extends AInterpreter {

  case class ISRAM(dims: Seq[Int], v: Array[Any]) {
    def index(dims: Seq[Int], ind: Seq[Int], ofs: Int) = {
      val strides = List.tabulate(dims.length)(i =>
        dims.drop(i+1).fold(1)(_*_)
      )
      val posMult = ind.zip(strides).map { case (a,b) => a*b }
      posMult.sum + ofs
    }
    override def toString = {
      val vs = AInterpreter.stringify(v)
      s"SRAM($vs)"
    }
    
  }

  override def matchNode(lhs: Sym[_])  = super.matchNode(lhs).orElse {
    case SRAMNew(size) =>
      variables.get(lhs).getOrElse {
        val ab = size.map(x => eval[BigDecimal](x).toInt)
        ISRAM(ab, Array.fill[Any](ab.product)(null))
      }

    case SRAMStore(sram, dims, is, EInt(ofs), EAny(v), EBoolean(en)) =>
      if (en) {
        val isram = eval[ISRAM](sram)
        val ise = is.map(x =>  eval[BigDecimal](x).toInt)
        val dimse = dims.map(x => eval[BigDecimal](x).toInt)
        val i = isram.index(dimse, ise, ofs)
        isram.v(i) = v
      }

    case ParSRAMStore(sram, inds, data, ens) =>
      val isram = eval[ISRAM](sram)
      val ense = ens.map(eval[Boolean])
      inds.zipWithIndex.foreach { case (ind, i: Int)  => {
        if (ense(i)) {
          val ise = ind.map(x => eval[BigDecimal](x).toInt)
          val indV = isram.index(isram.dims, ise, 0)
          isram.v(indV) = eval[Any](data(i))
        }
      }}
      

    case SRAMLoad(sram, dims, is, EInt(ofs), EBoolean(en)) =>
      if (en) {
        val isram = eval[ISRAM](sram)
        val ise = is.map(x => {println(x); val bd = eval[BigDecimal](x).toInt; println(bd); bd})
        val dimse = dims.map(x => eval[BigDecimal](x).toInt)
        val i = isram.index(dimse, ise, ofs)
        isram.v(i)
      }


  }

}


