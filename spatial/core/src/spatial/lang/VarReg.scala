package spatial.lang

import argon.core._
import forge._
import spatial.nodes._

case class VarReg[T:Type](s: Exp[VarReg[T]]) extends Template[VarReg[T]]

object VarReg {
  implicit def varRegType[T:Type]: Type[VarReg[T]] = VarRegType(typ[T])

  @internal def alloc[T:Type](tp: Type[T]): Sym[VarReg[T]] = stageMutable( VarRegNew[T](tp) )(ctx)
  @internal def read[T:Type](reg: Exp[VarReg[T]]): Sym[T] = stageUnique( VarRegRead(reg) )(ctx)
  @internal def write[T:Type](reg: Exp[VarReg[T]], data: Exp[T], en: Exp[Bit]) = {
    stageWrite(reg)( VarRegWrite(reg, data, en) )(ctx)
  }
}
