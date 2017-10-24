package spatial.transform

import argon.core._
import argon.emul._
import argon.nodes._
import argon.util._
import argon.transform.ForwardTransformer
import spatial.aliases.spatialConfig
import spatial.lang._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

case class RewriteTransformer(var IR: State) extends ForwardTransformer{
  override val name = "Rewrite Transformer"

  private case object RemovedParallel { override def toString = "\"Parallel was removed\"" }

  override def transform[T:Type](lhs: Sym[T], rhs: Op[T])(implicit ctx: SrcCtx) = rhs match {
    // Change a write from a mux with the register or some other value to an enabled register write
    case RegWrite(Mirrored(reg), Mirrored(data), Mirrored(en)) if !spatialConfig.enablePIR => data match {
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

    case ParallelPipe(en,func) if spatialConfig.removeParallelNodes =>
      func.inline // TODO: Need to account for enables here?
      constant(typ[T])(RemovedParallel)

    case op @ FixMod(x, Literal(y)) if isPow2(y) =>
      def selectMod[S:BOOL,I:INT,F:INT](x: Exp[FixPt[S,I,F]], y: Double): Exp[FixPt[S,I,F]] = {
        val data = BitOps.dataAsBitVector(wrap(x))
        val range = (log2(y.toDouble)-1).toInt :: 0  //Range.alloc(None, FixPt.int32(log2(y.toDouble) - 1),None,None)
        val selected = data.apply(range)
        implicit val vT = VectorN.typeFromLen[Bit](selected.width)
        BitOps.bitVectorAsData[FixPt[S,I,F]](selected, enWarn = false).s
      }
      selectMod(f(x), y.toDouble)(op.mS,op.mI,op.mF).asInstanceOf[Exp[T]]


    case _ => super.transform(lhs, rhs)
  }
}
