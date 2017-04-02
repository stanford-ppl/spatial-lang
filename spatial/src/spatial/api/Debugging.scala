package spatial.api

import argon.core.Staging
import spatial.SpatialExp
import forge._

trait DebuggingApi extends DebuggingExp {
  this: SpatialExp =>

  @api def println(): Void = println("")
  @api def print[T:Type](x: T): Void = Void(printIf(bool(true),textify(x).s))
  @api def println[T:Type](x: T): Void = Void(printlnIf(bool(true),textify(x).s))

  @api def print(x: String): Void = print(string2text(x))
  @api def println(x: String): Void = println(string2text(x))

  @api def assert(cond: Bool, msg: Text): Void = Void(assertIf(bool(true), cond.s, Some(msg.s)))
  @api def assert(cond: Bool): Void = Void(assertIf(bool(true), cond.s, None))
}


trait DebuggingExp extends Staging {
  this: SpatialExp =>

  /** Debugging IR Nodes **/
  case class PrintIf(en: Exp[Bool], x: Exp[Text]) extends EnabledOp[Void](en) { def mirror(f:Tx) = printIf(f(en),f(x)) }
  case class PrintlnIf(en: Exp[Bool], x: Exp[Text]) extends EnabledOp[Void](en) {def mirror(f:Tx) = printlnIf(f(en),f(x)) }
  case class AssertIf(en: Exp[Bool], cond: Exp[Bool], msg: Option[Exp[Text]]) extends EnabledOp[Void](en) {
    def mirror(f:Tx) = assertIf(f(en),f(cond),f(msg))
  }

  /** Constructors **/
  def printIf(en: Exp[Bool], x: Exp[Text])(implicit ctx: SrcCtx): Exp[Void] = stageSimple(PrintIf(en,x))(ctx)
  def printlnIf(en: Exp[Bool], x: Exp[Text])(implicit ctx: SrcCtx): Exp[Void] = stageSimple(PrintlnIf(en,x))(ctx)
  def assertIf(en: Exp[Bool], cond: Exp[Bool], msg: Option[Exp[Text]])(implicit ctx: SrcCtx) = stageGlobal(AssertIf(en,cond,msg))(ctx)
}
