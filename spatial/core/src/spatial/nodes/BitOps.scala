package spatial.nodes

import argon.core._
import spatial.aliases._

/** IR Nodes **/
// Type matters for CSE here!
case class BitsAsData[T:Type:Bits](a: Exp[BitVector], mT: Type[T]) extends Op[T] {
  def mirror(f:Tx) = BitOps.bits_as_data[T](f(a))
}

case class DataAsBits[T:Type:Bits](a: Exp[T])(implicit mV: Type[BitVector]) extends Op[BitVector] {
  def mirror(f:Tx) = BitOps.data_as_bits[T](f(a))
  val mT = typ[T]
}
