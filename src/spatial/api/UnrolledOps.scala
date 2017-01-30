package spatial.api

import spatial.SpatialExp

trait UnrolledOps
trait UnrolledApi extends UnrolledOps

trait UnrolledExp extends UnrolledOps with ControllerExp with VectorExp {
  this: SpatialExp =>

  /** IR Nodes **/
  case class UnrolledForeach(
    cchain: Exp[CounterChain],
    func:   Block[Void],
    iters:  Seq[Seq[Bound[Index]]],
    valids: Seq[Seq[Bound[Bool]]]
  ) extends Op[Controller] {
    def mirror(f:Tx) = op_unrolled_foreach(f(cchain),f(func),iters,valids)

    override def inputs = syms(cchain) ++ syms(func)
    override def freqs = normal(cchain) ++ cold(func)
    override def binds = super.binds ++ iters.flatten ++ valids.flatten
  }

  case class UnrolledReduce[T,C[T]](
    cchain: Exp[CounterChain],
    accum:  Exp[C[T]],
    func:   Block[Void],
    reduce: Block[T],
    iters:  Seq[Seq[Bound[Index]]],
    valids: Seq[Seq[Bound[Bool]]],
    rV:     (Bound[T], Bound[T])
  )(implicit val mT: Staged[T], val mC: Staged[C[T]]) extends Op[Controller] {
    def mirror(f:Tx) = op_unrolled_reduce(f(cchain),f(accum),f(func),f(reduce),iters,valids,rV)

    override def inputs = syms(cchain, accum) ++ syms(func) ++ syms(reduce)
    override def freqs = normal(cchain) ++ normal(accum) ++ cold(func) ++ cold(reduce)
    override def binds = super.binds ++ iters.flatten ++ valids.flatten ++ Seq(rV._1, rV._2)
    override def tunnels = syms(accum)
  }

  case class ParSRAMLoad[T:Bits](
    sram: Exp[SRAM[T]],
    addr: Seq[Seq[Exp[Index]]]
  ) extends Op[Vector[T]] {
    def mirror(f:Tx) = par_sram_load(f(sram), addr.map{inds => f(inds)})
    val bT = bits[T]
  }

  case class ParSRAMStore[T:Bits](
    sram: Exp[SRAM[T]],
    addr: Seq[Seq[Exp[Index]]],
    data: Exp[Vector[T]],
    ens:  Exp[Vector[Bool]]
  ) extends Op[Void] {
    def mirror(f:Tx) = par_sram_store(f(sram),addr.map{inds => f(inds)},f(data),f(ens))
    val bT = bits[T]
  }

  case class ParFIFODeq[T:Bits](
    fifo: Exp[FIFO[T]],
    ens:  Exp[Vector[Bool]],
    zero: Exp[T]
  ) extends Op[Vector[T]] {
    def mirror(f:Tx) = par_fifo_deq(f(fifo),f(ens),f(zero))
    val bT = bits[T]
  }

  case class ParFIFOEnq[T:Bits](
    fifo: Exp[FIFO[T]],
    data: Exp[Vector[T]],
    ens:  Exp[Vector[Bool]]
  ) extends Op[Void] {
    def mirror(f:Tx) = par_fifo_enq(f(fifo),f(data),f(ens))
    val bT = bits[T]
  }


  /** Constructors **/
  private[spatial] def op_unrolled_foreach(
    cchain: Exp[CounterChain],
    func:   => Exp[Void],
    iters:  Seq[Seq[Bound[Index]]],
    valids: Seq[Seq[Bound[Bool]]]
  )(implicit ctx: SrcCtx): Exp[Controller] = {
    val fBlk = stageBlock { func }
    val effects = fBlk.summary.star
    stageEffectful(UnrolledForeach(cchain, fBlk, iters, valids), effects)(ctx)
  }

  private[spatial] def op_unrolled_reduce[T,C[T]](
    cchain: Exp[CounterChain],
    accum:  Exp[C[T]],
    func:   => Exp[Void],
    reduce: => Exp[T],
    iters:  Seq[Seq[Bound[Index]]],
    valids: Seq[Seq[Bound[Bool]]],
    rV:     (Bound[T], Bound[T])
  )(implicit ctx: SrcCtx, mT: Staged[T], mC: Staged[C[T]]): Exp[Controller] = {
    val fBlk = stageBlock { func }
    val rBlk = stageBlock { reduce }
    val effects = fBlk.summary andAlso rBlk.summary
    stageEffectful(UnrolledReduce(cchain, accum, fBlk, rBlk, iters, valids, rV), effects.star)(ctx)
  }

  private[spatial] def par_sram_load[T:Bits](
    sram: Exp[SRAM[T]],
    addr: Seq[Seq[Exp[Index]]]
  )(implicit ctx: SrcCtx): Exp[Vector[T]] = {
    stage( ParSRAMLoad(sram, addr) )(ctx)
  }

  private[spatial] def par_sram_store[T:Bits](
    sram: Exp[SRAM[T]],
    addr: Seq[Seq[Exp[Index]]],
    data: Exp[Vector[T]],
    ens:  Exp[Vector[Bool]]
  )(implicit ctx: SrcCtx): Exp[Void] = {
    stageWrite(sram)( ParSRAMStore(sram, addr, data, ens) )(ctx)
  }

  private[spatial] def par_fifo_deq[T:Bits](
    fifo: Exp[FIFO[T]],
    ens:  Exp[Vector[Bool]],
    zero: Exp[T]
  )(implicit ctx: SrcCtx): Exp[Vector[T]] = {
    stage( ParFIFODeq(fifo, ens, zero) )(ctx)
  }

  private[spatial] def par_fifo_enq[T:Bits](
    fifo: Exp[FIFO[T]],
    data: Exp[Vector[T]],
    ens:  Exp[Vector[Bool]]
  )(implicit ctx: SrcCtx): Exp[Void] = {
    stageWrite(fifo)( ParFIFOEnq(fifo, data, ens) )(ctx)
  }
}