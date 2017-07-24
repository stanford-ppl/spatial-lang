package spatial.nodes

import argon.core._
import forge._
import spatial.aliases._

/** IR Nodes **/
case class Hwblock(func: Block[MUnit], isForever: Boolean) extends Pipeline {
  def en: Seq[Exp[Bit]] = Nil
  def mirrorWithEn(f:Tx, addEn: Seq[Exp[Bit]]) = Accel.op_accel(f(func), isForever)
}

case class UnitPipe(en: Seq[Exp[Bit]], func: Block[MUnit]) extends Pipeline {
  def mirrorWithEn(f:Tx, addEn: Seq[Exp[Bit]]) = Pipe.op_unit_pipe(f(en) ++ addEn, f(func))
}

case class ParallelPipe(en: Seq[Exp[Bit]], func: Block[MUnit]) extends EnabledControlNode {
  def mirrorWithEn(f:Tx, addEn: Seq[Exp[Bit]]) = Parallel.op_parallel_pipe(f(en) ++ addEn, f(func))
}


case class OpForeach(en: Seq[Exp[Bit]], cchain: Exp[CounterChain], func: Block[MUnit], iters: List[Bound[Index]]) extends Loop {
  def mirrorWithEn(f:Tx, addEn: Seq[Exp[Bit]]) = Foreach.op_foreach(f(en) ++ addEn, f(cchain), f(func), iters)

  override def inputs = dyns(en) ++ dyns(cchain) ++ dyns(func)
  override def binds  = super.binds ++ iters
}

case class OpReduce[T:Type:Bits](
  en:     Seq[Exp[Bit]],
  cchain: Exp[CounterChain],
  accum:  Exp[Reg[T]],
  map:    Block[T],
  load:   Lambda1[Reg[T],T],
  reduce: Lambda2[T,T,T],
  store:  Lambda2[Reg[T],T,MUnit],
  ident:  Option[Exp[T]],
  fold:   Option[Exp[T]],
  rV:     (Bound[T],Bound[T]),
  iters:  List[Bound[Index]]
) extends Loop {
  def mirrorWithEn(f:Tx, addEn: Seq[Exp[Bit]]) = {
    Reduce.op_reduce(f(en) ++ addEn, f(cchain), f(accum), f(map), f(load), f(reduce), f(store), f(ident), f(fold), rV, iters)
  }

  override def inputs = dyns(en) ++ dyns(cchain) ++ dyns(map) ++ dyns(reduce) ++ dyns(accum) ++ dyns(load) ++ dyns(store) ++ dyns(ident)
  override def binds  = super.binds ++ iters ++ List(rV._1, rV._2)
  override def aliases = Nil
  val mT = typ[T]
  val bT = bits[T]
}

case class OpMemReduce[T:Type:Bits,C[T]](
  en:        Seq[Exp[Bit]],
  cchainMap: Exp[CounterChain],
  cchainRed: Exp[CounterChain],
  accum:     Exp[C[T]],
  map:       Block[C[T]],
  loadRes:   Lambda1[C[T],T],
  loadAcc:   Lambda1[C[T],T],
  reduce:    Lambda2[T,T,T],
  storeAcc:  Lambda2[C[T],T,MUnit],
  ident:     Option[Exp[T]],
  fold:      Boolean,
  rV:        (Bound[T], Bound[T]),
  itersMap:  Seq[Bound[Index]],
  itersRed:  Seq[Bound[Index]]
)(implicit val mem: Mem[T,C], val mC: Type[C[T]]) extends Loop {
  def mirrorWithEn(f:Tx, addEn: Seq[Exp[Bit]]) = {
    MemReduce.op_mem_reduce(f(en) ++ addEn,f(cchainMap),f(cchainRed),f(accum),f(map),f(loadRes),f(loadAcc),f(reduce), f(storeAcc), f(ident), fold, rV, itersMap, itersRed)
  }

  override def inputs = dyns(en) ++ dyns(cchainMap) ++ dyns(cchainRed) ++ dyns(accum) ++ dyns(map) ++ dyns(reduce) ++
    dyns(ident) ++ dyns(loadRes) ++ dyns(loadAcc) ++ dyns(storeAcc)
  override def binds = super.binds ++ itersMap ++ itersRed ++ List(rV._1, rV._2)
  override def aliases = Nil

  val mT = typ[T]
  val bT = bits[T]
}

case class StateMachine[T:Type:Bits](
  en:        Seq[Exp[Bit]],
  start:     Exp[T],
  notDone:   Lambda1[T,Bit],
  action:    Lambda1[T,MUnit],
  nextState: Lambda1[T,T],
  state:     Bound[T]
) extends Loop {
  def mirrorWithEn(f:Tx, addEn: Seq[Exp[Bit]]) = FSM.op_state_machine(f(en) ++ addEn,f(start),f(notDone),f(action),f(nextState),state)

  override def binds = state +: super.binds
  val mT = typ[T]
  val bT = bits[T]
}


case class UnrolledForeach(
  en:     Seq[Exp[Bit]],
  cchain: Exp[CounterChain],
  func:   Block[MUnit],
  iters:  Seq[Seq[Bound[Index]]],
  valids: Seq[Seq[Bound[Bit]]]
) extends Loop {
  def mirrorWithEn(f:Tx, addEn: Seq[Exp[Bit]]) = Foreach.op_unrolled_foreach(f(en)++addEn,f(cchain),f(func),iters,valids)

  override def inputs = syms(en) ++ syms(cchain) ++ syms(func)
  override def binds = super.binds ++ iters.flatten ++ valids.flatten
}

case class UnrolledReduce[T,C[T]](
  en:     Seq[Exp[Bit]],
  cchain: Exp[CounterChain],
  accum:  Exp[C[T]],
  func:   Block[MUnit],
  iters:  Seq[Seq[Bound[Index]]],
  valids: Seq[Seq[Bound[Bit]]]
)(implicit val mT: Type[T], val mC: Type[C[T]]) extends Loop {
  def mirrorWithEn(f:Tx, addEn: Seq[Exp[Bit]]) = Reduce.op_unrolled_reduce(f(en)++addEn,f(cchain),f(accum),f(func),iters,valids)

  override def inputs = syms(en) ++ syms(cchain, accum) ++ syms(func)
  override def binds = super.binds ++ iters.flatten ++ valids.flatten
}