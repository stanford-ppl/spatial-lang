package spatial.nodes

import argon.core._
import spatial.aliases._

/** IR Nodes **/
case class PrintIf(en: Exp[MBoolean], x: Exp[MString]) extends EnabledOp[MUnit](en) {
  def mirror(f:Tx) = DebuggingOps.printIf(f(en),f(x))
}
case class PrintlnIf(en: Exp[MBoolean], x: Exp[MString]) extends EnabledOp[MUnit](en) {
  def mirror(f:Tx) = DebuggingOps.printlnIf(f(en),f(x))
}
case class AssertIf(en: Exp[MBoolean], cond: Exp[MBoolean], msg: Option[Exp[MString]]) extends EnabledOp[MUnit](en) {
  def mirror(f:Tx) = DebuggingOps.assertIf(f(en),f(cond),f(msg))
}
case class BreakpointIf(en: Exp[MBoolean])  extends EnabledOp[MUnit](en) {
  def mirror(f:Tx) = DebuggingOps.breakpointIf(f(en))
}

case class ExitIf(en: Exp[MBoolean]) extends EnabledOp[MUnit](en) {
  def mirror(f:Tx) = DebuggingOps.exitIf(f(en))
}
