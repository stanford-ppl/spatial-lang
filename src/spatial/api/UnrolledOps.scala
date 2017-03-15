package spatial.api

import argon.core.Staging
import spatial.SpatialExp

trait UnrolledApi extends UnrolledExp {this: SpatialExp => }

trait UnrolledExp extends Staging with ControllerExp with VectorExp {
  this: SpatialExp =>

  /** IR Nodes **/
  case class UnrolledForeach(
    en:     Seq[Exp[Bool]],
    cchain: Exp[CounterChain],
    func:   Block[Void],
    iters:  Seq[Seq[Bound[Index]]],
    valids: Seq[Seq[Bound[Bool]]]
  ) extends Op[Controller] {
    def mirror(f:Tx) = op_unrolled_foreach(f(en),f(cchain),f(func),iters,valids)

    override def inputs = syms(en) ++ syms(cchain) ++ syms(func)
    override def freqs = normal(cchain) ++ cold(func)
    override def binds = super.binds ++ iters.flatten ++ valids.flatten
  }

  case class UnrolledReduce[T,C[T]](
    en:     Seq[Exp[Bool]],
    cchain: Exp[CounterChain],
    accum:  Exp[C[T]],
    func:   Block[Void],
    reduce: Block[T],
    iters:  Seq[Seq[Bound[Index]]],
    valids: Seq[Seq[Bound[Bool]]],
    rV:     (Bound[T], Bound[T])
  )(implicit val mT: Staged[T], val mC: Staged[C[T]]) extends Op[Controller] {
    def mirror(f:Tx) = op_unrolled_reduce(f(en),f(cchain),f(accum),f(func),f(reduce),iters,valids,rV)

    override def inputs = syms(en) ++ syms(cchain, accum) ++ syms(func) ++ syms(reduce)
    override def freqs = normal(cchain) ++ normal(accum) ++ cold(func) ++ cold(reduce)
    override def binds = super.binds ++ iters.flatten ++ valids.flatten ++ Seq(rV._1, rV._2)
  }

  case class ParSRAMLoad[T:Staged:Bits](
    sram: Exp[SRAM[T]],
    addr: Seq[Seq[Exp[Index]]]
  )(implicit val W: INT[Vector[T]]) extends Op[Vector[T]] {
    def mirror(f:Tx) = par_sram_load(f(sram), addr.map{inds => f(inds)})
    val mT = typ[T]
  }

  case class ParSRAMStore[T:Staged:Bits](
    sram: Exp[SRAM[T]],
    addr: Seq[Seq[Exp[Index]]],
    data: Exp[Vector[T]],
    ens:  Exp[Vector[Bool]]
  ) extends Op[Void] {
    def mirror(f:Tx) = par_sram_store(f(sram),addr.map{inds => f(inds)},f(data),f(ens))
    val mT = typ[T]
  }

  case class ParFIFODeq[T:Staged:Bits](
    fifo: Exp[FIFO[T]],
    ens:  Exp[Vector[Bool]],
    zero: Exp[T]
  )(implicit val W: INT[Vector[T]]) extends Op[Vector[T]] {
    def mirror(f:Tx) = par_fifo_deq(f(fifo),f(ens),f(zero))
    val mT = typ[T]
  }

  case class ParFIFOEnq[T:Staged:Bits](
    fifo: Exp[FIFO[T]],
    data: Exp[Vector[T]],
    ens:  Exp[Vector[Bool]]
  ) extends Op[Void] {
    def mirror(f:Tx) = par_fifo_enq(f(fifo),f(data),f(ens))
    val mT = typ[T]
  }

  case class ParStreamDeq[T:Staged:Bits](
    stream: Exp[StreamIn[T]],
    ens:    Exp[Vector[Bool]],
    zero:   Exp[T]
  )(implicit val W: INT[Vector[T]]) extends Op[Vector[T]] {
    def mirror(f:Tx) = par_stream_deq(f(stream),f(ens),f(zero))
    val mT = typ[T]
    val bT = bits[T]
  }

