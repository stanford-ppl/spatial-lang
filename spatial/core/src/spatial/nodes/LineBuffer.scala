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
  @api def load(mem: LineBuffer[T], addr: Seq[Index], en: Bit): T = {
    wrap(LineBuffer.load(mem.s, addr(0).s, addr(1).s, en.s))
  }

  @api def store(mem: LineBuffer[T], data: T, addr: Seq[Index], en: Bit): MUnit = {
    if (addr.length >= 2) {
      // Use the second to last index as the row command
      // TODO: Not sure if this is right in all cases
      wrap(LineBuffer.rotateEnq(mem.s, data.s, en.s, addr(addr.length - 2).s))
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
  buff: Exp[LineBuffer[T]],
  row:  Exp[Index],
  col:  Exp[Index],
  en:   Exp[Bit],
  len:  Int
)(implicit val vT: Type[VectorN[T]]) extends VectorReaderOp[T](buff,addr=Seq(row,col), en=en, ax=1,len=len) {
  def mirror(f:Tx) = LineBuffer.col_slice(f(buff),f(row),f(col),f(en),len)
  override def aliases = Nil
}

case class LineBufferRowSlice[T:Type:Bits](
  buff: Exp[LineBuffer[T]],
  row:  Exp[Index],
  col:  Exp[Index],
  en:   Exp[Bit],
  len:  Int
)(implicit val vT: Type[VectorN[T]]) extends VectorReaderOp[T](buff, addr=Seq(row,col), en=en, ax=0,len=len) {
  def mirror(f:Tx) = LineBuffer.row_slice(f(buff),f(row),f(col),f(en),len)
  override def aliases = Nil
}

case class LineBufferLoad[T:Type:Bits](
  linebuffer: Exp[LineBuffer[T]],
  row:        Exp[Index],
  col:        Exp[Index],
  en:         Exp[Bit]
) extends ReaderOp[T,T](linebuffer, addr=Seq(row,col), en=en) {
  def mirror(f:Tx) = LineBuffer.load(f(linebuffer),f(row),f(col),f(en))
  override def aliases = Nil
}

case class LineBufferEnq[T:Type:Bits](
  linebuffer: Exp[LineBuffer[T]],
  data:       Exp[T],
  en:         Exp[Bit]
) extends EnqueueLikeOp[T](linebuffer,data,en=en) {
  def mirror(f:Tx) = LineBuffer.enq(f(linebuffer),f(data),f(en))
  override def aliases = Nil
}

case class LineBufferRotateEnq[T:Type:Bits](
  linebuffer: Exp[LineBuffer[T]],
  data:       Exp[T],
  en:         Exp[Bit],
  row:        Exp[Index]
) extends EnqueueLikeOp[T](linebuffer,data,en=en) {
  def mirror(f:Tx) = LineBuffer.rotateEnq(f(linebuffer),f(data),f(en),f(row))
  override def aliases = Nil
}

case class BankedLineBufferLoad[T:Type:Bits](
  buff: Exp[LineBuffer[T]],
  bank: Seq[Seq[Exp[Index]]],
  addr: Seq[Exp[Index]],
  ens:  Seq[Exp[Bit]]
)(implicit val vT: Type[VectorN[T]]) extends BankedReaderOp[T](buff,bank,addr,ens) {
  def mirror(f:Tx) = LineBuffer.banked_load(f(buff),bank.map{a=>f(a)},f(addr),f(ens))
  override def aliases = Nil
}

case class BankedLineBufferEnq[T:Type:Bits](
  buff: Exp[LineBuffer[T]],
  data: Seq[Exp[T]],
  ens:  Seq[Exp[Bit]]
) extends BankedEnqueueLikeOp[T](buff,data, ens=ens) {
  def mirror(f:Tx) = LineBuffer.banked_enq(f(buff),f(data),f(ens))
  override def aliases = Nil
}

case class BankedLineBufferRotateEnq[T:Type:Bits](
  buff: Exp[LineBuffer[T]],
  data: Seq[Exp[T]],
  ens:  Seq[Exp[Bit]],
  row:  Exp[Index]
) extends BankedEnqueueLikeOp[T](buff,data, ens=ens) {
  def mirror(f:Tx) = LineBuffer.banked_rotateEnq(f(buff),f(data),f(ens),f(row))
  override def aliases = Nil
}
