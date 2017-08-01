package spatial.transform

import argon.core._
import argon.emul._
import argon.transform.ForwardTransformer
import spatial.SpatialConfig
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

case class RewriteTransformer(var IR: State) extends ForwardTransformer{
  override val name = "Rewrite Transformer"

  object Mirrored { def unapply[T](x: Exp[T]): Option[Exp[T]] = Some(f(x)) }

  override def transform[T:Type](lhs: Sym[T], rhs: Op[T])(implicit ctx: SrcCtx) = rhs match {
    // Change a write from a mux with the register or some other value to an enabled register write
    case RegWrite(Mirrored(reg), Mirrored(data), Mirrored(en)) if !SpatialConfig.enablePIR => data match {
      case Op( Mux(sel, Op(e@RegRead(`reg`)), b) ) =>
        val lhs2 = Reg.write(reg, b, Bit.and(en, Bit.not(sel)))(e.mT,e.bT,ctx,state)
        dbg(c"Rewrote ${str(lhs)}")
        dbg(c"  to ${str(lhs2)}")
        transferMetadata(lhs, lhs2)
        lhs2.asInstanceOf[Exp[T]]

      case Op( Mux(sel, a, Op(e @ RegRead(`reg`))) ) =>
        val lhs2 = Reg.write(reg, a, Bit.and(en, sel))(e.mT,e.bT,ctx,state)
        dbg(c"Rewrote ${str(lhs)}")
        dbg(c"  to ${str(lhs2)}")
        transferMetadata(lhs, lhs2)
        lhs2.asInstanceOf[Exp[T]]

      case _ => super.transform(lhs, rhs)
    }

    case Switch(body, selects, cases) =>
      if (selects.forall(_.isConst)) {
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
      }
      else super.transform(lhs, rhs)


    case _ => super.transform(lhs, rhs)
  }
}
