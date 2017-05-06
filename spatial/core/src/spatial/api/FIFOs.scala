package spatial.api

import spatial._
import forge._

trait FIFOApi extends FIFOExp { this: SpatialApi =>

  @api def FIFO[T:Type:Bits](size: Index): FIFO[T] = FIFO(fifo_alloc[T](size.s))
}

trait FIFOExp { this: SpatialExp =>

  /** Infix methods **/
  case class FIFO[T:Meta:Bits](s: Exp[FIFO[T]]) extends Template[FIFO[T]] {
    @api def enq(data: T): Void = this.enq(data, true)
    @api def enq(data: T, en: Bool): Void = Void(fifo_enq(this.s, data.s, en.s))

    @api def deq(): T = this.deq(true)
    @api def deq(en: Bool): T = wrap(fifo_deq(this.s, en.s))

    @api def empty(): Bool = wrap(fifo_empty(this.s))
    @api def full(): Bool = wrap(fifo_full(this.s))

    //@api def load(dram: DRAM1[T]): Void = dense_transfer(dram.toTile(this.ranges), this, isLoad = true)
    @api def load(dram: DRAMDenseTile1[T]): Void = dense_transfer(dram, this, isLoad = true)
    // @api def gather(dram: DRAMSparseTile[T]): Void = copy_sparse(dram, this, isLoad = true)

    @util def ranges: Seq[Range] = Seq(range_alloc(None, wrap(sizeOf(s)),None,None))
  }


  /** Type classes **/
  // --- Staged
  case class FIFOType[T:Bits](child: Meta[T]) extends Meta[FIFO[T]] {
    override def wrapped(x: Exp[FIFO[T]]) = FIFO(x)(child,bits[T])
    override def typeArguments = List(child)
    override def stagedClass = classOf[FIFO[T]]
    override def isPrimitive = false
  }
  implicit def fifoType[T:Meta:Bits]: Meta[FIFO[T]] = FIFOType(meta[T])

  // --- Memory
  class FIFOIsMemory[T:Type:Bits] extends Mem[T,FIFO] {
    def load(mem: FIFO[T], is: Seq[Index], en: Bool)(implicit ctx: SrcCtx): T = mem.deq(en)
    def store(mem: FIFO[T], is: Seq[Index], data: T, en: Bool)(implicit ctx: SrcCtx): Void = mem.enq(data, en)

    def iterators(mem: FIFO[T])(implicit ctx: SrcCtx): Seq[Counter] = Seq(Counter(0,sizeOf(mem),1,1))
  }
  implicit def fifoIsMemory[T:Type:Bits]: Mem[T, FIFO] = new FIFOIsMemory[T]


  /** IR Nodes **/
  case class FIFONew[T:Type:Bits](size: Exp[Index]) extends Op2[T,FIFO[T]] {
    def mirror(f:Tx) = fifo_alloc[T](f(size))
    val mT = typ[T]
    val bT = bits[T]
  }
  case class FIFOEnq[T:Type:Bits](fifo: Exp[FIFO[T]], data: Exp[T], en: Exp[Bool]) extends EnabledOp[Void](en) {
    def mirror(f:Tx) = fifo_enq(f(fifo),f(data),f(en))
    val mT = typ[T]
    val bT = bits[T]
  }
  case class FIFODeq[T:Type:Bits](fifo: Exp[FIFO[T]], en: Exp[Bool]) extends EnabledOp[T](en) {
    def mirror(f:Tx) = fifo_deq(f(fifo), f(en))
    val mT = typ[T]
    val bT = bits[T]
  }
  case class FIFOEmpty[T:Type:Bits](fifo: Exp[FIFO[T]]) extends Op[Bool] {
    def mirror(f:Tx) = fifo_empty(f(fifo))
    val mT = typ[T]
    val bT = bits[T]
  }
  case class FIFOFull[T:Type:Bits](fifo: Exp[FIFO[T]]) extends Op[Bool] {
    def mirror(f:Tx) = fifo_full(f(fifo))
    val mT = typ[T]
    val bT = bits[T]
  }

  /** Constructors **/
  @internal def fifo_alloc[T:Type:Bits](size: Exp[Index]): Exp[FIFO[T]] = {
    stageMutable(FIFONew[T](size))(ctx)
  }
  @internal def fifo_enq[T:Type:Bits](fifo: Exp[FIFO[T]], data: Exp[T], en: Exp[Bool]): Exp[Void] = {
    stageWrite(fifo)(FIFOEnq(fifo, data, en))(ctx)
  }
  @internal def fifo_deq[T:Type:Bits](fifo: Exp[FIFO[T]], en: Exp[Bool]): Exp[T] = {
    stageWrite(fifo)(FIFODeq(fifo,en))(ctx)
  }
  @internal def fifo_empty[T:Type:Bits](fifo: Exp[FIFO[T]]): Exp[Bool] = {
    stage(FIFOEmpty(fifo))(ctx)
  }
  @internal def fifo_full[T:Type:Bits](fifo: Exp[FIFO[T]]): Exp[Bool] = {
    stage(FIFOFull(fifo))(ctx)
  }
}
