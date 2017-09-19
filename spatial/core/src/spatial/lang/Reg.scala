package spatial.lang

import argon.core._
import forge._
import spatial.metadata._
import spatial.nodes._

case class Reg[T:Type:Bits](s: Exp[Reg[T]]) extends Template[Reg[T]] {
  /** Returns the value currently held by this register. **/
  @api def value: T = wrap(Reg.read(this.s))
  /** Writes the given `data` to this register. **/
  @api def :=(data: T): MUnit = MUnit(Reg.write(this.s, data.s, Bit.const(true)))

  /** Resets the value of this register back to its reset value. **/
  @api def reset: MUnit = wrap(Reg.reset(this.s, Bit.const(true)))
  /** Conditionally resets the value of this register back to its reset value if `cond` is true. **/
  @api def reset(cond: Bit): MUnit = wrap(Reg.reset(this.s, cond.s))

  @api def ==[A](that: A)(implicit lift: Lift[A,T]): MBoolean = this.value === lift(that)
  @api def !=[A](that: A)(implicit lift: Lift[A,T]): MBoolean = this.value =!= lift(that)
  @api def ===[A](that: A)(implicit lift: Lift[A,T]): MBoolean = this.value === lift(that)
  @api def =!=[A](that: A)(implicit lift: Lift[A,T]): MBoolean = this.value =!= lift(that)

  @api override def ===(that: Reg[T]): MBoolean = this.value === that.value
  @api override def =!=(that: Reg[T]): MBoolean = this.value =!= that.value
  @api override def toText: MString = this.value.toText
}

object Reg {
  implicit def regType[T:Type:Bits]: Type[Reg[T]] = RegType(typ[T])
  implicit def regIsMemory[T:Type:Bits]: Mem[T, Reg] = new RegIsMemory[T]

  /** Creates a register of type T with a reset value of zero. **/
  @api def apply[T:Type:Bits]: Reg[T] = Reg(Reg.alloc[T](unwrap(implicitly[Bits[T]].zero)))
  /** Creates a register of type T with the given `reset` value. **/
  @api def apply[T:Type:Bits](reset: T): Reg[T] = Reg(Reg.alloc[T](unwrap(reset)))

  @api def buffer[T:Type:Bits]: Reg[T] = {
    val reg = Reg.alloc[T](unwrap(implicitly[Bits[T]].zero))
    isExtraBufferable.enableOn(reg)
    Reg(reg)
  }
  @api def buffer[T:Type:Bits](reset: T): Reg[T] = {
    val reg = Reg.alloc[T](unwrap(reset))
    isExtraBufferable.enableOn(reg)
    Reg(reg)
  }


  /** Constructors **/
  @internal def alloc[T:Type:Bits](init: Exp[T]): Sym[Reg[T]] = {
    stageMutable( RegNew[T](init) )(ctx)
  }

  @internal def read[T:Type:Bits](reg: Exp[Reg[T]]): Sym[T] = stageUnique( RegRead(reg) )(ctx)

  @internal def write[T:Type:Bits](reg: Exp[Reg[T]], data: Exp[T], en: Exp[Bit]): Sym[MUnit] = {
    stageWrite(reg)( RegWrite(reg, data, en) )(ctx)
  }

  @internal def reset[T:Type:Bits](reg: Exp[Reg[T]], en: Exp[Bit]): Sym[MUnit] = {
    stageWrite(reg)( RegReset(reg, en) )(ctx)
  }
}

object ArgIn {
  /** Creates an input argument register of type T with a reset value of zero. **/
  @api def apply[T:Type:Bits]: Reg[T] = Reg(alloc[T](unwrap(implicitly[Bits[T]].zero)))

  /** Constructors **/
  @internal def alloc[T:Type:Bits](init: Exp[T]): Sym[Reg[T]] = stageMutable( ArgInNew[T](init) )(ctx)
}

object ArgOut {
  /** Creates an output argument register of type T with a reset value of zero. **/
  @api def apply[T:Type:Bits]: Reg[T] = Reg(alloc[T](unwrap(implicitly[Bits[T]].zero)))

  @internal def alloc[T:Type:Bits](init: Exp[T]): Sym[Reg[T]] = stageMutable( ArgOutNew[T](init) )(ctx)
}

object HostIO {
  /** Creates a host I/O register of type T with a reset value of zero. **/
  @api def apply[T:Type:Bits]: Reg[T] = Reg(alloc[T](unwrap(implicitly[Bits[T]].zero)))

  @internal def alloc[T:Type:Bits](init: Exp[T]): Sym[Reg[T]] = stageMutable( HostIONew[T](init) )(ctx)
}
