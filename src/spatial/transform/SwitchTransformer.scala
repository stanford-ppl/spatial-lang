package spatial.transform

import argon.transform.ForwardTransformer
import spatial.SpatialExp
import org.virtualized.SourceContext
import spatial.analysis.SpatialTraversal

trait SwitchTransformer extends ForwardTransformer with SpatialTraversal {
  val IR: SpatialExp
  import IR._
  override val name = "Switch Transformer"

  var inAccel = false
  var controlStyle: Option[ControlStyle] = None
  var controlLevel: Option[ControlLevel] = None

  def extractSwitches[T:Staged](elsep: Block[T], cases: Seq[() => Exp[T]]): Seq[() => Exp[T]] = {
    val contents = blockContents(elsep)
    elsep.result match {
      // If all operations preceeding the IfThenElse are primitives, just move them outside
      case Op(IfThenElse(cond,then2,else2)) if contents.drop(1).forall{stm => isPrimitiveNode(stm.lhs.head) } =>
        visitStms(contents.drop(1))  // Mirror all but the last symbol (will now be outside the switch - this is deliberate)

        val cond2 = f(cond)
        val scase = () => op_case[T](cond2, f(then2))
        extractSwitches[T](else2.asInstanceOf[Block[T]], cases :+ scase)

      case _ =>
        val default = () => op_case[T](bool(true), f(elsep))
        cases :+ default
    }
  }

  override def transform[T:Staged](lhs: Sym[T], rhs: Op[T])(implicit ctx: SrcCtx): Exp[T] = rhs match {
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
      val scase = () => op_case(f(cond), f(elsep))
      val cases = extractSwitches(elsep, Seq(scase))(op.mR)

      var caseSymbols: Seq[Exp[_]] = Nil

      val switch = op_switch{
        val caseSymbols = cases.map{c => c()}
        caseSymbols.last
      }(mtyp(lhs.tp), ctxOrHere(lhs))

      styleOf(switch) = ForkSwitch
      levelOf(switch) = controlLevel.getOrElse(InnerControl)

      dbg(c"Created switch: ${str(switch)}")
      dbg(c"Created case symbols: ")
      caseSymbols.foreach{s =>
        dbg(c"  ${str(s)}")
        styleOf(s) = controlStyle.getOrElse(InnerPipe)
        levelOf(s) = controlLevel.getOrElse(InnerControl)
      }

      switch


    case _ => super.transform(lhs, rhs)
  }


}
