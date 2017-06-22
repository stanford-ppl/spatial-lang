package spatial.nodes

import argon.core._
import spatial.aliases._

case class DelayLine[T:Type:Bits](size: Int, data: Exp[T]) extends Op[T] {
  def mirror(f:Tx) = Delays.delayLine[T](size, f(data))
  val mT = typ[T]
  val bT = bits[T]
}
