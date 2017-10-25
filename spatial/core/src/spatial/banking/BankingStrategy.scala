package spatial.banking

import argon.core._
import spatial.aliases._

trait BankingStrategy {
  def registerIndices(indices: Seq[Exp[Index]]): Unit
  def bankAccesses(reads: Set[AccessMatrix], writes: Set[AccessMatrix]): Seq[ModBanking]
}
