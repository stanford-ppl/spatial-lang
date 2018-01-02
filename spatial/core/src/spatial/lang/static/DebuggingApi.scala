package spatial.lang.static

import argon.core._
import forge._
import spatial.lang.DebuggingOps._

trait DebuggingApi { this: SpatialApi =>
  @api def println(): MUnit = println("")
  @api def print[A,T<:MetaAny[T]](x: A)(implicit lift: Lift[A,T]): MUnit = wrap(printIf(Bit.const(true), lift(x).toText.s))
  @api def println[A,T<:MetaAny[T]](x: A)(implicit lift: Lift[A,T]): MUnit = wrap(printlnIf(Bit.const(true), lift(x).toText.s))

  @api def print(x: CString): MUnit = print(lift(x))
  @api def println(x: CString): MUnit = println(lift(x))

  @api def assert(cond: Bit, msg: MString): MUnit = wrap(assertIf(Bit.const(true), cond.s, Some(msg.s)))
  @api def assert(cond: Bit): MUnit = wrap(assertIf(Bit.const(true), cond.s, None))

  @api def breakpoint() = wrap(breakpointIf(Bit.const(true)))
  @api def exit() = wrap(exitIf(Bit.const(true))) 

  @api def sleep(cycles: Index): MUnit = Foreach(cycles by 1) {_ => } 
} 