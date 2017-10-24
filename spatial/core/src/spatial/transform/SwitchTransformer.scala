package spatial.transform

import argon.core._
import argon.nodes._
import argon.transform.ForwardTransformer
import spatial.analysis.SpatialTraversal
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

case class SwitchTransformer(var IR: State) extends ForwardTransformer with SpatialTraversal {
  override val name = "Switch Transformer"

  var inAccel = false
  var controlStyle: Option[ControlStyle] = None
  var controlLevel: Option[ControlLevel] = None
  var enable: Option[Exp[Bit]] = None

  def withEnable[T](en: Exp[Bit])(blk: => T)(implicit ctx: SrcCtx): T = {
    var prevEnable = enable
    dbgs(s"Enable was $enable")
    enable = Some(enable.map(Bit.and(_,en)).getOrElse(en) )
    dbgs(s"Enable is now $enable")
    val result = blk
    enable = prevEnable
    result
  }


  def create_case[T:Type](cond: Exp[Bit], body: Block[T])(implicit ctx: SrcCtx) = () => {
    dbg(c"Creating SwitchCase from cond $cond and body $body")
    val c = withEnable(cond){ Switches.op_case(f(body) )}
    dbg(c"  ${str(c)}")
    styleOf(c) = controlStyle.getOrElse(InnerPipe)
    levelOf(c) = controlLevel.getOrElse(InnerControl)
    c
  }
  /**
    * Note: A sequence of if-then-else statements will be nested in the IR as follows:
    *
    * if (x) { blkA }
    * else if (y) { blkB }
    * else if (z) { blkC }
    * else { blkD }
    *
    * // Logic to compute x
    * IfThenElse(x, blkA, blkX)
    * blkX:
    *   // Logic to compute y
    *   IfThenElse(y, blkB, blkY)
    *   blkY:
    *     // Logic to compute z
    *     IfThenElse(z, blkC, blkD)
    *
    * This means, except in trivial cases, IfThenElses will often not be perfectly nested, as the block containing
    * an IfThenElse will usually also contain the corresponding condition logic
    */
  def extractSwitches[T:Type](
    elseBlock: Block[T],
    precCond:  Exp[Bit],
    selects:   Seq[Exp[Bit]],
    cases:     Seq[() => Exp[T]]
  )(implicit ctx: SrcCtx): (Seq[Exp[Bit]], Seq[() => Exp[T]]) = {
    val contents = blockContents(elseBlock)
    // Only create a flattened switch if the else block contains no enabled operations
    val shouldNest = contents.map(_.rhs).exists{case _:EnabledOp[_] | _:EnabledControlNode => true; case _ => false}

    elseBlock.result match {
      case Op(IfThenElse(cond,thenBlk,elseBlk)) if !shouldNest =>
        // Mirror all primitives within the else block prior to the inner if-then-else
        // This will push these statements outside the switch, but this is expected
        withEnable(precCond){ visitStms(contents.dropRight(1)) }

        val cond2 = f(cond)
        dbg(c"Transforming condition ${str(cond)}")
        dbg(c"is now ${str(cond2)}")

        val caseCond = Bit.and(cond2, precCond)
        val elseCond = Bit.and(Bit.not(cond2), precCond)

        val scase = create_case(caseCond, thenBlk)

        extractSwitches[T](elseBlk.asInstanceOf[Block[T]], elseCond, selects :+ caseCond, cases :+ scase)

      case _ =>
        val default = create_case(precCond, elseBlock)
        (selects :+ precCond, cases :+ default)
    }
  }

  override def transform[T:Type](lhs: Sym[T], rhs: Op[T])(implicit ctx: SrcCtx): Exp[T] = rhs match {
    case Hwblock(func,_) =>
      inAccel = true
      controlStyle = styleOf.get(lhs)
      controlLevel = levelOf.get(lhs)
      val lhs2 = super.transform(lhs, rhs)
      inAccel = false
      lhs2

    case _ if isControlNode(lhs) =>
      val prevStyle = controlStyle
      val prevLevel = controlLevel
      controlStyle = styleOf.get(lhs)
      controlLevel = levelOf.get(lhs)
      val lhs2 = super.transform(lhs,rhs)
      controlStyle = prevStyle
      controlLevel = prevLevel
      lhs2

    case op @ IfThenElse(cond,thenBlk,elseBlk) if inAccel =>
      val contents = blockNestedContents(thenBlk) ++ blockNestedContents(elseBlk)
      val level = if (contents.exists(stm => isControlNode(stm.rhs))) OuterControl else InnerControl
      val prevLevel = controlLevel
      controlLevel = Some(level)

      val cond2 = f(cond)
      val elseCond = Bit.not(cond2)
      val scase = create_case(cond2, thenBlk)
      val (selects, cases) = extractSwitches(elseBlk, elseCond, Seq(cond2), Seq(scase))

      // Switch acts as a one-hot mux if the type being selected is bit-based
      dbg(c"Created case symbols: ")
      val switch = Switches.create_switch(selects, cases)
      dbg(c"Created switch: ${str(switch)}")

      styleOf(switch) = ForkSwitch
      levelOf(switch) = if (spatialConfig.enablePIR) OuterControl else level

      controlLevel = prevLevel

      switch

    case _ => super.transform(lhs, rhs)
  }

  override def mirror(lhs: Seq[Sym[_]], rhs: Def): Seq[Exp[_]] = rhs match {
    case op: EnabledControlNode => transferMetadataIfNew(lhs){ Seq(op.mirrorAndEnable(f, enable.toSeq)) }._1
    case op: EnabledPrimitive[_] if enable.isDefined => transferMetadataIfNew(lhs){ Seq(op.mirrorAndEnable(this, () => enable.get)) }._1
    case _ => super.mirror(lhs, rhs)
  }

}
