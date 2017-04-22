package spatial.api

import spatial._
import forge._

trait DebuggingApi extends DebuggingExp { this: SpatialApi =>


  @api def println(): Void = println("")
  @api def print[A,T<:MetaAny[T]](x: A)(implicit lift: Lift[A,T]): Void = Void(printIf(bool(true),lift(x).toText.s))
  @api def println[A,T<:MetaAny[T]](x: A)(implicit lift: Lift[A,T]): Void = Void(printlnIf(bool(true),lift(x).toText.s))

  @api def print(x: java.lang.String): Void = print(string2text(x))
  @api def println(x: java.lang.String): Void = println(string2text(x))


  @api def assert(cond: Bool, msg: Text): Void = Void(assertIf(bool(true), cond.s, Some(msg.s)))
  @api def assert(cond: Bool): Void = Void(assertIf(bool(true), cond.s, None))
}


trait DebuggingExp { this: SpatialExp =>

  /** Debugging IR Nodes **/
  case class PrintIf(en: Exp[Bool], x: Exp[Text]) extends EnabledOp[Void](en) { def mirror(f:Tx) = printIf(f(en),f(x)) }
  case class PrintlnIf(en: Exp[Bool], x: Exp[Text]) extends EnabledOp[Void](en) {def mirror(f:Tx) = printlnIf(f(en),f(x)) }
  case class AssertIf(en: Exp[Bool], cond: Exp[Bool], msg: Option[Exp[Text]]) extends EnabledOp[Void](en) {
    def mirror(f:Tx) = assertIf(f(en),f(cond),f(msg))
  }

  /** Constructors **/
  @internal def printIf(en: Exp[Bool], x: Exp[Text]): Exp[Void] = stageSimple(PrintIf(en,x))(ctx)
  @internal def printlnIf(en: Exp[Bool], x: Exp[Text]): Exp[Void] = stageSimple(PrintlnIf(en,x))(ctx)
  @internal def assertIf(en: Exp[Bool], cond: Exp[Bool], msg: Option[Exp[Text]]) = stageGlobal(AssertIf(en,cond,msg))(ctx)
}
