package spatial.lang

import argon.core._
import forge._
import spatial.nodes._
import spatial.utils._
import spatial.DimensionMismatchError

trait SRAM[T] { this: Template[_] =>
  def s: Exp[SRAM[T]]
  @internal protected def ofs = lift[Int, Index](0).s
  protected[spatial] var p: Option[Index] = None

  @internal def ranges: Seq[Range] = stagedDimsOf(s).map{d => Range.alloc(None, wrap(d),None,None)}
}
object SRAM {
  @api def apply[T:Type:Bits](c: Index): SRAM1[T] = SRAM1(SRAM.alloc[T,SRAM1](c.s))
  @api def apply[T:Type:Bits](r: Index, c: Index): SRAM2[T] = SRAM2(SRAM.alloc[T,SRAM2](r.s,c.s))
  @api def apply[T:Type:Bits](p: Index, r: Index, c: Index): SRAM3[T] = SRAM3(SRAM.alloc[T,SRAM3](p.s,r.s,c.s))
  @api def apply[T:Type:Bits](q: Index, p: Index, r: Index, c: Index): SRAM4[T] = SRAM4(SRAM.alloc[T,SRAM4](q.s,p.s,r.s,c.s))
  @api def apply[T:Type:Bits](m: Index, q: Index, p: Index, r: Index, c: Index): SRAM5[T] = SRAM5(SRAM.alloc[T,SRAM5](m.s,q.s,p.s,r.s,c.s))

  /** Constructors **/
  @internal def alloc[T:Type:Bits,C[_]<:SRAM[_]](dims: Exp[Index]*)(implicit mC: Type[C[T]]): Exp[C[T]] = {
    stageMutable( SRAMNew[T,C](dims) )(ctx)
  }
  @internal def load[T:Type:Bits](sram: Exp[SRAM[T]], dims: Seq[Exp[Index]], indices: Seq[Exp[Index]], ofs: Exp[Index], en: Exp[Bit]): Exp[T] = {
    if (indices.length != dims.length) new DimensionMismatchError(sram, dims.length, indices.length)
    stage( SRAMLoad(sram, dims, indices, ofs, en) )(ctx)
  }
  @internal def store[T:Type:Bits](sram: Exp[SRAM[T]], dims: Seq[Exp[Index]], indices: Seq[Exp[Index]], ofs: Exp[Index], data: Exp[T], en: Exp[Bit]): Exp[MUnit] = {
    if (indices.length != dims.length) new DimensionMismatchError(sram, dims.length, indices.length)
    stageWrite(sram)( SRAMStore(sram, dims, indices, ofs, data, en) )(ctx)
  }

  @internal def par_load[T:Type:Bits](
    sram: Exp[SRAM[T]],
    addr: Seq[Seq[Exp[Index]]],
    ens:  Seq[Exp[Bit]]
  ) = {
    implicit val vT = VectorN.typeFromLen[T](addr.length)
    stage( ParSRAMLoad(sram, addr, ens) )(ctx)
  }

  @internal def par_store[T:Type:Bits](
    sram: Exp[SRAM[T]],
    addr: Seq[Seq[Exp[Index]]],
    data: Seq[Exp[T]],
    ens:  Seq[Exp[Bit]]
  ) = {
    stageWrite(sram)( ParSRAMStore(sram, addr, data, ens) )(ctx)
  }

}

case class SRAM1[T:Type:Bits](s: Exp[SRAM1[T]]) extends Template[SRAM1[T]] with SRAM[T] {
  @api def apply(a: Index) = wrap(SRAM.load(this.s, stagedDimsOf(s), Seq(a.s), ofs, Bit.const(true)))
  @api def update(a: Index, data: T): MUnit = MUnit(SRAM.store(this.s, stagedDimsOf(s), Seq(a.s), ofs, data.s, Bit.const(true)))
  @api def par(p: Index): SRAM1[T] = { val x = SRAM1(s); x.p = Some(p); x }

  @api def gather(dram: DRAMSparseTile[T]): MUnit = DRAMTransfers.sparse_transfer(dram, this, isLoad = true)

  // @api def cols(): Index = field[Index]("c")
  @api def load(dram: DRAM1[T]): MUnit = DRAMTransfers.dense_transfer(dram.toTile(ranges), this, isLoad = true)
  @api def load(dram: DRAMDenseTile1[T]): MUnit = DRAMTransfers.dense_transfer(dram, this, isLoad = true)
}
object SRAM1 {
  implicit def sram1Type[T:Type:Bits]: Type[SRAM1[T]] = SRAM1Type(typ[T])
  implicit def sram1IsMemory[T:Type:Bits]: Mem[T,SRAM1] = new SRAMIsMemory[T,SRAM1]
}

