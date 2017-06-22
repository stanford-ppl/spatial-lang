package spatial.nodes

import argon.core._
import spatial.aliases._

/** IR Nodes **/
case class RangeForeach(
  start: Exp[Index],
  end:   Exp[Index],
  step:  Exp[Index],
  func:  Lambda1[Index,MUnit],
  i:     Bound[Index]
) extends Op[MUnit] {
  def mirror(f:Tx) = MRange.foreach(f(start),f(end),f(step),f(func),i)
  override def inputs = dyns(start,end,step) ++ dyns(func)
  override def freqs  = normal(start) ++ normal(end) ++ normal(step) ++ hot(func)
  override def binds  = i +: super.binds
}

/*case class RangeReduce[T](
  start:  Exp[Index],
  end:    Exp[Index],
  step:   Exp[Index],
  func:   Block[T],
  reduce: Block[T],
  rV:     (Bound[Index],Bound[Index]),
  i:      Bound[Index]
)*/