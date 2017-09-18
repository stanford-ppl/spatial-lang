package spatial.lang

import argon.core._
import forge._
import spatial.nodes._
import spatial.utils._

trait DRAM[T] { this: Template[_] =>
  def s: Exp[DRAM[T]]

  /** Returns the 64-bit physical address in main memory of the start of this DRAM **/
  @api def address: Int64
  /** Returns a Scala List of the dimensions of this DRAM **/
  @api def dims: List[Index] = wrap(stagedDimsOf(s)).toList
}
object DRAM {
  /**
    * Creates a reference to a 1-dimensional array in main memory with the given length.
    *
    * Dimensions of a DRAM should be statically calculable functions of constants, parameters, and ArgIns.
    */
  @api def apply[T:Type:Bits](length: Index): DRAM1[T] = DRAM1(alloc[T,DRAM1](length.s))
  /**
    * Creates a reference to a 2-dimensional array in main memory with given rows and cols.
    *
    * Dimensions of a DRAM should be statically calculable functions of constants, parameters, and ArgIns.
    */
  @api def apply[T:Type:Bits](d1: Index, d2: Index): DRAM2[T] = DRAM2(alloc[T,DRAM2](d1.s,d2.s))
  /**
    * Creates a reference to a 3-dimensional array in main memory with given dimensions.
    *
    * Dimensions of a DRAM should be statically calculable functions of constants, parameters, and ArgIns.
    */
  @api def apply[T:Type:Bits](d1: Index, d2: Index, d3: Index): DRAM3[T] = DRAM3(alloc[T,DRAM3](d1.s,d2.s,d3.s))
  /**
    * Creates a reference to a 4-dimensional array in main memory with given dimensions.
    *
    * Dimensions of a DRAM should be statically calculable functions of constants, parameters, and ArgIns.
    */
  @api def apply[T:Type:Bits](d1: Index, d2: Index, d3: Index, d4: Index): DRAM4[T] = DRAM4(alloc[T,DRAM4](d1.s,d2.s,d3.s,d4.s))
  /**
    * Creates a reference to a 5-dimensional array in main memory with given dimensions.
    *
    * Dimensions of a DRAM should be statically calculable functions of constants, parameters, and ArgIns.
    */
  @api def apply[T:Type:Bits](d1: Index, d2: Index, d3: Index, d4: Index, d5: Index): DRAM5[T] = DRAM5(alloc[T,DRAM5](d1.s,d2.s,d3.s,d4.s,d5.s))

  /** Constructors **/
  @internal def alloc[T:Type:Bits,C[_]<:DRAM[_]](dims: Exp[Index]*)(implicit cT: Type[C[T]]): Exp[C[T]] = {
    stageMutable( DRAMNew[T,C](dims, implicitly[Bits[T]].zero.s) )(ctx)
  }
  @internal def addr[T:Type:Bits](dram: Exp[DRAM[T]]): Exp[Int64] = {
    stage( GetDRAMAddress(dram) )(ctx)
  }
}

