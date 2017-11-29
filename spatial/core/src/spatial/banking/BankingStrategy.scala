package spatial.banking

import argon.core._

trait BankingStrategy {
  implicit val IR: State

  def bankAccesses(
    mem:    Exp[_],                        // Memory to be banked
    dims:   Seq[Int],                      // Bankable dimensions of mem
    reads:  Seq[Set[AccessMatrix]],        // Reads to this banked memory
    writes: Seq[Set[AccessMatrix]],        // Writes to this banked memory
    domain: IndexDomain                    // Iteration domain
  ): Seq[ModBanking]

}
