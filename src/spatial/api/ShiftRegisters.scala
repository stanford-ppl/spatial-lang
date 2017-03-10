package spatial.api

import argon.core.Staging
import spatial.SpatialExp

trait ShiftRegisterApi extends ShiftRegisterExp {
  this: SpatialExp =>


}

trait ShiftRegisterExp extends Staging {
  this: SpatialExp =>

  case class ShiftReg[T:Staged:Bits](s: Exp[ShiftReg[T]]) {
    def apply(i: Index)(implicit ctx: SrcCtx): T = wrap(shiftreg_apply(s, i.s))
    def :=(data: Vector[T])(implicit ctx: SrcCtx): Void = wrap(shiftreg_shiftin(s, data.s))
  }

  object ShiftReg {
    def apply[T:Staged:Bits](size: Index)(implicit ctx: SrcCtx): ShiftReg[T] = wrap(shiftreg_new[T](size.s))
  }

  /** Type classes **/
  case class ShiftRegType[T:Bits](child: Staged[T]) extends Staged[ShiftReg[T]] {
    override def unwrapped(x: ShiftReg[T]) = x.s
    override def wrapped(x: Exp[ShiftReg[T]]) = ShiftReg(x)(child,bits[T])
    override def typeArguments = List(child)
    override def stagedClass = classOf[ShiftReg[T]]
    override def isPrimitive = false
  }
  implicit def shiftRegType[T:Staged:Bits]: Staged[ShiftReg[T]] = ShiftRegType(typ[T])


  /** IR Nodes **/
  case class ShiftRegNew[T:Staged:Bits](size: Exp[Index]) extends Op[ShiftReg[T]] {
    def mirror(f:Tx) = shiftreg_new[T](f(size))
  }

  case class ShiftRegApply[T:Staged:Bits](reg: Exp[ShiftReg[T]], i: Exp[Index]) extends Op[T] {
    def mirror(f:Tx) = shiftreg_apply(f(reg),f(i))
  }

  case class ShiftRegShiftIn[T:Staged:Bits](reg: Exp[ShiftReg[T]], data: Exp[Vector[T]]) extends Op[Void] {
    def mirror(f:Tx) = shiftreg_shiftin(f(reg),f(data))
  }

  /** Constructors **/
  private[spatial] def shiftreg_new[T:Staged:Bits](size: Exp[Index])(implicit ctx: SrcCtx) = {
    stageMutable(ShiftRegNew[T](size))(ctx)
  }

  private[spatial] def shiftreg_apply[T:Staged:Bits](reg: Exp[ShiftReg[T]], i: Exp[Index])(implicit ctx: SrcCtx) = {
    stageCold(ShiftRegApply(reg, i))(ctx)
  }

  private[spatial] def shiftreg_shiftin[T:Staged:Bits](reg: Exp[ShiftReg[T]], data: Exp[Vector[T]])(implicit ctx: SrcCtx) = {
    stageWrite(reg)(ShiftRegShiftIn(reg, data))(ctx)
  }

}