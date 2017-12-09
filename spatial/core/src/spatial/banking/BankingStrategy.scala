package spatial.banking

import argon.core._

trait BankingStrategy {
  implicit val IR: State

  def bankAccesses(
    mem:    Exp[_],                        // Memory to be banked
    dims:   Seq[Int],                      // Bankable dimensions of mem
    reads:  Seq[Set[AccessMatrix]],        // Reads to this banked memory
    writes: Seq[Set[AccessMatrix]],        // Writes to this banked memory
    domain: IndexDomain,                   // Iteration domain
    dimGrps: Seq[Seq[Seq[Int]]]            // Sequence of dimension groupings
                                           // Seq(Seq(0), Seq(1)) is hierarchical banking for a 2D memory
                                           // Seq(Seq(0,1)) is flattened banking for a 2D memory

  ): Seq[Seq[ModBanking]]                  // Sequence of possible multidimensional banking

}
