package spatial.nodes

import argon.core._
import forge._
import spatial.aliases._

case class MemoryDimension[C](mem: Exp[C], i: Int) extends Op[Index] {
  def mirror(f:Tx) = memAliasDim(f(mem),i)
}

object memAliasDim {
  @internal def apply[C](mem: Exp[C], i: Int): Exp[Index] = stage(MemoryDimension(mem,i))(ctx)
}
