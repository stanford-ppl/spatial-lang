package spatial.metadata

import argon.core._
import forge._

case class LocalMemories(mems: Seq[Exp[_]]) extends Globaldata[LocalMemories] {
  def mirror(f:Tx) = LocalMemories(f.tx(mems))
  override def staleOnTransform: Boolean = true
}

@data object localMems {
  def grab: Seq[Exp[_]] = globaldata[LocalMemories].mems
  def get: Option[Seq[Exp[_]]] = globaldata.get[LocalMemories].map(_.mems)
}