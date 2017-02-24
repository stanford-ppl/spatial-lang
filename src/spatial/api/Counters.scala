package spatial.api

import argon.core.Staging
import spatial.SpatialExp

trait CounterApi extends CounterExp {
  this: SpatialExp =>

  def *()(implicit ctx: SrcCtx): Counter = forever
}

trait CounterExp extends Staging with RangeExp with SpatialExceptions {
  this: SpatialExp =>

  /** API **/
  case class Counter(s: Exp[Counter])
  case class CounterChain(s: Exp[CounterChain])

  /** Direct methods **/
  def CounterChain(counters: Counter*)(implicit ctx: SrcCtx): CounterChain = CounterChain(counterchain_new(unwrap(counters)))
  def Counter(start: Index, end: Index, step: Index, par: Index)(implicit ctx: SrcCtx): Counter = {
    counter(start, end, step, Some(par))
  }
  def Counter(end: Index)(implicit ctx: SrcCtx): Counter = counter(0, end, 1, Some( wrap(intParam(1)) ))
  def Counter(start: Index, end: Index)(implicit ctx: SrcCtx): Counter = counter(start, end, 1, Some(wrap(intParam(1))))
  def Counter(start: Index, end: Index, step: Index)(implicit ctx: SrcCtx): Counter = counter(start, end, step, Some(wrap(intParam(1))))

  implicit def range2counter(range: Range)(implicit ctx: SrcCtx): Counter = {
    val start = range.start.getOrElse(lift[Int,Index](0))
    val end = range.end
    val step = range.step.getOrElse(lift[Int,Index](1))
    val par = range.p
    counter(start, end, step, par)
  }

  def extractParFactor(par: Option[Index])(implicit ctx: SrcCtx): Const[Index] = par.map(_.s) match {
    case Some(x: Const[_]) if isIndexType(x.tp) => x.asInstanceOf[Const[Index]]
    case None => intParam(1)
    case Some(x) => new InvalidParallelFactorError(x)(ctx); intParam(1)
  }

  def counter(start: Index, end: Index, step: Index, par: Option[Index])(implicit ctx: SrcCtx): Counter = {
    val p = extractParFactor(par)
    Counter(counter_new(start.s, end.s, step.s, p))
  }

  def forever(implicit ctx: SrcCtx): Counter = Counter(forever_counter())

  /** Staged types **/
  implicit object CounterType extends Staged[Counter] {
    override def wrapped(x: Exp[Counter]) = Counter(x)
    override def unwrapped(x: Counter) = x.s
    override def typeArguments = Nil
    override def isPrimitive = false
    override def stagedClass = classOf[Counter]
  }
  implicit object CounterChainType extends Staged[CounterChain] {
    override def wrapped(x: Exp[CounterChain]) = CounterChain(x)
    override def unwrapped(x: CounterChain) = x.s
    override def typeArguments = Nil
    override def isPrimitive = false
    override def stagedClass = classOf[CounterChain]
  }



  /** IR Nodes **/
  case class CounterNew(start: Exp[Index], end: Exp[Index], step: Exp[Index], par: Const[Index]) extends Op[Counter] {
    def mirror(f:Tx) = counter_new(f(start), f(end), f(step), par)
  }
  case class CounterChainNew(counters: Seq[Exp[Counter]]) extends Op[CounterChain] {
    def mirror(f:Tx) = counterchain_new(f(counters))
  }

  case class Forever() extends Op[Counter] { def mirror(f:Tx) = forever_counter() }

  /** Constructors **/
  def counter_new(start: Exp[Index], end: Exp[Index], step: Exp[Index], par: Const[Index])(implicit ctx: SrcCtx): Sym[Counter] = {
    val counter = stageCold(CounterNew(start,end,step,par))(ctx)
    par match {
      case Const(0) =>
        warn(ctx)
        warn(ctx, u"Counter $counter has parallelization of 0")
      case _ =>
    }
    step match {
      case Const(0) =>
        warn(ctx)
        warn(ctx, u"Counter $counter has step of 0")
      case _ =>
    }
    counter
  }
  def counterchain_new(counters: Seq[Exp[Counter]])(implicit ctx: SrcCtx) = stageCold(CounterChainNew(counters))(ctx)

  def forever_counter()(implicit ctx: SrcCtx) = stageCold(Forever())(ctx)

  /** Internals **/
  def isUnitCounter(x: Exp[Counter]): Boolean = x match {
    case Op(CounterNew(Const(0), Const(1), Const(1), _)) => true
    case _ => false
  }

  def countersOf(x: Exp[CounterChain]): Seq[Exp[Counter]] = x match {
    case Op(CounterChainNew(ctrs)) => ctrs
    case _ => Nil
  }
  def isForeverCounterChain(x: Exp[CounterChain]): Boolean = countersOf(x).exists(isForever)
  def isUnitCounterChain(x: Exp[CounterChain]): Boolean = countersOf(x).forall(isUnitCounter)
}
