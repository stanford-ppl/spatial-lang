package spatial.metadata

import argon.core._

case class LocalMemories(mems: Seq[Exp[_]]) extends Globaldata[LocalMemories] {
  def mirror(f:Tx) = LocalMemories(f.tx(mems))
  override def staleOnTransform: Boolean = true
}
