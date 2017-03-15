package spatial.api

import argon.core.Staging
import argon.typeclasses.CustomBitWidths
import spatial.SpatialExp

trait LineBufferApi extends LineBufferExp {
  this: SpatialExp =>
}

trait LineBufferExp extends Staging with SRAMExp with CustomBitWidths {
  this: SpatialExp =>

  case class LineBuffer[T:Staged:Bits](s: Exp[LineBuffer[T]]) {
    def apply(row: Index, col: Index)(implicit ctx: SrcCtx): T = this.apply(row, index_to_range(col)).apply(0)

    def apply(row: Index, cols: Range)(implicit ctx: SrcCtx): Vector[T] = {
      // UNSUPPORTED: Strided range apply of line buffer
      cols.step.map(_.s) match {
        case None | Some(Const(1)) =>
        case _ => error(ctx, "Unsupported stride in LineBuffer apply")
      }

      val start  = cols.start.map(_.s).getOrElse(int32(0))
      val length = cols.length
      val exp = linebuffer_col_slice(s, row.s, start, length.s)
      exp.tp.wrapped(exp)
    }
    def apply(rows: Range, col: Index)(implicit ctx: SrcCtx): Vector[T] = {
      // UNSUPPORTED: Strided range apply of line buffer
      rows.step.map(_.s) match {
        case None | Some(Const(1)) =>
        case _ => error(ctx, "Unsupported stride in LineBuffer apply")
      }

      val start = rows.start.map(_.s).getOrElse(int32(0))
      val length = rows.length
      val exp = linebuffer_row_slice(s, start, length.s, col.s)
      exp.tp.wrapped(exp)
    }

    def load(dram: DRAMDenseTile[T])(implicit ctx: SrcCtx): Void = {
      if (!dram.ranges.head.isUnit || dram.ranges.length != 2) {
        error(ctx, "Loading into a LineBuffer from DRAM must be row-based")
      }
      dense_transfer(dram, this, isLoad = true)
    }
  }

  object LineBuffer {
    def apply[T:Staged:Bits](rows: Index, cols: Index)(implicit ctx: SrcCtx): LineBuffer[T] = {
      wrap(linebuffer_new[T](rows.s, cols.s))
    }
  }


  /** Type classes **/
  case class LineBufferType[T:Bits](child: Staged[T]) extends Staged[LineBuffer[T]] {
    override def unwrapped(x: LineBuffer[T]) = x.s
    override def wrapped(x: Exp[LineBuffer[T]]) = LineBuffer(x)(child,bits[T])
    override def typeArguments = List(child)
    override def stagedClass = classOf[LineBuffer[T]]
    override def isPrimitive = false
  }
  implicit def lineBufferType[T:Staged:Bits]: Staged[LineBuffer[T]] = LineBufferType(typ[T])

  class LineBufferIsMemory[T:Staged:Bits] extends Mem[T, LineBuffer] {
    def load(mem: LineBuffer[T], is: Seq[Index], en: Bool)(implicit ctx: SrcCtx): T = mem.apply(is(0),is(1))

    def store(mem: LineBuffer[T], is: Seq[Index], data: T, en: Bool)(implicit ctx: SrcCtx): Void = {
      wrap(linebuffer_store(mem.s, is(0).s, data.s))
    }
    def iterators(mem: LineBuffer[T])(implicit ctx: SrcCtx): Seq[Counter] = {
      // Hack: Use only columns for dense transfers
      stagedDimsOf(mem.s).drop(1).map{d => Counter(0, wrap(d), 1, 1) }
    }
  }
  implicit def linebufferIsMemory[T:Staged:Bits]: Mem[T, LineBuffer] = new LineBufferIsMemory[T]



  /** IR Nodes **/

  case class LineBufferNew[T:Staged:Bits](rows: Exp[Index], cols: Exp[Index]) extends Op[LineBuffer[T]] {
    def mirror(f:Tx) = linebuffer_new[T](f(rows),f(cols))
  }

  case class LineBufferColSlice[T:Staged:Bits](
    linebuffer: Exp[LineBuffer[T]],
    row:        Exp[Index],
    colStart:   Exp[Index],
    colLength:  Exp[Index]
  )(implicit val W: INT[Vector[T]]) extends Op[Vector[T]] {
    def mirror(f:Tx) = linebuffer_col_slice(f(linebuffer),f(row),f(colStart),f(colLength))
    override def aliases = Nil
  }

  case class LineBufferRowSlice[T:Staged:Bits](
    linebuffer: Exp[LineBuffer[T]],
    rowStart:   Exp[Index],
    rowEnd:     Exp[Index],
    col:        Exp[Index]
  )(implicit val W: INT[Vector[T]]) extends Op[Vector[T]] {
    def mirror(f:Tx) = linebuffer_row_slice(f(linebuffer),f(rowStart),f(rowEnd),f(col))
    override def aliases = Nil
  }

  case class LineBufferStore[T:Staged:Bits](
    linebuffer: Exp[LineBuffer[T]],
    col:        Exp[Index],
    data:       Exp[T]
  ) extends Op[Void] {
    def mirror(f:Tx) = linebuffer_store(f(linebuffer),f(col),f(data))
    override def aliases = Nil
  }

  /** Constructors **/

  private[spatial] def linebuffer_new[T:Staged:Bits](rows: Exp[Index], cols: Exp[Index])(implicit ctx: SrcCtx) = {
    stageMutable(LineBufferNew[T](rows, cols))(ctx)
  }

  private[spatial] def linebuffer_col_slice[T:Staged:Bits](
    linebuffer: Exp[LineBuffer[T]],
    row:        Exp[Index],
    colStart:   Exp[Index],
    length:     Exp[Index]
  )(implicit ctx: SrcCtx) = {
    implicit val W: INT[Vector[T]] = length match {
      case Final(c) => Width[T](c.toInt)
      case _ =>
        error(ctx, "Cannot create parameterized or dynamically sized line buffer slice")
        Width[T](0)
    }
    stageCold(LineBufferColSlice(linebuffer, row, colStart, length))(ctx)
  }

  private[spatial] def linebuffer_row_slice[T:Staged:Bits](
    linebuffer: Exp[LineBuffer[T]],
    rowStart:   Exp[Index],
    length:     Exp[Index],
    col:        Exp[Index]
  )(implicit ctx: SrcCtx) = {
    implicit val W: INT[Vector[T]] = length match {
      case Final(c) => Width[T](c.toInt)
      case _ =>
        error(ctx, "Cannot create parameterized or dynamically sized line buffer slice")
        Width[T](0)
    }
    stageCold(LineBufferRowSlice(linebuffer, rowStart, length, col))(ctx)
  }

  private[spatial] def linebuffer_store[T:Staged:Bits](
    linebuffer: Exp[LineBuffer[T]],
    col:        Exp[Index],
    data:       Exp[T]
  )(implicit ctx: SrcCtx) = {
    stageWrite(linebuffer)(LineBufferStore(linebuffer,col,data))(ctx)
  }

  /** Internal **/
  override def stagedDimsOf(x: Exp[_]): Seq[Exp[Index]] = x match {
    case Def(LineBufferNew(rows,cols)) => Seq(rows, cols)
    case _ => super.stagedDimsOf(x)
  }

}
