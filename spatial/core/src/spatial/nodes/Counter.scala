package spatial.nodes

import spatial.compiler._

/** Staged types **/
object CounterType extends Type[Counter] {
  override def wrapped(x: Exp[Counter]) = Counter(x)
  override def unwrapped(x: Counter) = x.s
  override def typeArguments = Nil
  override def isPrimitive = false
  override def stagedClass = classOf[Counter]
}


/** IR Nodes **/
case class CounterNew(start: Exp[Index], end: Exp[Index], step: Exp[Index], par: Const[Index]) extends Op[Counter] {
  def mirror(f:Tx) = Counter.counter_new(f(start), f(end), f(step), par)
}

case class Forever() extends Op[Counter] { def mirror(f:Tx) = Counter.forever_counter() }
