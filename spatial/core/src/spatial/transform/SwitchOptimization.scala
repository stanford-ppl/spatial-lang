package spatial.transform

import argon.core._
import argon.emul._
import argon.transform.ForwardTransformer
import spatial.aliases._
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

    // This version is only better on Plasticine (otherwise should use one-hot mux template on FPGA)
    case op @ Switch(body, selects, _) if spatialConfig.enablePIR =>
      val contents = blockNestedContents(body)
      val (cases, interior) = contents.partition{stm => isSwitchCase(stm.rhs) }
      if (interior.forall{stm => !isControlNode(stm.rhs) && effectsOf(stm.lhs.head).isIdempotent }) {
        val selects2 = f(selects)
        val outputs2 = cases.map(_.lhs.head).map{case Def(SwitchCase(b)) => b.inline.asInstanceOf[Exp[T]] }

        def linearMux[T:Type:Bits](selects: Seq[Exp[Bit]], words: Seq[Exp[T]]): Exp[T] = {
          if (words.length > 1) {
            spatial.lang.Math.math_mux(selects.head, words.head, linearMux(selects.tail, words.tail))
          }
          else words.head
        }

        typ(op.mT) match {
          case mT @ Bits(bT) => linearMux[T](selects2, outputs2)(mT, bT)
          case _ => super.transform(lhs, rhs)
        }
      }
      else super.transform(lhs, rhs)



    case _ => super.transform(lhs, rhs)
  }

}
