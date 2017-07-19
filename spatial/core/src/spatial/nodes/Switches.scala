package spatial.nodes

import argon.core._
import spatial.aliases._

case class SwitchCase[T:Type](body: Block[T]) extends ControlNode[T] {
  def mirror(f:Tx) = Switches.op_case(f(body))

  override def freqs = cold(body)
  val mT = typ[T]
}

case class Switch[T:Type](body: Block[T], selects: Seq[Exp[Bit]], cases: Seq[Exp[T]]) extends ControlNode[T] {
  def mirror(f:Tx) = {
    val body2 = stageHotBlock{
      val body2 = f(body)
      body2()
    }
    Switches.op_switch(body2, f(selects), f(cases))
  }
  override def inputs = dyns(selects) ++ dyns(cases)
  override def binds = super.binds ++ dyns(cases) // Have to honor bound symbols in blocks...
  override def freqs = hot(body)   // Move everything except cases out of body
  val mT = typ[T]
}