case class DRAM1[T:Type:Bits](s: Exp[DRAM1[T]]) extends Template[DRAM1[T]] with DRAM[T] {
  /** Returns the 64-bit physical address in main memory of the start of this DRAM **/
  @api def address: Int64 = wrap(DRAM.addr(this.s))
  @api def toTile(ranges: Seq[Range]): DRAMDenseTile1[T] = DRAMDenseTile1(s, ranges)

  /** Returns the total number of elements in this DRAM1. **/
  @api def size: Index = wrap(stagedDimsOf(s).head)
  /** Returns the total number of elements in this DRAM1. **/
  @api def length: Index = wrap(stagedDimsOf(s).head)

  /** Creates a reference to a dense region of this DRAM1 for creating burst loads and stores. **/
  @api def apply(range: Range): DRAMDenseTile1[T] = DRAMDenseTile1(this.s, Seq(range))

  /**
    * Creates a reference to a sparse region of this DRAM1 for use in scatter and gather transfers
    * using all addresses in `addrs`.
    **/
  @api def apply(addrs: SRAM1[Index]): DRAMSparseTile[T] = this.apply(addrs, wrap(stagedDimsOf(addrs.s).head))
  /**
    * Creates a reference to a sparse region of this DRAM1 for use in scatter and gather transfers
    * using the first `size` addresses in `addrs`.
    */
  @api def apply(addrs: SRAM1[Index], size: Index): DRAMSparseTile[T] = DRAMSparseTile(this.s, addrs, size)

  // TODO: Should this be sizeOf(addrs) or addrs.numel?
  /**
    * Creates a reference to a sparse region of this DRAM1 for use in scatter and gather transfers
    * using all addresses in `addrs`.
    **/
  @api def apply(addrs: FIFO[Index]): DRAMSparseTileMem[T,FIFO] = this.apply(addrs, addrs.numel())
  /**
    * Creates a reference to a sparse region of this DRAM1 for use in scatter and gather transfers
    * using the first `size` addresses in `addrs`.
    */
  @api def apply(addrs: FIFO[Index], size: Index): DRAMSparseTileMem[T,FIFO] = DRAMSparseTileMem(this.s, addrs, size)
  /**
    * Creates a reference to a sparse region of this DRAM1 for use in scatter and gather transfers
    * using all addresses in `addrs`.
    **/
  @api def apply(addrs: FILO[Index]): DRAMSparseTileMem[T,FILO] = this.apply(addrs, addrs.numel())
  /**
    * Creates a reference to a sparse region of this DRAM1 for use in scatter and gather transfers
    * using the first `size` addresses in `addrs`.
    */
  @api def apply(addrs: FILO[Index], size: Index): DRAMSparseTileMem[T,FILO] = DRAMSparseTileMem(this.s, addrs, size)

  /** Creates a dense, burst transfer from the given on-chip `data` to this DRAM's region of main memory. **/
  @api def store(data: SRAM1[T]): MUnit = DRAMTransfers.dense_transfer(this.toTile(data.ranges), data, isLoad = false)
  /** Creates a dense, burst transfer from the given on-chip `data` to this DRAM's region of main memory. **/
  @api def store(data: FIFO[T]): MUnit = DRAMTransfers.dense_transfer(this.toTile(data.ranges), data, isLoad = false)
  /** Creates a dense, burst transfer from the given on-chip `data` to this DRAM's region of main memory. **/
  @api def store(data: FILO[T]): MUnit = DRAMTransfers.dense_transfer(this.toTile(data.ranges), data, isLoad = false)
  /** Creates a dense, burst transfer from the given on-chip `data` to this DRAM's region of main memory. **/
  @api def store(data: RegFile1[T]): MUnit = DRAMTransfers.dense_transfer(this.toTile(data.ranges), data, isLoad = false)

  @api def storeAligned(sram: SRAM1[T]): MUnit = DRAMTransfers.dense_transfer(this.toTile(sram.ranges), sram, isLoad = false, isAlign = true)
  @api def storeAligned(fifo: FIFO[T]): MUnit = DRAMTransfers.dense_transfer(this.toTile(fifo.ranges), fifo, isLoad = false, isAlign = true)
  @api def storeAligned(filo: FILO[T]): MUnit = DRAMTransfers.dense_transfer(this.toTile(filo.ranges), filo, isLoad = false, isAlign = true)
  @api def storeAligned(regs: RegFile1[T]): MUnit = DRAMTransfers.dense_transfer(this.toTile(regs.ranges), regs, isLoad = false, isAlign = true)

}
object DRAM1 {
  implicit def dram1Type[T:Type:Bits]: Type[DRAM1[T]] = DRAM1Type(typ[T])
}

case class DRAM2[T:Type:Bits](s: Exp[DRAM2[T]]) extends Template[DRAM2[T]] with DRAM[T] {
  @api def address: Int64 = wrap(DRAM.addr(this.s))
  @api def toTile(ranges: Seq[Range]): DRAMDenseTile2[T] = DRAMDenseTile2(this.s, ranges)

  /** Returns the number of rows in this DRAM2 **/
  @api def rows: Index = wrap(stagedDimsOf(s).apply(0))
  /** Returns the number of columns in this DRAM2 **/
  @api def cols: Index = wrap(stagedDimsOf(s).apply(1))
  /** Returns the total number of elements in this DRAM2 **/
  @api def size: Index = rows * cols

  /** Creates a reference to a dense slice of a row of this DRAM2 for creating burst loads and stores. **/
  @api def apply(row: Index, cols: Range) = DRAMDenseTile1(this.s, Seq(row.toRange, cols))
  /** Creates a reference to a dense slice of a column of this DRAM2 for creating burst loads and stores. **/
  @api def apply(rows: Range, col: Index) = DRAMDenseTile1(this.s, Seq(rows, col.toRange))
  /** Creates a reference to a 2-dimensional, dense region of this DRAM2 for creating burst loads and stores. **/
  @api def apply(rows: Range, cols: Range) = DRAMDenseTile2(this.s, Seq(rows, cols))

  /** Creates a dense, burst transfer from the given on-chip `data` to this DRAM's region of main memory. **/
  @api def store(sram: SRAM2[T]): MUnit = DRAMTransfers.dense_transfer(this.toTile(sram.ranges), sram, isLoad = false)
  /** Creates a dense, burst transfer from the given on-chip `data` to this DRAM's region of main memory. **/
  @api def store(regs: RegFile2[T]): MUnit = DRAMTransfers.dense_transfer(this.toTile(regs.ranges), regs, isLoad = false)

  @api def storeAligned(sram: SRAM2[T]): MUnit = DRAMTransfers.dense_transfer(this.toTile(sram.ranges), sram, isLoad = false, isAlign = true)
  @api def storeAligned(regs: RegFile2[T]): MUnit = DRAMTransfers.dense_transfer(this.toTile(regs.ranges), regs, isLoad = false, isAlign = true)
}
object DRAM2 {
  implicit def dram2Type[T:Type:Bits]: Type[DRAM2[T]] = DRAM2Type(typ[T])
}

