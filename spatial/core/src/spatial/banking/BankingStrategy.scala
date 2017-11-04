package spatial.banking

import argon.core._

trait BankingStrategy {
  implicit val IR: State

  def bankAccesses(
    mem:    Exp[_],                   // Memory to be banked
    reads:  Set[AccessMatrix],        // Reads to this banked memory
    writes: Set[AccessMatrix],        // Writes to this banked memory
    domain: Array[(Array[Int],Int)]   // Iteration domain
  ): Seq[ModBanking]

}
