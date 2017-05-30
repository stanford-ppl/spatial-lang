package spatial.lang

import forge._
import spatial.nodes._

object DebuggingOps {
  /** Constructors **/
  @internal def printIf(en: Exp[MBoolean], x: Exp[MString]): Exp[MUnit] = stageSimple(PrintIf(en,x))(ctx)
  @internal def printlnIf(en: Exp[MBoolean], x: Exp[MString]): Exp[MUnit] = stageSimple(PrintlnIf(en,x))(ctx)
  @internal def assertIf(en: Exp[MBoolean], cond: Exp[MBoolean], msg: Option[Exp[MString]]) = stageGlobal(AssertIf(en,cond,msg))(ctx)
}

trait DebuggingApi {
  import DebuggingOps._

  @api def println(): MUnit = println("")
  @api def print[A,T<:MetaAny[T]](x: A)(implicit lift: Lift[A,T]): MUnit = MUnit(printIf(MBoolean.const(true), lift(x).toText.s))
  @api def println[A,T<:MetaAny[T]](x: A)(implicit lift: Lift[A,T]): MUnit = MUnit(printlnIf(MBoolean.const(true), lift(x).toText.s))

  @api def print(x: CString): MUnit = print(MString(x))
  @api def println(x: CString): MUnit = println(MString(x))

  @api def assert(cond: MBoolean, msg: MString): MUnit = MUnit(assertIf(MBoolean.const(true), cond.s, Some(msg.s)))
  @api def assert(cond: MBoolean): MUnit = MUnit(assertIf(MBoolean.const(true), cond.s, None))
}
