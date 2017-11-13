package spatial.lang

import argon.core._
import forge._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

trait RegFile[T] { this: Template[_] =>
  def s: Exp[RegFile[T]]

  @internal def ranges: Seq[Range] = stagedDimsOf(s).map{d => Range.alloc(None, wrap(d), None, None)}
}
object RegFile {
  /** Allocates a 1-dimensional Regfile with specified `length`. **/
  @api def apply[T:Type:Bits](length: Index): RegFile1[T] = wrap(alloc[T,RegFile1](None, length.s))

  /**
    * Allocates a 1-dimensional RegFile with specified `length` and initial values `inits`.
    *
    * The number of initial values must be the same as the total size of the RegFile.
    */
  @api def apply[T:Type:Bits](length: Int, inits: List[T]): RegFile1[T] = {
    checkDims(Seq(length), inits)
    wrap(alloc[T,RegFile1](Some(unwrap(inits)),FixPt.int32s(length)))
  }
  /** Allocates a 2-dimensional RegFile with specified `rows` and `cols`. **/
  @api def apply[T:Type:Bits](rows: Index, cols: Index): RegFile2[T] = wrap(alloc[T,RegFile2](None,rows.s,cols.s))

  /**
    * Allocates a 2-dimensional RegFile with specified `rows` and `cols` and initial values `inits`.
    *
    * The number of initial values must be the same as the total size of the RegFile
    */
  @api def apply[T:Type:Bits](rows: Int, cols: Int, inits: List[T]): RegFile2[T] = {
    checkDims(Seq(cols, rows), inits)
    wrap(alloc[T,RegFile2](Some(unwrap(inits)),FixPt.int32s(rows),FixPt.int32s(cols)))
  }
  /** Allocates a 3-dimensional RegFile with specified dimensions. **/
  @api def apply[T:Type:Bits](dim0: Index, dim1: Index, dim2: Index): RegFile3[T] = wrap(alloc[T,RegFile3](None,dim0.s, dim1.s, dim2.s))
  /**
    * Allocates a 3-dimensional RegFile with specified dimensions and initial values `inits`.
    *
    * The number of initial values must be the same as the total size of the RegFile
    */
  @api def apply[T:Type:Bits](dim0: Int, dim1: Int, dim2: Int, inits: List[T]): RegFile3[T] = {
    checkDims(Seq(dim0,dim1,dim2), inits)
    wrap(alloc[T,RegFile3](Some(unwrap(inits)),FixPt.int32s(dim0), FixPt.int32s(dim1), FixPt.int32s(dim2)))
  }

  @internal def checkDims(dims: Seq[Int], elems: Seq[_]) = {
    if ((dims.product) != elems.length) {
      error(ctx, c"Specified dimensions of the RegFile do not match the number of supplied elements (${dims.product} != ${elems.length})")
      error(ctx)
    }
  }

  @api def buffer[T:Type:Bits](cols: Index): RegFile1[T] = {
    val rf = alloc[T,RegFile1](None, cols.s)
    isExtraBufferable.enableOn(rf)
    wrap(rf)
  }
  @api def buffer[T:Type:Bits](rows: Index, cols: Index): RegFile2[T] = {
    val rf = alloc[T,RegFile2](None, rows.s,cols.s)
    isExtraBufferable.enableOn(rf)
    wrap(rf)
  }
  @api def buffer[T:Type:Bits](dim0: Index, dim1: Index, dim2: Index): RegFile3[T] = {
    val rf = alloc[T,RegFile3](None, dim0.s, dim1.s, dim2.s)
    isExtraBufferable.enableOn(rf)
    wrap(rf)
  }


  /** Constructors **/
  @internal def alloc[T:Type:Bits,C[_]<:RegFile[_]](inits: Option[Seq[Exp[T]]], dims: Exp[Index]*)(implicit cT: Type[C[T]]) = {
    stageMutable(RegFileNew[T,C](dims, inits))(ctx)
  }

  @internal def load[T:Type:Bits](reg: Exp[RegFile[T]], inds: Seq[Exp[Index]], en: Exp[Bit]) = {
    stage(RegFileLoad(reg, inds, en))(ctx)
  }

  @internal def store[T:Type:Bits](reg: Exp[RegFile[T]], inds: Seq[Exp[Index]], data: Exp[T], en: Exp[Bit]) = {
    stageWrite(reg)(RegFileStore(reg, inds, data, en))(ctx)
  }

  @internal def reset[T:Type:Bits](rf: Exp[RegFile[T]], en: Exp[Bit]): Sym[MUnit] = {
    stageWrite(rf)( RegFileReset(rf, en) )(ctx)
  }

