package spatial.lang

import spatial._

trait UnrolledApi extends UnrolledExp { this: SpatialApi => }

trait UnrolledExp { this: SpatialExp =>

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

    override def inputs = syms(en) ++ syms(cchain, accum) ++ syms(func) ++ syms(reduce)
    override def binds = super.binds ++ iters.flatten ++ valids.flatten ++ Seq(rV._1, rV._2)
  }

  case class ParSRAMLoad[T:Type:Bits](
    sram: Exp[SRAM[T]],
    addr: Seq[Seq[Exp[Index]]],
    ens:  Seq[Exp[Bool]]
  )(implicit val vT: Type[VectorN[T]]) extends EnabledOp[VectorN[T]](ens:_*) {
    def mirror(f:Tx) = par_sram_load(f(sram), addr.map{inds => f(inds)}, f(ens))
    val mT = typ[T]
  }

  case class ParSRAMStore[T:Type:Bits](
    sram: Exp[SRAM[T]],
    addr: Seq[Seq[Exp[Index]]],
    data: Seq[Exp[T]],
    ens:  Seq[Exp[Bool]]
  ) extends EnabledOp[Void](ens:_*) {
    def mirror(f:Tx) = par_sram_store(f(sram),addr.map{inds => f(inds)},f(data),f(ens))
    val mT = typ[T]
  }

  case class ParFIFODeq[T:Type:Bits](
    fifo: Exp[FIFO[T]],
    ens:  Seq[Exp[Bool]]
  )(implicit val vT: Type[VectorN[T]]) extends EnabledOp[VectorN[T]](ens:_*) {
    def mirror(f:Tx) = par_fifo_deq(f(fifo),f(ens))
    val mT = typ[T]
  }

  case class ParFIFOEnq[T:Type:Bits](
    fifo: Exp[FIFO[T]],
    data: Seq[Exp[T]],
    ens:  Seq[Exp[Bool]]
  ) extends EnabledOp[Void](ens:_*) {
    def mirror(f:Tx) = par_fifo_enq(f(fifo),f(data),f(ens))
    val mT = typ[T]
  }

  case class ParFILOPop[T:Type:Bits](
    filo: Exp[FILO[T]],
    ens:  Seq[Exp[Bool]]
  )(implicit val vT: Type[VectorN[T]]) extends EnabledOp[VectorN[T]](ens:_*) {
    def mirror(f:Tx) = par_filo_pop(f(filo),f(ens))
    val mT = typ[T]
  }

  case class ParFILOPush[T:Type:Bits](
    filo: Exp[FILO[T]],
    data: Seq[Exp[T]],
    ens:  Seq[Exp[Bool]]
  ) extends EnabledOp[Void](ens:_*) {
    def mirror(f:Tx) = par_filo_push(f(filo),f(data),f(ens))
    val mT = typ[T]
  }

  case class ParStreamRead[T:Type:Bits](
    stream: Exp[StreamIn[T]],
    ens:    Seq[Exp[Bool]]
  )(implicit val vT: Type[VectorN[T]]) extends EnabledOp[VectorN[T]](ens:_*) {
    def mirror(f:Tx) = par_stream_read(f(stream),f(ens))
    val mT = typ[T]
    val bT = bits[T]
  }

  case class ParStreamWrite[T:Type:Bits](
    stream: Exp[StreamOut[T]],
    data:   Seq[Exp[T]],
    ens:    Seq[Exp[Bool]]
  ) extends EnabledOp[Void](ens:_*) {
    def mirror(f:Tx) = par_stream_write(f(stream),f(data),f(ens))
    val mT = typ[T]
    val bT = bits[T]
  }

  case class ParLineBufferLoad[T:Type:Bits](
    linebuffer: Exp[LineBuffer[T]],
    rows:       Seq[Exp[Index]],
    cols:       Seq[Exp[Index]],
    ens:        Seq[Exp[Bool]]
  )(implicit val vT: Type[VectorN[T]]) extends EnabledOp[VectorN[T]](ens:_*) {
    def mirror(f:Tx) = par_linebuffer_load(f(linebuffer),f(rows),f(cols),f(ens))
    override def aliases = Nil
    val mT = typ[T]
  }

  case class ParLineBufferEnq[T:Type:Bits](
    linebuffer: Exp[LineBuffer[T]],
    data:       Seq[Exp[T]],
    ens:        Seq[Exp[Bool]]
  ) extends EnabledOp[Void](ens:_*) {
    def mirror(f:Tx) = par_linebuffer_enq(f(linebuffer),f(data),f(ens))
    override def aliases = Nil
    val mT = typ[T]
  }

  case class ParRegFileLoad[T:Type:Bits](
    reg:  Exp[RegFile[T]],
    inds: Seq[Seq[Exp[Index]]],
    ens:  Seq[Exp[Bool]]
  )(implicit val vT: Type[VectorN[T]]) extends EnabledOp[VectorN[T]](ens:_*) {
    def mirror(f:Tx) = par_regfile_load(f(reg),inds.map(is => f(is)),f(ens))
    override def aliases = Nil
    val mT = typ[T]
  }

  case class ParRegFileStore[T:Type:Bits](
    reg:  Exp[RegFile[T]],
    inds: Seq[Seq[Exp[Index]]],
    data: Seq[Exp[T]],
    ens:  Seq[Exp[Bool]]
  ) extends EnabledOp[Void](ens:_*) {
    def mirror(f:Tx) = par_regfile_store(f(reg),inds.map(is => f(is)),f(data),f(ens))
    val mT = typ[T]
  }

  /** Constructors **/
  private[spatial] def op_unrolled_foreach(
    en:     Seq[Exp[Bool]],
    cchain: Exp[CounterChain],
    func:   => Exp[Void],
    iters:  Seq[Seq[Bound[Index]]],
    valids: Seq[Seq[Bound[Bool]]]
  )(implicit ctx: SrcCtx): Exp[Controller] = {
    val fBlk = stageColdBlock { func }
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
    val fBlk = stageColdLambda(accum) { func }
    val rBlk = stageColdBlock { reduce }
    val effects = fBlk.summary andAlso rBlk.summary
    stageEffectful(UnrolledReduce(en, cchain, accum, fBlk, rBlk, iters, valids, rV), effects.star)(ctx)
  }

  private[spatial] def par_sram_load[T:Type:Bits](
    sram: Exp[SRAM[T]],
    addr: Seq[Seq[Exp[Index]]],
    ens:  Seq[Exp[Bool]]
  )(implicit ctx: SrcCtx) = {
    implicit val vT = vectorNType[T](addr.length)
    stage( ParSRAMLoad(sram, addr, ens) )(ctx)
  }

  private[spatial] def par_sram_store[T:Type:Bits](
    sram: Exp[SRAM[T]],
    addr: Seq[Seq[Exp[Index]]],
    data: Seq[Exp[T]],
    ens:  Seq[Exp[Bool]]
  )(implicit ctx: SrcCtx) = {
    stageWrite(sram)( ParSRAMStore(sram, addr, data, ens) )(ctx)
  }

  private[spatial] def par_fifo_deq[T:Type:Bits](
    fifo: Exp[FIFO[T]],
    ens:  Seq[Exp[Bool]]
  )(implicit ctx: SrcCtx) = {
    implicit val vT = vectorNType[T](ens.length)
    stageWrite(fifo)( ParFIFODeq(fifo, ens) )(ctx)
  }

  private[spatial] def par_fifo_enq[T:Type:Bits](
    fifo: Exp[FIFO[T]],
    data: Seq[Exp[T]],
    ens:  Seq[Exp[Bool]]
  )(implicit ctx: SrcCtx) = {
    stageWrite(fifo)( ParFIFOEnq(fifo, data, ens) )(ctx)
  }

  private[spatial] def par_filo_pop[T:Type:Bits](
    filo: Exp[FILO[T]],
    ens:  Seq[Exp[Bool]]
  )(implicit ctx: SrcCtx) = {
    implicit val vT = vectorNType[T](ens.length)
    stageWrite(filo)( ParFILOPop(filo, ens) )(ctx)
  }

  private[spatial] def par_filo_push[T:Type:Bits](
    filo: Exp[FILO[T]],
    data: Seq[Exp[T]],
    ens:  Seq[Exp[Bool]]
  )(implicit ctx: SrcCtx) = {
    stageWrite(filo)( ParFILOPush(filo, data, ens) )(ctx)
  }

  private[spatial] def par_stream_read[T:Type:Bits](
    stream: Exp[StreamIn[T]],
    ens:    Seq[Exp[Bool]]
  )(implicit ctx: SrcCtx) = {
    implicit val vT = vectorNType[T](ens.length)
    stageWrite(stream)( ParStreamRead(stream, ens) )(ctx)
  }

  private[spatial] def par_stream_write[T:Type:Bits](
    stream: Exp[StreamOut[T]],
    data:   Seq[Exp[T]],
    ens:    Seq[Exp[Bool]]
  )(implicit ctx: SrcCtx): Exp[Void] = {
    stageWrite(stream)( ParStreamWrite(stream, data, ens) )(ctx)
  }

  private[spatial] def par_linebuffer_load[T:Type:Bits](
    linebuffer: Exp[LineBuffer[T]],
    rows:       Seq[Exp[Index]],
    cols:       Seq[Exp[Index]],
    ens:        Seq[Exp[Bool]]
  )(implicit ctx: SrcCtx) = {
    implicit val vT = vectorNType[T](ens.length)
    stageWrite(linebuffer)(ParLineBufferLoad(linebuffer,rows,cols,ens))(ctx)
  }

  private[spatial] def par_linebuffer_enq[T:Type:Bits](
    linebuffer: Exp[LineBuffer[T]],
    data:       Seq[Exp[T]],
    ens:        Seq[Exp[Bool]]
  )(implicit ctx: SrcCtx) = {
    stageWrite(linebuffer)(ParLineBufferEnq(linebuffer,data,ens))(ctx)
  }

  private[spatial] def par_regfile_load[T:Type:Bits](
    reg:  Exp[RegFile[T]],
    inds: Seq[Seq[Exp[Index]]],
    ens:  Seq[Exp[Bool]]
  )(implicit ctx: SrcCtx) = {
    implicit val vT = vectorNType[T](ens.length)
    stageCold(ParRegFileLoad(reg, inds, ens))(ctx)
  }

  private[spatial] def par_regfile_store[T:Type:Bits](
    reg:  Exp[RegFile[T]],
    inds: Seq[Seq[Exp[Index]]],
    data: Seq[Exp[T]],
    ens:  Seq[Exp[Bool]]
  )(implicit ctx: SrcCtx) = {
    stageWrite(reg)(ParRegFileStore(reg, inds, data, ens))(ctx)
  }

}