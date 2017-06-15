package spatial.transform

import argon.internals._
import argon.transform.ForwardTransformer
import spatial.compiler._
import spatial.nodes._

trait TransferSpecialization extends ForwardTransformer {
  override val name = "Transfer Specialization"

  override def transform[T: Type](lhs: Sym[T], rhs: Op[T])(implicit ctx: SrcCtx): Exp[T] = rhs match {
    case e: DenseTransfer[_,_] => e.expand(f).asInstanceOf[Exp[T]]
    case e: SparseTransfer[_]  => e.expand(f).asInstanceOf[Exp[T]]
    case _ => super.transform(lhs, rhs)
  }

}
