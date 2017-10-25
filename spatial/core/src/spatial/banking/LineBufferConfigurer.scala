package spatial.banking

import argon.analysis._
import argon.core._
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

class LineBufferConfigurer(override val mem: Exp[_]) extends AddressedMemoryConfigurer(mem) {

  override def getAccessVector(access: Access): Seq[Seq[AccessVector]] = access.node match {
    case Def(LineBufferColSlice(_, row, col, Exact(len))) => accessPatternOf(access.node).last match {
      case BankableAffine(as, is, ps, b) =>
        Seq.tabulate(len.toInt) { c => Seq(DenseAccessVector(as, is, is.map {_ => 1}, b + c, access)) }
      case _ =>
        // EXPERIMENTAL: Treat the col address as its own index
        Seq.tabulate(len.toInt) { c => Seq(DenseAccessVector(Array(1), Seq(col), Seq(1), c, access)) }
    }
    case _ => accessPatternOf(access.node).last match {
      case BankableAffine(as, is, ps, b) => Seq(Seq(DenseAccessVector(as, is, ps, b, access)))
      case _ => Seq(Seq(RandomAddress(access)))
    }
  }

}
