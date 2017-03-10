package spatial.api

import argon.core.Staging
import spatial.SpatialExp

trait LineBufferApi extends LineBufferExp {
  this: SpatialExp =>
}

trait LineBufferExp extends Staging {
  this: SpatialExp =>

  case class LineBuffer[T:Staged:Bits](s: Exp[LineBuffer[T]]) {
    def apply(row: Index, cols: Range)(implicit ctx: SrcCtx): Vector[T] = {
      // UNSUPPORTED: Strided range apply of line buffer
      cols.step.map(_.s) match {
        case None | Some(Const(1)) =>
        case _ => error(ctx, "Unsupported stride in LineBuffer apply")
      }

      val start  = cols.start.map(_.s).getOrElse(int32(0))
      val length = cols.length
      wrap(linebuffer_region(s, row.s, start, length.s))
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

  /** IR Nodes **/

  case class LineBufferNew[T:Staged:Bits](rows: Exp[Index], cols: Exp[Index]) extends Op[LineBuffer[T]] {
    def mirror(f:Tx) = linebuffer_new[T](f(rows),f(cols))
  }

  case class LineBufferColSlice[T:Staged:Bits](
    linebuffer: Exp[LineBuffer[T]],
    row:        Exp[Index],
    colStart:   Exp[Index],
    colLength:  Exp[Index]
  ) extends Op[Vector[T]] {
    def mirror(f:Tx) = linebuffer_region(f(linebuffer),f(row),f(colStart),f(colLength))
    override def aliases = Nil
  }

  /** Constructors **/

  private[spatial] def linebuffer_new[T:Staged:Bits](rows: Exp[Index], cols: Exp[Index])(implicit ctx: SrcCtx) = {
    stageMutable(LineBufferNew[T](rows, cols))(ctx)
  }

  private[spatial] def linebuffer_region[T:Staged:Bits](
    linebuffer: Exp[LineBuffer[T]],
    row:        Exp[Index],
    colStart:   Exp[Index],
    colLength:  Exp[Index]
  )(implicit ctx: SrcCtx) = {
    stageCold(LineBufferColSlice(linebuffer, row, colStart, colLength))(ctx)
  }

}
