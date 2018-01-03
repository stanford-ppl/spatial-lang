package spatial.lang
package control

import argon.core._
import forge._
import spatial.metadata._
import spatial.nodes._

protected class ReduceAccum[T](accum: Option[Reg[T]], style: ControlStyle, ii: Option[Double], zero: Option[T], fold: Option[T]) {
  /** 1 dimensional reduction **/
  @api def apply(domain1D: Counter)(map: Index => T)(reduce: (T,T) => T)(implicit mT: Type[T], bits: Bits[T]): Reg[T] = {
    val acc = accum.getOrElse(Reg[T])
    Reduce.alloc(List(domain1D), acc, {x: List[Index] => map(x.head)}, reduce, style, ii, zero, fold)
    acc
  }
  /** 2 dimensional reduction **/
  @api def apply(domain1: Counter, domain2: Counter)(map: (Index,Index) => T)(reduce: (T,T) => T)(implicit mT: Type[T], bits: Bits[T]): Reg[T] = {
    val acc = accum.getOrElse(Reg[T])
    Reduce.alloc(List(domain1, domain2), acc, {x: List[Index] => map(x(0),x(1)) }, reduce, style, ii, zero, fold)
    acc
  }

  /** 3 dimensional reduction **/
  @api def apply(domain1: Counter, domain2: Counter, domain3: Counter)(map: (Index,Index,Index) => T)(reduce: (T,T) => T)(implicit mT: Type[T], bits: Bits[T]): Reg[T] = {
    val acc = accum.getOrElse(Reg[T])
    Reduce.alloc(List(domain1, domain2, domain3), acc, {x: List[Index] => map(x(0),x(1),x(2)) }, reduce, style, ii, zero, fold)
    acc
  }

  /** N dimensional reduction **/
  @api def apply(domain1: Counter, domain2: Counter, domain3: Counter, domain4: Counter, domain5plus: Counter*)(map: List[Index] => T)(reduce: (T,T) => T)(implicit mT: Type[T], bits: Bits[T]): Reg[T] = {
    val acc = accum.getOrElse(Reg[T])
    Reduce.alloc(List(domain1, domain2, domain3, domain4) ++ domain5plus, acc, map, reduce, style, ii, zero, fold)
    acc
  }
}
protected class ReduceConstant[A,T](style: ControlStyle, ii: Option[Double], a: A, isFold: Boolean) {
  @api def apply(domain1D: Counter)(map: Index => T)(reduce: (T, T) => T)(implicit bT: Bits[T], lift: Lift[A, T]): Reg[T] = {
    implicit val mT: Type[T] = lift.staged
    val accum = Some(Reg[T](lift(a)))
    val init = Some(lift(a))
    val fold = if (isFold) init else None
    val zero = if (!isFold) init else None
    new ReduceAccum(accum, style, ii, zero, fold).apply(domain1D)(map)(reduce)
  }
  @api def apply(domain1: Counter, domain2: Counter)(map: (Index, Index) => T)(reduce: (T, T) => T)(implicit bT: Bits[T], lift: Lift[A, T]): Reg[T] = {
    implicit val mT: Type[T] = lift.staged
    val accum = Some(Reg[T](lift(a)))
    val init = Some(lift(a))
    val fold = if (isFold) init else None
    val zero = if (!isFold) init else None
    new ReduceAccum(accum, style, ii, zero, fold).apply(domain1, domain2)(map)(reduce)
  }
  @api def apply(domain1: Counter, domain2: Counter, domain3: Counter)(map: (Index, Index, Index) => T)(reduce: (T, T) => T)(implicit bT: Bits[T], lift: Lift[A, T]): Reg[T] = {
    implicit val mT: Type[T] = lift.staged
    val accum = Some(Reg[T](lift(a)))
    val init = Some(lift(a))
    val fold = if (isFold) init else None
    val zero = if (!isFold) init else None
    new ReduceAccum(accum, style, ii, zero, fold).apply(domain1, domain2, domain3)(map)(reduce)
  }
  @api def apply(domain1: Counter, domain2: Counter, domain3: Counter, domain4: Counter, domain5plus: Counter*)(map: List[Index] => T)(reduce: (T, T) => T)(implicit bT: Bits[T], lift: Lift[A, T]): Reg[T] = {
    implicit val mT: Type[T] = lift.staged
    val accum = Some(Reg[T](lift(a)))
    val init = Some(lift(a))
    val fold = if (isFold) init else None
    val zero = if (!isFold) init else None
    new ReduceAccum(accum, style, ii, zero, fold).apply(domain1, domain2, domain3, domain4, domain5plus: _*)(map)(reduce)
  }
}

