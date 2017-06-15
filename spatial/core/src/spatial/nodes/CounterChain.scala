package spatial.nodes

import argon.internals._
import spatial.compiler._

object CounterChainType extends Type[CounterChain] {
  override def wrapped(x: Exp[CounterChain]) = CounterChain(x)
  override def unwrapped(x: CounterChain) = x.s
  override def typeArguments = Nil
  override def isPrimitive = false
  override def stagedClass = classOf[CounterChain]
}

case class CounterChainNew(counters: Seq[Exp[Counter]]) extends DynamicAlloc[CounterChain] {
  def mirror(f:Tx) = CounterChain.fromseq(f(counters))
}
