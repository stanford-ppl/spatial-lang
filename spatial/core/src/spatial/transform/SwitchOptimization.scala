package spatial.transform

import argon.core._
import argon.emul._
import argon.transform.ForwardTransformer
import spatial.nodes._
import spatial.utils._

case class SwitchOptimization(var IR: State) extends ForwardTransformer {
  override val name = "Switch Optimization"

  override def transform[T:Type](lhs: Sym[T], rhs: Op[T])(implicit ctx: SrcCtx): Exp[T] = rhs match {

    case Switch(body, selects, cases) if selects.forall(_.isConst) =>
      val trueSelects = selects.zipWithIndex.filter{case (Const(TRUE),_) => true; case (Const(c: Boolean),_) => c; case _ => false }
      if (trueSelects.length > 1) {
        warn(lhs.ctx, "Switch has multiple values enabled at once!")
        warn(lhs.ctx)
        super.transform(lhs, rhs)
      }
      else {
        val cas = cases(trueSelects.head._2)
        val Def(SwitchCase(body)) = cas
        body.inline.asInstanceOf[Exp[T]]
      }

    // TODO: Depends on desired representation of one-hot mux for Plasticine
    /*case Switch(body, selects, cases) =>
      val contents = blockNestedContents(body)
      if (contents.forall{stm => !isControlNode(stm.rhs) && effectsOf(stm.lhs.head).isIdempotent }) {
        val selects2 = f(selects)
        val outputs2 = cases.map{case Def(SwitchCase(b)) => b.inline }

      }
      else super.transform(lhs, rhs)*/



    case _ => super.transform(lhs, rhs)
  }

}
