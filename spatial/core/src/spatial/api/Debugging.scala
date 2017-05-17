package spatial.api

import argon.nodes._
import spatial._
import forge._

trait DebuggingApi extends DebuggingExp { this: SpatialApi =>
  @api def println(): MUnit = println("")
  @api def print[A,T<:MetaAny[T]](x: A)(implicit lift: Lift[A,T]): MUnit = MUnit(printIf(bool(true),lift(x).toText.s))
  @api def println[A,T<:MetaAny[T]](x: A)(implicit lift: Lift[A,T]): MUnit = MUnit(printlnIf(bool(true),lift(x).toText.s))

  @api def print(x: java.lang.String): MUnit = print(string2text(x))
  @api def println(x: java.lang.String): MUnit = println(string2text(x))


  @api def assert(cond: Bool, msg: Text): MUnit = MUnit(assertIf(bool(true), cond.s, Some(msg.s)))
  @api def assert(cond: Bool): MUnit = MUnit(assertIf(bool(true), cond.s, None))
}


trait DebuggingExp { this: SpatialExp =>

  /** Debugging IR Nodes **/
  case class PrintIf(en: Exp[Bool], x: Exp[Text]) extends EnabledOp[MUnit](en) { def mirror(f:Tx) = printIf(f(en),f(x)) }
  case class PrintlnIf(en: Exp[Bool], x: Exp[Text]) extends EnabledOp[MUnit](en) {def mirror(f:Tx) = printlnIf(f(en),f(x)) }
  case class AssertIf(en: Exp[Bool], cond: Exp[Bool], msg: Option[Exp[Text]]) extends EnabledOp[MUnit](en) {
    def mirror(f:Tx) = assertIf(f(en),f(cond),f(msg))
  }

  /** Constructors **/
  @internal def printIf(en: Exp[Bool], x: Exp[Text]): Exp[MUnit] = stageSimple(PrintIf(en,x))(ctx)
  @internal def printlnIf(en: Exp[Bool], x: Exp[Text]): Exp[MUnit] = stageSimple(PrintlnIf(en,x))(ctx)
  @internal def assertIf(en: Exp[Bool], cond: Exp[Bool], msg: Option[Exp[Text]]) = stageGlobal(AssertIf(en,cond,msg))(ctx)
}
