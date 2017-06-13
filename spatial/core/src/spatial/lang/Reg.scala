package spatial.lang

import forge._
import spatial.nodes._

case class Reg[T:Type:Bits](s: Exp[Reg[T]]) extends Template[Reg[T]] {
  @api def value: T = wrap(Reg.read(this.s))
  @api def :=(data: T): MUnit = MUnit(Reg.write(this.s, data.s, Bit.const(true)))
  @api def reset(cond: Bit): MUnit = wrap(Reg.reset(this.s, cond.s))
  @api def reset: MUnit = wrap(Reg.reset(this.s, Bit.const(true)))
}
object Reg {
  implicit def regType[T:Type:Bits]: Type[Reg[T]] = RegType(typ[T])

  @api def apply[T:Type:Bits]: Reg[T] = Reg(Reg.alloc[T](unwrap(implicitly[Bits[T]].zero)))
  @api def apply[T:Type:Bits](reset: T): Reg[T] = Reg(Reg.alloc[T](unwrap(reset)))

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
  @api def apply[T:Type:Bits]: Reg[T] = Reg(alloc[T](unwrap(implicitly[Bits[T]].zero)))

  /** Constructors **/
  @internal def alloc[T:Type:Bits](init: Exp[T]): Sym[Reg[T]] = stageMutable( ArgInNew[T](init) )(ctx)
}

object ArgOut {
  @api def apply[T:Type:Bits]: Reg[T] = Reg(alloc[T](unwrap(implicitly[Bits[T]].zero)))

  @internal def alloc[T:Type:Bits](init: Exp[T]): Sym[Reg[T]] = stageMutable( ArgOutNew[T](init) )(ctx)
}

object HostIO {
  @api def apply[T:Type:Bits]: Reg[T] = Reg(alloc[T](unwrap(implicitly[Bits[T]].zero)))

  @internal def alloc[T:Type:Bits](init: Exp[T]): Sym[Reg[T]] = stageMutable( HostIONew[T](init) )(ctx)
}

trait RegApi {
  @api implicit def readReg[T](reg: Reg[T]): T = reg.value

  implicit class RegNumericOperators[T:Type:Num](reg: Reg[T]) {
    @api def :+=(data: T): MUnit = reg := (reg.value + data)
    @api def :-=(data: T): MUnit = reg := (reg.value - data)
    @api def :*=(data: T): MUnit = reg := (reg.value * data)
    
  }

  class RegIsMemory[T:Type:Bits] extends Mem[T, Reg] {
    @api def load(mem: Reg[T], is: Seq[Index], en: Bit): T = mem.value
    @api def store(mem: Reg[T], is: Seq[Index], data: T, en: Bit): MUnit = MUnit(Reg.write(mem.s, data.s, en.s))
    @api def iterators(mem: Reg[T]): Seq[Counter] = Seq(Counter(0, 1, 1, 1))
  }
  implicit def regIsMemory[T:Type:Bits]: Mem[T, Reg] = new RegIsMemory[T]
}

/*private[spatial] sealed abstract class RegisterType
private[spatial] case object Regular     extends RegisterType
private[spatial] case object ArgumentIn  extends RegisterType
private[spatial] case object ArgumentOut extends RegisterType
private[spatial] case object HostInOut   extends RegisterType

private[spatial] def regType(x: Exp[_]) = {
  if      (isArgIn(x))  ArgumentIn
  else if (isArgOut(x)) ArgumentOut
  else if (isHostIO(x)) HostInOut
  else                  Regular
}*/