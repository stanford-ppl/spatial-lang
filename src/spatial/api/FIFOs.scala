package spatial.api

import spatial.{SpatialApi, SpatialExp, SpatialOps}

trait FIFOOps extends MemoryOps { this: SpatialOps =>
  type FIFO[T] <: FIFOOps[T]

  protected trait FIFOOps[T] {
    def enq(data: T)(implicit ctx: SrcCtx): Void = this.enq(data, true)
    def enq(data: T, en: Bool)(implicit ctx: SrcCtx): Void
    def deq()(implicit ctx: SrcCtx): T = this.deq(true)
    def deq(en: Bool)(implicit ctx: SrcCtx): T

    def load(dram: DRAMDenseTile[T])(implicit ctx: SrcCtx): Void
    //def gather(dram: DRAMSparseTile[T])(implicit ctx: SrcCtx): Void
  }

  def FIFO[T:Bits](size: Index)(implicit ctx: SrcCtx): FIFO[T]

  implicit def fifoType[T:Bits]: Staged[FIFO[T]]
  implicit def fifoIsMemory[T:Bits]: Mem[T, FIFO]
}
trait FIFOApi extends FIFOOps with MemoryApi { this: SpatialApi => }


trait FIFOExp extends FIFOOps with MemoryExp with SpatialExceptions { this: SpatialExp =>
  /** API **/
  case class FIFO[T:Bits](s: Exp[FIFO[T]]) extends FIFOOps[T] {
    def enq(data: T, en: Bool)(implicit ctx: SrcCtx): Void = Void(fifo_enq(this.s, data.s, en.s))
    def deq(en: Bool)(implicit ctx: SrcCtx): T = wrap(fifo_deq(this.s, en.s, bits[T].zero.s))

    def load(dram: DRAMDenseTile[T])(implicit ctx: SrcCtx): Void = coarse_burst(dram, this, isLoad = true)
    //def gather(dram: DRAMSparseTile[T])(implicit ctx: SrcCtx): Void = copy_sparse(dram, this, isLoad = true)
  }

  def FIFO[T:Bits](size: Index)(implicit ctx: SrcCtx): FIFO[T] = FIFO(fifo_alloc[T](size.s))


  /** Staged Type **/
  case class FIFOType[T](bits: Bits[T]) extends Staged[FIFO[T]] {
    override def unwrapped(x: FIFO[T]) = x.s
    override def wrapped(x: Exp[FIFO[T]]) = FIFO(x)(bits)
    override def typeArguments = List(bits)
    override def stagedClass = classOf[FIFO[T]]
    override def isPrimitive = false
  }
  implicit def fifoType[T:Bits]: Staged[FIFO[T]] = FIFOType[T](bits[T])

  case class FIFOIsMemory[T]()(implicit val bits: Bits[T]) extends Mem[T, FIFO] {
    def load(mem: FIFO[T], is: Seq[Index], en: Bool)(implicit ctx: SrcCtx): T = mem.deq(en)
    def store(mem: FIFO[T], is: Seq[Index], data: T, en: Bool)(implicit ctx: SrcCtx): Void = mem.enq(data, en)

    def iterators(mem: FIFO[T])(implicit ctx: SrcCtx): Seq[Counter] = Seq(Counter(0,sizeOf(mem),1,1))
  }
  implicit def fifoIsMemory[T:Bits]: Mem[T, FIFO] = FIFOIsMemory[T]()(bits[T])

  /** IR Nodes **/
  case class FIFONew[T:Bits](size: Exp[Index]) extends Op2[T,FIFO[T]] {
    def mirror(f:Tx) = fifo_alloc[T](f(size))
    val bT = bits[T]
  }
  case class FIFOEnq[T:Bits](fifo: Exp[FIFO[T]], data: Exp[T], en: Exp[Bool]) extends Op[Void] {
    def mirror(f:Tx) = fifo_enq(f(fifo),f(data),f(en))
    val bT = bits[T]
  }
  case class FIFODeq[T:Bits](fifo: Exp[FIFO[T]], en: Exp[Bool], zero: Exp[T]) extends Op[T] {
    def mirror(f:Tx) = fifo_deq(f(fifo), f(en), f(zero))
    val bT = bits[T]
  }

  /** Smart Constructors **/
  def fifo_alloc[T:Bits](size: Exp[Index])(implicit ctx: SrcCtx): Exp[FIFO[T]] = {
    stageMutable(FIFONew[T](size))(ctx)
  }
  def fifo_enq[T:Bits](fifo: Exp[FIFO[T]], data: Exp[T], en: Exp[Bool])(implicit ctx: SrcCtx): Exp[Void] = {
    stageWrite(fifo)(FIFOEnq(fifo, data, en))(ctx)
  }
  def fifo_deq[T:Bits](fifo: Exp[FIFO[T]], en: Exp[Bool], z: Exp[T])(implicit ctx: SrcCtx): Exp[T] = {
    stage(FIFODeq(fifo,en,z))(ctx)
  }

  /** Internals **/
  def sizeOf(fifo: FIFO[_])(implicit ctx: SrcCtx): Index = wrap(sizeOf(fifo.s))
  def sizeOf[T](fifo: Exp[FIFO[T]])(implicit ctx: SrcCtx): Exp[Index] = fifo match {
    case Op(FIFONew(size)) => size
    case x => throw new UndefinedDimensionsError(x, None)
  }
}