  @internal def shift_in[T:Type:Bits](
    reg:  Exp[RegFile[T]],
    inds: Seq[Exp[Index]],
    dim:  Int,
    data: Exp[T],
    en:   Exp[Bit]
  ) = {
    stageWrite(reg)(RegFileShiftIn(reg, inds, dim, data, en))(ctx)
  }

  @internal def par_shift_in[T:Type:Bits](
    reg:  Exp[RegFile[T]],
    inds: Seq[Exp[Index]],
    dim:  Int,
    data: Exp[Vector[T]],
    en:   Exp[Bit]
  ) = {
    stageWrite(reg)(ParRegFileShiftIn(reg, inds, dim, data, en))(ctx)
  }

  @internal def par_load[T:Type:Bits](
    reg:  Exp[RegFile[T]],
    inds: Seq[Seq[Exp[Index]]],
    ens:  Seq[Exp[Bit]]
  ) = {
    implicit val vT = VectorN.typeFromLen[T](ens.length)
    stage(ParRegFileLoad(reg, inds, ens))(ctx)
  }

  @internal def par_store[T:Type:Bits](
    reg:  Exp[RegFile[T]],
    inds: Seq[Seq[Exp[Index]]],
    data: Seq[Exp[T]],
    ens:  Seq[Exp[Bit]]
  ) = {
    stageWrite(reg)(ParRegFileStore(reg, inds, data, ens))(ctx)
  }
}

case class RegFile1[T:Type:Bits](s: Exp[RegFile1[T]]) extends Template[RegFile1[T]] with RegFile[T] {
  @api def length: Index = wrap(stagedDimsOf(s).apply(0))
  @api def size: Index = wrap(stagedDimsOf(s).apply(0))
  @api def dim0: Index = wrap(stagedDimsOf(s).apply(0))

  /** Returns the value held by the register at address `i`. **/
  @api def apply(i: Index): T = wrap(RegFile.load(s, Seq(i.s), Bit.const(true)))
  /** Updates the register at address `i` to hold `data`. **/
  @api def update(i: Index, data: T): MUnit = MUnit(RegFile.store(s, Seq(i.s), data.s, Bit.const(true)))

  /** Shifts in `data` into the first register, shifting all other values over by one position. **/
  @api def <<=(data: T): MUnit = wrap(RegFile.shift_in(s, Seq(int32s(0)), 0, data.s, Bit.const(true)))

  /**
    * Shifts in `data` into the first N registers, where N is the size of the given Vector.
    * All other elements are shifted by N positions.
    */
  @api def <<=(data: Vector[T]): MUnit = wrap(RegFile.par_shift_in(s, Seq(int32s(0)), 0, data.s, Bit.const(true)))

  /** Creates a dense, burst load from the specified region of DRAM to this on-chip memory. **/
  @api def load(dram: DRAM1[T]): MUnit = DRAMTransfers.dense_transfer(dram.toTile(ranges), this, isLoad = true)
  /** Creates a dense, burst load from the specified region of DRAM to this on-chip memory. **/
  @api def load(dram: DRAMDenseTile1[T]): MUnit = DRAMTransfers.dense_transfer(dram, this, isLoad = true)

  /** Resets this RegFile to its initial values (or zeros, if unspecified). **/
  @api def reset: MUnit = wrap(RegFile.reset(this.s, Bit.const(true)))
  /** Conditionally resets this RegFile based on `cond` to its inital values (or zeros if unspecified). **/
  @api def reset(cond: Bit): MUnit = wrap(RegFile.reset(this.s, cond.s))
}
object RegFile1 {
  implicit def regFile1Type[T:Type:Bits]: Type[RegFile1[T]] = RegFile1Type(typ[T])
  implicit def regfile1IsMemory[T:Type:Bits]: Mem[T,RegFile1] = new RegFileIsMemory[T,RegFile1]
}

