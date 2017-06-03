package spatial.nodes

import forge._
import spatial.compiler._

case class LineBufferType[T:Bits](child: Type[T]) extends Type[LineBuffer[T]] {
  override def wrapped(x: Exp[LineBuffer[T]]) = LineBuffer(x)(child,bits[T])
  override def typeArguments = List(child)
  override def stagedClass = classOf[LineBuffer[T]]
  override def isPrimitive = false
}

class LineBufferIsMemory[T:Type:Bits] extends Mem[T, LineBuffer] {
  @api def load(mem: LineBuffer[T], is: Seq[Index], en: Bit): T = {
    wrap(LineBuffer.load(mem.s, is(0).s, is(1).s, en.s))
  }

  @api def store(mem: LineBuffer[T], is: Seq[Index], data: T, en: Bit): MUnit = {
    wrap(LineBuffer.enq(mem.s, data.s, en.s))
  }
  @api def iterators(mem: LineBuffer[T]): Seq[Counter] = {
    // Hack: Use only columns for dense transfers
    stagedDimsOf(mem.s).drop(1).map{d => Counter(0, wrap(d), 1, 1) }
  }
}


/** IR Nodes **/
case class LineBufferNew[T:Type:Bits](rows: Exp[Index], cols: Exp[Index]) extends Op[LineBuffer[T]] {
  def mirror(f:Tx) = LineBuffer.alloc[T](f(rows),f(cols))
  val mT = typ[T]
}

case class LineBufferColSlice[T:Type:Bits](
  linebuffer: Exp[LineBuffer[T]],
  row:        Exp[Index],
  colStart:   Exp[Index],
  length:     Exp[Index]
)(implicit val vT: Type[VectorN[T]]) extends Op[VectorN[T]] {
  def mirror(f:Tx) = LineBuffer.col_slice(f(linebuffer),f(row),f(colStart),f(length))
  override def aliases = Nil
  val mT = typ[T]
}

case class LineBufferRowSlice[T:Type:Bits](
  linebuffer: Exp[LineBuffer[T]],
  rowStart:   Exp[Index],
  length:     Exp[Index],
  col:        Exp[Index]
)(implicit val vT: Type[VectorN[T]]) extends Op[VectorN[T]] {
  def mirror(f:Tx) = LineBuffer.row_slice(f(linebuffer),f(rowStart),f(length),f(col))
  override def aliases = Nil
  val mT = typ[T]
}

case class LineBufferLoad[T:Type:Bits](
  linebuffer: Exp[LineBuffer[T]],
  row:        Exp[Index],
  col:        Exp[Index],
  en:         Exp[Bit]
) extends EnabledOp[T](en) {
  def mirror(f:Tx) = LineBuffer.load(f(linebuffer),f(row),f(col),f(en))
  override def aliases = Nil
  val mT = typ[T]
  val bT = bits[T]
}

case class LineBufferEnq[T:Type:Bits](
  linebuffer: Exp[LineBuffer[T]],
  data:       Exp[T],
  en:         Exp[Bit]
) extends EnabledOp[MUnit](en) {
  def mirror(f:Tx) = LineBuffer.enq(f(linebuffer),f(data),f(en))
  override def aliases = Nil
  val mT = typ[T]
  val bT = bits[T]
}