  case class ParStreamEnq[T:Staged:Bits](
    stream: Exp[StreamOut[T]],
    data:   Exp[Vector[T]],
    ens:    Exp[Vector[Bool]]
  ) extends Op[Void] {
    def mirror(f:Tx) = par_stream_enq(f(stream),f(data),f(ens))
    val mT = typ[T]
    val bT = bits[T]
  }

  /** Constructors **/
  private[spatial] def op_unrolled_foreach(
    en:     Seq[Exp[Bool]],
    cchain: Exp[CounterChain],
    func:   => Exp[Void],
    iters:  Seq[Seq[Bound[Index]]],
    valids: Seq[Seq[Bound[Bool]]]
  )(implicit ctx: SrcCtx): Exp[Controller] = {
    val fBlk = stageBlock { func }
    val effects = fBlk.summary.star
    stageEffectful(UnrolledForeach(en, cchain, fBlk, iters, valids), effects)(ctx)
  }

  private[spatial] def op_unrolled_reduce[T,C[T]](
    en:     Seq[Exp[Bool]],
    cchain: Exp[CounterChain],
    accum:  Exp[C[T]],
    func:   => Exp[Void],
    reduce: => Exp[T],
    iters:  Seq[Seq[Bound[Index]]],
    valids: Seq[Seq[Bound[Bool]]],
    rV:     (Bound[T], Bound[T])
  )(implicit ctx: SrcCtx, mT: Staged[T], mC: Staged[C[T]]): Exp[Controller] = {
    val fBlk = stageLambda(accum) { func }
    val rBlk = stageBlock { reduce }
    val effects = fBlk.summary andAlso rBlk.summary
    stageEffectful(UnrolledReduce(en, cchain, accum, fBlk, rBlk, iters, valids, rV), effects.star)(ctx)
  }

  private[spatial] def par_sram_load[T:Staged:Bits](
    sram: Exp[SRAM[T]],
    addr: Seq[Seq[Exp[Index]]]
  )(implicit ctx: SrcCtx): Exp[Vector[T]] = {
    implicit val W = Width[T](addr.length)
    stage( ParSRAMLoad(sram, addr) )(ctx)
  }

  private[spatial] def par_sram_store[T:Staged:Bits](
    sram: Exp[SRAM[T]],
    addr: Seq[Seq[Exp[Index]]],
    data: Exp[Vector[T]],
    ens:  Exp[Vector[Bool]]
  )(implicit ctx: SrcCtx): Exp[Void] = {
    stageWrite(sram)( ParSRAMStore(sram, addr, data, ens) )(ctx)
  }

  private[spatial] def par_fifo_deq[T:Staged:Bits](
    fifo: Exp[FIFO[T]],
    ens:  Exp[Vector[Bool]],
    zero: Exp[T]
  )(implicit ctx: SrcCtx): Exp[Vector[T]] = {
    implicit val W = Width[T](lenOf(ens))
    stageWrite(fifo)( ParFIFODeq(fifo, ens, zero) )(ctx)
  }

  private[spatial] def par_fifo_enq[T:Staged:Bits](
    fifo: Exp[FIFO[T]],
    data: Exp[Vector[T]],
    ens:  Exp[Vector[Bool]]
  )(implicit ctx: SrcCtx): Exp[Void] = {
    stageWrite(fifo)( ParFIFOEnq(fifo, data, ens) )(ctx)
  }

  private[spatial] def par_stream_deq[T:Staged:Bits](
    stream: Exp[StreamIn[T]],
    ens:    Exp[Vector[Bool]],
    zero:   Exp[T]
  )(implicit ctx: SrcCtx): Exp[Vector[T]] = {
    implicit val W = Width[T](lenOf(ens))
    stageWrite(stream)( ParStreamDeq(stream, ens, zero) )(ctx)
  }

  private[spatial] def par_stream_enq[T:Staged:Bits](
    stream: Exp[StreamOut[T]],
    data:   Exp[Vector[T]],
    ens:    Exp[Vector[Bool]]
  )(implicit ctx: SrcCtx): Exp[Void] = {
    stageWrite(stream)( ParStreamEnq(stream, data, ens) )(ctx)
  }

}