package spatial.lang

import argon.core._
import forge._
import spatial.nodes._
import spatial.utils._
import spatial.DimensionMismatchError
import spatial.metadata._

trait SRAM[T] { this: Template[_] =>
  def s: Exp[SRAM[T]]
  @internal protected def ofs = lift[Int, Index](0).s
  protected[spatial] var p: Option[Index] = None

  @internal def ranges: Seq[Range] = stagedDimsOf(s).map{d => Range.alloc(None, wrap(d),None,None)}

  /** Returns a Scala List of the dimensions of this DRAM **/
  @api def dims: List[Index] = wrap(stagedDimsOf(s)).toList
}
object SRAM {
  /** Allocates a 1-dimensional SRAM with the specified `length`. **/
  @api def apply[T:Type:Bits](length: Index): SRAM1[T] = SRAM1(SRAM.alloc[T,SRAM1](length.s))
  /** Allocates a 2-dimensional SRAM with the specified number of `rows` and `cols`. **/
  @api def apply[T:Type:Bits](rows: Index, cols: Index): SRAM2[T] = SRAM2(SRAM.alloc[T,SRAM2](rows.s,cols.s))
  /** Allocates a 3-dimensional SRAM with the specified dimensions. **/
  @api def apply[T:Type:Bits](p: Index, r: Index, c: Index): SRAM3[T] = SRAM3(SRAM.alloc[T,SRAM3](p.s,r.s,c.s))
  /** Allocates a 4-dimensional SRAM with the specified dimensions. **/
  @api def apply[T:Type:Bits](q: Index, p: Index, r: Index, c: Index): SRAM4[T] = SRAM4(SRAM.alloc[T,SRAM4](q.s,p.s,r.s,c.s))
  /** Allocates a 5-dimensional SRAM with the specified dimensions. **/
  @api def apply[T:Type:Bits](m: Index, q: Index, p: Index, r: Index, c: Index): SRAM5[T] = SRAM5(SRAM.alloc[T,SRAM5](m.s,q.s,p.s,r.s,c.s))

  @api def buffer[T:Type:Bits](c: Index): SRAM1[T] = SRAM1(SRAM.bufferable[T,SRAM1](c.s))
  @api def buffer[T:Type:Bits](r: Index, c: Index): SRAM2[T] = SRAM2(SRAM.bufferable[T,SRAM2](r.s,c.s))
  @api def buffer[T:Type:Bits](p: Index, r: Index, c: Index): SRAM3[T] = SRAM3(SRAM.bufferable[T,SRAM3](p.s,r.s,c.s))
  @api def buffer[T:Type:Bits](q: Index, p: Index, r: Index, c: Index): SRAM4[T] = SRAM4(SRAM.bufferable[T,SRAM4](q.s,p.s,r.s,c.s))
  @api def buffer[T:Type:Bits](m: Index, q: Index, p: Index, r: Index, c: Index): SRAM5[T] = SRAM5(SRAM.bufferable[T,SRAM5](m.s,q.s,p.s,r.s,c.s))

  /** Constructors **/
  @internal def bufferable[T:Type:Bits,C[_]<:SRAM[_]](dims: Exp[Index]*)(implicit mC: Type[C[T]]): Exp[C[T]] = {
    val sram = alloc[T,C](dims:_*)
    isExtraBufferable.enableOn(sram)
    sram
  }

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
  /** Returns the total size of this SRAM1. **/
  @api def length: Index = wrap(stagedDimsOf(s).head)
  /** Returns the total size of this SRAM1. **/
  @api def size: Index = wrap(stagedDimsOf(s).head)

  /**
    * Annotates that addresses in this SRAM1 can be read in parallel by factor `p`.
    *
    * Used when creating references to sparse regions of DRAM.
    */
  @api def par(p: Index): SRAM1[T] = { val x = SRAM1(s); x.p = Some(p); x }

