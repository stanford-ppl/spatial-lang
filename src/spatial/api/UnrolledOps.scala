package spatial.api

import spatial.SpatialExp

trait UnrolledOps
trait UnrolledApi extends UnrolledOps

trait UnrolledExp extends UnrolledOps with ControllerExp with VectorExp {
  this: SpatialExp =>

  /** IR Nodes **/
  case class UnrolledForeach(
    cc:     Exp[CounterChain],
    func:   Block[Void],
    inds:   Seq[Seq[Bound[Index]]],
    valids: Seq[Seq[Bound[Bool]]]
  ) extends Op[Controller] {
    def mirror(f:Tx) = op_unrolled_foreach(f(cc),f(func),inds,valids)

    override def inputs = syms(cc) ++ syms(func)
    override def freqs = normal(cc) ++ cold(func)
    override def binds = super.binds ++ inds.flatten ++ valids.flatten
  }

  case class UnrolledReduce[T,C[T]](
    cc:     Exp[CounterChain],
    accum:  Exp[C[T]],
    func:   Block[Void],
    reduce: Block[T],
    iters:  Seq[Seq[Bound[Index]]],
    valids: Seq[Seq[Bound[Bool]]],
    rV:     (Bound[T], Bound[T])
  )(implicit val mT: Staged[T], val mC: Staged[C[T]]) extends Op[Controller] {
    def mirror(f:Tx) = op_unrolled_reduce(f(cc),f(accum),f(func),f(reduce),iters,valids,rV)

    override def inputs = syms(cc, accum) ++ syms(func) ++ syms(reduce)
    override def freqs = normal(cc) ++ normal(accum) ++ cold(func) ++ cold(reduce)
    override def binds = super.binds ++ iters.flatten ++ valids.flatten ++ Seq(rV._1, rV._2)
    override def tunnels = syms(accum)
  }

  case class ParSRAMLoad[T:Bits](
    sram: Exp[SRAM[T]],
    addr: Exp[Vector[Vector[Index]]]
  ) extends Op[Vector[T]] {
    def mirror(f:Tx) = par_sram_load(sram, addr)
  }

  case class ParSRAMStore[T:Bits](
    sram: Exp[SRAM[T]],
    addr: Exp[Vector[Vector[Index]]],
    data: Exp[Vector[T]],
    ens:  Exp[Vector[Bool]]
  ) extends Op[Void] {
    def mirror(f:Tx) = par_sram_store(f(sram),f(addr),f(data),f(ens))
  }

  case class ParFIFODeq[T:Bits](
    fifo: Exp[FIFO[T]],
    ens:  Exp[Vector[Bool]]
  ) extends Op[Vector[T]] {
    def mirror(f:Tx) = par_fifo_deq(f(fifo),f(ens))
  }

  case class ParFIFOEnq[T:Bits](
    fifo: Exp[FIFO[T]],
    data: Exp[Vector[T]],
    ens:  Exp[Vector[Bool]]
  ) extends Op[Void] {
    def mirror(f:Tx) = par_fifo_enq(f(fifo),f(data),f(ens))
  }


  /** Constructors **/
  private[spatial] def op_unrolled_foreach(
    cc:     Exp[CounterChain],
    func:   => Exp[Void],
    inds:   Seq[Seq[Bound[Index]]],
    valids: Seq[Seq[Bound[Bool]]]
  )(implicit ctx: SrcCtx): Exp[Controller] = {
    val fBlk = stageBlock { func }
    val effects = fBlk.summary.star
    stageEffectful(UnrolledForeach(cc, fBlk, inds, valids), effects)(ctx)
  }

  private[spatial] def op_unrolled_reduce[T,C[T]](
    cc:     Exp[CounterChain],
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
    stageEffectful(UnrolledReduce(cc, accum, fBlk, rBlk, iters, valids, rV), effects.star)(ctx)
  }

  private[spatial] def par_sram_load[T:Bits](
    sram: Exp[SRAM[T]],
    addr: Exp[Vector[Vector[Index]]]
  )(implicit ctx: SrcCtx): Exp[Vector[T]] = {
    stage( ParSRAMLoad(sram, addr) )(ctx)
  }

  private[spatial] def par_sram_store[T:Bits](
    sram: Exp[SRAM[T]],
    addr: Exp[Vector[Vector[Index]]],
    data: Exp[Vector[T]],
    ens:  Exp[Vector[Bool]]
  )(implicit ctx: SrcCtx): Exp[Void] = {
    stageWrite(sram)( ParSRAMStore(sram, addr, data, ens) )(ctx)
  }

  private[spatial] def par_fifo_deq[T:Bits](
    fifo: Exp[FIFO[T]],
    ens:  Exp[Vector[Bool]]
  )(implicit ctx: SrcCtx): Exp[Vector[T]] = {
    stage( ParFIFODeq(fifo, ens) )(ctx)
  }

  private[spatial] def par_fifo_enq[T:Bits](
    fifo: Exp[FIFO[T]],
    data: Exp[Vector[T]],
    ens:  Exp[Vector[Bool]]
  )(implicit ctx: SrcCtx): Exp[Void] = {
    stageWrite(fifo)( ParFIFOEnq(fifo, data, ens) )(ctx)
  }
}