case class DRAM3[T:Type:Bits](s: Exp[DRAM3[T]]) extends Template[DRAM3[T]] with DRAM[T] {
  @api def address: Int64 = wrap(DRAM.addr(this.s))
  @api def toTile(ranges: Seq[Range]): DRAMDenseTile3[T] = DRAMDenseTile3(this.s, ranges)

  /** Returns the first dimension for this DRAM3. **/
  @api def dim0: Index = wrap(stagedDimsOf(s).apply(0))
  /** Returns the second dimension for this DRAM3. **/
  @api def dim1: Index = wrap(stagedDimsOf(s).apply(1))
  /** Returns the third dimension for this DRAM3. **/
  @api def dim2: Index = wrap(stagedDimsOf(s).apply(2))
  /** Returns the total number of elements in this DRAM3. **/
  @api def size: Index = dim0 * dim1 * dim2

  /** Creates a reference to a 1-dimensional, dense region of this DRAM3 for creating burst loads and stores. **/
  @api def apply(p: Index, r: Index, c: Range) = DRAMDenseTile1(this.s, Seq(p.toRange, r.toRange, c))
  /** Creates a reference to a 1-dimensional, dense region of this DRAM3 for creating burst loads and stores. **/
  @api def apply(p: Index, r: Range, c: Index) = DRAMDenseTile1(this.s, Seq(p.toRange, r, c.toRange))
  /** Creates a reference to a 1-dimensional, dense region of this DRAM3 for creating burst loads and stores. **/
  @api def apply(p: Range, r: Index, c: Index) = DRAMDenseTile1(this.s, Seq(p, r.toRange, c.toRange))
  /** Creates a reference to a 2-dimensional, dense region of this DRAM3 for creating burst loads and stores. **/
  @api def apply(p: Index, r: Range, c: Range) = DRAMDenseTile2(this.s, Seq(p.toRange, r, c))
  /** Creates a reference to a 2-dimensional, dense region of this DRAM3 for creating burst loads and stores. **/
  @api def apply(p: Range, r: Index, c: Range) = DRAMDenseTile2(this.s, Seq(p, r.toRange, c))
  /** Creates a reference to a 2-dimensional, dense region of this DRAM3 for creating burst loads and stores. **/
  @api def apply(p: Range, r: Range, c: Index) = DRAMDenseTile2(this.s, Seq(p, r, c.toRange))
  /** Creates a reference to a 3-dimensional, dense region of this DRAM3 for creating burst loads and stores. **/
  @api def apply(p: Range, r: Range, c: Range) = DRAMDenseTile3(this.s, Seq(p, r, c))

  /** Creates a dense, burst transfer from the given on-chip `data` to this DRAM's region of main memory. **/
  @api def store(sram: SRAM3[T]): MUnit = DRAMTransfers.dense_transfer(this.toTile(sram.ranges), sram, isLoad = false)
}
object DRAM3 {
  implicit def dram3Type[T:Type:Bits]: Type[DRAM3[T]] = DRAM3Type(typ[T])
}

