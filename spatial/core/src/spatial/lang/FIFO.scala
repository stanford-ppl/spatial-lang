package spatial.lang

import argon.internals._
import forge._
import spatial.nodes._
import spatial.utils._

/** Infix methods **/
case class FIFO[T:Type:Bits](s: Exp[FIFO[T]]) extends Template[FIFO[T]] {
  @api def enq(data: T): MUnit = this.enq(data, true)
  @api def enq(data: T, en: Bit): MUnit = MUnit(FIFO.enq(this.s, data.s, en.s))

  @api def deq(): T = this.deq(true)
  @api def deq(en: Bit): T = wrap(FIFO.deq(this.s, en.s))

  @api def empty(): Bit = wrap(FIFO.is_empty(this.s))
  @api def full(): Bit = wrap(FIFO.is_full(this.s))
  @api def almostEmpty(): Bit = wrap(FIFO.is_almost_empty(this.s))
  @api def almostFull(): Bit = wrap(FIFO.is_almost_full(this.s))
  @api def numel(): Index = wrap(FIFO.numel(this.s))

  //@api def load(dram: DRAM1[T]): MUnit = dense_transfer(dram.toTile(this.ranges), this, isLoad = true)
  @api def load(dram: DRAMDenseTile1[T]): MUnit = DRAMTransfers.dense_transfer(dram, this, isLoad = true)
  // @api def gather(dram: DRAMSparseTile[T]): MUnit = copy_sparse(dram, this, isLoad = true)

  @internal def ranges: Seq[Range] = Seq(Range.alloc(None, wrap(sizeOf(s)),None,None))
}

object FIFO {
  implicit def fifoType[T:Type:Bits]: Type[FIFO[T]] = FIFOType(typ[T])
  implicit def fifoIsMemory[T:Type:Bits]: Mem[T, FIFO] = new FIFOIsMemory[T]

  @api def apply[T:Type:Bits](size: Index): FIFO[T] = FIFO(alloc[T](size.s))

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