protected case class ReduceClass(style: ControlStyle, ii: Option[Double] = None) extends ReduceAccum(None, style, ii, None, None) {
  /** Reduction with implicit accumulator **/
  // TODO: Can't use ANY implicits if we want to be able to use Reduce(0)(...). Maybe a macro can help here?
  def apply(zero: scala.Int) = new ReduceConstant[Int,Int32](style, ii, zero, isFold = false)
  def apply(zero: scala.Long) = new ReduceConstant[Long,Int64](style, ii, zero, isFold = false)
  def apply(zero: scala.Float) = new ReduceConstant[Float,Float32](style, ii, zero, isFold = false)
  def apply(zero: scala.Double) = new ReduceConstant[Double,Float64](style, ii, zero, isFold = false)

  //def apply(zero: FixPt[_,_,_]) = new ReduceAccum(Reg[FixPt[S,I,F]](zero), style)
  //def apply(zero: FltPt[_,_]) = new ReduceAccum(Reg[FltPt[G,E]](zero), style)

  /** Reduction with explicit accumulator **/
  // TODO: Should initial value of accumulator be assumed to be the identity value?
  def apply[T](accum: Reg[T]) = new ReduceAccum(Some(accum), style, ii, None, None)
}

protected case class FoldClass(style: ControlStyle, ii: Option[Double] = None) {
  /** Fold with implicit accumulator **/
  // TODO: Can't use ANY implicits if we want to be able to use Reduce(0)(...). Maybe a macro can help here?
  def apply(zero: scala.Int) = new ReduceConstant[Int,Int32](style, ii, zero, isFold = true)
  def apply(zero: scala.Long) = new ReduceConstant[Long,Int64](style, ii, zero, isFold = true)
  def apply(zero: scala.Float) = new ReduceConstant[Float,Float32](style, ii, zero, isFold = true)
  def apply(zero: scala.Double) = new ReduceConstant[Double,Float64](style, ii, zero, isFold = true)

  def apply[T](accum: Reg[T]) = {
    val sty = if (style == InnerPipe) MetaPipe else style
    MemReduceAccum(accum, sty, ii, None, true, None)
  }
}


object Fold   extends FoldClass(InnerPipe)
object Reduce extends ReduceClass(InnerPipe) {

  @internal def alloc[T:Type:Bits](
    domain: Seq[Counter],
    reg:    Reg[T],
    map:    List[Index] => T,
    reduce: (T,T) => T,
    style:  ControlStyle,
    ii:     Option[Double],
    ident:  Option[T],
    fold:   Option[T]
  ): Controller = {

    val rV = (fresh[T], fresh[T])
    val iters = List.tabulate(domain.length){_ => fresh[Index] }

    val mBlk  = stageSealedBlock{ map(wrap(iters)).s }
    val ldBlk = stageColdLambda1(reg.s) { reg.value.s }
    val rBlk  = stageColdLambda2(rV._1,rV._2){ reduce(wrap(rV._1),wrap(rV._2)).s }
    val stBlk = stageColdLambda2(reg.s, rBlk.result){ unwrap( reg := wrap(rBlk.result) ) }

    val cchain = CounterChain(domain: _*)
    val z = ident.map(_.s)
    val f = fold.map(_.s)

    val effects = mBlk.effects andAlso ldBlk.effects andAlso rBlk.effects andAlso stBlk.effects
    val pipe = stageEffectful(OpReduce[T](Nil, cchain.s, reg.s, mBlk, ldBlk, rBlk, stBlk, z, f, rV, iters), effects)(ctx)
    styleOf(pipe) = style
    userIIOf(pipe) = ii
    levelOf(pipe) = InnerControl // Fixed in Level Analyzer
    Controller(pipe)
  }

  @internal def op_reduce[T:Type:Bits](
    ens:    Seq[Exp[Bit]],
    cchain: Exp[CounterChain],
    reg:    Exp[Reg[T]],
    map:    () => Exp[T],
    load:   Exp[Reg[T]] => Exp[T],
    reduce: (Exp[T], Exp[T]) => Exp[T],
    store:  (Exp[Reg[T]], Exp[T]) => Exp[MUnit],
    ident:  Option[Exp[T]],
    fold:   Option[Exp[T]],
    rV:     (Bound[T],Bound[T]),
    iters:  List[Bound[Index]]
  ): Sym[Controller] = {

    val mBlk  = stageSealedBlock{ map() }
    val ldBlk = stageColdLambda1(reg){ load(reg) }
    val rBlk  = stageColdLambda2(rV._1,rV._2){ reduce(rV._1,rV._2) }
    val stBlk = stageColdLambda2(reg, rBlk.result){ store(reg, rBlk.result) }

    val effects = mBlk.effects andAlso ldBlk.effects andAlso rBlk.effects andAlso stBlk.effects
    stageEffectful( OpReduce[T](ens, cchain, reg, mBlk, ldBlk, rBlk, stBlk, ident, fold, rV, iters), effects)(ctx)
  }

  @internal def op_unrolled_reduce[T,C[T]](
    en:     Seq[Exp[Bit]],
    cchain: Exp[CounterChain],
    accum:  Exp[C[T]],
    func:   () => Exp[MUnit],
    iters:  Seq[Seq[Bound[Index]]],
    valids: Seq[Seq[Bound[Bit]]]
  )(implicit mT: Type[T], mC: Type[C[T]]): Exp[Controller] = {
    val fBlk = stageSealedLambda1(accum) { func() }
    val effects = fBlk.effects
    stageEffectful(UnrolledReduce(en, cchain, accum, fBlk, iters, valids), effects.star)(ctx)
  }
}