case class DRAM4[T:Type:Bits](s: Exp[DRAM4[T]]) extends Template[DRAM4[T]] with DRAM[T] {
  @api def address: Int64 = wrap(DRAM.addr(this.s))
  @api def toTile(ranges: Seq[Range]): DRAMDenseTile4[T] = DRAMDenseTile4(this.s, ranges)

  /** Returns the first dimension of this DRAM4. **/
  @api def dim0: Index = wrap(stagedDimsOf(s).apply(0))
  /** Returns the second dimension of this DRAM4. **/
  @api def dim1: Index = wrap(stagedDimsOf(s).apply(1))
  /** Returns the third dimension of this DRAM4. **/
  @api def dim2: Index = wrap(stagedDimsOf(s).apply(2))
  /** Returns the fourth dimension of this DRAM4. **/
  @api def dim3: Index = wrap(stagedDimsOf(s).apply(3))
  /** Returns the total number of elements in this DRAM4. **/
  @api def size: Index = dim0 * dim1 * dim2 * dim3

  /** Creates a reference to a 1-dimensional, dense region of this DRAM4 for creating burst loads and stores. **/
  @api def apply(q: Index, p: Index, r: Index, c: Range) = DRAMDenseTile1(this.s, Seq(q.toRange, p.toRange, r.toRange, c))
  /** Creates a reference to a 1-dimensional, dense region of this DRAM4 for creating burst loads and stores. **/
  @api def apply(q: Index, p: Index, r: Range, c: Index) = DRAMDenseTile1(this.s, Seq(q.toRange, p.toRange, r, c.toRange))
  /** Creates a reference to a 1-dimensional, dense region of this DRAM4 for creating burst loads and stores. **/
  @api def apply(q: Index, p: Range, r: Index, c: Index) = DRAMDenseTile1(this.s, Seq(q.toRange, p, r.toRange, c.toRange))
  /** Creates a reference to a 1-dimensional, dense region of this DRAM4 for creating burst loads and stores. **/
  @api def apply(q: Range, p: Index, r: Index, c: Index) = DRAMDenseTile1(this.s, Seq(q, p.toRange, r.toRange, c.toRange))

  /** Creates a reference to a 2-dimensional, dense region of this DRAM4 for creating burst loads and stores. **/
  @api def apply(q: Index, p: Index, r: Range, c: Range) = DRAMDenseTile2(this.s, Seq(q.toRange, p.toRange, r, c))
  /** Creates a reference to a 2-dimensional, dense region of this DRAM4 for creating burst loads and stores. **/
  @api def apply(q: Range, p: Index, r: Index, c: Range) = DRAMDenseTile2(this.s, Seq(q, p.toRange, r.toRange, c))
  /** Creates a reference to a 2-dimensional, dense region of this DRAM4 for creating burst loads and stores. **/
  @api def apply(q: Range, p: Range, r: Index, c: Index) = DRAMDenseTile2(this.s, Seq(q, p, r.toRange, c.toRange))
  /** Creates a reference to a 2-dimensional, dense region of this DRAM4 for creating burst loads and stores. **/
  @api def apply(q: Index, p: Range, r: Index, c: Range) = DRAMDenseTile2(this.s, Seq(q.toRange, p, r.toRange, c))
  /** Creates a reference to a 2-dimensional, dense region of this DRAM4 for creating burst loads and stores. **/
  @api def apply(q: Range, p: Index, r: Range, c: Index) = DRAMDenseTile2(this.s, Seq(q, p.toRange, r, c.toRange))
  /** Creates a reference to a 2-dimensional, dense region of this DRAM4 for creating burst loads and stores. **/
  @api def apply(q: Index, p: Range, r: Range, c: Index) = DRAMDenseTile2(this.s, Seq(q.toRange, p, r, c.toRange))

  /** Creates a reference to a 3-dimensional, dense region of this DRAM4 for creating burst loads and stores. **/
  @api def apply(q: Index, p: Range, r: Range, c: Range) = DRAMDenseTile3(this.s, Seq(q.toRange, p, r, c))
  /** Creates a reference to a 3-dimensional, dense region of this DRAM4 for creating burst loads and stores. **/
  @api def apply(q: Range, p: Index, r: Range, c: Range) = DRAMDenseTile3(this.s, Seq(q, p.toRange, r, c))
  /** Creates a reference to a 3-dimensional, dense region of this DRAM4 for creating burst loads and stores. **/
  @api def apply(q: Range, p: Range, r: Index, c: Range) = DRAMDenseTile3(this.s, Seq(q, p, r.toRange, c))
  /** Creates a reference to a 3-dimensional, dense region of this DRAM4 for creating burst loads and stores. **/
  @api def apply(q: Range, p: Range, r: Range, c: Index) = DRAMDenseTile3(this.s, Seq(q, p, r, c.toRange))

  /** Creates a reference to a 4-dimensional, dense region of this DRAM4 for creating burst loads and stores. **/
  @api def apply(q: Range, p: Range, r: Range, c: Range) = DRAMDenseTile4(this.s, Seq(q, p, r, c))

  /** Creates a dense, burst transfer from the given on-chip `data` to this DRAM's region of main memory. **/
  @api def store(data: SRAM4[T]): MUnit = DRAMTransfers.dense_transfer(this.toTile(data.ranges), data, isLoad = false)
}
object DRAM4 {
  implicit def dram4Type[T:Type:Bits]: Type[DRAM4[T]] = DRAM4Type(typ[T])
}

