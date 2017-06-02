package spatial.transform

import argon.transform.ForwardTransformer
import spatial.SpatialExp
import spatial.analysis.SpatialTraversal

trait SwitchTransformer extends ForwardTransformer with SpatialTraversal {
  val IR: SpatialExp
  import IR._
  override val name = "Switch Transformer"

  var inAccel = false
  var controlStyle: Option[ControlStyle] = None
  var controlLevel: Option[ControlLevel] = None
  var enable: Option[Exp[Bool]] = None

  def withEnable[T](en: Exp[Bool])(blk: => T)(implicit ctx: SrcCtx): T = {
    var prevEnable = enable
    dbgs(s"Enable was $enable")
    enable = Some(enable.map(bool_and(_,en)).getOrElse(en) )
    dbgs(s"Enable is now $enable")
    val result = blk
    enable = prevEnable
    result
  }


  def create_case[T:Type](cond: Exp[Bool], body: Block[T])(implicit ctx: SrcCtx) = () => {
    dbg(c"Creating SwitchCase from cond $cond and body $body")
    val c = withEnable(cond){ op_case(() => f(body) )}
    dbg(c"  ${str(c)}")
    styleOf(c) = controlStyle.getOrElse(InnerPipe)
    levelOf(c) = controlLevel.getOrElse(InnerControl)
    c
  }

  // if (x) { blkA }
  // else if (y) { blkB }
  // else { blkC }
  //
  // IfThenElse(x, blkA, blkX)
  // blkX:
  //   IfThenElse(y, blkB, blkC)

  def extractSwitches[T:Type](
    elseBlock: Block[T],
    precCond:  Exp[Bool],
    selects:   Seq[Exp[Bool]],
    cases:     Seq[() => Exp[T]]
  )(implicit ctx: SrcCtx): (Seq[Exp[Bool]], Seq[() => Exp[T]]) = {
    val contents = blockContents(elseBlock)
    // Only create a flattened switch if the else block contains no enabled operations
    val shouldNest = contents.map(_.rhs).exists{case _:EnabledOp[_] | _:EnabledController => true; case _ => false}

    elseBlock.result match {
      case Op(IfThenElse(cond,thenBlk,elseBlk)) if !shouldNest =>
        // Mirror all primitives within the else block prior to the inner if-then-else
        // This will push these statements outside the switch, but this is expected
        withEnable(precCond){ visitStms(contents.dropRight(1)) }

        val cond2 = f(cond)
        dbg(c"Transforming condition ${str(cond)}")
        dbg(c"is now ${str(cond2)}")

        val caseCond = bool_and(cond2, precCond)
        val elseCond = bool_and(bool_not(cond2), precCond)

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
      val cond2 = f(cond)
      val elseCond = bool_not(cond2)
      val scase = create_case(cond2, thenBlk)
      val (selects, cases) = extractSwitches(elseBlk, elseCond, Seq(cond2), Seq(scase))

      // Switch acts as a one-hot mux if the type being selected is bit-based
      dbg(c"Created case symbols: ")
      val switch = create_switch(selects, cases)
      dbg(c"Created switch: ${str(switch)}")

      styleOf(switch) = ForkSwitch
      levelOf(switch) = controlLevel.getOrElse(InnerControl)

      switch

    case _ => super.transform(lhs, rhs)
  }

  override def mirror(lhs: Seq[Sym[_]], rhs: Def): Seq[Exp[_]] = rhs match {
    case op: EnabledController => transferMetadataIfNew(lhs){ Seq(op.mirrorWithEn(f, enable.toSeq)) }._1
    case op: EnabledOp[_] if enable.isDefined => transferMetadataIfNew(lhs){ Seq(op.mirrorWithEn(f, enable.get)) }._1
    case _ => super.mirror(lhs, rhs)
  }

}
