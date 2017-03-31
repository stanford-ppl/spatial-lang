package spatial.api

import argon.core.Staging
import org.virtualized.CurriedUpdate
import org.virtualized.SourceContext
import spatial.SpatialExp
import forge._

trait SRAMApi extends SRAMExp {
  this: SpatialExp =>

  @api def SRAM[T:Meta:Bits](c: Index): SRAM1[T] = SRAM1(sram_alloc[T,SRAM1](Seq(c.s)))
  @api def SRAM[T:Meta:Bits](r: Index, c: Index): SRAM2[T] = SRAM2(sram_alloc[T,SRAM2](Seq(r.s,c.s)))
  @api def SRAM[T:Meta:Bits](p: Index, r: Index, c: Index): SRAM3[T] = SRAM3(sram_alloc[T,SRAM3](Seq(p.s,r.s,c.s)))
  @api def SRAM[T:Meta:Bits](q: Index, p: Index, r: Index, c: Index): SRAM4[T] = SRAM4(sram_alloc[T,SRAM4](Seq(q.s,p.s,r.s,c.s)))
  @api def SRAM[T:Meta:Bits](m: Index, q: Index, p: Index, r: Index, c: Index): SRAM5[T] = SRAM5(sram_alloc[T,SRAM5](Seq(m.s,q.s,p.s,r.s,c.s)))
}


trait SRAMExp extends Staging with MemoryExp with RangeExp with MathExp with SpatialExceptions {
  this: SpatialExp =>

  /** Infix methods **/
  type SRAM[T] = SRAMN[T,_]

  abstract class SRAMN[T:Meta:Bits,C[_]<:SRAM[_]] extends Template[C[T]] {
    def s: Exp[SRAM[T]]
    protected def ofs = lift[Int, Index](0).s
    protected[spatial] var p: Option[Index] = None

    // TODO: Dimensions should line up on these too
    @api def load(dram: DRAM[T])(implicit ctx: SrcCtx): Void = dense_transfer(dram.toTile, this, isLoad = true)
    @api def load(dram: DRAMDenseTile[T])(implicit ctx: SrcCtx): Void = dense_transfer(dram, this, isLoad = true)
  }

  case class SRAM1[T:Meta:Bits](s: Exp[SRAM1[T]]) extends SRAMN[T,SRAM1] {
    @api def apply(a: Index)
      = wrap(sram_load(this.s, stagedDimsOf(s), Seq(a.s), ofs, bool(true)))
    @api def update(a: Index, data: T): Void
      = Void(sram_store(this.s, stagedDimsOf(s), Seq(a.s), ofs, data.s, bool(true)))
    @api def par(p: Index): SRAM1[T] = { val x = SRAM1(s); x.p = Some(p); x }

    @api def gather(dram: DRAMSparseTile[T])(implicit ctx: SrcCtx): Void = sparse_transfer(dram, this, isLoad = true)
  }
  case class SRAM2[T:Meta:Bits](s: Exp[SRAM2[T]]) extends SRAMN[T,SRAM2] {
    @api def apply(a: Index, b: Index)
      = wrap(sram_load(this.s, stagedDimsOf(s), Seq(a.s,b.s), ofs, bool(true)))
    @api def update(a: Index, b: Index, data: T): Void
      = Void(sram_store(this.s, stagedDimsOf(s), Seq(a.s,b.s), ofs, data.s, bool(true)))
    @api def par(p: Index): SRAM2[T] = { val x = SRAM2(s); x.p = Some(p); x }
  }
  case class SRAM3[T:Meta:Bits](s: Exp[SRAM3[T]]) extends SRAMN[T,SRAM3] {
    @api def apply(a: Index, b: Index, c: Index)
      = wrap(sram_load(this.s, stagedDimsOf(s), Seq(a.s,b.s,c.s), ofs, bool(true)))
    @api def update(a: Index, b: Index, c: Index, data: T): Void
      = Void(sram_store(this.s, stagedDimsOf(s), Seq(a.s,b.s,c.s), ofs, data.s, bool(true)))
    @api def par(p: Index): SRAM3[T] = { val x = SRAM3(s); x.p = Some(p); x }
  }
  case class SRAM4[T:Meta:Bits](s: Exp[SRAM4[T]]) extends SRAMN[T,SRAM4] {
    @api def apply(a: Index, b: Index, c: Index, d: Index)
      = wrap(sram_load(this.s, stagedDimsOf(s), Seq(a.s,b.s,c.s,d.s), ofs, bool(true)))
    @api def update(a: Index, b: Index, c: Index, d: Index, data: T): Void
      = Void(sram_store(this.s, stagedDimsOf(s), Seq(a.s,b.s,c.s,d.s), ofs, data.s, bool(true)))
    @api def par(p: Index): SRAM4[T] = { val x = SRAM4(s); x.p = Some(p); x }
  }
  case class SRAM5[T:Meta:Bits](s: Exp[SRAM5[T]]) extends SRAMN[T,SRAM5] {
    @api def apply(a: Index, b: Index, c: Index, d: Index, e: Index)
      = wrap(sram_load(this.s, stagedDimsOf(s), Seq(a.s,b.s,c.s,d.s,e.s), ofs, bool(true)))
    @api def update(a: Index, b: Index, c: Index, d: Index, e: Index, data: T): Void
      = Void(sram_store(this.s, stagedDimsOf(s), Seq(a.s,b.s,c.s,d.s,e.s), ofs, data.s, bool(true)))
    @api def par(p: Index): SRAM5[T] = { val x = SRAM5(s); x.p = Some(p); x }
  }