case class DRAM5[T:Type:Bits](s: Exp[DRAM5[T]]) extends Template[DRAM5[T]] with DRAM[T] {
  @api def address: Int64 = wrap(DRAM.addr(this.s))
  @api def toTile(ranges: Seq[Range]): DRAMDenseTile5[T] = DRAMDenseTile5(this.s, ranges)

  /** Returns the first dimension of this DRAM5. **/
  @api def dim0: Index = wrap(stagedDimsOf(s).apply(0))
  /** Returns the second dimension of this DRAM5. **/
  @api def dim1: Index = wrap(stagedDimsOf(s).apply(1))
  /** Returns the third dimension of this DRAM5. **/
  @api def dim2: Index = wrap(stagedDimsOf(s).apply(2))
  /** Returns the fourth dimension of this DRAM5. **/
  @api def dim3: Index = wrap(stagedDimsOf(s).apply(3))
  /** Returns the fifth dimension of this DRAM5. **/
  @api def dim4: Index = wrap(stagedDimsOf(s).apply(4))
  /** Returns the total number of elements in this DRAM5. **/
  @api def size: Index = dim0 * dim1 * dim2 * dim3 * dim4

  // I'm not getting carried away, you're getting carried away! By the amazingness of this code!
  /** Creates a reference to a 1-dimensional, dense region of this DRAM5 for creating burst loads and stores. **/
  @api def apply(x: Index, q: Index, p: Index, r: Index, c: Range): DRAMDenseTile1[T] = DRAMDenseTile1(this.s, Seq(x.toRange, q.toRange, p.toRange, r.toRange, c))
  /** Creates a reference to a 1-dimensional, dense region of this DRAM5 for creating burst loads and stores. **/
  @api def apply(x: Index, q: Index, p: Index, r: Range, c: Index): DRAMDenseTile1[T] = DRAMDenseTile1(this.s, Seq(x.toRange, q.toRange, p.toRange, r, c.toRange))
  /** Creates a reference to a 1-dimensional, dense region of this DRAM5 for creating burst loads and stores. **/
  @api def apply(x: Index, q: Index, p: Range, r: Index, c: Index): DRAMDenseTile1[T] = DRAMDenseTile1(this.s, Seq(x.toRange, q.toRange, p, r.toRange, c.toRange))
  /** Creates a reference to a 1-dimensional, dense region of this DRAM5 for creating burst loads and stores. **/
  @api def apply(x: Index, q: Range, p: Index, r: Index, c: Index): DRAMDenseTile1[T] = DRAMDenseTile1(this.s, Seq(x.toRange, q, p.toRange, r.toRange, c.toRange))
  /** Creates a reference to a 1-dimensional, dense region of this DRAM5 for creating burst loads and stores. **/
  @api def apply(x: Range, q: Index, p: Index, r: Index, c: Index): DRAMDenseTile1[T] = DRAMDenseTile1(this.s, Seq(x, q.toRange, p.toRange, r.toRange, c.toRange))

  /** Creates a reference to a 2-dimensional, dense region of this DRAM5 for creating burst loads and stores. **/
  @api def apply(x: Index, q: Index, p: Index, r: Range, c: Range): DRAMDenseTile2[T] = DRAMDenseTile2(this.s, Seq(x.toRange, q.toRange, p.toRange, r, c))
  /** Creates a reference to a 2-dimensional, dense region of this DRAM5 for creating burst loads and stores. **/
  @api def apply(x: Index, q: Index, p: Range, r: Index, c: Range): DRAMDenseTile2[T] = DRAMDenseTile2(this.s, Seq(x.toRange, q.toRange, p, r.toRange, c))
  /** Creates a reference to a 2-dimensional, dense region of this DRAM5 for creating burst loads and stores. **/
  @api def apply(x: Index, q: Index, p: Range, r: Range, c: Index): DRAMDenseTile2[T] = DRAMDenseTile2(this.s, Seq(x.toRange, q.toRange, p, r, c.toRange))
  /** Creates a reference to a 2-dimensional, dense region of this DRAM5 for creating burst loads and stores. **/
  @api def apply(x: Index, q: Range, p: Index, r: Index, c: Range): DRAMDenseTile2[T] = DRAMDenseTile2(this.s, Seq(x.toRange, q, p.toRange, r.toRange, c))
  /** Creates a reference to a 2-dimensional, dense region of this DRAM5 for creating burst loads and stores. **/
  @api def apply(x: Index, q: Range, p: Index, r: Range, c: Index): DRAMDenseTile2[T] = DRAMDenseTile2(this.s, Seq(x.toRange, q, p.toRange, r, c.toRange))
  /** Creates a reference to a 2-dimensional, dense region of this DRAM5 for creating burst loads and stores. **/
  @api def apply(x: Index, q: Range, p: Range, r: Index, c: Index): DRAMDenseTile2[T] = DRAMDenseTile2(this.s, Seq(x.toRange, q, p, r.toRange, c.toRange))
  /** Creates a reference to a 2-dimensional, dense region of this DRAM5 for creating burst loads and stores. **/
  @api def apply(x: Range, q: Index, p: Index, r: Index, c: Range): DRAMDenseTile2[T] = DRAMDenseTile2(this.s, Seq(x, q.toRange, p.toRange, r.toRange, c))
  /** Creates a reference to a 2-dimensional, dense region of this DRAM5 for creating burst loads and stores. **/
  @api def apply(x: Range, q: Index, p: Index, r: Range, c: Index): DRAMDenseTile2[T] = DRAMDenseTile2(this.s, Seq(x, q.toRange, p.toRange, r, c.toRange))
  /** Creates a reference to a 2-dimensional, dense region of this DRAM5 for creating burst loads and stores. **/
  @api def apply(x: Range, q: Index, p: Range, r: Index, c: Index): DRAMDenseTile2[T] = DRAMDenseTile2(this.s, Seq(x, q.toRange, p, r.toRange, c.toRange))
  /** Creates a reference to a 2-dimensional, dense region of this DRAM5 for creating burst loads and stores. **/
  @api def apply(x: Range, q: Range, p: Index, r: Index, c: Index): DRAMDenseTile2[T] = DRAMDenseTile2(this.s, Seq(x, q, p.toRange, r.toRange, c.toRange))

  /** Creates a reference to a 3-dimensional, dense region of this DRAM5 for creating burst loads and stores. **/
  @api def apply(x: Index, q: Index, p: Range, r: Range, c: Range): DRAMDenseTile3[T] = DRAMDenseTile3(this.s, Seq(x.toRange, q.toRange, p, r, c))
  /** Creates a reference to a 3-dimensional, dense region of this DRAM5 for creating burst loads and stores. **/
  @api def apply(x: Index, q: Range, p: Index, r: Range, c: Range): DRAMDenseTile3[T] = DRAMDenseTile3(this.s, Seq(x.toRange, q, p.toRange, r, c))
  /** Creates a reference to a 3-dimensional, dense region of this DRAM5 for creating burst loads and stores. **/
  @api def apply(x: Index, q: Range, p: Range, r: Index, c: Range): DRAMDenseTile3[T] = DRAMDenseTile3(this.s, Seq(x.toRange, q, p, r.toRange, c))
  /** Creates a reference to a 3-dimensional, dense region of this DRAM5 for creating burst loads and stores. **/
  @api def apply(x: Index, q: Range, p: Range, r: Range, c: Index): DRAMDenseTile3[T] = DRAMDenseTile3(this.s, Seq(x.toRange, q, p, r, c.toRange))
  /** Creates a reference to a 3-dimensional, dense region of this DRAM5 for creating burst loads and stores. **/
  @api def apply(x: Range, q: Index, p: Index, r: Range, c: Range): DRAMDenseTile3[T] = DRAMDenseTile3(this.s, Seq(x, q.toRange, p.toRange, r, c))
  /** Creates a reference to a 3-dimensional, dense region of this DRAM5 for creating burst loads and stores. **/
  @api def apply(x: Range, q: Index, p: Range, r: Index, c: Range): DRAMDenseTile3[T] = DRAMDenseTile3(this.s, Seq(x, q.toRange, p, r.toRange, c))
  /** Creates a reference to a 3-dimensional, dense region of this DRAM5 for creating burst loads and stores. **/
  @api def apply(x: Range, q: Index, p: Range, r: Range, c: Index): DRAMDenseTile3[T] = DRAMDenseTile3(this.s, Seq(x, q.toRange, p, r, c.toRange))
  /** Creates a reference to a 3-dimensional, dense region of this DRAM5 for creating burst loads and stores. **/
  @api def apply(x: Range, q: Range, p: Index, r: Index, c: Range): DRAMDenseTile3[T] = DRAMDenseTile3(this.s, Seq(x, q, p.toRange, r.toRange, c))
  /** Creates a reference to a 3-dimensional, dense region of this DRAM5 for creating burst loads and stores. **/
  @api def apply(x: Range, q: Range, p: Index, r: Range, c: Index): DRAMDenseTile3[T] = DRAMDenseTile3(this.s, Seq(x, q, p.toRange, r, c.toRange))
  /** Creates a reference to a 3-dimensional, dense region of this DRAM5 for creating burst loads and stores. **/
  @api def apply(x: Range, q: Range, p: Range, r: Index, c: Index): DRAMDenseTile3[T] = DRAMDenseTile3(this.s, Seq(x, q, p, r.toRange, c.toRange))

  /** Creates a reference to a 4-dimensional, dense region of this DRAM5 for creating burst loads and stores. **/
  @api def apply(x: Index, q: Range, p: Range, r: Range, c: Range): DRAMDenseTile4[T] = DRAMDenseTile4(this.s, Seq(x.toRange, q, p, r, c))
  /** Creates a reference to a 4-dimensional, dense region of this DRAM5 for creating burst loads and stores. **/
  @api def apply(x: Range, q: Index, p: Range, r: Range, c: Range): DRAMDenseTile4[T] = DRAMDenseTile4(this.s, Seq(x, q.toRange, p, r, c))
  /** Creates a reference to a 4-dimensional, dense region of this DRAM5 for creating burst loads and stores. **/
  @api def apply(x: Range, q: Range, p: Index, r: Range, c: Range): DRAMDenseTile4[T] = DRAMDenseTile4(this.s, Seq(x, q, p.toRange, r, c))
  /** Creates a reference to a 4-dimensional, dense region of this DRAM5 for creating burst loads and stores. **/
  @api def apply(x: Range, q: Range, p: Range, r: Index, c: Range): DRAMDenseTile4[T] = DRAMDenseTile4(this.s, Seq(x, q, p, r.toRange, c))
  /** Creates a reference to a 4-dimensional, dense region of this DRAM5 for creating burst loads and stores. **/
  @api def apply(x: Range, q: Range, p: Range, r: Range, c: Index): DRAMDenseTile4[T] = DRAMDenseTile4(this.s, Seq(x, q, p, r, c.toRange))

  /** Creates a reference to a 5-dimensional, dense region of this DRAM5 for creating burst loads and stores. **/
  @api def apply(x: Range, q: Range, p: Range, r: Range, c: Range): DRAMDenseTile5[T] = DRAMDenseTile5(this.s, Seq(x, q, p, r, c))

  /** Creates a dense, burst transfer from the given on-chip `data` to this DRAM's region of main memory. **/
  @api def store(data: SRAM5[T]): MUnit = DRAMTransfers.dense_transfer(this.toTile(data.ranges), data, isLoad = false)
}
object DRAM5 {
  implicit def dram5Type[T:Type:Bits]: Type[DRAM5[T]] = DRAM5Type(typ[T])
}

