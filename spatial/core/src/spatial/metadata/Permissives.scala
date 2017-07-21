package spatial.metadata

import argon.core._
import forge._

case class ExtraBufferable(flag: Boolean) extends Metadata[ExtraBufferable] { def mirror(f:Tx) = this }

@data object isExtraBufferable {
  def apply(x: Exp[_]): Boolean = metadata[ExtraBufferable](x).exists(_.flag)
  def enableOn(x: Exp[_]): Unit = metadata.add(x, ExtraBufferable(true))
}