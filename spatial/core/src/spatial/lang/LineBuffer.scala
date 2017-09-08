package spatial.lang

import argon.core._
import forge._
import spatial.metadata._
import spatial.nodes._

case class LineBuffer[T:Type:Bits](s: Exp[LineBuffer[T]]) extends Template[LineBuffer[T]] {
  @api def apply(row: Index, col: Index): T = wrap(LineBuffer.load(s, row.s, col.s, Bit.const(true)))

  @api def apply(row: Index, cols: Range)(implicit ctx: SrcCtx): Vector[T] = {
    // UNSUPPORTED: Strided range apply of line buffer
    cols.step.map(_.s) match {
      case None | Some(Const(1)) =>
      case _ => error(ctx, "Unsupported stride in LineBuffer apply")
    }

    val start  = cols.start.map(_.s).getOrElse(int32s(0))
    val length = cols.length
    val exp = LineBuffer.col_slice(s, row.s, start, length.s)
    exp.tp.wrapped(exp)
  }
  @api def apply(rows: Range, col: Index)(implicit ctx: SrcCtx): Vector[T] = {
    // UNSUPPORTED: Strided range apply of line buffer
    rows.step.map(_.s) match {
      case None | Some(Const(1)) =>
      case _ => error(ctx, "Unsupported stride in LineBuffer apply")
    }

    val start = rows.start.map(_.s).getOrElse(int32s(0))
    val length = rows.length
    val exp = LineBuffer.row_slice(s, start, length.s, col.s)
    exp.tp.wrapped(exp)
  }

  @api def enq(data: T): MUnit = MUnit(LineBuffer.enq(this.s, data.s, Bit.const(true)))
  @api def enq(data: T, en: Bit): MUnit = MUnit(LineBuffer.enq(this.s, data.s, en.s))

  @api def load(dram: DRAMDenseTile1[T])(implicit ctx: SrcCtx): MUnit = {
    /*if (!dram.ranges.head.isUnit || dram.ranges.length != 2) {
      error(ctx, "Loading into a LineBuffer from DRAM must be row-based")
    }*/
    DRAMTransfers.dense_transfer(dram, this, isLoad = true)
  }
}

object LineBuffer {
  /** Static methods **/
  implicit def lineBufferType[T:Type:Bits]: Type[LineBuffer[T]] = LineBufferType(typ[T])
  implicit def linebufferIsMemory[T:Type:Bits]: Mem[T, LineBuffer] = new LineBufferIsMemory[T]

  @api def apply[T:Type:Bits](rows: Index, cols: Index): LineBuffer[T] = wrap(alloc[T](rows.s, cols.s, int32s(1)))
  @api def strided[T:Type:Bits](rows: Index, cols: Index, stride: Index): LineBuffer[T] = wrap(alloc[T](rows.s, cols.s, stride.s))

  @internal def alloc[T:Type:Bits](rows: Exp[Index], cols: Exp[Index], verticalStride: Exp[Index]) = {
    stageMutable(LineBufferNew[T](rows, cols, int32s(1)))(ctx)
  }

  @internal def col_slice[T:Type:Bits](
    linebuffer: Exp[LineBuffer[T]],
    row:        Exp[Index],
    colStart:   Exp[Index],
    length:     Exp[Index]
  ) = {
    implicit val vT = length match {
      case Final(c) => VectorN.typeFromLen[T](c.toInt)
      case _ =>
        error(ctx, "Cannot create parametrized or dynamically sized line buffer slice")
        VectorN.typeFromLen[T](0)
    }
    stageUnique(LineBufferColSlice(linebuffer, row, colStart, length))(ctx)
  }

  @internal def row_slice[T:Type:Bits](
    linebuffer: Exp[LineBuffer[T]],
    rowStart:   Exp[Index],
    length:     Exp[Index],
    col:        Exp[Index]
  ) = {
    implicit val vT = length match {
      case Final(c) => VectorN.typeFromLen[T](c.toInt)
      case _ =>
        error(ctx, "Cannot create parametrized or dynamically sized line buffer slice")
        VectorN.typeFromLen[T](0)
    }
    stageUnique(LineBufferRowSlice(linebuffer, rowStart, length, col))(ctx)
  }

  @internal def load[T:Type:Bits](
    linebuffer: Exp[LineBuffer[T]],
    row:        Exp[Index],
    col:        Exp[Index],
    en:         Exp[Bit]
  ) = {
    stageWrite(linebuffer)(LineBufferLoad(linebuffer,row,col,en))(ctx)
  }

  @internal def enq[T:Type:Bits](
    linebuffer: Exp[LineBuffer[T]],
    data:       Exp[T],
    en:         Exp[Bit]
  ) = {
    stageWrite(linebuffer)(LineBufferEnq(linebuffer,data,en))(ctx)
  }

  @internal def par_load[T:Type:Bits](
    linebuffer: Exp[LineBuffer[T]],
    rows:       Seq[Exp[Index]],
    cols:       Seq[Exp[Index]],
    ens:        Seq[Exp[Bit]]
  ) = {
    implicit val vT = VectorN.typeFromLen[T](ens.length)
    stageWrite(linebuffer)(ParLineBufferLoad(linebuffer,rows,cols,ens))(ctx)
  }

  @internal def par_enq[T:Type:Bits](
    linebuffer: Exp[LineBuffer[T]],
    data:       Seq[Exp[T]],
    ens:        Seq[Exp[Bit]]
  ) = {
    stageWrite(linebuffer)(ParLineBufferEnq(linebuffer,data,ens))(ctx)
  }

}