  /** Returns the value in this SRAM1 at the given address `a`. **/
  @api def apply(a: Index): T = wrap(SRAM.load(this.s, stagedDimsOf(s), Seq(a.s), ofs, Bit.const(true)))
  /** Updates the value in this SRAM1 at the given address `a` to `data`. **/
  @api def update(a: Index, data: T): MUnit = MUnit(SRAM.store(this.s, stagedDimsOf(s), Seq(a.s), ofs, data.s, Bit.const(true)))

  /**
    * Create a sparse load from the given sparse region of DRAM to this on-chip memory.
    *
    * Elements will be gathered and stored contiguously in this memory.
    */
  @api def gather(dram: DRAMSparseTile[T]): MUnit = DRAMTransfers.sparse_transfer(dram, this, isLoad = true)

  /** Create a dense, burst load from the given region of DRAM to this on-chip memory. **/
  @api def load(dram: DRAM1[T]): MUnit = DRAMTransfers.dense_transfer(dram.toTile(ranges), this, isLoad = true)
  /** Create a dense, burst load from the given region of DRAM to this on-chip memory. **/
  @api def load(dram: DRAMDenseTile1[T]): MUnit = DRAMTransfers.dense_transfer(dram, this, isLoad = true)

  @api def gather[A[_]](dram: DRAMSparseTileMem[T,A]): MUnit = DRAMTransfers.sparse_transfer_mem(dram, this, isLoad = true)

  @api def loadAligned(dram: DRAM1[T]): MUnit = DRAMTransfers.dense_transfer(dram.toTile(ranges), this, isLoad = true, isAlign = true)
  @api def loadAligned(dram: DRAMDenseTile1[T]): MUnit = DRAMTransfers.dense_transfer(dram, this, isLoad = true, isAlign = true)
}
object SRAM1 {
  implicit def sram1Type[T:Type:Bits]: Type[SRAM1[T]] = SRAM1Type(typ[T])
  implicit def sram1IsMemory[T:Type:Bits]: Mem[T,SRAM1] = new SRAMIsMemory[T,SRAM1]
}

case class SRAM2[T:Type:Bits](s: Exp[SRAM2[T]]) extends Template[SRAM2[T]] with SRAM[T] {
  /** Returns the number of rows in this SRAM2. **/
  @api def rows: Index = wrap(stagedDimsOf(s).head)
  /** Returns the number of columns in this SRAM2. **/
  @api def cols: Index = wrap(stagedDimsOf(s).apply(1))
  /** Returns the total size of this SRAM2. **/
  @api def size: Index = rows * cols

  /** Returns the value in this SRAM2 at the given `row` and `col`. **/
  @api def apply(row: Index, col: Index): T = wrap(SRAM.load(this.s, stagedDimsOf(s), Seq(row.s,col.s), ofs, Bit.const(true)))
  /** Updates the value in this SRAM2 at the given `row` and `col` to `data`. **/
  @api def update(row: Index, col: Index, data: T): MUnit = MUnit(SRAM.store(this.s, stagedDimsOf(s), Seq(row.s,col.s), ofs, data.s, Bit.const(true)))
  /**
    * Annotates that addresses in this SRAM2 can be read in parallel by factor `p`.
    *
    * Used when creating references to sparse regions of DRAM.
    */
  @api def par(p: Index): SRAM2[T] = { val x = SRAM2(s); x.p = Some(p); x }

  /** Create a dense, burst load from the given region of DRAM to this on-chip memory. **/
  @api def load(dram: DRAM2[T]): MUnit = DRAMTransfers.dense_transfer(dram.toTile(ranges), this, isLoad = true)
  /** Create a dense, burst load from the given region of DRAM to this on-chip memory. **/
  @api def load(dram: DRAMDenseTile2[T]): MUnit = DRAMTransfers.dense_transfer(dram, this, isLoad = true)

  @api def loadAligned(dram: DRAM2[T]): MUnit = DRAMTransfers.dense_transfer(dram.toTile(ranges), this, isLoad = true, isAlign = true)
  @api def loadAligned(dram: DRAMDenseTile2[T]): MUnit = DRAMTransfers.dense_transfer(dram, this, isLoad = true, isAlign = true)


}
object SRAM2 {
  implicit def sram2Type[T:Type:Bits]: Type[SRAM2[T]] = SRAM2Type(typ[T])
  implicit def sram2IsMemory[T:Type:Bits]: Mem[T,SRAM2] = new SRAMIsMemory[T,SRAM2]
}

