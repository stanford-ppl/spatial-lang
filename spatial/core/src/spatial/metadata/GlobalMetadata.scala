package spatial.metadata

import argon.core._

case class LocalMemories(mems: List[Exp[_]]) extends Globaldata[LocalMemories] {
  def mirror(f:Tx) = LocalMemories(f(mems))
  override def staleOnTransform: Boolean = true
}
