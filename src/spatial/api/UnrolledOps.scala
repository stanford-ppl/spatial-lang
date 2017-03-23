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
  ) extends EnabledController {
    def mirrorWithEn(f:Tx, addEn: Seq[Exp[Bool]]) = op_unrolled_foreach(f(en)++addEn,f(cchain),f(func),iters,valids)

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
  )(implicit val mT: Staged[T], val mC: Staged[C[T]]) extends EnabledController {
    def mirrorWithEn(f:Tx, addEn: Seq[Exp[Bool]]) = op_unrolled_reduce(f(en)++addEn,f(cchain),f(accum),f(func),f(reduce),iters,valids,rV)

    override def inputs = syms(en) ++ syms(cchain, accum) ++ syms(func) ++ syms(reduce)
    override def freqs = normal(cchain) ++ normal(accum) ++ cold(func) ++ cold(reduce)
    override def binds = super.binds ++ iters.flatten ++ valids.flatten ++ Seq(rV._1, rV._2)
  }

  case class ParSRAMLoad[T:Staged:Bits](
    sram: Exp[SRAM[T]],
    addr: Seq[Seq[Exp[Index]]],
    ens:  Seq[Exp[Bool]]
  )(implicit val W: INT[Vector[T]]) extends EnabledOp[Vector[T]](ens:_*) {
    def mirror(f:Tx) = par_sram_load(f(sram), addr.map{inds => f(inds)}, f(ens))
    val mT = typ[T]
  }

  case class ParSRAMStore[T:Staged:Bits](
    sram: Exp[SRAM[T]],
    addr: Seq[Seq[Exp[Index]]],
    data: Exp[Vector[T]],
    ens:  Seq[Exp[Bool]]
  ) extends EnabledOp[Void](ens:_*) {
    def mirror(f:Tx) = par_sram_store(f(sram),addr.map{inds => f(inds)},f(data),f(ens))
    val mT = typ[T]
  }

  case class ParFIFODeq[T:Staged:Bits](
    fifo: Exp[FIFO[T]],
    ens:  Seq[Exp[Bool]]
  )(implicit val W: INT[Vector[T]]) extends EnabledOp[Vector[T]](ens:_*) {
    def mirror(f:Tx) = par_fifo_deq(f(fifo),f(ens))
    val mT = typ[T]
  }

  case class ParFIFOEnq[T:Staged:Bits](
    fifo: Exp[FIFO[T]],
    data: Exp[Vector[T]],
    ens:  Seq[Exp[Bool]]
  ) extends EnabledOp[Void](ens:_*) {
    def mirror(f:Tx) = par_fifo_enq(f(fifo),f(data),f(ens))
    val mT = typ[T]
  }

  case class ParStreamRead[T:Staged:Bits](
    stream: Exp[StreamIn[T]],
    ens:    Seq[Exp[Bool]]
  )(implicit val W: INT[Vector[T]]) extends EnabledOp[Vector[T]](ens:_*) {
    def mirror(f:Tx) = par_stream_read(f(stream),f(ens))
    val mT = typ[T]
    val bT = bits[T]
  }

  case class ParStreamWrite[T:Staged:Bits](
    stream: Exp[StreamOut[T]],
    data:   Exp[Vector[T]],
    ens:    Seq[Exp[Bool]]
  ) extends EnabledOp[Void](ens:_*) {
    def mirror(f:Tx) = par_stream_write(f(stream),f(data),f(ens))
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
    addr: Seq[Seq[Exp[Index]]],
    ens:  Seq[Exp[Bool]]
  )(implicit ctx: SrcCtx): Exp[Vector[T]] = {
    implicit val W = Width[T](addr.length)
    stage( ParSRAMLoad(sram, addr, ens) )(ctx)
  }

  private[spatial] def par_sram_store[T:Staged:Bits](
    sram: Exp[SRAM[T]],
    addr: Seq[Seq[Exp[Index]]],
    data: Exp[Vector[T]],
    ens:  Seq[Exp[Bool]]
  )(implicit ctx: SrcCtx): Exp[Void] = {
    stageWrite(sram)( ParSRAMStore(sram, addr, data, ens) )(ctx)
  }

  private[spatial] def par_fifo_deq[T:Staged:Bits](
    fifo: Exp[FIFO[T]],
    ens:  Seq[Exp[Bool]]
  )(implicit ctx: SrcCtx): Exp[Vector[T]] = {
    implicit val W = Width[T](ens.length)
    stageWrite(fifo)( ParFIFODeq(fifo, ens) )(ctx)
  }

  private[spatial] def par_fifo_enq[T:Staged:Bits](
    fifo: Exp[FIFO[T]],
    data: Exp[Vector[T]],
    ens:  Seq[Exp[Bool]]
  )(implicit ctx: SrcCtx): Exp[Void] = {
    stageWrite(fifo)( ParFIFOEnq(fifo, data, ens) )(ctx)
  }

  private[spatial] def par_stream_read[T:Staged:Bits](
    stream: Exp[StreamIn[T]],
    ens:    Seq[Exp[Bool]]
  )(implicit ctx: SrcCtx): Exp[Vector[T]] = {
    implicit val W = Width[T](ens.length)
    stageWrite(stream)( ParStreamRead(stream, ens) )(ctx)
  }

  private[spatial] def par_stream_write[T:Staged:Bits](
    stream: Exp[StreamOut[T]],
    data:   Exp[Vector[T]],
    ens:    Seq[Exp[Bool]]
  )(implicit ctx: SrcCtx): Exp[Void] = {
    stageWrite(stream)( ParStreamWrite(stream, data, ens) )(ctx)
  }

}