  /** Staged Type **/
  trait SRAMType[T] {
    def child: Meta[T]
    def isPrimitive = false
  }
  /*case class SRAMNType[T:Bits](child: Meta[T]) extends Meta[SRAMN[T]] with SRAMType[T] {
    override def wrapped(x: Exp[SRAMN[T]]) = new SRAMN(x)(child,bits[T])
    override def typeArguments = List(child)
    override def stagedClass = classOf[SRAMN[T]]
  }*/
  case class SRAM1Type[T:Bits](child: Meta[T]) extends Meta[SRAM1[T]] with SRAMType[T] {
    override def wrapped(x: Exp[SRAM1[T]]) = SRAM1(x)(child,bits[T])
    override def typeArguments = List(child)
    override def stagedClass = classOf[SRAM1[T]]
  }
  case class SRAM2Type[T:Bits](child: Meta[T]) extends Meta[SRAM2[T]] with SRAMType[T] {
    override def wrapped(x: Exp[SRAM2[T]]) = SRAM2(x)(child,bits[T])
    override def typeArguments = List(child)
    override def stagedClass = classOf[SRAM2[T]]
  }
  case class SRAM3Type[T:Bits](child: Meta[T]) extends Meta[SRAM3[T]] with SRAMType[T] {
    override def wrapped(x: Exp[SRAM3[T]]) = SRAM3(x)(child,bits[T])
    override def typeArguments = List(child)
    override def stagedClass = classOf[SRAM3[T]]
  }
  case class SRAM4Type[T:Bits](child: Meta[T]) extends Meta[SRAM4[T]] with SRAMType[T] {
    override def wrapped(x: Exp[SRAM4[T]]) = SRAM4(x)(child,bits[T])
    override def typeArguments = List(child)
    override def stagedClass = classOf[SRAM4[T]]
  }
  case class SRAM5Type[T:Bits](child: Meta[T]) extends Meta[SRAM5[T]] with SRAMType[T] {
    override def wrapped(x: Exp[SRAM5[T]]) = SRAM5(x)(child,bits[T])
    override def typeArguments = List(child)
    override def stagedClass = classOf[SRAM5[T]]
  }

  //implicit def sramNType[T:Meta:Bits]: Meta[SRAMN[T]] = SRAMNType(meta[T])
  implicit def sram1Type[T:Meta:Bits]: Meta[SRAM1[T]] = SRAM1Type(meta[T])
  implicit def sram2Type[T:Meta:Bits]: Meta[SRAM2[T]] = SRAM2Type(meta[T])
  implicit def sram3Type[T:Meta:Bits]: Meta[SRAM3[T]] = SRAM3Type(meta[T])
  implicit def sram4Type[T:Meta:Bits]: Meta[SRAM4[T]] = SRAM4Type(meta[T])
  implicit def sram5Type[T:Meta:Bits]: Meta[SRAM5[T]] = SRAM5Type(meta[T])

