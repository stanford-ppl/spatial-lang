package spatial.lang

import argon.core._
import forge._
import spatial.nodes._
import spatial.utils._

case class FILO[T:Type:Bits](s: Exp[FILO[T]]) extends Template[FILO[T]] {
  /**
    * Annotates that addresses in this FIFO can be read in parallel by factor `p`.
    *
    * Used when creating references to sparse regions of DRAM.
    */
  @api def par(p: Index): FILO[T] = { val x = FILO(s); x.p = Some(p); x }

  /** Returns true when this FILO contains no elements, false otherwise. **/
  @api def empty(): Bit = wrap(FILO.is_empty(this.s))
  /** Returns true when this FILO cannot fit any more elements, false otherwise. **/
  @api def full(): Bit = wrap(FILO.is_full(this.s))
  /** Returns true when this FILO contains exactly one element, false otherwise. **/
  @api def almostEmpty(): Bit = wrap(FILO.is_almost_empty(this.s))
  /** Returns true when this FILO can fit exactly one more element, false otherwise. **/
  @api def almostFull(): Bit = wrap(FILO.is_almost_full(this.s))
  /** Returns the number of elements currently in this FILO. **/
  @api def numel(): Index = wrap(FILO.numel(this.s))

  /** Creates a push (write) port to this FILO of `data`. **/
  @api def push(data: T): MUnit = this.push(data, true)
  /** Creates a conditional push (write) port to this FILO of `data` enabled by `en`. **/
  @api def push(data: T, en: Bit): MUnit = MUnit(FILO.push(this.s, data.s, en.s))

  /** Creates a pop (destructive read) port to this FILO. **/
  @api def pop(): T = this.pop(true)
  /** Creates a conditional pop (destructive read) port to this FILO enabled by `en`. **/
  @api def pop(en: Bit): T = wrap(FILO.pop(this.s, en.s))

  /** Creates a non-destructive read port to this FILO. **/
  @api def peek(): T = wrap(FILO.peek(this.s))

  /** Creates a dense, burst load from the specified region of DRAM to this on-chip memory. **/
  @api def load(dram: DRAMDenseTile1[T]): MUnit = DRAMTransfers.dense_transfer(dram, this, isLoad = true)
  /** Creates a sparse load from the specified sparse region of DRAM to this on-chip memory. **/
  @api def gather(dram: DRAMSparseTile[T]): MUnit = DRAMTransfers.sparse_transfer_mem(dram.toSparseTileMem, this, isLoad = true)


  //@api def load(dram: DRAM1[T]): MUnit = dense_transfer(dram.toTile(this.ranges), this, isLoad = true)
  @api def gather[A[_]](dram: DRAMSparseTileMem[T,A]): MUnit = DRAMTransfers.sparse_transfer_mem(dram, this, isLoad = true)

  @internal def ranges: Seq[Range] = Seq(Range.alloc(None, wrap(stagedSizeOf(s)),None,None))

  protected[spatial] var p: Option[Index] = None
}

object FILO {
  /** Static methods **/
  implicit def filoType[T:Type:Bits]: Type[FILO[T]] = FILOType(typ[T])
  implicit def filoIsMemory[T:Type:Bits]: Mem[T, FILO] = new FILOIsMemory[T]

  /**
    * Creates a FILO with given `depth`.
    *
    * Depth must be a statically determinable signed integer.
    **/
  @api def apply[T:Type:Bits](size: Index): FILO[T] = FILO(FILO.alloc[T](size.s))


  /** Constructors **/
  @internal def alloc[T:Type:Bits](size: Exp[Index]): Exp[FILO[T]] = {
    stageMutable(FILONew[T](size))(ctx)
  }
  @internal def push[T:Type:Bits](filo: Exp[FILO[T]], data: Exp[T], en: Exp[Bit]): Exp[MUnit] = {
    stageWrite(filo)(FILOPush(filo, data, en))(ctx)
  }
  @internal def pop[T:Type:Bits](filo: Exp[FILO[T]], en: Exp[Bit]): Exp[T] = {
    stageWrite(filo)(FILOPop(filo,en))(ctx)
  }
  @internal def peek[T:Type:Bits](filo: Exp[FILO[T]]): Exp[T] = {
    stageWrite(filo)(FILOPeek(filo))(ctx)
  }
  @internal def is_empty[T:Type:Bits](filo: Exp[FILO[T]]): Exp[Bit] = {
    stage(FILOEmpty(filo))(ctx)
  }
  @internal def is_full[T:Type:Bits](filo: Exp[FILO[T]]): Exp[Bit] = {
    stage(FILOFull(filo))(ctx)
  }
  @internal def is_almost_empty[T:Type:Bits](filo: Exp[FILO[T]]): Exp[Bit] = {
    stage(FILOAlmostEmpty(filo))(ctx)
  }
  @internal def is_almost_full[T:Type:Bits](filo: Exp[FILO[T]]): Exp[Bit] = {
    stage(FILOAlmostFull(filo))(ctx)
  }
  @internal def numel[T:Type:Bits](filo: Exp[FILO[T]]): Exp[Index] = {
    stage(FILONumel(filo))(ctx)
  }

  @internal def par_pop[T:Type:Bits](
    filo: Exp[FILO[T]],
    ens:  Seq[Exp[Bit]]
  )(implicit ctx: SrcCtx) = {
    implicit val vT = VectorN.typeFromLen[T](ens.length)
    stageWrite(filo)( ParFILOPop(filo, ens) )(ctx)
  }

  @internal def par_push[T:Type:Bits](
    filo: Exp[FILO[T]],
    data: Seq[Exp[T]],
    ens:  Seq[Exp[Bit]]
  )(implicit ctx: SrcCtx) = {
    stageWrite(filo)( ParFILOPush(filo, data, ens) )(ctx)
  }
}

