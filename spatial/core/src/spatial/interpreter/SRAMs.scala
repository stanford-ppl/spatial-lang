package spatial.interpreter

import argon.core._
import argon.nodes._
import spatial.nodes._
import argon.interpreter.{Interpreter => AInterpreter}


trait SRAMs extends AInterpreter {

  case class ISRAM1(v: Array[Any])
  case class ISRAM2(v: Array[Array[Any]])

  override def matchNode(lhs: Sym[_])  = super.matchNode(lhs).orElse {
    case SRAMNew(size) =>
      variables.get(lhs).getOrElse {
        val ab = size.map(x => eval[BigDecimal](x).toInt)
        ab.size match {
          case 1 =>
            ISRAM1(Array.fill[Any](ab(0))(null))
          case 2 =>
            ISRAM2(Array.fill[Any](ab(0), ab(1))(null))
          case _ =>
            ???
        }
      }

    case SRAMStore(sram, dim, pose, ofs, EAny(v), EBoolean(en)) =>
      if (en) {
        val pos = pose.map(x => eval[BigDecimal](x).toInt)
        dim.size match {
          case 1 =>
            val is = eval[ISRAM1](sram)
            is.v(pos(0)) = v
          case 2 =>
            val is = eval[ISRAM2](sram)
            is.v(pos(0))(pos(1)) = v
          case _ =>
            ???
        }
      }


  }

}


