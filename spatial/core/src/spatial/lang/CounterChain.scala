package spatial.lang

import forge._
import spatial.nodes._

case class CounterChain(s: Exp[CounterChain]) extends Template[CounterChain]

object CounterChain {
  implicit def counterchainIsStaged: Type[CounterChain] = CounterChainType

  /** Static Methods **/
  @api def apply(counters: Counter*): CounterChain = CounterChain(fromseq(unwrap(counters)))

  /** Constructors **/
  @internal def fromseq(counters: Seq[Exp[Counter]]) = stageUnique(CounterChainNew(counters))(ctx)
}
