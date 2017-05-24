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

  var enableBits: Seq[Exp[Bool]] = Nil

  // Single global valid - should only be used in inner pipes - creates AND tree
  def globalEnable = {
    if (enableBits.isEmpty) bool(true)
    else reduceTree(enableBits){(a,b) => bool_and(a,b) }
  }

  // Sequence of valid bits associated with current unrolling scope
  def globalEnables = if (enableBits.nonEmpty) enableBits else Seq(bool(true))

  def withEnable[T](en: Exp[Bool])(blk: => T): T = {
    val prevEnables = enableBits
    enableBits = en +: enableBits
    val result = blk
    enableBits = prevEnables
    result
  }

  def create_case[T:Type](cond: Exp[Bool], body: => Exp[T])(implicit ctx: SrcCtx) = () => {
    val c = op_case(cond, body)
    dbg(c"  ${str(c)}")
    styleOf(c) = controlStyle.getOrElse(InnerPipe)
    levelOf(c) = controlLevel.getOrElse(InnerControl)
    c
  }

  def mirrorConditionalBlock[T](cond: Exp[Bool], block: Block[T]): Exp[T] = {
    val contents = blockContents(block)
    withEnable(cond){
      visitStms(contents.drop(1))  // Mirror all but the last symbol (will now be outside the switch - this is deliberate)
    }
    visitStm(contents.last) // Condition for this statement is kept in the case statement
    f(block.result)
  }

  def extractSwitches[T:Type](elsep: Block[T], precCond: Exp[Bool], cases: Seq[() => Exp[T]])(implicit ctx: SrcCtx): Seq[() => Exp[T]] = {
    elsep.result match {
      // If all operations preceding the IfThenElse are primitives, just move them outside
      case Op(IfThenElse(cond,then2,else2)) =>
        val cond2 = f(cond)
        val caseCond = bool_and(cond2, precCond)
        val elseCond = bool_and(bool_not(cond2), precCond)

        val thenResult = mirrorConditionalBlock(caseCond, then2)
        val scase = create_case(caseCond, thenResult)

        extractSwitches[T](else2.asInstanceOf[Block[T]], elseCond, cases :+ scase)

      case _ =>
        val elseResult = mirrorConditionalBlock(precCond, elsep)
        val default = create_case(precCond, elseResult)
        cases :+ default
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

    case op @ IfThenElse(cond,thenp,elsep) if inAccel =>
      val cond2 = f(cond)
      val elseCond = bool_not(cond2)
      val thenResult = mirrorConditionalBlock(cond2, thenp)
      val scase = create_case(cond2, thenResult)
      val cases = extractSwitches(elsep, elseCond, Seq(scase))

      dbg(c"Created case symbols: ")
      val switch = create_switch(cases)
      dbg(c"Created switch: ${str(switch)}")

      styleOf(switch) = ForkSwitch
      levelOf(switch) = controlLevel.getOrElse(InnerControl)

      switch

    case _ => super.transform(lhs, rhs)
  }

  override def mirror(lhs: Seq[Sym[_]], rhs: Def): Seq[Exp[_]] = transferMetadataIfNew(lhs){
    lhs.head match {
      case Def(op: EnabledController) => Seq(op.mirrorWithEn(f, globalEnables))
      case Def(op: EnabledOp[_]) => Seq(op.mirrorWithEn(f, globalEnable))
      case _ => rhs.mirrorNode(lhs, f)
    }
  }._1

}