case class RegFile2[T:Type:Bits](s: Exp[RegFile2[T]]) extends Template[RegFile2[T]] with RegFile[T] {
  @api def rows: Index = wrap(stagedDimsOf(s).apply(0))
  @api def cols: Index = wrap(stagedDimsOf(s).apply(1))
  @api def dim0: Index = wrap(stagedDimsOf(s).apply(0))
  @api def dim1: Index = wrap(stagedDimsOf(s).apply(1))
  /** Returns the value held by the register at row `r`, column `c`. **/
  @api def apply(r: Index, c: Index): T = wrap(RegFile.load(s, Seq(r.s, c.s), Bit.const(true)))
  /** Updates the register at row `r`, column `c` to hold the given `data`. **/
  @api def update(r: Index, c: Index, data: T): MUnit = MUnit(RegFile.store(s, Seq(r.s, c.s), data.s, Bit.const(true)))

  /** Returns a view of row `i` of this RegFile. **/
  @api def apply(i: Index, y: Wildcard) = RegFileView(s, Seq(i,lift[Int,Index](0)), 1)
  /** Returns a view of column `i` of this RegFile. **/
  @api def apply(y: Wildcard, i: Index) = RegFileView(s, Seq(lift[Int,Index](0),i), 0)

  /** Creates a dense, burst load from the specified region of DRAM to this on-chip memory. **/
  @api def load(dram: DRAM2[T]): MUnit = DRAMTransfers.dense_transfer(dram.toTile(ranges), this, isLoad = true)
  /** Creates a dense, burst load from the specified region of DRAM to this on-chip memory. **/
  @api def load(dram: DRAMDenseTile2[T]): MUnit = DRAMTransfers.dense_transfer(dram, this, isLoad = true)

  /** Resets this RegFile to its initial values (or zeros, if unspecified). **/
  @api def reset: MUnit = wrap(RegFile.reset(this.s, Bit.const(true)))
  /** Conditionally resets this RegFile based on `cond` to its inital values (or zeros if unspecified). **/
  @api def reset(cond: Bit): MUnit = wrap(RegFile.reset(this.s, cond.s))
}
object RegFile2 {
  implicit def regFile2Type[T:Type:Bits]: Type[RegFile2[T]] = RegFile2Type(typ[T])
  implicit def regfile2IsMemory[T:Type:Bits]: Mem[T,RegFile2] = new RegFileIsMemory[T,RegFile2]
}

case class RegFile3[T:Type:Bits](s: Exp[RegFile3[T]]) extends Template[RegFile3[T]] with RegFile[T] {
  @api def dim0: Index = wrap(stagedDimsOf(s).apply(0))
  @api def dim1: Index = wrap(stagedDimsOf(s).apply(1))
  @api def dim2: Index = wrap(stagedDimsOf(s).apply(2))

  /** Returns the value held by the register at the given 3-dimensional address. **/
  @api def apply(dim0: Index, dim1: Index, dim2: Index): T = wrap(RegFile.load(s, Seq(dim0.s, dim1.s, dim2.s), Bit.const(true)))
  /** Updates the register at the given 3-dimensional address to hold the given `data`. **/
  @api def update(dim0: Index, dim1: Index, dim2: Index, data: T): MUnit = MUnit(RegFile.store(s, Seq(dim0.s, dim1.s, dim2.s), data.s, Bit.const(true)))

  /** Returns a 1-dimensional view of part of this RegFile3. **/
  @api def apply(i: Index, j: Index, y: Wildcard) = RegFileView(s, Seq(i,j,lift[Int,Index](0)), 2)
  /** Returns a 1-dimensional view of part of this RegFile3. **/
  @api def apply(i: Index, y: Wildcard, j: Index) = RegFileView(s, Seq(i,lift[Int,Index](0),j), 1)
  /** Returns a 1-dimensional view of part of this RegFile3. **/
  @api def apply(y: Wildcard, i: Index, j: Index) = RegFileView(s, Seq(lift[Int,Index](0),i,j), 0)

  /** Creates a dense, burst load from the specified region of DRAM to this on-chip memory. **/
  @api def load(dram: DRAM3[T]): MUnit = DRAMTransfers.dense_transfer(dram.toTile(ranges), this, isLoad = true)
  /** Creates a dense, burst load from the specified region of DRAM to this on-chip memory. **/
  @api def load(dram: DRAMDenseTile3[T]): MUnit = DRAMTransfers.dense_transfer(dram, this, isLoad = true)

  /** Resets this RegFile to its initial values (or zeros, if unspecified). **/
  @api def reset: MUnit = wrap(RegFile.reset(this.s, Bit.const(true)))
  /** Conditionally resets this RegFile based on `cond` to its inital values (or zeros if unspecified). **/
  @api def reset(cond: Bit): MUnit = wrap(RegFile.reset(this.s, cond.s))
}
object RegFile3 {
  implicit def regFile3Type[T:Type:Bits]: Type[RegFile3[T]] = RegFile3Type(typ[T])
  implicit def regfile3IsMemory[T:Type:Bits]: Mem[T,RegFile3] = new RegFileIsMemory[T,RegFile3]
}

case class RegFileView[T:Type:Bits](s: Exp[RegFile[T]], i: Seq[Index], dim: Int) {
  @api def <<=(data: T): MUnit = wrap(RegFile.shift_in(s, unwrap(i), dim, data.s, Bit.const(true)))
  @api def <<=(data: Vector[T]): MUnit = wrap(RegFile.par_shift_in(s, unwrap(i), dim, data.s, Bit.const(true)))
}

