package spatial.transform

import argon.transform.ForwardTransformer

import argon.core._
import argon.nodes._
import spatial.lang.LineBuffer
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

case class RotateTransformer(var IR: State) extends ForwardTransformer {
  override val name = "Rotate Transformer"

  def isLoopIterator(x: Exp[_]): Boolean = {
    val y = delayLineTrace(x)
    ctrlOf(y).isDefined
  }

  override def transform[T:Type](lhs: Sym[T], rhs: Op[T])(implicit ctx: SrcCtx): Exp[T] = rhs match {
    case op@LineBufferRotateEnq(lb,row,data,en) if !isLoopIterator(row) =>
      dbgs(s"${str(lhs)}")
      dbgs(s"Row $row is not a valid loop iterator - transforming to regular Enq")
      val lhs2 = LineBuffer.enq(f(lb),f(data),f(en))(op.mT,op.bT,ctx,state)
      transferMetadata(lhs, lhs2)
      lhs2.asInstanceOf[Exp[T]]

    case op@ParLineBufferRotateEnq(lb,row,data,ens) if !isLoopIterator(row) =>
      dbgs(s"${str(lhs)}")
      dbgs(s"Row $row is not a valid loop iterator - transforming to regular Enq")
      val lhs2 = LineBuffer.par_enq(f(lb),f(data),f(ens))(op.mT,op.bT,ctx,state)
      transferMetadata(lhs, lhs2)
      lhs2.asInstanceOf[Exp[T]]

    case _ => super.transform(lhs, rhs)
  }

}
