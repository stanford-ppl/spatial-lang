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

    override def inputs = dyns(en) ++ dyns(cchain) ++ dyns(func)
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
  )(implicit val mT: Type[T], val mC: Type[C[T]]) extends EnabledController {
    def mirrorWithEn(f:Tx, addEn: Seq[Exp[Bool]]) = op_unrolled_reduce(f(en)++addEn,f(cchain),f(accum),f(func),f(reduce),iters,valids,rV)

    override def inputs = dyns(en) ++ dyns(cchain, accum) ++ dyns(func) ++ dyns(reduce)
    override def freqs = normal(cchain) ++ normal(accum) ++ cold(func) ++ cold(reduce)
    override def binds = super.binds ++ iters.flatten ++ valids.flatten ++ Seq(rV._1, rV._2)
  }

  case class ParSRAMLoad[W:INT,T:Type:Bits](
    sram: Exp[SRAM[T]],
    addr: Seq[Seq[Exp[Index]]],
    ens:  Seq[Exp[Bool]]
  ) extends EnabledOp[Vector[W,T]](ens:_*) {
    def mirror(f:Tx) = par_sram_load(f(sram), addr.map{inds => f(inds)}, f(ens))
    val mT = typ[T]
  }

  case class ParSRAMStore[W:INT,T:Type:Bits](
    sram: Exp[SRAM[T]],
    addr: Seq[Seq[Exp[Index]]],
    data: Exp[Vector[W,T]],
    ens:  Seq[Exp[Bool]]
  ) extends EnabledOp[Void](ens:_*) {
    def mirror(f:Tx) = par_sram_store(f(sram),addr.map{inds => f(inds)},f(data),f(ens))
    val mT = typ[T]
  }

  case class ParFIFODeq[W:INT,T:Type:Bits](
    fifo: Exp[FIFO[T]],
    ens:  Seq[Exp[Bool]]
  ) extends EnabledOp[Vector[W,T]](ens:_*) {
    def mirror(f:Tx) = par_fifo_deq(f(fifo),f(ens))
    val mT = typ[T]
  }

  case class ParFIFOEnq[W:INT,T:Type:Bits](
    fifo: Exp[FIFO[T]],
    data: Exp[Vector[W,T]],
    ens:  Seq[Exp[Bool]]
  ) extends EnabledOp[Void](ens:_*) {
    def mirror(f:Tx) = par_fifo_enq(f(fifo),f(data),f(ens))
    val mT = typ[T]
  }

  case class ParStreamRead[W:INT,T:Type:Bits](
    stream: Exp[StreamIn[T]],
    ens:    Seq[Exp[Bool]]
  ) extends EnabledOp[Vector[W,T]](ens:_*) {
    def mirror(f:Tx) = par_stream_read(f(stream),f(ens))
    val mT = typ[T]
    val bT = bits[T]
  }

  case class ParStreamWrite[W:INT,T:Type:Bits](
    stream: Exp[StreamOut[T]],
    data:   Exp[Vector[W,T]],
    ens:    Seq[Exp[Bool]]
  ) extends EnabledOp[Void](ens:_*) {
    def mirror(f: Tx) = par_stream_write(f(stream), f(data), f(ens))
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
  )(implicit ctx: SrcCtx, mT: Type[T], mC: Type[C[T]]): Exp[Controller] = {
    val fBlk = stageLambda(accum) { func }
    val rBlk = stageBlock { reduce }
    val effects = fBlk.summary andAlso rBlk.summary
    stageEffectful(UnrolledReduce(en, cchain, accum, fBlk, rBlk, iters, valids, rV), effects.star)(ctx)
  }

  private[spatial] def par_sram_load[W:INT,T:Type:Bits](
    sram: Exp[SRAM[T]],
    addr: Seq[Seq[Exp[Index]]],
    ens:  Seq[Exp[Bool]]
  )(implicit ctx: SrcCtx): Exp[Vector[W,T]] = {
    stage( ParSRAMLoad(sram, addr, ens) )(ctx)
  }

  private[spatial] def par_sram_store[W:INT,T:Type:Bits](
    sram: Exp[SRAM[T]],
    addr: Seq[Seq[Exp[Index]]],
    data: Exp[Vector[W,T]],
    ens:  Seq[Exp[Bool]]
  )(implicit ctx: SrcCtx): Exp[Void] = {
    stageWrite(sram)( ParSRAMStore(sram, addr, data, ens) )(ctx)
  }

  private[spatial] def par_fifo_deq[W:INT,T:Type:Bits](
    fifo: Exp[FIFO[T]],
    ens:  Seq[Exp[Bool]]
  )(implicit ctx: SrcCtx): Exp[Vector[W,T]] = {
    stageWrite(fifo)( ParFIFODeq(fifo, ens) )(ctx)
  }

  private[spatial] def par_fifo_enq[W:INT,T:Type:Bits](
    fifo: Exp[FIFO[T]],
    data: Exp[Vector[W,T]],
    ens:  Seq[Exp[Bool]]
  )(implicit ctx: SrcCtx): Exp[Void] = {
    stageWrite(fifo)( ParFIFOEnq(fifo, data, ens) )(ctx)
  }

  private[spatial] def par_stream_read[W:INT,T:Type:Bits](
    stream: Exp[StreamIn[T]],
    ens:    Seq[Exp[Bool]]
  )(implicit ctx: SrcCtx): Exp[Vector[W,T]] = {
    stageWrite(stream)( ParStreamRead(stream, ens) )(ctx)
  }

  private[spatial] def par_stream_write[W:INT,T:Type:Bits](
    stream: Exp[StreamOut[T]],
    data:   Exp[Vector[W,T]],
    ens:    Seq[Exp[Bool]]
  )(implicit ctx: SrcCtx): Exp[Void] = {
    stageWrite(stream)( ParStreamWrite(stream, data, ens) )(ctx)
  }

}