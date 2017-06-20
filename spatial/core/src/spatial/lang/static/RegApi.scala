package spatial.lang.static

import argon.core._
import forge._

trait RegApi { this: SpatialApi =>

  @api implicit def readReg[T](reg: Reg[T]): T = reg.value

  implicit class RegNumericOperators[T:Type:Num](reg: Reg[T]) {
    @api def :+=(data: T): MUnit = reg := implicitly[Num[T]].plus(reg.value, data)
    @api def :-=(data: T): MUnit = reg := implicitly[Num[T]].minus(reg.value, data)
    @api def :*=(data: T): MUnit = reg := implicitly[Num[T]].times(reg.value, data)

  }

}
