package spatial.api

import org.virtualized.CurriedUpdate
import org.virtualized.SourceContext
import spatial.{SpatialApi, SpatialExp, SpatialOps}

trait SRAMOps extends MemoryOps with RangeOps { this: SpatialOps =>
  type SRAM[T] <: SRAMOps[T]

  protected trait SRAMOps[T] {
    // NOTE: Only dense SRAMViews are currently supported
    //def apply(range: Range*)(implicit ctx: SrcCtx): SRAMView[T]

    def apply(indices: Index*)(implicit ctx: SrcCtx): T
    @CurriedUpdate
    def update(indices: Index*)(value: T): Void

    def load(dram: DRAMDenseTile[T])(implicit ctx: SrcCtx): Void
    def gather(dram: DRAMSparseTile[T])(implicit ctx: SrcCtx): Void
  }

  implicit class SRAMAddressBlock(x: SRAM[Index]) {
    def par(p: Index)(implicit ctx: SrcCtx): SRAM[Index] = sram_par(x, p)
  }


  def SRAM[T:Bits](dimA: Index, dimsB: Index*)(implicit ctx: SrcCtx): SRAM[T]

  private[spatial] def sram_par(sram: SRAM[Index], p: Index)(implicit ctx: SrcCtx): SRAM[Index]

  implicit def sramType[T:Bits]: Staged[SRAM[T]]
  implicit def sramIsMemory[T:Bits]: Mem[T, SRAM]
}
trait SRAMApi extends SRAMOps with MemoryApi with RangeApi { this: SpatialApi => }


trait SRAMExp extends SRAMOps with MemoryExp with RangeExp with MathExp with SpatialExceptions { this: SpatialExp =>
  /** API **/
  case class SRAM[T:Bits](s: Exp[SRAM[T]]) extends SRAMOps[T] {
    private def ofs = lift[Int,Index](0).s
    private[spatial] var _par: Option[Index] = None
    private[spatial] def par: Index = _par.getOrElse(lift[Int,Index](1))

    //def apply(ranges: Range*)(implicit ctx: SrcCtx): SRAMView[T] = SRAMView(this.s, ranges)
    def apply(indices: Index*)(implicit ctx: SrcCtx): T = wrap(sram_load(this.s, stagedDimsOf(s), unwrap(indices), ofs))
    @CurriedUpdate
    override def update(indices: Index*)(data: T): Void = Void(sram_store(this.s, stagedDimsOf(s), unwrap(indices), ofs, data.s, bool(true)))

    def load(dram: DRAMDenseTile[T])(implicit ctx: SrcCtx): Void = coarse_burst(dram, this, isLoad = true)
    def gather(dram: DRAMSparseTile[T])(implicit ctx: SrcCtx): Void = copy_sparse(dram, this, isLoad = true)
  }

  private[spatial] def sram_par(sram: SRAM[Index], p: Index)(implicit ctx: SrcCtx): SRAM[Index] = {
    val sram2 = SRAM(sram.s)
    sram2._par = Some(p)
    sram2
  }

  def SRAM[T:Bits](dimA: Index, dimsB: Index*)(implicit ctx: SrcCtx): SRAM[T] = SRAM(sram_alloc[T](unwrap(dimA +: dimsB)))

  /** Staged Type **/
  case class SRAMType[T](bits: Bits[T]) extends Staged[SRAM[T]] {
    override def unwrapped(x: SRAM[T]) = x.s
    override def wrapped(x: Exp[SRAM[T]]) = SRAM(x)(bits)
    override def typeArguments = List(bits)
    override def stagedClass = classOf[SRAM[T]]
    override def isPrimitive = false
  }
  implicit def sramType[T:Bits]: Staged[SRAM[T]] = SRAMType[T](bits[T])


