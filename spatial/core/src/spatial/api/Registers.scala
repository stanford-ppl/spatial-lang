package spatial.api

import argon.core.Staging
import spatial.SpatialExp
import forge._

trait RegApi extends RegExp {
  this: SpatialExp =>

  @api def ArgIn[T:Meta:Bits]: Reg[T] = Reg(argin_alloc[T](zero[T].s))
  @api def ArgOut[T:Meta:Bits]: Reg[T] = Reg(argout_alloc[T](zero[T].s))

  @api def Reg[T:Meta:Bits]: Reg[T] = Reg(reg_alloc[T](zero[T].s))
  @api def Reg[T:Meta:Bits](init: T): Reg[T] = Reg(reg_alloc[T](init.s))

  @api implicit def readReg[T](reg: Reg[T]): T = reg.value
}


trait RegExp extends Staging {
  this: SpatialExp =>

  /** Infix methods **/
  case class Reg[T:Meta:Bits](s: Exp[Reg[T]]) extends Template[Reg[T]] {
    @api def value: T = wrap(reg_read(this.s))
    @api def :=(data: T): Void = Void(reg_write(this.s, data.s, bool(true)))
  }

  /** Staged Type **/
  case class RegType[T:Bits](child: Meta[T]) extends Meta[Reg[T]] {
    override def wrapped(x: Exp[Reg[T]]) = Reg(x)(child,bits[T])
    override def typeArguments = List(child)
    override def stagedClass = classOf[Reg[T]]
    override def isPrimitive = false
  }
  implicit def regType[T:Meta:Bits]: Meta[Reg[T]] = RegType(meta[T])

  class RegIsMemory[T:Meta:Bits] extends Mem[T, Reg] {
    def load(mem: Reg[T], is: Seq[Index], en: Bool)(implicit ctx: SrcCtx): T = mem.value
    def store(mem: Reg[T], is: Seq[Index], data: T, en: Bool)(implicit ctx: SrcCtx): Void = {
      Void(reg_write(mem.s, data.s, en.s))
    }
    def iterators(mem: Reg[T])(implicit ctx: SrcCtx): Seq[Counter] = Seq(Counter(0, 1, 1, 1))
  }
  implicit def regIsMemory[T:Meta:Bits]: Mem[T, Reg] = new RegIsMemory[T]


  /** IR Nodes **/
  case class ArgInNew[T:Type:Bits](init: Exp[T]) extends Op[Reg[T]] {
    def mirror(f:Tx) = argin_alloc[T](f(init))
    val mT = typ[T]
    val bT = bits[T]
  }
  case class ArgOutNew[T:Type:Bits](init: Exp[T]) extends Op[Reg[T]] {
    def mirror(f:Tx) = argout_alloc[T](f(init))
    val mT = typ[T]
    val bT = bits[T]
  }
  case class RegNew[T:Type:Bits](init: Exp[T]) extends Op[Reg[T]] {
    def mirror(f:Tx) = reg_alloc[T](f(init))
    val mT = typ[T]
    val bT = bits[T]
  }
  case class RegRead[T:Type:Bits](reg: Exp[Reg[T]]) extends Op[T] {
    def mirror(f:Tx) = reg_read(f(reg))
    val mT = typ[T]
    val bT = bits[T]
    override def aliases = Nil
  }
  case class RegWrite[T:Type:Bits](reg: Exp[Reg[T]], data: Exp[T], en: Exp[Bool]) extends EnabledOp[Void](en) {
    def mirror(f:Tx) = reg_write(f(reg),f(data), f(en))
    val mT = typ[T]
    val bT = bits[T]
  }

  /** Constructors **/
  @internal def argin_alloc[T:Type:Bits](init: Exp[T]): Sym[Reg[T]] = stageMutable( ArgInNew[T](init) )(ctx)
  @internal def argout_alloc[T:Type:Bits](init: Exp[T]): Sym[Reg[T]] = stageMutable( ArgOutNew[T](init) )(ctx)

  private[spatial] def reg_alloc[T:Type:Bits](init: Exp[T])(implicit ctx: SrcCtx): Sym[Reg[T]] = {
    stageMutable( RegNew[T](init) )(ctx)
  }

  private[spatial] def reg_read[T:Type:Bits](reg: Exp[Reg[T]])(implicit ctx: SrcCtx): Sym[T] = stageCold( RegRead(reg) )(ctx)

  private[spatial] def reg_write[T:Type:Bits](reg: Exp[Reg[T]], data: Exp[T], en: Exp[Bool])(implicit ctx: SrcCtx): Sym[Void] = {
    stageWrite(reg)( RegWrite(reg, data, en) )(ctx)
  }


  /** Internal methods **/
  private[spatial] def isArgIn(x: Exp[_]): Boolean = getDef(x).exists{case ArgInNew(_) => true; case _ => false }
  private[spatial] def isArgOut(x: Exp[_]): Boolean = getDef(x).exists{case ArgOutNew(_) => true; case _ => false }

  private[spatial] sealed abstract class RegisterType
  private[spatial] case object Regular     extends RegisterType
  private[spatial] case object ArgumentIn  extends RegisterType
  private[spatial] case object ArgumentOut extends RegisterType

  private[spatial] def regType(x: Exp[_]) = {
    if      (isArgIn(x))  ArgumentIn
    else if (isArgOut(x)) ArgumentOut
    else                  Regular
  }

  private[spatial] def resetValue[T](x: Exp[Reg[T]]): Exp[T] = x match {
    case Op(RegNew(init))    => init
    case Op(ArgInNew(init))  => init
    case Op(ArgOutNew(init)) => init
  }

}

