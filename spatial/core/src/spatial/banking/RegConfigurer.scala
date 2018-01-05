package spatial.banking

import argon.analysis._
import argon.core._
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

class RegConfigurer(override val mem: Exp[_], override val strategy: BankingStrategy)(override implicit val IR: State) extends MemoryConfigurer(mem,strategy)(IR) {

  override def unroll(matrix: CompactMatrix, indices: Seq[Exp[Index]]): Seq[AccessMatrix] = {
    lazy val zeros = Array.fill(indices.length)(0)
    lazy val const = Array[AccessVector](AffineVector(zeros,indices,0))

    val accesses = super.unroll(matrix, indices)

    // Registers don't actually have addresses
    accesses.map{case AccessMatrix(_,access,_,id) => AccessMatrix(const,access,indices,id) }
  }


}
