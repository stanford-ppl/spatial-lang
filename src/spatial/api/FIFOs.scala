package spatial.api

import argon.core.Staging
import spatial.SpatialExp

trait FIFOApi extends FIFOExp {
  this: SpatialExp =>

  def FIFO[T:Staged:Bits](size: Index)(implicit ctx: SrcCtx): FIFO[T] = FIFO(fifo_alloc[T](size.s))

}

trait FIFOExp extends Staging with MemoryExp with SpatialExceptions {
  this: SpatialExp =>

  /** Infix methods **/
  case class FIFO[T:Staged:Bits](s: Exp[FIFO[T]]) {
    def enq(data: T)(implicit ctx: SrcCtx): Void = this.enq(data, true)
    def enq(data: T, en: Bool)(implicit ctx: SrcCtx): Void = Void(fifo_enq(this.s, data.s, en.s))

    def deq()(implicit ctx: SrcCtx): T = this.deq(true)
    def deq(en: Bool)(implicit ctx: SrcCtx): T = wrap(fifo_deq(this.s, en.s, bits[T].zero.s))

    def load(dram: DRAM[T])(implicit ctx: SrcCtx): Void = dense_transfer(dram.toTile, this, isLoad = true)
    def load(dram: DRAMDenseTile[T])(implicit ctx: SrcCtx): Void = dense_transfer(dram, this, isLoad = true)
    //def gather(dram: DRAMSparseTile[T])(implicit ctx: SrcCtx): Void = copy_sparse(dram, this, isLoad = true)
  }


  /** Type classes **/
  // --- Staged
  case class FIFOType[T:Bits](child: Staged[T]) extends Staged[FIFO[T]] {
    override def unwrapped(x: FIFO[T]) = x.s
    override def wrapped(x: Exp[FIFO[T]]) = FIFO(x)(child,bits[T])
    override def typeArguments = List(child)
    override def stagedClass = classOf[FIFO[T]]
    override def isPrimitive = false
  }
  implicit def fifoType[T:Staged:Bits]: Staged[FIFO[T]] = FIFOType(typ[T])

  // --- Memory
  class FIFOIsMemory[T:Staged:Bits] extends Mem[T,FIFO] {
    def load(mem: FIFO[T], is: Seq[Index], en: Bool)(implicit ctx: SrcCtx): T = mem.deq(en)
    def store(mem: FIFO[T], is: Seq[Index], data: T, en: Bool)(implicit ctx: SrcCtx): Void = mem.enq(data, en)

    def iterators(mem: FIFO[T])(implicit ctx: SrcCtx): Seq[Counter] = Seq(Counter(0,sizeOf(mem),1,1))
  }
  implicit def fifoIsMemory[T:Staged:Bits]: Mem[T, FIFO] = new FIFOIsMemory[T]


  /** IR Nodes **/
  case class FIFONew[T:Staged:Bits](size: Exp[Index]) extends Op2[T,FIFO[T]] {
    def mirror(f:Tx) = fifo_alloc[T](f(size))
    val mT = typ[T]
    val bT = bits[T]
  }
  case class FIFOEnq[T:Staged:Bits](fifo: Exp[FIFO[T]], data: Exp[T], en: Exp[Bool]) extends Op[Void] {
    def mirror(f:Tx) = fifo_enq(f(fifo),f(data),f(en))
    val mT = typ[T]
    val bT = bits[T]
  }
  case class FIFODeq[T:Staged:Bits](fifo: Exp[FIFO[T]], en: Exp[Bool], zero: Exp[T]) extends Op[T] {
    def mirror(f:Tx) = fifo_deq(f(fifo), f(en), f(zero))
    val mT = typ[T]
    val bT = bits[T]
  }

  /** Constructors **/
  def fifo_alloc[T:Staged:Bits](size: Exp[Index])(implicit ctx: SrcCtx): Exp[FIFO[T]] = {
    stageMutable(FIFONew[T](size))(ctx)
  }
  def fifo_enq[T:Staged:Bits](fifo: Exp[FIFO[T]], data: Exp[T], en: Exp[Bool])(implicit ctx: SrcCtx): Exp[Void] = {
    stageWrite(fifo)(FIFOEnq(fifo, data, en))(ctx)
  }
  def fifo_deq[T:Staged:Bits](fifo: Exp[FIFO[T]], en: Exp[Bool], z: Exp[T])(implicit ctx: SrcCtx): Exp[T] = {
    stage(FIFODeq(fifo,en,z))(ctx)
  }

  /** Internals **/
  def sizeOf(fifo: FIFO[_])(implicit ctx: SrcCtx): Index = wrap(sizeOf(fifo.s))
  def sizeOf[T](fifo: Exp[FIFO[T]])(implicit ctx: SrcCtx): Exp[Index] = fifo match {
    case Op(FIFONew(size)) => size
    case x => throw new UndefinedDimensionsError(x, None)
  }
}
