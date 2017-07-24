package spatial.lang

import argon.core._
import forge._
import spatial.nodes._

object DebuggingOps {
  /** Constructors **/
  @internal def exitIf(en: Exp[MBoolean]): Exp[MUnit] = stageSimple(ExitIf(en))(ctx)
  @internal def breakpointIf(en: Exp[MBoolean]): Exp[MUnit] = stageSimple(BreakpointIf(en))(ctx)
  @internal def printIf(en: Exp[MBoolean], x: Exp[MString]): Exp[MUnit] = stageSimple(PrintIf(en,x))(ctx)
  @internal def printlnIf(en: Exp[MBoolean], x: Exp[MString]): Exp[MUnit] = stageSimple(PrintlnIf(en,x))(ctx)
  @internal def assertIf(en: Exp[MBoolean], cond: Exp[MBoolean], msg: Option[Exp[MString]]) = stageGlobal(AssertIf(en,cond,msg))(ctx)
}


