package spatial.lang

import spatial._
import forge._

trait ShiftRegApi extends ShiftRegExp { this: SpatialExp => }

trait ShiftRegExp { this: SpatialExp =>

  /** Infix methods **/
  case class ShiftReg[T:Meta:Bits](s: Exp[ShiftReg[T]]) extends Template[ShiftReg[T]] {
    def value(implicit ctx: SrcCtx): T = wrap(shift_reg_read(this.s))
    def :=(data: T)(implicit ctx: SrcCtx): Void = Void(shift_reg_write(this.s, data.s, bool(true)))
  }

  /** Type Type **/
  case class ShiftRegType[T:Bits](child: Meta[T]) extends Meta[ShiftReg[T]] {
    override def wrapped(x: Exp[ShiftReg[T]]) = ShiftReg(x)(child,bits[T])
    override def typeArguments = List(child)
    override def stagedClass = classOf[ShiftReg[T]]
    override def isPrimitive = false
  }
  implicit def shiftRegType[T:Meta:Bits]: Meta[ShiftReg[T]] = ShiftRegType(meta[T])

  /** IR Nodes **/
  case class ValueDelay[T:Type:Bits](size: Int, data: Exp[T]) extends Op[T] {
    def mirror(f:Tx) = value_delay_alloc[T](size, f(data))
    val mT = typ[T]
    val bT = bits[T]
  }
  case class ShiftRegNew[T:Type:Bits](size: Int, init: Exp[T]) extends Op[ShiftReg[T]] {
    def mirror(f:Tx) = shift_reg_alloc[T](size, f(init))
    val mT = typ[T]
    val bT = bits[T]
  }
  case class ShiftRegRead[T:Type:Bits](shiftReg: Exp[ShiftReg[T]]) extends Op[T] {
    def mirror(f:Tx) = shift_reg_read(f(shiftReg))
    val mT = typ[T]
    val bT = bits[T]
    override def aliases = Nil
  }
  case class ShiftRegWrite[T:Type:Bits](shiftReg: Exp[ShiftReg[T]], data: Exp[T], en: Exp[Bool]) extends Op[Void] {
    def mirror(f:Tx) = shift_reg_write(f(shiftReg),f(data), f(en))
    val mT = typ[T]
    val bT = bits[T]
  }

  /** Constructors **/
  private[spatial] def value_delay_alloc[T:Type:Bits](size: Int, data: Exp[T])(implicit ctx: SrcCtx): Sym[T] = {
    stageCold( ValueDelay[T](size, data) )(ctx)
  }
  private[spatial] def shift_reg_alloc[T:Type:Bits](size: Int, init: Exp[T])(implicit ctx: SrcCtx): Sym[ShiftReg[T]] = {
    stageMutable( ShiftRegNew[T](size, init) )(ctx)
  }
  private[spatial] def shift_reg_read[T:Type:Bits](shiftReg: Exp[ShiftReg[T]])(implicit ctx: SrcCtx): Sym[T] = {
    stageCold( ShiftRegRead(shiftReg) )(ctx)
  }
  private[spatial] def shift_reg_write[T:Type:Bits](shiftReg: Exp[ShiftReg[T]], data: Exp[T], en: Exp[Bool])(implicit ctx: SrcCtx): Sym[Void] = {
    stageWrite(shiftReg)( ShiftRegWrite(shiftReg, data, en) )(ctx)
  }
}