case class SRAM2[T:Type:Bits](s: Exp[SRAM2[T]]) extends Template[SRAM2[T]] with SRAM[T] {
  @api def apply(a: Index, b: Index)
  = wrap(SRAM.load(this.s, stagedDimsOf(s), Seq(a.s,b.s), ofs, Bit.const(true)))
  @api def update(a: Index, b: Index, data: T): MUnit
  = MUnit(SRAM.store(this.s, stagedDimsOf(s), Seq(a.s,b.s), ofs, data.s, Bit.const(true)))
  @api def par(p: Index): SRAM2[T] = { val x = SRAM2(s); x.p = Some(p); x }

  @api def load(dram: DRAM2[T]): MUnit = DRAMTransfers.dense_transfer(dram.toTile(ranges), this, isLoad = true)
  @api def load(dram: DRAMDenseTile2[T]): MUnit = DRAMTransfers.dense_transfer(dram, this, isLoad = true)
}
object SRAM2 {
  implicit def sram2Type[T:Type:Bits]: Type[SRAM2[T]] = SRAM2Type(typ[T])
  implicit def sram2IsMemory[T:Type:Bits]: Mem[T,SRAM2] = new SRAMIsMemory[T,SRAM2]
}

case class SRAM3[T:Type:Bits](s: Exp[SRAM3[T]]) extends Template[SRAM3[T]] with SRAM[T] {
  @api def apply(a: Index, b: Index, c: Index)
  = wrap(SRAM.load(this.s, stagedDimsOf(s), Seq(a.s,b.s,c.s), ofs, Bit.const(true)))
  @api def update(a: Index, b: Index, c: Index, data: T): MUnit
  = MUnit(SRAM.store(this.s, stagedDimsOf(s), Seq(a.s,b.s,c.s), ofs, data.s, Bit.const(true)))
  @api def par(p: Index): SRAM3[T] = { val x = SRAM3(s); x.p = Some(p); x }

  @api def load(dram: DRAM3[T]): MUnit = DRAMTransfers.dense_transfer(dram.toTile(ranges), this, isLoad = true)
  @api def load(dram: DRAMDenseTile3[T]): MUnit = DRAMTransfers.dense_transfer(dram, this, isLoad = true)
}
object SRAM3 {
  implicit def sram3Type[T:Type:Bits]: Type[SRAM3[T]] = SRAM3Type(typ[T])
  implicit def sram3IsMemory[T:Type:Bits]: Mem[T,SRAM3] = new SRAMIsMemory[T,SRAM3]
}

case class SRAM4[T:Type:Bits](s: Exp[SRAM4[T]]) extends Template[SRAM4[T]] with SRAM[T] {
  @api def apply(a: Index, b: Index, c: Index, d: Index)
  = wrap(SRAM.load(this.s, stagedDimsOf(s), Seq(a.s,b.s,c.s,d.s), ofs, Bit.const(true)))
  @api def update(a: Index, b: Index, c: Index, d: Index, data: T): MUnit
  = MUnit(SRAM.store(this.s, stagedDimsOf(s), Seq(a.s,b.s,c.s,d.s), ofs, data.s, Bit.const(true)))
  @api def par(p: Index): SRAM4[T] = { val x = SRAM4(s); x.p = Some(p); x }

  @api def load(dram: DRAM4[T]): MUnit = DRAMTransfers.dense_transfer(dram.toTile(ranges), this, isLoad = true)
  @api def load(dram: DRAMDenseTile4[T]): MUnit = DRAMTransfers.dense_transfer(dram, this, isLoad = true)
}
object SRAM4 {
  implicit def sram4Type[T:Type:Bits]: Type[SRAM4[T]] = SRAM4Type(typ[T])
  implicit def sram4IsMemory[T:Type:Bits]: Mem[T,SRAM4] = new SRAMIsMemory[T,SRAM4]
}

case class SRAM5[T:Type:Bits](s: Exp[SRAM5[T]]) extends Template[SRAM5[T]] with SRAM[T] {
  @api def apply(a: Index, b: Index, c: Index, d: Index, e: Index)
  = wrap(SRAM.load(this.s, stagedDimsOf(s), Seq(a.s,b.s,c.s,d.s,e.s), ofs, Bit.const(true)))
  @api def update(a: Index, b: Index, c: Index, d: Index, e: Index, data: T): MUnit
  = MUnit(SRAM.store(this.s, stagedDimsOf(s), Seq(a.s,b.s,c.s,d.s,e.s), ofs, data.s, Bit.const(true)))
  @api def par(p: Index): SRAM5[T] = { val x = SRAM5(s); x.p = Some(p); x }

  @api def load(dram: DRAM5[T]): MUnit = DRAMTransfers.dense_transfer(dram.toTile(ranges), this, isLoad = true)
  @api def load(dram: DRAMDenseTile5[T]): MUnit = DRAMTransfers.dense_transfer(dram, this, isLoad = true)
}
object SRAM5 {
  implicit def sram5Type[T:Type:Bits]: Type[SRAM5[T]] = SRAM5Type(typ[T])
  implicit def sram5IsMemory[T:Type:Bits]: Mem[T,SRAM5] = new SRAMIsMemory[T,SRAM5]
}
