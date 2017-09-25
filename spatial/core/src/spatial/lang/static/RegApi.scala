package spatial.lang.static

import argon.core._
import forge._

trait LowPriorityRegImplicits { this: SpatialApi =>

  implicit class RegArith[T:Arith](reg: Reg[T]) {
    @api def +[A](rhs: A)(implicit lift: Lift[A, T]): T = reg.value + lift(rhs)
    @api def -[A](rhs: A)(implicit lift: Lift[A, T]): T = reg.value - lift(rhs)
    @api def *[A](rhs: A)(implicit lift: Lift[A, T]): T = reg.value * lift(rhs)
    @api def /[A](rhs: A)(implicit lift: Lift[A, T]): T = reg.value / lift(rhs)

    @api def +(rhs: Reg[T]): T = reg.value + rhs.value
    @api def -(rhs: Reg[T]): T = reg.value - rhs.value
    @api def *(rhs: Reg[T]): T = reg.value * rhs.value
    @api def /(rhs: Reg[T]): T = reg.value / rhs.value
  }

  implicit class RegOrder[T:Order](reg: Reg[T]) {
    @api def <[A](rhs: A)(implicit lift: Lift[A,T]): MBoolean = reg.value < lift(rhs)
    @api def <=[A](rhs: A)(implicit lift: Lift[A,T]): MBoolean = reg.value <= lift(rhs)
    @api def >[A](rhs: A)(implicit lift: Lift[A,T]): MBoolean = reg.value > lift(rhs)
    @api def >=[A](rhs: A)(implicit lift: Lift[A,T]): MBoolean = reg.value >= lift(rhs)

    @api def <(rhs: Reg[T]): MBoolean = reg.value < rhs.value
    @api def <=(rhs: Reg[T]): MBoolean = reg.value <= rhs.value
    @api def >(rhs: Reg[T]): MBoolean = reg.value > rhs.value
    @api def >=(rhs: Reg[T]): MBoolean = reg.value >= rhs.value
  }

}

trait RegApi extends LowPriorityRegImplicits { this: SpatialApi =>

  @api implicit def readReg[T](reg: Reg[T]): T = reg.value

  implicit class RegNumericOperators[T:Type:Num](reg: Reg[T]) {
    @api def :+=(data: T): MUnit = reg := implicitly[Num[T]].plus(reg.value, data)
    @api def :-=(data: T): MUnit = reg := implicitly[Num[T]].minus(reg.value, data)
    @api def :*=(data: T): MUnit = reg := implicitly[Num[T]].times(reg.value, data)
  }

  implicit class NumericComparisions[A](x: A) {
    @api def +[T:Type:Num](reg: Reg[T])(implicit lift: Lift[A,T]): T = lift(x) + reg.value
    @api def -[T:Type:Num](reg: Reg[T])(implicit lift: Lift[A,T]): T = lift(x) - reg.value
    @api def *[T:Type:Num](reg: Reg[T])(implicit lift: Lift[A,T]): T = lift(x) * reg.value
    @api def /[T:Type:Num](reg: Reg[T])(implicit lift: Lift[A,T]): T = lift(x) / reg.value

    @api def <[T:Type:Num](reg: Reg[T])(implicit lift: Lift[A,T]): MBoolean = lift(x) < reg.value
    @api def <=[T:Type:Num](reg: Reg[T])(implicit lift: Lift[A,T]): MBoolean = lift(x) <= reg.value
    @api def >[T:Type:Num](reg: Reg[T])(implicit lift: Lift[A,T]): MBoolean = lift(x) > reg.value
    @api def >=[T:Type:Num](reg: Reg[T])(implicit lift: Lift[A,T]): MBoolean = lift(x) >= reg.value
  }



}
