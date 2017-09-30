package spatial.transform

import argon.core._
import argon.transform.ForwardTransformer
import spatial.lang._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

/**
  * A friendly transformer for a friendlier world.
  * Automatically fixes common, simple syntax mistakes and gives warnings if appropriate.
  */
case class FriendlyTransformer(var IR: State) extends ForwardTransformer {
  override val name = "Friendly Transformer"
  private var inHwScope: Boolean = false

  private var dimMapping: Map[Exp[Index],Exp[Index]] = Map.empty
  private var oldDimMapping: Map[Exp[Index], Exp[Index]] = Map.empty

  private var mostRecentWrite: Map[Exp[_], Exp[_]] = Map.empty

  override def transform[T: Type](lhs: Sym[T], rhs: Op[T])(implicit ctx: SrcCtx): Exp[T] = rhs match {
    case DRAMNew(dims,zero) =>
      val dimsMirrored = dims.map{f(_)}
      dimMapping ++= dimsMirrored.distinct.map{
        case d @ Def(RegRead(reg)) if isArgIn(reg) => d -> d
        case d if dimMapping.contains(d) => d -> dimMapping(d)
        case d =>
          // TODO: Why doesn't this work?
          val isConst = d.isConst || d.isParam //.dependsOnlyOnType{case Exact(_) => true}
          if (!isConst) {
            val argIn = ArgIn[Index]
            setArg(argIn, wrap(d))
            d -> argIn.value.s
          }
          else d -> d
      }

      val dims2 = dimsMirrored.zipWithIndex.map{case (d,i) =>
        val d2 = dimMapping(d)
        if (d != d2) {
          warn(lhs.ctx, u"Inserted an implicit ArgIn for DRAM $lhs dimension $i")
          warn(lhs.ctx)
        }
        d2
      }
      oldDimMapping ++= dims.zip(dims2)

      withSubstScope(dims.zip(dims2):_*){ super.transform(lhs,rhs) }

    case Hwblock(_,_) =>
      inHwScope = true
      // Replace dimensions with inserted ArgIn reads
      val lhs2 = withSubstScope(oldDimMapping.toSeq:_*){ super.transform(lhs, rhs) }
      inHwScope = false
      lhs2

    case op @ RegRead(Mirrored(reg)) if !inHwScope && isArgIn(reg) =>
      mostRecentWrite.get(reg) match {
        case Some(Def(SetArg(_,data)))  =>
          // Don't get rid of reads being used for DRAM allocations
          if (lhs.dependents.exists{case Def(DRAMNew(_,_)) => true; case _ => false})
            super.transform(lhs,rhs)
          else
            data.asInstanceOf[Exp[T]]

        case None =>
          warn(lhs.ctx, u"ArgIn $reg was used before being set. This will always result in 0s at runtime.")
          warn(lhs.ctx)
          zero[T](op.bT,ctx,state).s

        case _ =>
          super.transform(lhs, rhs)
      }

    // Outside Accel: reg.value -> getArg(reg)
    case op @ RegRead(Mirrored(reg)) if !inHwScope && (isArgOut(reg) || isHostIO(reg)) =>
      // Use register reads for dimensions of off-chip memories
      HostTransferOps.get_arg(reg)(op.mT,op.bT,ctx,state)

    // Inside Accel: getArg(reg) -> reg.value
    case op @ GetArg(Mirrored(reg)) if inHwScope && (isArgOut(reg) || isHostIO(reg)) =>
      Reg.read(reg)(op.mT,op.bT,ctx,state)

    case op @ RegWrite(Mirrored(reg),Mirrored(data),_) =>
      // Outside Accel: reg := data -> setArg(reg, data)
      val lhs2 = if (!inHwScope && isOffChipMemory(reg)) {
        HostTransferOps.set_arg(reg, data)(op.mT, op.bT, ctx, state).asInstanceOf[Exp[T]]
      }
      else super.transform(lhs, rhs)

      mostRecentWrite += reg -> lhs2
      lhs2


    case op @ SetArg(Mirrored(reg),Mirrored(data)) =>
      val lhs2 = if (inHwScope && (isArgOut(reg) || isHostIO(reg))) {
        Reg.write(reg, data, Bit.const(true))(op.mT, op.bT, ctx, state).asInstanceOf[Exp[T]]
      }
      else super.transform(lhs, rhs)

      mostRecentWrite += reg -> lhs2
      lhs2

    case _ => super.transform(lhs, rhs)
  }
}
