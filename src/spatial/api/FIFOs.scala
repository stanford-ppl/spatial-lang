package spatial.api

import spatial.{SpatialApi, SpatialExp, SpatialOps}

trait FIFOOps extends MemoryOps { this: SpatialOps =>
  type FIFO[T] <: FIFOOps[T]

  protected trait FIFOOps[T] {
    def enq(value: T)(implicit ctx: SrcCtx): Void = this.enq(value, true)
    def enq(value: T, en: Bool)(implicit ctx: SrcCtx): Void
    def deq()(implicit ctx: SrcCtx): T = this.deq(true)
    def deq(en: Bool)(implicit ctx: SrcCtx): T
  }

  def FIFO[T:Bits](size: Index)(implicit ctx: SrcCtx): FIFO[T]

  implicit def fifoType[T:Bits]: Staged[FIFO[T]]
  implicit def fifoIsMemory[T:Bits]: Mem[T, FIFO]
}
trait FIFOApi extends FIFOOps with MemoryApi { this: SpatialApi => }


trait FIFOExp extends FIFOOps with MemoryExp with SpatialExceptions { this: SpatialExp =>
  /** API **/
  case class FIFO[T:Bits](s: Exp[FIFO[T]]) extends FIFOOps[T] {
    def enq(value: T, en: Bool)(implicit ctx: SrcCtx): Void = Void(fifo_enq(this.s, value.s, en.s))
    def deq(en: Bool)(implicit ctx: SrcCtx): T = wrap(fifo_deq(this.s, en.s))
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
    def store(mem: FIFO[T], is: Seq[Index], v: T, en: Bool)(implicit ctx: SrcCtx): Void = mem.enq(v, en)

    def iterators(mem: FIFO[T])(implicit ctx: SrcCtx): Seq[Counter] = Seq(Counter(0,sizeOf(mem),1,1))
  }
  implicit def fifoIsMemory[T:Bits]: Mem[T, FIFO] = FIFOIsMemory[T]()(bits[T])

  /** IR Nodes **/
  case class FIFONew[T:Bits](size: Exp[Index]) extends Op2[T,FIFO[T]] {
    def mirror(f:Tx) = fifo_alloc[T](f(size))
  }
  case class FIFOEnq[T:Bits](fifo: Exp[FIFO[T]], value: Exp[T], en: Exp[Bool]) extends Op[Void] {
    def mirror(f:Tx) = fifo_enq(f(fifo),f(value),f(en))
  }
  case class FIFODeq[T:Bits](fifo: Exp[FIFO[T]], en: Exp[Bool]) extends Op[T] {
    def mirror(f:Tx) = fifo_deq(f(fifo), f(en))
  }

  /** Smart Constructors **/
  def fifo_alloc[T:Bits](size: Exp[Index])(implicit ctx: SrcCtx): Exp[FIFO[T]] = {
    size match {case Param(_) => ; case dim => new InvalidDimensionError(dim)(ctx) }
    stageMutable(FIFONew[T](size))(ctx)
  }
  def fifo_enq[T:Bits](fifo: Exp[FIFO[T]], value: Exp[T], en: Exp[Bool])(implicit ctx: SrcCtx): Exp[Void] = {
    stageWrite(fifo)(FIFOEnq(fifo, value, en))(ctx)
  }
  def fifo_deq[T:Bits](fifo: Exp[FIFO[T]], en: Exp[Bool])(implicit ctx: SrcCtx): Exp[T] = stage(FIFODeq(fifo,en))(ctx)

  /** Internals **/
  def sizeOf(fifo: FIFO[_])(implicit ctx: SrcCtx): Index = fifo.s match {
    case Op(FIFONew(size)) => wrap(size)
    case x => throw new UndefinedDimensionsError(x, None)
  }
}