case class SRAM3[T:Type:Bits](s: Exp[SRAM3[T]]) extends Template[SRAM3[T]] with SRAM[T] {
  /** Returns the first dimension of this SRAM3. **/
  @api def dim0: Index = wrap(stagedDimsOf(s).apply(0))
  /** Returns the second dimension of this SRAM3. **/
  @api def dim1: Index = wrap(stagedDimsOf(s).apply(1))
  /** Returns the third dimension of this SRAM3. **/
  @api def dim2: Index = wrap(stagedDimsOf(s).apply(2))
  /** Returns the total size of this SRAM3. **/
  @api def size: Index = dim0 * dim1 * dim2

  /** Returns the value in this SRAM3 at the given 3-dimensional address `a`, `b`, `c`. **/
  @api def apply(a: Index, b: Index, c: Index): T = wrap(SRAM.load(this.s, stagedDimsOf(s), Seq(a.s,b.s,c.s), ofs, Bit.const(true)))
  /** Updates the value in this SRAM3 at the given 3-dimensional address to `data`. **/
  @api def update(a: Index, b: Index, c: Index, data: T): MUnit = MUnit(SRAM.store(this.s, stagedDimsOf(s), Seq(a.s,b.s,c.s), ofs, data.s, Bit.const(true)))
  /**
    * Annotates that addresses in this SRAM2 can be read in parallel by factor `p`.
    *
    * Used when creating references to sparse regions of DRAM.
    */
  @api def par(p: Index): SRAM3[T] = { val x = SRAM3(s); x.p = Some(p); x }

  /** Create a dense, burst load from the given region of DRAM to this on-chip memory. **/
  @api def load(dram: DRAM3[T]): MUnit = DRAMTransfers.dense_transfer(dram.toTile(ranges), this, isLoad = true)
  /** Create a dense, burst load from the given region of DRAM to this on-chip memory. **/
  @api def load(dram: DRAMDenseTile3[T]): MUnit = DRAMTransfers.dense_transfer(dram, this, isLoad = true)
}
object SRAM3 {
  implicit def sram3Type[T:Type:Bits]: Type[SRAM3[T]] = SRAM3Type(typ[T])
  implicit def sram3IsMemory[T:Type:Bits]: Mem[T,SRAM3] = new SRAMIsMemory[T,SRAM3]
}