trait DRAMDenseTile[T] {
  def dram: Exp[DRAM[T]]
  def ranges: Seq[Range]
}

case class DRAMDenseTile1[T:Type:Bits](dram: Exp[DRAM[T]], ranges: Seq[Range]) extends DRAMDenseTile[T] {
  /** Creates a dense, burst transfer from the given on-chip `data` to this tiles's region of main memory. **/
  @api def store(data: SRAM1[T]): MUnit = DRAMTransfers.dense_transfer(this, data, isLoad = false)
  /** Creates a dense, burst transfer from the given on-chip `data` to this tiles's region of main memory. **/
  @api def store(data: FIFO[T]): MUnit = DRAMTransfers.dense_transfer(this, data, isLoad = false)
  /** Creates a dense, burst transfer from the given on-chip `data` to this tiles's region of main memory. **/
  @api def store(data: FILO[T]): MUnit = DRAMTransfers.dense_transfer(this, data, isLoad = false)
  /** Creates a dense, burst transfer from the given on-chip `data` to this tiles's region of main memory. **/
  @api def store(data: RegFile1[T]): MUnit = DRAMTransfers.dense_transfer(this, data, isLoad = false)
  @api def storeAligned(sram: SRAM1[T]): MUnit    = DRAMTransfers.dense_transfer(this, sram, isLoad = false, isAlign = true)
  @api def storeAligned(fifo: FIFO[T]): MUnit     = DRAMTransfers.dense_transfer(this, fifo, isLoad = false, isAlign = true)
  @api def storeAligned(filo: FILO[T]): MUnit     = DRAMTransfers.dense_transfer(this, filo, isLoad = false, isAlign = true)
  @api def storeAligned(regs: RegFile1[T]): MUnit = DRAMTransfers.dense_transfer(this, regs, isLoad = false, isAlign = true)
}
case class DRAMDenseTile2[T:Type:Bits](dram: Exp[DRAM[T]], ranges: Seq[Range]) extends DRAMDenseTile[T] {
  /** Creates a dense, burst transfer from the given on-chip `data` to this tiles's region of main memory. **/
  @api def store(data: SRAM2[T]): MUnit    = DRAMTransfers.dense_transfer(this, data, isLoad = false)
  /** Creates a dense, burst transfer from the given on-chip `data` to this tiles's region of main memory. **/
  @api def store(data: RegFile2[T]): MUnit = DRAMTransfers.dense_transfer(this, data, isLoad = false)
  @api def storeAligned(sram: SRAM2[T]): MUnit    = DRAMTransfers.dense_transfer(this, sram, isLoad = false, isAlign = true)
  @api def storeAligned(regs: RegFile2[T]): MUnit = DRAMTransfers.dense_transfer(this, regs, isLoad = false, isAlign = true)
}
case class DRAMDenseTile3[T:Type:Bits](dram: Exp[DRAM[T]], ranges: Seq[Range]) extends DRAMDenseTile[T] {
  /** Creates a dense, burst transfer from the given on-chip `data` to this tiles's region of main memory. **/
  @api def store(data: SRAM3[T]): MUnit   = DRAMTransfers.dense_transfer(this, data, isLoad = false)
}
case class DRAMDenseTile4[T:Type:Bits](dram: Exp[DRAM[T]], ranges: Seq[Range]) extends DRAMDenseTile[T] {
  /** Creates a dense, burst transfer from the given on-chip `data` to this tiles's region of main memory. **/
  @api def store(data: SRAM4[T]): MUnit   = DRAMTransfers.dense_transfer(this, data, isLoad = false)
}
case class DRAMDenseTile5[T:Type:Bits](dram: Exp[DRAM[T]], ranges: Seq[Range]) extends DRAMDenseTile[T] {
  /** Creates a dense, burst transfer from the given on-chip `data` to this tiles's region of main memory. **/
  @api def store(data: SRAM5[T]): MUnit   = DRAMTransfers.dense_transfer(this, data, isLoad = false)
}

