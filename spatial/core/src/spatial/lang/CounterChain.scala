package spatial.lang

import argon.core._
import forge._
import spatial.nodes._

case class CounterChain(s: Exp[CounterChain]) extends Template[CounterChain]

object CounterChain {
  implicit def counterchainIsStaged: Type[CounterChain] = CounterChainType

  /**Creates a chain of counters. Order is specified as outermost on the left to innermost on the right. **/
  @api def apply(counters: Counter*): CounterChain = CounterChain(fromseq(unwrap(counters)))

  /** Constructors **/
  @internal def fromseq(counters: Seq[Exp[Counter]]) = stageUnique(CounterChainNew(counters))(ctx)
}