  case class SRAMIsMemory[T]()(implicit val bits: Bits[T]) extends Mem[T, SRAM] {
    def load(mem: SRAM[T], is: Seq[Index], en: Bool)(implicit ctx: SrcCtx): T = mem.apply(is: _*)
    def store(mem: SRAM[T], is: Seq[Index], data: T, en: Bool)(implicit ctx: SrcCtx): Void = {
      wrap(sram_store[T](mem.s, stagedDimsOf(mem.s),unwrap(is),lift[Int,Index](0).s,bits.unwrapped(data),en.s))
    }
    def iterators(mem: SRAM[T])(implicit ctx: SrcCtx): Seq[Counter] = {
      stagedDimsOf(mem.s).map{d => Counter(0, wrap(d), 1, 1) }
    }
    //def empty(dims: Seq[Index])(implicit ctx: SrcCtx, nT: Bits[T]): SRAM[T] = SRAM(sram_alloc[T](unwrap(dims)))
    //def ranges(mem: SRAM[T])(implicit ctx: SrcCtx) = stagedDimsOf(mem.s).map{d => wrap(d) by 1}
  }
  implicit def sramIsMemory[T:Bits]: Mem[T, SRAM] = SRAMIsMemory[T]()(bits[T])


  /** IR Nodes **/
  case class SRAMNew[T:Bits](dims: Seq[Exp[Index]]) extends Op2[T,SRAM[T]] {
    def mirror(f:Tx) = sram_alloc[T](f(dims))
    val bT = bits[T]
  }
  case class SRAMLoad[T:Bits](mem: Exp[SRAM[T]], dims: Seq[Exp[Index]], is: Seq[Exp[Index]], ofs: Exp[Index]) extends Op[T] {
    def mirror(f:Tx) = sram_load(f(mem), f(dims), f(is), f(ofs))
    val bT = bits[T]
  }
  case class SRAMStore[T:Bits](mem: Exp[SRAM[T]], dims: Seq[Exp[Index]], is: Seq[Exp[Index]], ofs: Exp[Index], data: Exp[T], en: Exp[Bool]) extends Op[Void] {
    def mirror(f:Tx) = sram_store(f(mem), f(dims), f(is), f(ofs), f(data), f(en))
    val bT = bits[T]
  }

  /** Smart Constructors **/
  def sram_alloc[T:Bits](dims: Seq[Exp[Index]])(implicit ctx: SrcCtx): Exp[SRAM[T]] = {
    stageMutable( SRAMNew[T](dims) )(ctx)
  }
  def sram_load[T:Bits](sram: Exp[SRAM[T]], dims: Seq[Exp[Index]], indices: Seq[Exp[Index]], ofs: Exp[Index])(implicit ctx: SrcCtx): Exp[T] = {
    if (indices.length != dims.length) new DimensionMismatchError(sram, dims.length, indices.length)(ctx)
    stage( SRAMLoad(sram, dims, indices, ofs) )(ctx)
  }
  def sram_store[T:Bits](sram: Exp[SRAM[T]], dims: Seq[Exp[Index]], indices: Seq[Exp[Index]], ofs: Exp[Index], data: Exp[T], en: Exp[Bool])(implicit ctx: SrcCtx): Exp[Void] = {
    if (indices.length != dims.length) new DimensionMismatchError(sram, dims.length, indices.length)(ctx)
    stageWrite(sram)( SRAMStore(sram, dims, indices, ofs, data, en) )(ctx)
  }


  /** Internal Methods **/
  def stagedDimsOf(x: Exp[SRAM[_]]): Seq[Exp[Index]] = x match {
    case Def(SRAMNew(dims)) => dims
    case _ => throw new UndefinedDimensionsError(x, None)
  }

  def dimsOf(x: Exp[SRAM[_]]): Seq[Int] = x match {
    case Def(SRAMNew(dims)) => dims.map {
      case Const(c: BigInt) => c.toInt
      case dim => throw new UndefinedDimensionsError(x, Some(dim))
    }
    case _ => throw new UndefinedDimensionsError(x, None)
  }
  def rankOf(x: Exp[SRAM[_]]): Int = dimsOf(x).length
  def rankOf(x: SRAM[_]): Int = rankOf(x.s)

  def flatIndex(indices: Seq[Index], dims: Seq[Index]): Index = {
    val strides = List.tabulate(dims.length){d => productTree(dims.drop(d+1)) }
    sumTree(indices.zip(strides).map{case (a,b) => a*b })
  }

  def constDimsToStrides(dims: Seq[Int]): Seq[Int] = List.tabulate(dims.length){d => dims.drop(d + 1).product}

}