/** Sparse Tiles are limited to 1D right now **/
case class DRAMSparseTile[T:Type:Bits](dram: Exp[DRAM[T]], addrs: SRAM1[Index], len: Index) {
  /** Creates a sparse transfer from the given on-chip `data` to this sparse region of main memory. **/
  @api def scatter(data: SRAM1[T]): MUnit = DRAMTransfers.sparse_transfer(this, data, isLoad = false)
  /** Creates a sparse transfer from the given on-chip `data` to this sparse region of main memory. **/
  @api def scatter(data: FIFO[T]): MUnit = DRAMTransfers.sparse_transfer_mem(this.toSparseTileMem, data, isLoad = false)
  /** Creates a sparse transfer from the given on-chip `data` to this sparse region of main memory. **/
  @api def scatter(data: FILO[T]): MUnit = DRAMTransfers.sparse_transfer_mem(this.toSparseTileMem, data, isLoad = false)

  protected[spatial] def toSparseTileMem = DRAMSparseTileMem[T,SRAM1](dram, addrs, len)
}

// TODO: Should replace DRAMSparseTile when confirmed to work
case class DRAMSparseTileMem[T:Type:Bits,A[_]](dram: Exp[DRAM[T]], addrs: A[Index], len: Index)(implicit val memA: Mem[Index,A], val mA: Type[A[Index]]) {
  @api def scatter(sram: SRAM1[T]): MUnit = DRAMTransfers.sparse_transfer_mem(this, sram, isLoad = false)
  @api def scatter(fifo: FIFO[T]): MUnit = DRAMTransfers.sparse_transfer_mem(this, fifo, isLoad = false)
  @api def scatter(filo: FILO[T]): MUnit = DRAMTransfers.sparse_transfer_mem(this, filo, isLoad = false)
}
