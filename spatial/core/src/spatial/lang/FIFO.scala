package spatial.lang

import argon.core._
import forge._
import spatial.nodes._
import spatial.utils._

/** Infix methods **/
case class FIFO[T:Type:Bits](s: Exp[FIFO[T]]) extends Template[FIFO[T]] {
  /**
    * Annotates that addresses in this FIFO can be read in parallel by factor `p`.
    *
    * Used when creating references to sparse regions of DRAM.
    */
  @api def par(p: Index): FIFO[T] = { val x = FIFO(s); x.p = Some(p); x }

  /** Returns true when this FIFO contains no elements, false otherwise. **/
  @api def empty(): Bit = wrap(FIFO.is_empty(this.s))
  /** Returns true when this FIFO cannot fit any more elements, false otherwise. **/
  @api def full(): Bit = wrap(FIFO.is_full(this.s))
  /** Returns true when this FIFO contains exactly one element, false otherwise. **/
  @api def almostEmpty(): Bit = wrap(FIFO.is_almost_empty(this.s))
  /** Returns true when this FIFO can fit exactly one more element, false otherwise. **/
  @api def almostFull(): Bit = wrap(FIFO.is_almost_full(this.s))
  /** Returns the number of elements currently in this FIFO. **/
  @api def numel(): Index = wrap(FIFO.numel(this.s))

  /** Creates an enqueue (write) port of `data` to this FIFO. **/
  @api def enq(data: T): MUnit = this.enq(data, true)
  /** Creates an enqueue (write) port of `data` to this FIFO, enabled by `en`. **/
  @api def enq(data: T, en: Bit): MUnit = MUnit(FIFO.enq(this.s, data.s, en.s))

  /** Creates a dequeue (destructive read) port from this FIFO. **/
  @api def deq(): T = this.deq(true)
  /** Creates a dequeue (destructive read) port from this FIFO, enabled by `en`. **/
  @api def deq(en: Bit): T = wrap(FIFO.deq(this.s, en.s))

  /** Creates a non-destructive read port from this FIFO. **/
  @api def peek(): T = wrap(FIFO.peek(this.s))

  /** Creates a dense, burst load from the specified region of DRAM to this on-chip memory. **/
  @api def load(dram: DRAMDenseTile1[T]): MUnit = DRAMTransfers.dense_transfer(dram, this, isLoad = true)
  /** Creates a sparse load from the specified sparse region of DRAM to this on-chip memory. **/
  @api def gather(dram: DRAMSparseTile[T]): MUnit = DRAMTransfers.sparse_transfer_mem(dram.toSparseTileMem, this, isLoad = true)


  @api def gather[A[_]](dram: DRAMSparseTileMem[T,A]): MUnit = DRAMTransfers.sparse_transfer_mem(dram, this, isLoad = true)
  //@api def load(dram: DRAM1[T]): MUnit = dense_transfer(dram.toTile(this.ranges), this, isLoad = true)

  @internal def ranges: Seq[Range] = Seq(Range.alloc(None, wrap(stagedSizeOf(s)),None,None))

  protected[spatial] var p: Option[Index] = None
}

object FIFO {
  implicit def fifoType[T:Type:Bits]: Type[FIFO[T]] = FIFOType(typ[T])
  implicit def fifoIsMemory[T:Type:Bits]: Mem[T, FIFO] = new FIFOIsMemory[T]

  /**
    * Creates a FIFO with given `depth`.
    *
    * Depth must be a statically determinable signed integer.
    **/
  @api def apply[T:Type:Bits](depth: Index): FIFO[T] = FIFO(alloc[T](depth.s))

  /** Constructors **/
  @internal def alloc[T:Type:Bits](size: Exp[Index]): Exp[FIFO[T]] = {
    stageMutable(FIFONew[T](size))(ctx)
  }
  @internal def enq[T:Type:Bits](fifo: Exp[FIFO[T]], data: Exp[T], en: Exp[Bit]): Exp[MUnit] = {
    stageWrite(fifo)(FIFOEnq(fifo, data, en))(ctx)
  }
  @internal def deq[T:Type:Bits](fifo: Exp[FIFO[T]], en: Exp[Bit]): Exp[T] = {
    stageWrite(fifo)(FIFODeq(fifo,en))(ctx)
  }
  @internal def peek[T:Type:Bits](fifo: Exp[FIFO[T]]): Exp[T] = {
    stageWrite(fifo)(FIFOPeek(fifo))(ctx)
  }
  @internal def is_empty[T:Type:Bits](fifo: Exp[FIFO[T]]): Exp[Bit] = {
    stage(FIFOEmpty(fifo))(ctx)
  }
  @internal def is_full[T:Type:Bits](fifo: Exp[FIFO[T]]): Exp[Bit] = {
    stage(FIFOFull(fifo))(ctx)
  }
  @internal def is_almost_empty[T:Type:Bits](fifo: Exp[FIFO[T]]): Exp[Bit] = {
    stage(FIFOAlmostEmpty(fifo))(ctx)
  }
  @internal def is_almost_full[T:Type:Bits](fifo: Exp[FIFO[T]]): Exp[Bit] = {
    stage(FIFOAlmostFull(fifo))(ctx)
  }
  @internal def numel[T:Type:Bits](fifo: Exp[FIFO[T]]): Exp[Index] = {
    stage(FIFONumel(fifo))(ctx)
  }

  @internal def par_deq[T:Type:Bits](
    fifo: Exp[FIFO[T]],
    ens:  Seq[Exp[Bit]]
  )(implicit ctx: SrcCtx) = {
    implicit val vT = VectorN.typeFromLen[T](ens.length)
    stageWrite(fifo)( ParFIFODeq(fifo, ens) )(ctx)
  }

  @internal def par_enq[T:Type:Bits](
    fifo: Exp[FIFO[T]],
    data: Seq[Exp[T]],
    ens:  Seq[Exp[Bit]]
  )(implicit ctx: SrcCtx) = {
    stageWrite(fifo)( ParFIFOEnq(fifo, data, ens) )(ctx)
  }
}
