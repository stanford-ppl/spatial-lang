package spatial.lang

import forge._
import spatial.nodes._
import spatial.InvalidParallelFactorError

case class Counter(s: Exp[Counter]) extends MetaAny[Counter] {
  @api override def ===(that: Counter) = this.s == that.s
  @api override def =!=(that: Counter) = this.s != that.s
  @api override def toText = MString.ify(this)
}

object Counter {
  implicit def counterIsStaged: Type[Counter] = CounterType

  @api implicit def range2counter(range: Range): Counter = {
    val start = range.start.getOrElse(lift[Int,Index](0))
    val end = range.end
    val step = range.step.getOrElse(lift[Int,Index](1))
    val par = range.p
    counter(start, end, step, par)
  }
  @api implicit def wildcard2counter(wild: Wildcard): Counter = wrap(forever_counter())

  @api def apply(end: Index): Counter = counter(0, end, 1, Some( wrap(intParam(1)) ))
  @api def apply(start: Index, end: Index): Counter = counter(start, end, 1, Some(wrap(intParam(1))))
  @api def apply(start: Index, end: Index, step: Index): Counter = counter(start, end, step, Some(wrap(intParam(1))))
  @api def apply(start: Index, end: Index, step: Index, par: Index): Counter = counter(start, end, step, Some(par))

  @internal def counter(start: Index, end: Index, step: Index, par: Option[Index]): Counter = {
    val p = extractParFactor(par)
    Counter(counter_new(start.s, end.s, step.s, p))
  }
  @internal def forever_counter() = stageUnique(Forever())(ctx)

  def extractParFactor(par: Option[Index])(implicit ctx: SrcCtx): Const[Index] = par.map(_.s) match {
    case Some(x: Const[_]) if isIndexType(x.tp) => x.asInstanceOf[Const[Index]]
    case None => intParam(1)
    case Some(x) => new InvalidParallelFactorError(x)(ctx); intParam(1)
  }

  /** Constructors **/
  def counter_new(start: Exp[Index], end: Exp[Index], step: Exp[Index], par: Const[Index])(implicit ctx: SrcCtx): Sym[Counter] = {
    val counter = stageUnique(CounterNew(start,end,step,par))(ctx)
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
}



trait CounterExp {
  /** Direct methods **/
  //@internal def forever: Counter = Counter(forever_counter())

  /** Internals **/
  def isUnitCounter(x: Exp[Counter]): Boolean = x match {
    case Op(CounterNew(Const(0), Const(1), Const(1), _)) => true
    case _ => false
  }

  def countersOf(x: Exp[CounterChain]): Seq[Exp[Counter]] = x match {
    case Op(CounterChainNew(ctrs)) => ctrs
    case _ => Nil
  }

  private[spatial] def counterStarts(x: Exp[CounterChain]): Seq[Option[Exp[Index]]] = countersOf(x) map {
    case Def(CounterNew(start,_,_,_)) => Some(start)
    case _ => None
  }

  def isForeverCounterChain(x: Exp[CounterChain]): Boolean = countersOf(x).exists(isForever)
  def isUnitCounterChain(x: Exp[CounterChain]): Boolean = countersOf(x).forall(isUnitCounter)
}
