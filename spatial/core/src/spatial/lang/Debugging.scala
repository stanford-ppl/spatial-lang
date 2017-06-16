package spatial.lang

import argon.core._
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
  @api def print[A,T<:MetaAny[T]](x: A)(implicit lift: Lift[A,T]): MUnit = MUnit(printIf(Bit.const(true), lift(x).toText.s))
  @api def println[A,T<:MetaAny[T]](x: A)(implicit lift: Lift[A,T]): MUnit = MUnit(printlnIf(Bit.const(true), lift(x).toText.s))

  @api def print(x: CString): MUnit = print(MString(x))
  @api def println(x: CString): MUnit = println(MString(x))

  @api def assert(cond: Bit, msg: MString): MUnit = MUnit(assertIf(Bit.const(true), cond.s, Some(msg.s)))
  @api def assert(cond: Bit): MUnit = MUnit(assertIf(Bit.const(true), cond.s, None))
}
