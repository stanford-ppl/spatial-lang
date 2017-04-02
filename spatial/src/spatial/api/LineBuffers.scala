package spatial.api

import argon.core.Staging
import argon.typeclasses.CustomBitWidths
import spatial.SpatialExp
import forge._

trait LineBufferApi extends LineBufferExp {
  this: SpatialExp =>
}

trait LineBufferExp extends Staging with SRAMExp with CustomBitWidths {
  this: SpatialExp =>

  case class LineBuffer[T:Meta:Bits](s: Exp[LineBuffer[T]]) extends Template[LineBuffer[T]] {
    @api def apply(row: Index, col: Index): T = {
      wrap(linebuffer_load(s, row.s, col.s, bool(true)))
    }

    @api def apply(row: Index, cols: Range)(implicit ctx: SrcCtx): Vector[T] = {
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
    @api def apply(rows: Range, col: Index)(implicit ctx: SrcCtx): Vector[T] = {
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

    @api def enq(data: T, en: Bool): Void = Void(linebuffer_enq(this.s, data.s, en.s))

    @api def load(dram: DRAMDenseTile1[T])(implicit ctx: SrcCtx): Void = {
      /*if (!dram.ranges.head.isUnit || dram.ranges.length != 2) {
        error(ctx, "Loading into a LineBuffer from DRAM must be row-based")
      }*/
      dense_transfer(dram, this, isLoad = true)
    }
  }

  object LineBuffer {
    @api def apply[T:Meta:Bits](rows: Index, cols: Index): LineBuffer[T] = wrap(linebuffer_new[T](rows.s, cols.s))
  }


  /** Type classes **/
  case class LineBufferType[T:Bits](child: Meta[T]) extends Meta[LineBuffer[T]] {
    override def wrapped(x: Exp[LineBuffer[T]]) = LineBuffer(x)(child,bits[T])
    override def typeArguments = List(child)
    override def stagedClass = classOf[LineBuffer[T]]
    override def isPrimitive = false
  }
  implicit def lineBufferType[T:Meta:Bits]: Meta[LineBuffer[T]] = LineBufferType(typ[T])

  class LineBufferIsMemory[T:Meta:Bits] extends Mem[T, LineBuffer] {
    def load(mem: LineBuffer[T], is: Seq[Index], en: Bool)(implicit ctx: SrcCtx): T = {
      wrap(linebuffer_load(mem.s, is(0).s, is(1).s, en.s))
    }

    def store(mem: LineBuffer[T], is: Seq[Index], data: T, en: Bool)(implicit ctx: SrcCtx): Void = {
      wrap(linebuffer_enq(mem.s, data.s, en.s))
    }
    def iterators(mem: LineBuffer[T])(implicit ctx: SrcCtx): Seq[Counter] = {
      // Hack: Use only columns for dense transfers
      stagedDimsOf(mem.s).drop(1).map{d => Counter(0, wrap(d), 1, 1) }
    }
  }
  implicit def linebufferIsMemory[T:Meta:Bits]: Mem[T, LineBuffer] = new LineBufferIsMemory[T]


  /** IR Nodes **/
  case class LineBufferNew[T:Type:Bits](rows: Exp[Index], cols: Exp[Index]) extends Op[LineBuffer[T]] {
    def mirror(f:Tx) = linebuffer_new[T](f(rows),f(cols))
    val mT = typ[T]
  }

  case class LineBufferColSlice[T:Type:Bits](
    linebuffer: Exp[LineBuffer[T]],
    row:        Exp[Index],
    colStart:   Exp[Index],
    length:     Exp[Index]
  )(implicit val vT: Type[VectorN[T]]) extends Op[VectorN[T]] {
    def mirror(f:Tx) = linebuffer_col_slice(f(linebuffer),f(row),f(colStart),f(length))
    override def aliases = Nil
    val mT = typ[T]
  }

  case class LineBufferRowSlice[T:Type:Bits](
    linebuffer: Exp[LineBuffer[T]],
    rowStart:   Exp[Index],
    length:     Exp[Index],
    col:        Exp[Index]
  )(implicit val vT: Type[VectorN[T]]) extends Op[VectorN[T]] {
    def mirror(f:Tx) = linebuffer_row_slice(f(linebuffer),f(rowStart),f(length),f(col))
    override def aliases = Nil
    val mT = typ[T]
  }

  case class LineBufferLoad[T:Type:Bits](
    linebuffer: Exp[LineBuffer[T]],
    row:        Exp[Index],
    col:        Exp[Index],
    en:         Exp[Bool]
  ) extends EnabledOp[T](en) {
    def mirror(f:Tx) = linebuffer_load(f(linebuffer),f(row),f(col),f(en))
    override def aliases = Nil
    val mT = typ[T]
    val bT = bits[T]
  }

  case class LineBufferEnq[T:Type:Bits](
    linebuffer: Exp[LineBuffer[T]],
    data:       Exp[T],
    en:         Exp[Bool]
  ) extends EnabledOp[Void](en) {
    def mirror(f:Tx) = linebuffer_enq(f(linebuffer),f(data),f(en))
    override def aliases = Nil
    val mT = typ[T]
    val bT = bits[T]
  }

  /** Constructors **/

  private[spatial] def linebuffer_new[T:Type:Bits](rows: Exp[Index], cols: Exp[Index])(implicit ctx: SrcCtx) = {
    stageMutable(LineBufferNew[T](rows, cols))(ctx)
  }

  private[spatial] def linebuffer_col_slice[T:Type:Bits](
    linebuffer: Exp[LineBuffer[T]],
    row:        Exp[Index],
    colStart:   Exp[Index],
    length:     Exp[Index]
  )(implicit ctx: SrcCtx) = {
    implicit val vT = length match {
      case Final(c) => VecType[T](c.toInt)
      case _ =>
        error(ctx, "Cannot create parameterized or dynamically sized line buffer slice")
        VecType[T](0)
    }
    stageCold(LineBufferColSlice(linebuffer, row, colStart, length))(ctx)
  }

  private[spatial] def linebuffer_row_slice[T:Type:Bits](
    linebuffer: Exp[LineBuffer[T]],
    rowStart:   Exp[Index],
    length:     Exp[Index],
    col:        Exp[Index]
  )(implicit ctx: SrcCtx) = {
    implicit val vT = length match {
      case Final(c) => VecType[T](c.toInt)
      case _ =>
        error(ctx, "Cannot create parameterized or dynamically sized line buffer slice")
        VecType[T](0)
    }
    stageCold(LineBufferRowSlice(linebuffer, rowStart, length, col))(ctx)
  }

  private[spatial] def linebuffer_load[T:Type:Bits](
    linebuffer: Exp[LineBuffer[T]],
    row:        Exp[Index],
    col:        Exp[Index],
    en:         Exp[Bool]
  )(implicit ctx: SrcCtx) = {
    stageWrite(linebuffer)(LineBufferLoad(linebuffer,row,col,en))(ctx)
  }

  private[spatial] def linebuffer_enq[T:Type:Bits](
    linebuffer: Exp[LineBuffer[T]],
    data:       Exp[T],
    en:         Exp[Bool]
  )(implicit ctx: SrcCtx) = {
    stageWrite(linebuffer)(LineBufferEnq(linebuffer,data,en))(ctx)
  }

}
