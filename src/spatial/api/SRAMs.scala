package spatial.api

import argon.core.Staging
import org.virtualized.CurriedUpdate
import org.virtualized.SourceContext
import spatial.SpatialExp

trait SRAMApi extends SRAMExp {
  this: SpatialExp =>

  def SRAM[T:Staged:Bits](dimA: Index, dimsB: Index*)(implicit ctx: SrcCtx): SRAM[T] = {
    SRAM(sram_alloc[T](unwrap(dimA +: dimsB)))
  }

}


trait SRAMExp extends Staging with MemoryExp with RangeExp with MathExp with SpatialExceptions {
  this: SpatialExp =>

  /** Infix methods **/
  case class SRAM[T:Staged:Bits](s: Exp[SRAM[T]]) {
    private def ofs = lift[Int,Index](0).s
    private[spatial] var p: Option[Index] = None

    def par(p: Index): SRAM[T] = { val x = SRAM(s); x.p = Some(p); x }

    //def apply(ranges: Range*)(implicit ctx: SrcCtx): SRAMView[T] = SRAMView(this.s, ranges)
    def apply(indices: Index*)(implicit ctx: SrcCtx): T = wrap(sram_load(this.s, stagedDimsOf(s), unwrap(indices), ofs))
    @CurriedUpdate
    def update(indices: Index*)(data: T): Void = Void(sram_store(this.s, stagedDimsOf(s), unwrap(indices), ofs, data.s, bool(true)))

    def load(dram: DRAM[T])(implicit ctx: SrcCtx): Void = dense_transfer(dram.toTile, this, isLoad = true)
    def load(dram: DRAMDenseTile[T])(implicit ctx: SrcCtx): Void = dense_transfer(dram, this, isLoad = true)
    def gather(dram: DRAMSparseTile[T])(implicit ctx: SrcCtx): Void = sparse_transfer(dram, this, isLoad = true)
  }

  /** Staged Type **/
  case class SRAMType[T:Bits](child: Staged[T]) extends Staged[SRAM[T]] {
    override def unwrapped(x: SRAM[T]) = x.s
    override def wrapped(x: Exp[SRAM[T]]) = SRAM(x)(child,bits[T])
    override def typeArguments = List(child)
    override def stagedClass = classOf[SRAM[T]]
    override def isPrimitive = false
  }
  implicit def sramType[T:Staged:Bits]: Staged[SRAM[T]] = SRAMType(typ[T])


  class SRAMIsMemory[T:Staged:Bits] extends Mem[T, SRAM] {
    def load(mem: SRAM[T], is: Seq[Index], en: Bool)(implicit ctx: SrcCtx): T = mem.apply(is: _*)
    def store(mem: SRAM[T], is: Seq[Index], data: T, en: Bool)(implicit ctx: SrcCtx): Void = {
      wrap(sram_store[T](mem.s, stagedDimsOf(mem.s),unwrap(is),lift[Int,Index](0).s,data.s,en.s))
    }
    def iterators(mem: SRAM[T])(implicit ctx: SrcCtx): Seq[Counter] = {
      stagedDimsOf(mem.s).map{d => Counter(0, wrap(d), 1, 1) }
    }
  }
  implicit def sramIsMemory[T:Staged:Bits]: Mem[T, SRAM] = new SRAMIsMemory[T]

  /** IR Nodes **/
  case class SRAMNew[T:Staged:Bits](dims: Seq[Exp[Index]]) extends Op[SRAM[T]] {
    def mirror(f:Tx) = sram_alloc[T](f(dims))
    val mT = typ[T]
    val bT = bits[T]
  }
  case class SRAMLoad[T:Staged:Bits](mem: Exp[SRAM[T]], dims: Seq[Exp[Index]], is: Seq[Exp[Index]], ofs: Exp[Index]) extends Op[T] {
    def mirror(f:Tx) = sram_load(f(mem), f(dims), f(is), f(ofs))
    val mT = typ[T]
    val bT = bits[T]
  }
  case class SRAMStore[T:Staged:Bits](mem: Exp[SRAM[T]], dims: Seq[Exp[Index]], is: Seq[Exp[Index]], ofs: Exp[Index], data: Exp[T], en: Exp[Bool]) extends Op[Void] {
    def mirror(f:Tx) = sram_store(f(mem), f(dims), f(is), f(ofs), f(data), f(en))
    val mT = typ[T]
    val bT = bits[T]
  }

  /** Constructors **/
  def sram_alloc[T:Staged:Bits](dims: Seq[Exp[Index]])(implicit ctx: SrcCtx): Exp[SRAM[T]] = {
    stageMutable( SRAMNew[T](dims) )(ctx)
  }
  def sram_load[T:Staged:Bits](sram: Exp[SRAM[T]], dims: Seq[Exp[Index]], indices: Seq[Exp[Index]], ofs: Exp[Index])(implicit ctx: SrcCtx): Exp[T] = {
    if (indices.length != dims.length) new DimensionMismatchError(sram, dims.length, indices.length)(ctx)
    stage( SRAMLoad(sram, dims, indices, ofs) )(ctx)
  }
  def sram_store[T:Staged:Bits](sram: Exp[SRAM[T]], dims: Seq[Exp[Index]], indices: Seq[Exp[Index]], ofs: Exp[Index], data: Exp[T], en: Exp[Bool])(implicit ctx: SrcCtx): Exp[Void] = {
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
      case Const(c: BigDecimal) => c.toInt
      case dim => throw new UndefinedDimensionsError(x, Some(dim))
    }
    case _ => throw new UndefinedDimensionsError(x, None)
  }
  def rankOf(x: Exp[SRAM[_]]): Int = dimsOf(x).length
  def rankOf(x: SRAM[_]): Int = rankOf(x.s)

  def flatIndex(indices: Seq[Index], dims: Seq[Index])(implicit ctx: SrcCtx): Index = {
    val strides = List.tabulate(dims.length){d => productTree(dims.drop(d+1)) }
    sumTree(indices.zip(strides).map{case (a,b) => a*b })
  }

  def constDimsToStrides(dims: Seq[Int]): Seq[Int] = List.tabulate(dims.length){d => dims.drop(d + 1).product}

}