case class SRAM4[T:Type:Bits](s: Exp[SRAM4[T]]) extends Template[SRAM4[T]] with SRAM[T] {
  @api def par(p: Index): SRAM4[T] = { val x = SRAM4(s); x.p = Some(p); x }

  /** Returns the first dimension of this SRAM4. **/
  @api def dim0: Index = wrap(stagedDimsOf(s).apply(0))
  /** Returns the second dimension of this SRAM4. **/
  @api def dim1: Index = wrap(stagedDimsOf(s).apply(1))
  /** Returns the third dimension of this SRAM4. **/
  @api def dim2: Index = wrap(stagedDimsOf(s).apply(2))
  /** Returns the fourth dimension of this SRAM4. **/
  @api def dim3: Index = wrap(stagedDimsOf(s).apply(3))
  /** Returns the total size of this SRAM4. **/
  @api def size: Index = dim0 * dim1 * dim2 * dim3

  /** Returns the value in this SRAM4 at the 4-dimensional address `a`, `b`, `c`, `d`. **/
  @api def apply(a: Index, b: Index, c: Index, d: Index): T = wrap(SRAM.load(this.s, stagedDimsOf(s), Seq(a.s,b.s,c.s,d.s), ofs, Bit.const(true)))
  /** Updates the value in this SRAM4 at the 4-dimensional address to `data`. **/
  @api def update(a: Index, b: Index, c: Index, d: Index, data: T): MUnit = MUnit(SRAM.store(this.s, stagedDimsOf(s), Seq(a.s,b.s,c.s,d.s), ofs, data.s, Bit.const(true)))

  /** Create a dense, burst load from the given region of DRAM to this on-chip memory. **/
  @api def load(dram: DRAM4[T]): MUnit = DRAMTransfers.dense_transfer(dram.toTile(ranges), this, isLoad = true)
  /** Create a dense, burst load from the given region of DRAM to this on-chip memory. **/
  @api def load(dram: DRAMDenseTile4[T]): MUnit = DRAMTransfers.dense_transfer(dram, this, isLoad = true)
}
object SRAM4 {
  implicit def sram4Type[T:Type:Bits]: Type[SRAM4[T]] = SRAM4Type(typ[T])
  implicit def sram4IsMemory[T:Type:Bits]: Mem[T,SRAM4] = new SRAMIsMemory[T,SRAM4]
}

case class SRAM5[T:Type:Bits](s: Exp[SRAM5[T]]) extends Template[SRAM5[T]] with SRAM[T] {
  @api def par(p: Index): SRAM5[T] = { val x = SRAM5(s); x.p = Some(p); x }

  /** Returns the first dimension of this SRAM5. **/
  @api def dim0: Index = wrap(stagedDimsOf(s).apply(0))
  /** Returns the second dimension of this SRAM5. **/
  @api def dim1: Index = wrap(stagedDimsOf(s).apply(1))
  /** Returns the third dimension of this SRAM5. **/
  @api def dim2: Index = wrap(stagedDimsOf(s).apply(2))
  /** Returns the fourth dimension of this SRAM5. **/
  @api def dim3: Index = wrap(stagedDimsOf(s).apply(3))
  /** Returns the fifth dimension of this SRAM5. **/
  @api def dim4: Index = wrap(stagedDimsOf(s).apply(4))
  /** Returns the total size of this SRAM5. **/
  @api def size: Index = dim0 * dim1 * dim2 * dim3 * dim4

  /** Returns the value in this SRAM5 at the 5-dimensional address `a`, `b`, `c`, `d`, `e`. **/
  @api def apply(a: Index, b: Index, c: Index, d: Index, e: Index): T = wrap(SRAM.load(this.s, stagedDimsOf(s), Seq(a.s,b.s,c.s,d.s,e.s), ofs, Bit.const(true)))
  /** Updates the value in this SRAM5 at the 5-dimensional address to `data`. **/
  @api def update(a: Index, b: Index, c: Index, d: Index, e: Index, data: T): MUnit = MUnit(SRAM.store(this.s, stagedDimsOf(s), Seq(a.s,b.s,c.s,d.s,e.s), ofs, data.s, Bit.const(true)))

  /** Create a dense, burst load from the given region of DRAM to this on-chip memory. **/
  @api def load(dram: DRAM5[T]): MUnit = DRAMTransfers.dense_transfer(dram.toTile(ranges), this, isLoad = true)
  /** Create a dense, burst load from the given region of DRAM to this on-chip memory. **/
  @api def load(dram: DRAMDenseTile5[T]): MUnit = DRAMTransfers.dense_transfer(dram, this, isLoad = true)
}
object SRAM5 {
  implicit def sram5Type[T:Type:Bits]: Type[SRAM5[T]] = SRAM5Type(typ[T])
  implicit def sram5IsMemory[T:Type:Bits]: Mem[T,SRAM5] = new SRAMIsMemory[T,SRAM5]
}
