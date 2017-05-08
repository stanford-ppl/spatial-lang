package spatial.api

import spatial._
import forge._

trait FILOApi extends FILOExp { this: SpatialApi =>

  @api def FILO[T:Type:Bits](size: Index): FILO[T] = FILO(filo_alloc[T](size.s)) 
}

trait FILOExp { this: SpatialExp =>

  /** Infix methods **/
  case class FILO[T:Meta:Bits](s: Exp[FILO[T]]) extends Template[FILO[T]] {
    @api def push(data: T): Void = this.push(data, true)
    @api def push(data: T, en: Bool): Void = Void(filo_push(this.s, data.s, en.s))

    @api def pop(): T = this.pop(true)
    @api def pop(en: Bool): T = wrap(filo_pop(this.s, en.s))

    @api def empty(): Bool = wrap(filo_empty(this.s))
    @api def full(): Bool = wrap(filo_full(this.s))
    @api def almostEmpty(): Bool = wrap(filo_almost_empty(this.s))
    @api def almostFull(): Bool = wrap(filo_almost_full(this.s))
    @api def numel(): Index = wrap(filo_numel(this.s))

    //@api def load(dram: DRAM1[T]): Void = dense_transfer(dram.toTile(this.ranges), this, isLoad = true)
    @api def load(dram: DRAMDenseTile1[T]): Void = dense_transfer(dram, this, isLoad = true)
    // @api def gather(dram: DRAMSparseTile[T]): Void = copy_sparse(dram, this, isLoad = true)

    @util def ranges: Seq[Range] = Seq(range_alloc(None, wrap(sizeOf(s)),None,None))
  }


  /** Type classes **/
  // --- Staged
  case class FILOType[T:Bits](child: Meta[T]) extends Meta[FILO[T]] {
    override def wrapped(x: Exp[FILO[T]]) = FILO(x)(child,bits[T])
    override def typeArguments = List(child)
    override def stagedClass = classOf[FILO[T]]
    override def isPrimitive = false
  }
  implicit def filoType[T:Meta:Bits]: Meta[FILO[T]] = FILOType(meta[T])

  // --- Memory
  class FILOIsMemory[T:Type:Bits] extends Mem[T,FILO] {
    def load(mem: FILO[T], is: Seq[Index], en: Bool)(implicit ctx: SrcCtx): T = mem.pop(en)
    def store(mem: FILO[T], is: Seq[Index], data: T, en: Bool)(implicit ctx: SrcCtx): Void = mem.push(data, en)

    def iterators(mem: FILO[T])(implicit ctx: SrcCtx): Seq[Counter] = Seq(Counter(0,sizeOf(mem),1,1))
  }
  implicit def filoIsMemory[T:Type:Bits]: Mem[T, FILO] = new FILOIsMemory[T]


  /** IR Nodes **/
  case class FILONew[T:Type:Bits](size: Exp[Index]) extends Op2[T,FILO[T]] {
    def mirror(f:Tx) = filo_alloc[T](f(size))
    val mT = typ[T]
    val bT = bits[T]
  }
  case class FILOPush[T:Type:Bits](filo: Exp[FILO[T]], data: Exp[T], en: Exp[Bool]) extends EnabledOp[Void](en) {
    def mirror(f:Tx) = filo_push(f(filo),f(data),f(en))
    val mT = typ[T]
    val bT = bits[T]
  }
  case class FILOPop[T:Type:Bits](filo: Exp[FILO[T]], en: Exp[Bool]) extends EnabledOp[T](en) {
    def mirror(f:Tx) = filo_pop(f(filo), f(en))
    val mT = typ[T]
    val bT = bits[T]
  }
  case class FILOEmpty[T:Type:Bits](filo: Exp[FILO[T]]) extends Op[Bool] {
    def mirror(f:Tx) = filo_empty(f(filo))
    val mT = typ[T]
    val bT = bits[T]
  }
  case class FILOFull[T:Type:Bits](filo: Exp[FILO[T]]) extends Op[Bool] {
    def mirror(f:Tx) = filo_full(f(filo))
    val mT = typ[T]
    val bT = bits[T]
  }
  case class FILOAlmostEmpty[T:Type:Bits](filo: Exp[FILO[T]]) extends Op[Bool] {
    def mirror(f:Tx) = filo_almost_empty(f(filo))
    val mT = typ[T]
    val bT = bits[T]
  }
  case class FILOAlmostFull[T:Type:Bits](filo: Exp[FILO[T]]) extends Op[Bool] {
    def mirror(f:Tx) = filo_almost_full(f(filo))
    val mT = typ[T]
    val bT = bits[T]
  }
  case class FILONumel[T:Type:Bits](filo: Exp[FILO[T]]) extends Op[Index] {
    def mirror(f:Tx) = filo_numel(f(filo))
    val mT = typ[T]
    val bT = bits[T]
  }

  /** Constructors **/
  @internal def filo_alloc[T:Type:Bits](size: Exp[Index]): Exp[FILO[T]] = {
    stageMutable(FILONew[T](size))(ctx)
  }
  @internal def filo_push[T:Type:Bits](filo: Exp[FILO[T]], data: Exp[T], en: Exp[Bool]): Exp[Void] = {
    stageWrite(filo)(FILOPush(filo, data, en))(ctx)
  }
  @internal def filo_pop[T:Type:Bits](filo: Exp[FILO[T]], en: Exp[Bool]): Exp[T] = {
    stageWrite(filo)(FILOPop(filo,en))(ctx)
  }
  @internal def filo_empty[T:Type:Bits](filo: Exp[FILO[T]]): Exp[Bool] = {
    stage(FILOEmpty(filo))(ctx)
  }
  @internal def filo_full[T:Type:Bits](filo: Exp[FILO[T]]): Exp[Bool] = {
    stage(FILOFull(filo))(ctx)
  }
  @internal def filo_almost_empty[T:Type:Bits](filo: Exp[FILO[T]]): Exp[Bool] = {
    stage(FILOAlmostEmpty(filo))(ctx)
  }
  @internal def filo_almost_full[T:Type:Bits](filo: Exp[FILO[T]]): Exp[Bool] = {
    stage(FILOAlmostFull(filo))(ctx)
  }
  @internal def filo_numel[T:Type:Bits](filo: Exp[FILO[T]]): Exp[Index] = {
    stage(FILONumel(filo))(ctx)
  }
}
