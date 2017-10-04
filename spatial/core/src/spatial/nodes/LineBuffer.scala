package spatial.nodes

import argon.core._
import forge._
import spatial.aliases._
import spatial.metadata._
import spatial.utils._

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
    if (is.length >= 2) {
      // Use the second to last index as the row command
      // TODO: Not sure if this is right in all cases
      wrap(LineBuffer.rotateEnq(mem.s, is(is.length - 2).s, data.s, en.s))
    }
    else {
      wrap(LineBuffer.enq(mem.s, data.s, en.s))
    }
  }
  @api def iterators(mem: LineBuffer[T]): Seq[Counter] = {
    // Hack: Use only columns for dense transfers
    stagedDimsOf(mem.s).drop(1).map{d => Counter(0, wrap(d), 1, 1) }
  }
  def par(mem: LineBuffer[T]) = None
}


/** IR Nodes **/
/**
  * Allocation of a LineBuffer
  * @param rows The number of "active" rows at any given time - should be statically known
  * @param cols The number of columns (typically the image width) - should be statically known
  * @param stride The number of lines to skip between reads (1 by default) - should be statically known
  * @tparam T
  */
case class LineBufferNew[T:Type:Bits](rows: Exp[Index], cols: Exp[Index], stride: Exp[Index]) extends Alloc[LineBuffer[T]] {
  def mirror(f:Tx) = LineBuffer.alloc[T](f(rows),f(cols),f(stride))
  val mT = typ[T]
  val bT = bits[T]
}

case class LineBufferColSlice[T:Type:Bits](
  linebuffer: Exp[LineBuffer[T]],
  row:        Exp[Index],
  colStart:   Exp[Index],
  length:     Exp[Index]
)(implicit val vT: Type[VectorN[T]]) extends LocalReaderOp[VectorN[T]](linebuffer,addr=Seq(row,colStart)) {
  def mirror(f:Tx) = LineBuffer.col_slice(f(linebuffer),f(row),f(colStart),f(length))
  override def aliases = Nil
  val mT = typ[T]

  override def accessWidth: Int = length match {case Exact(len) => len.toInt}
}

case class LineBufferRowSlice[T:Type:Bits](
  linebuffer: Exp[LineBuffer[T]],
  rowStart:   Exp[Index],
  length:     Exp[Index],
  col:        Exp[Index]
)(implicit val vT: Type[VectorN[T]]) extends LocalReaderOp[VectorN[T]](linebuffer, addr=Seq(rowStart,col)) {
  def mirror(f:Tx) = LineBuffer.row_slice(f(linebuffer),f(rowStart),f(length),f(col))
  override def aliases = Nil
  val mT = typ[T]

  override def accessWidth: Int = length match {case Exact(len) => len.toInt}
}

case class LineBufferLoad[T:Type:Bits](
  linebuffer: Exp[LineBuffer[T]],
  row:        Exp[Index],
  col:        Exp[Index],
  en:         Exp[Bit]
) extends LocalReaderOp[T](linebuffer, addr=Seq(row,col), en=en) {
  def mirror(f:Tx) = LineBuffer.load(f(linebuffer),f(row),f(col),f(en))
  override def aliases = Nil
  val mT = typ[T]
  val bT = bits[T]
}

case class LineBufferEnq[T:Type:Bits](
  linebuffer: Exp[LineBuffer[T]],
  data:       Exp[T],
  en:         Exp[Bit]
) extends LocalWriterOp(linebuffer,value=data,en=en) {
  def mirror(f:Tx) = LineBuffer.enq(f(linebuffer),f(data),f(en))
  override def aliases = Nil
  val mT = typ[T]
  val bT = bits[T]
}

case class LineBufferRotateEnq[T:Type:Bits](
  linebuffer: Exp[LineBuffer[T]],
  row:        Exp[Index],
  data:       Exp[T],
  en:         Exp[Bit]
) extends LocalWriterOp(linebuffer,value=data,en=en) {
  def mirror(f:Tx) = LineBuffer.rotateEnq(f(linebuffer),f(row),f(data),f(en))
  override def aliases = Nil
  val mT = typ[T]
  val bT = bits[T]
}

case class ParLineBufferLoad[T:Type:Bits](
  linebuffer: Exp[LineBuffer[T]],
  rows:       Seq[Exp[Index]],
  cols:       Seq[Exp[Index]],
  ens:        Seq[Exp[Bit]]
)(implicit val vT: Type[VectorN[T]]) extends ParLocalReaderOp[VectorN[T]](linebuffer,rows.zip(cols).map{case (r,c) => Seq(r,c)}, ens=ens) {
  def mirror(f:Tx) = LineBuffer.par_load(f(linebuffer),f(rows),f(cols),f(ens))
  override def aliases = Nil
  val mT = typ[T]
}

case class ParLineBufferEnq[T:Type:Bits](
  linebuffer: Exp[LineBuffer[T]],
  data:       Seq[Exp[T]],
  ens:        Seq[Exp[Bit]]
) extends ParLocalWriterOp(linebuffer,values=data,ens=ens) {
  def mirror(f:Tx) = LineBuffer.par_enq(f(linebuffer),f(data),f(ens))
  override def aliases = Nil
  val mT = typ[T]
}

case class ParLineBufferRotateEnq[T:Type:Bits](
  linebuffer: Exp[LineBuffer[T]],
  row:        Exp[Index],
  data:       Seq[Exp[T]],
  ens:        Seq[Exp[Bit]]
) extends ParLocalWriterOp(linebuffer,values=data,ens=ens) {
  def mirror(f:Tx) = LineBuffer.par_rotateEnq(f(linebuffer),f(row),f(data),f(ens))
  override def aliases = Nil
  val mT = typ[T]
  val bT = bits[T]
}