package spatial.nodes

import argon.core._
import spatial.aliases._

object BinaryFileType extends Type[MBinaryFile] {
  override def wrapped(x: Exp[MBinaryFile]) = new MBinaryFile(x)
  override def stagedClass = classOf[MBinaryFile]
  override def isPrimitive = false
}

case class OpenBinaryFile(filename: Exp[MString], write: Boolean) extends Op[MBinaryFile] {
  def mirror(f:Tx) = MBinaryFile.open(f(filename), write)
}

case class CloseBinaryFile(file: Exp[MBinaryFile]) extends Op[MUnit] {
  def mirror(f:Tx) = MBinaryFile.close(f(file))
}

case class ReadBinaryFile[T:Type:Num](file: Exp[MBinaryFile]) extends Op[MArray[T]] {
  def mirror(f:Tx) = MBinaryFile.read_values(f(file))

  val mT = typ[T]
  val bT = bits[T]
}

case class WriteBinaryFile[T:Type:Num](
  file:  Exp[MBinaryFile],
  len:   Exp[Index],
  value: Lambda1[Index, T],
  index: Bound[Index]
) extends Op[MUnit] {
  def mirror(f:Tx) = MBinaryFile.write_values(f(file),f(len),f(value),index)

  val mT = typ[T]
  val bT = bits[T]
}
