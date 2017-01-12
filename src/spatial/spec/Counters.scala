package spatial.spec

trait CounterOps extends RangeOps {
  this: SpatialOps =>

  type Counter <: CounterOps
  type CounterChain <: CounterChainOps

  protected trait CounterOps
  protected trait CounterChainOps

  def CounterChain(counters: Counter*)(implicit ctx: SrcCtx): CounterChain
  def Counter(start: Index, end: Index, step: Index, par: Index)(implicit ctx: SrcCtx): Counter

  implicit def range2counter(range: Range)(implicit ctx: SrcCtx): Counter

  implicit val CounterType: Staged[Counter]
  implicit val CounterChainType: Staged[CounterChain]
}
trait CounterApi extends CounterOps with RangeApi { this: SpatialApi => }


trait CounterExp extends CounterOps with RangeExp with SpatialExceptions {
  this: SpatialExp =>

  /** API **/
  case class Counter(s: Sym[Counter]) extends CounterOps
  case class CounterChain(s: Sym[CounterChain]) extends CounterChainOps

  def CounterChain(counters: Counter*)(implicit ctx: SrcCtx): CounterChain = CounterChain(counterchain_new(unwrap(counters)))
  def Counter(start: Index, end: Index, step: Index, par: Index)(implicit ctx: SrcCtx): Counter = {
    counter(start, end, step, Some(par))
  }

  implicit def range2counter(range: Range)(implicit ctx: SrcCtx): Counter = {
    val start = range.start.getOrElse(lift[Int,Index](0))
    val end = range.end
    val step = range.step.getOrElse(lift[Int,Index](1))
    val par = range.p
    counter(start, end, step, par)
  }

  def counter(start: Index, end: Index, step: Index, par: Option[Index])(implicit ctx: SrcCtx): Counter = {
    val p: Const[Index] = par.map(_.s) match {
      case Some(x: Const[_]) if isIndexType(x.tp) => x.asInstanceOf[Const[Index]]
      case None => param(1)
      case Some(x) => new InvalidParallelFactorError(x)(ctx); param(1)
    }
    Counter(counter_new(start.s, end.s, step.s, p))
  }

  /** Staged Types **/
  implicit object CounterType extends Staged[Counter] {
    override def wrapped(x: Sym[Counter]) = Counter(x)
    override def unwrapped(x: Counter) = x.s
    override def typeArguments = Nil
    override def isPrimitive = false
    override def stagedClass = classOf[Counter]
  }
  implicit object CounterChainType extends Staged[CounterChain] {
    override def wrapped(x: Sym[CounterChain]) = CounterChain(x)
    override def unwrapped(x: CounterChain) = x.s
    override def typeArguments = Nil
    override def isPrimitive = false
    override def stagedClass = classOf[CounterChain]
  }



  /** IR Nodes **/
  case class CounterNew(start: Sym[Index], end: Sym[Index], step: Sym[Index], par: Const[Index]) extends Op[Counter] {
    def mirror(f:Tx) = counter_new(f(start), f(end), f(step), par)
  }
  case class CounterChainNew(counters: Seq[Sym[Counter]]) extends Op[CounterChain] {
    def mirror(f:Tx) = counterchain_new(f(counters))
  }

  def counter_new(start: Sym[Index], end: Sym[Index], step: Sym[Index], par: Const[Index])(implicit ctx: SrcCtx): Sym[Counter] = {
    stage(CounterNew(start,end,step,par))(ctx)
  }
  def counterchain_new(counters: Seq[Sym[Counter]])(implicit ctx: SrcCtx) = stage(CounterChainNew(counters))(ctx)

}
