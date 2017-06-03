package spatial.lang

import forge._
import spatial.nodes._

case class LineBuffer[T:Type:Bits](s: Exp[LineBuffer[T]]) extends Template[LineBuffer[T]] {
  @api def apply(row: Index, col: Index): T = wrap(LineBuffer.load(s, row.s, col.s, MBoolean.const(true)))

  @api def apply(row: Index, cols: Range)(implicit ctx: SrcCtx): Vector[T] = {
    // UNSUPPORTED: Strided range apply of line buffer
    cols.step.map(_.s) match {
      case None | Some(Const(1)) =>
      case _ => error(ctx, "Unsupported stride in LineBuffer apply")
    }

    val start  = cols.start.map(_.s).getOrElse(int32(0))
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

    val start = rows.start.map(_.s).getOrElse(int32(0))
    val length = rows.length
    val exp = LineBuffer.row_slice(s, start, length.s, col.s)
    exp.tp.wrapped(exp)
  }

  @api def enq(data: T): MUnit = MUnit(LineBuffer.enq(this.s, data.s, MBoolean.const(true)))
  @api def enq(data: T, en: Bit): MUnit = MUnit(LineBuffer.neq(this.s, data.s, en.s))

  @api def load(dram: DRAMDenseTile1[T])(implicit ctx: SrcCtx): MUnit = {
    /*if (!dram.ranges.head.isUnit || dram.ranges.length != 2) {
      error(ctx, "Loading into a LineBuffer from DRAM must be row-based")
    }*/
    dense_transfer(dram, this, isLoad = true)
  }
}

object LineBuffer {
  /** Static methods **/
  implicit def lineBufferType[T:Type:Bits]: Type[LineBuffer[T]] = LineBufferType(typ[T])
  implicit def linebufferIsMemory[T:Type:Bits]: Mem[T, LineBuffer] = new LineBufferIsMemory[T]

  @api def apply[T:Type:Bits](rows: Index, cols: Index): LineBuffer[T] = wrap(linebuffer_new[T](rows.s, cols.s))

  @internal def alloc[T:Type:Bits](rows: Exp[Index], cols: Exp[Index]) = {
    stageMutable(LineBufferNew[T](rows, cols))(ctx)
  }

  @internal def col_slice[T:Type:Bits](
    linebuffer: Exp[LineBuffer[T]],
    row:        Exp[Index],
    colStart:   Exp[Index],
    length:     Exp[Index]
  ) = {
    implicit val vT = length match {
      case Final(c) => vectorNType[T](c.toInt)
      case _ =>
        error(ctx, "Cannot create parameterized or dynamically sized line buffer slice")
        vectorNType[T](0)
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
      case Final(c) => vectorNType[T](c.toInt)
      case _ =>
        error(ctx, "Cannot create parameterized or dynamically sized line buffer slice")
        vectorNType[T](0)
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
}


trait LineBufferExp { this: SpatialExp =>




  /** Type classes **/


  /** Constructors **/



}
