package spatial.lang

import argon.core._
import forge._
import spatial.metadata._
import spatial.nodes._

case class LineBuffer[T:Type:Bits](s: Exp[LineBuffer[T]]) extends Template[LineBuffer[T]] {
  /** Creates a load port to this LineBuffer at the given `row` and `col`. **/
  @api def apply(row: Index, col: Index): T = wrap(LineBuffer.load(s, row.s, col.s, Bit.const(true)))

  /** Creates a vectorized load port to this LineBuffer at the given `row` and `cols`. **/
  @api def apply(row: Index, cols: Range)(implicit ctx: SrcCtx): Vector[T] = {
    // UNSUPPORTED: Strided range apply of line buffer
    cols.step.map(_.s) match {
      case None | Some(Const(1)) =>
      case _ => error(ctx, "Unsupported stride in vectorized LineBuffer read")
    }

    val start  = cols.start.map(_.s).getOrElse(int32s(0))
    val length = cols.length
    val exp = LineBuffer.col_slice(s, row.s, start, length.s)
    exp.tp.wrapped(exp)
  }
  /** Creates a vectorized load port to this LineBuffer at the given `rows` and `col`. **/
  @api def apply(rows: Range, col: Index)(implicit ctx: SrcCtx): Vector[T] = {
    // UNSUPPORTED: Strided range apply of line buffer
    rows.step.map(_.s) match {
      case None | Some(Const(1)) =>
      case _ => error(ctx, "Unsupported stride in vectorized LineBuffer read")
    }
    implicit val vT: Type[VectorN[T]] = length match {
      case Final(c) => VectorN.typeFromLen[T](c.toInt)
      case _ =>
        error(ctx, "Cannot create parametrized or dynamically sized line buffer slice")
        VectorN.typeFromLen[T](0)
    }

    val start = rows.start.map(_.s).getOrElse(int32s(0))
    val length = rows.length
    val exp = LineBuffer.row_slice(s, start, length.s, col.s)
    exp.tp.wrapped(exp)
  }

  /** Creates an enqueue (write) port of `data` to this LineBuffer. **/
  @api def enq(data: T): MUnit = MUnit(LineBuffer.enq(this.s, data.s, Bit.const(true)))
  /** Creates an enqueue (write) port of `data` to this LineBuffer, enabled by `en`. **/
  @api def enq(data: T, en: Bit): MUnit = MUnit(LineBuffer.enq(this.s, data.s, en.s))

  /** Creates an enqueue port of `data` to this LineBuffer which rotates when `row` changes. **/
  @api def enq(row: Index, data: T): MUnit = MUnit(LineBuffer.rotateEnq(this.s, data.s, Bit.const(true), row.s))
  /** Creates an enqueue port of `data` to this LineBuffer enabled by `en` which rotates when `row` changes. **/
  @api def enq(row: Index, data: T, en: Bit): MUnit = MUnit(LineBuffer.rotateEnq(this.s, data.s, en.s, row.s))

  /** Creates a dense transfer from the given region of DRAM to this on-chip memory. **/
  @api def load(dram: DRAMDenseTile1[T]): MUnit = DRAMTransfers.dense_transfer(dram, this, isLoad = true)

  /** Creates a dense transfer from the given region of DRAM to this on-chip memory. **/
  @api def load(dram: DRAMDenseTile2[T]): MUnit = DRAMTransfers.dense_transfer(dram, this, isLoad = true)
}

object LineBuffer {
  /** Allocates a LineBuffer with given `rows` and `cols`.
    * The contents of this LineBuffer are initially undefined.
    * `rows` and `cols` must be statically determinable integers.
    */
  @api def apply[T:Type:Bits](rows: Index, cols: Index): LineBuffer[T] = wrap(alloc[T](rows.s, cols.s, int32s(1)))

  /**
    * Allocates a LineBuffer with given number of `rows` and `cols`, and with given `stride`.
    * The contents of this LineBuffer are initially undefined.
    * `rows`, `cols`, and `stride` must be statically determinable integers.
    */
  @api def strided[T:Type:Bits](rows: Index, cols: Index, stride: Index): LineBuffer[T] = wrap(alloc[T](rows.s, cols.s, stride.s))

  implicit def lineBufferType[T:Type:Bits]: Type[LineBuffer[T]] = LineBufferType(typ[T])
  implicit def linebufferIsMemory[T:Type:Bits]: Mem[T, LineBuffer] = new LineBufferIsMemory[T]


  @internal def alloc[T:Type:Bits](rows: Exp[Index], cols: Exp[Index], verticalStride: Exp[Index]): Exp[LineBuffer[T]] = {
    stageMutable(LineBufferNew[T](rows, cols, verticalStride))(ctx)
  }

  @internal def col_slice[T:Type:Bits](
    buff: Exp[LineBuffer[T]],
    row:  Exp[Index],
    col:  Exp[Index],
    len:  Int
  ): Exp[VectorN[T]] = {
    stageUnique(LineBufferColSlice(buff, row, col, len))(ctx)
  }

  @internal def row_slice[T:Type:Bits](
    buff: Exp[LineBuffer[T]],
    row:  Exp[Index],
    col:  Exp[Index],
    len:  Int
  ): Exp[VectorN[T]] = {
    stageUnique(LineBufferRowSlice(buff, row, col, len))(ctx)
  }

  @internal def load[T:Type:Bits](
    buff: Exp[LineBuffer[T]],
    row:  Exp[Index],
    col:  Exp[Index],
    en:   Exp[Bit]
  ): Exp[T] = {
    stageWrite(buff)(LineBufferLoad(buff,row,col,en))(ctx)
  }

  @internal def enq[T:Type:Bits](
    buff: Exp[LineBuffer[T]],
    data: Exp[T],
    en:   Exp[Bit]
  ): Exp[MUnit] = {
    stageWrite(buff)(LineBufferEnq(buff,data,en))(ctx)
  }

  @internal def rotateEnq[T:Type:Bits](
    buff: Exp[LineBuffer[T]],
    data:       Exp[T],
    en:         Exp[Bit],
    row:        Exp[Index]
  ): Exp[MUnit] = {
    stageWrite(buff)(LineBufferRotateEnq(buff,data,en,row))(ctx)
  }

  @internal def banked_load[T:Type:Bits](
    buff: Exp[LineBuffer[T]],
    bank: Seq[Seq[Exp[Index]]],
    addr: Seq[Exp[Index]],
    ens:  Seq[Exp[Bit]]
  ): Exp[VectorN[T]] = {
    implicit val vT: Type[VectorN[T]] = VectorN.typeFromLen[T](ens.length)
    stageWrite(buff)(BankedLineBufferLoad(buff,bank,addr,ens))(ctx)
  }

  @internal def banked_enq[T:Type:Bits](
    buff: Exp[LineBuffer[T]],
    data: Seq[Exp[T]],
    ens:  Seq[Exp[Bit]]
  ): Exp[MUnit] = {
    stageWrite(buff)(BankedLineBufferEnq(buff,data,ens))(ctx)
  }

  @internal def banked_rotateEnq[T:Type:Bits](
    buff: Exp[LineBuffer[T]],
    data: Seq[Exp[T]],
    ens:  Seq[Exp[Bit]],
    row:  Exp[Index],
  ): Exp[MUnit] = {
    stageWrite(buff)(BankedLineBufferRotateEnq(buff,data,ens,row))(ctx)
  }

}
