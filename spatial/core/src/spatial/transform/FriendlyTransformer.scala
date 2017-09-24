package spatial.transform

import argon.core._
import argon.transform.ForwardTransformer
import spatial.lang._
import spatial.nodes._
import spatial.utils._

/**
  * A friendly transformer for a friendlier world.
  * Automatically fixes common, simple syntax mistakes and gives warnings if appropriate.
  */
case class FriendlyTransformer(var IR: State) extends ForwardTransformer {
  override val name = "Friendly Transformer"
  private var inHwScope: Boolean = false

  override def transform[T: Type](lhs: Sym[T], rhs: Op[T])(implicit ctx: SrcCtx): Exp[T] = rhs match {
    case Hwblock(_,_) =>
      inHwScope = true
      val lhs2 = super.transform(lhs, rhs)
      inHwScope = false
      lhs2

    // Outside Accel: reg.value -> getArg(reg)
    case op @ RegRead(Mirrored(reg)) if !inHwScope && isOffChipMemory(reg) =>
      // Use register reads for dimensions of off-chip memories
      if (lhs.dependents.exists{mem => isOffChipMemory(mem) }) {
        super.transform(lhs, rhs)
      }
      else HostTransferOps.get_arg(reg)(op.mT,op.bT,ctx,state)

    // Outside Accel: reg := data -> setArg(reg, data)
    case op @ RegWrite(Mirrored(reg),Mirrored(data),_) if !inHwScope && isOffChipMemory(reg) =>
      HostTransferOps.set_arg(reg,data)(op.mT,op.bT,ctx,state).asInstanceOf[Exp[T]]

    // Inside Accel: getArg(reg) -> reg.value
    case op @ GetArg(Mirrored(reg)) if inHwScope && isOffChipMemory(reg) =>
      Reg.read(reg)(op.mT,op.bT,ctx,state)

    case op @ SetArg(Mirrored(reg),Mirrored(data)) if inHwScope && isOffChipMemory(reg) =>
      Reg.write(reg, data, Bit.const(true))(op.mT,op.bT,ctx,state).asInstanceOf[Exp[T]]

    case _ => super.transform(lhs, rhs)
  }
}