  class SRAMIsMemory[T:Meta:Bits,C[T]<:SRAMN[T,C]](implicit mC: Meta[C[T]]) extends Mem[T,C] {
    def load(mem: C[T], is: Seq[Index], en: Bool)(implicit ctx: SrcCtx): T = {
      wrap(sram_load(mem.s, stagedDimsOf(mem.s), unwrap(is), lift[Int,Index](0).s, en.s))
    }
    def store(mem: C[T], is: Seq[Index], data: T, en: Bool)(implicit ctx: SrcCtx): Void = {
      wrap(sram_store[T](mem.s, stagedDimsOf(mem.s),unwrap(is),lift[Int,Index](0).s,data.s,en.s))
    }
    def iterators(mem: C[T])(implicit ctx: SrcCtx): Seq[Counter] = {
      stagedDimsOf(mem.s).map{d => Counter(0, wrap(d), 1, 1) }
    }
  }
  //implicit def sramNIsMemory[T:Meta:Bits]: Mem[T,SRAMN] = new SRAMIsMemory[T,SRAMN]
  implicit def sram1IsMemory[T:Meta:Bits]: Mem[T,SRAM1] = new SRAMIsMemory[T,SRAM1]
  implicit def sram2IsMemory[T:Meta:Bits]: Mem[T,SRAM2] = new SRAMIsMemory[T,SRAM2]
  implicit def sram3IsMemory[T:Meta:Bits]: Mem[T,SRAM3] = new SRAMIsMemory[T,SRAM3]
  implicit def sram4IsMemory[T:Meta:Bits]: Mem[T,SRAM4] = new SRAMIsMemory[T,SRAM4]
  implicit def sram5IsMemory[T:Meta:Bits]: Mem[T,SRAM5] = new SRAMIsMemory[T,SRAM5]

  /** IR Nodes **/
  case class SRAMNew[T:Type:Bits,C[_]<:SRAM[_]](dims: Seq[Exp[Index]]) extends Op[C[T]] {
    def mirror(f:Tx) = sram_alloc[T,C](f(dims))
    val mT = typ[T]
    val bT = bits[T]
  }
  case class SRAMLoad[T:Type:Bits](mem: Exp[SRAM[T]], dims: Seq[Exp[Index]], is: Seq[Exp[Index]], ofs: Exp[Index], en: Exp[Bool]) extends EnabledOp[T](en) {
    def mirror(f:Tx) = sram_load(f(mem), f(dims), f(is), f(ofs), f(en))
    val mT = typ[T]
    val bT = bits[T]
  }
  case class SRAMStore[T:Type:Bits](mem: Exp[SRAM[T]], dims: Seq[Exp[Index]], is: Seq[Exp[Index]], ofs: Exp[Index], data: Exp[T], en: Exp[Bool]) extends EnabledOp[Void](en) {
    def mirror(f:Tx) = sram_store(f(mem), f(dims), f(is), f(ofs), f(data), f(en))
    val mT = typ[T]
    val bT = bits[T]
  }

  /** Constructors **/
  def sram_alloc[T:Type:Bits,C[_]<:SRAM[_]](dims: Seq[Exp[Index]])(implicit ctx: SrcCtx): Exp[C[T]] = {
    stageMutable( SRAMNew[T,C](dims) )(ctx)
  }
  def sram_load[T:Type:Bits](sram: Exp[SRAM[T]], dims: Seq[Exp[Index]], indices: Seq[Exp[Index]], ofs: Exp[Index], en: Exp[Bool])(implicit ctx: SrcCtx): Exp[T] = {
    if (indices.length != dims.length) new DimensionMismatchError(sram, dims.length, indices.length)(ctx)
    stage( SRAMLoad(sram, dims, indices, ofs, en) )(ctx)
  }
  def sram_store[T:Type:Bits](sram: Exp[SRAM[T]], dims: Seq[Exp[Index]], indices: Seq[Exp[Index]], ofs: Exp[Index], data: Exp[T], en: Exp[Bool])(implicit ctx: SrcCtx): Exp[Void] = {
    if (indices.length != dims.length) new DimensionMismatchError(sram, dims.length, indices.length)(ctx)
    stageWrite(sram)( SRAMStore(sram, dims, indices, ofs, data, en) )(ctx)
  }


  /** Internal Methods **/
  def stagedDimsOf(x: Exp[_]): Seq[Exp[Index]] = x match {
    case Def(SRAMNew(dims)) => dims
    case _ => throw new UndefinedDimensionsError(x, None)
  }

  def dimsOf(x: Exp[_]): Seq[Int] = x match {
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
