package spatial.api

import argon.core.Staging
import spatial.SpatialExp

trait ShiftRegApi extends ShiftRegExp {
  this: SpatialExp =>

  def ShiftReg[T:Staged:Bits](size: Int)(implicit ctx: SrcCtx): ShiftReg[T] = ShiftReg(shift_reg_alloc[T](size, zero[T].s))
  def ShiftReg[T:Staged:Bits](size: Int, init: T)(implicit ctx: SrcCtx): ShiftReg[T] = ShiftReg(shift_reg_alloc[T](size, init.s))

  implicit def readShiftReg[T](shiftReg: ShiftReg[T])(implicit ctx: SrcCtx): T = shiftReg.value
}


trait ShiftRegExp extends Staging with MemoryExp {
  this: SpatialExp =>

  /** Infix methods **/
  case class ShiftReg[T:Staged:Bits](s: Exp[ShiftReg[T]]) {
    def value(implicit ctx: SrcCtx): T = wrap(shift_reg_read(this.s))
    def :=(data: T)(implicit ctx: SrcCtx): Void = Void(shift_reg_write(this.s, data.s, bool(true)))
  }

  /** Staged Type **/
  case class ShiftRegType[T:Bits](child: Staged[T]) extends Staged[ShiftReg[T]] {
    override def unwrapped(x: ShiftReg[T]) = x.s
    override def wrapped(x: Exp[ShiftReg[T]]) = ShiftReg(x)(child,bits[T])
    override def typeArguments = List(child)
    override def stagedClass = classOf[ShiftReg[T]]
    override def isPrimitive = false
  }
  implicit def shiftRegType[T:Staged:Bits]: Staged[ShiftReg[T]] = ShiftRegType(typ[T])

  class ShiftRegIsMemory[T:Staged:Bits] extends Mem[T, ShiftReg] {
    def load(mem: ShiftReg[T], is: Seq[Index], en: Bool)(implicit ctx: SrcCtx): T = mem.value
    def store(mem: ShiftReg[T], is: Seq[Index], data: T, en: Bool)(implicit ctx: SrcCtx): Void = {
      Void(shift_reg_write(mem.s, data.s, en.s))
    }
    def iterators(mem: ShiftReg[T])(implicit ctx: SrcCtx): Seq[Counter] = Seq(Counter(0, sizeOf(mem), 1, 1))
  }
  implicit def shiftRegIsMemory[T:Staged:Bits]: Mem[T, ShiftReg] = new ShiftRegIsMemory[T]


  /** IR Nodes **/
  case class ShiftRegNew[T:Staged:Bits](size: Int, init: Exp[T]) extends Op[ShiftReg[T]] {
    def mirror(f:Tx) = shift_reg_alloc[T](size, f(init))
    val mT = typ[T]
    val bT = bits[T]
  }
  case class ShiftRegRead[T:Staged:Bits](shiftReg: Exp[ShiftReg[T]]) extends Op[T] {
    def mirror(f:Tx) = shift_reg_read(f(shiftReg))
    val mT = typ[T]
    val bT = bits[T]
    override def aliases = Nil
  }
  case class ShiftRegWrite[T:Staged:Bits](shiftReg: Exp[ShiftReg[T]], data: Exp[T], en: Exp[Bool]) extends Op[Void] {
    def mirror(f:Tx) = shift_reg_write(f(shiftReg),f(data), f(en))
    val mT = typ[T]
    val bT = bits[T]
  }

  /** Constructors **/
  def shift_reg_alloc[T:Staged:Bits](size: Int, init: Exp[T])(implicit ctx: SrcCtx): Sym[ShiftReg[T]] = {
    stageMutable( ShiftRegNew[T](size, init) )(ctx)
  }

  def shift_reg_read[T:Staged:Bits](shiftReg: Exp[ShiftReg[T]])(implicit ctx: SrcCtx): Sym[T] = stageCold( ShiftRegRead(shiftReg) )(ctx)

  def shift_reg_write[T:Staged:Bits](shiftReg: Exp[ShiftReg[T]], data: Exp[T], en: Exp[Bool])(implicit ctx: SrcCtx): Sym[Void] = {
    stageWrite(shiftReg)( ShiftRegWrite(shiftReg, data, en) )(ctx)
  }


  /** Internals **/
  private def sizeOf(shiftReg: ShiftReg[_])(implicit ctx: SrcCtx): Int = sizeOf(shiftReg.s)
  private def sizeOf[T](shiftReg: Exp[ShiftReg[T]])(implicit ctx: SrcCtx): Int = shiftReg match {
    case Op(ShiftRegNew(size, init)) => size
    case x => throw new UndefinedDimensionsError(x, None)
  }

}

