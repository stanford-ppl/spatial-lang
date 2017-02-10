package spatial.api

import argon.core.Staging
import spatial.SpatialExp

trait RegApi extends RegExp {
  this: SpatialExp =>

  def ArgIn[T:Staged:Bits](implicit ctx: SrcCtx): Reg[T] = Reg(argin_alloc[T](zero[T].s))
  def ArgOut[T:Staged:Bits](implicit ctx: SrcCtx): Reg[T] = Reg(argout_alloc[T](zero[T].s))

  def Reg[T:Staged:Bits](implicit ctx: SrcCtx): Reg[T] = Reg(reg_alloc[T](zero[T].s))
  def Reg[T:Staged:Bits](init: T)(implicit ctx: SrcCtx): Reg[T] = Reg(reg_alloc[T](init.s))

  implicit def readReg[T](reg: Reg[T])(implicit ctx: SrcCtx): T = reg.value
}


trait RegExp extends Staging with MemoryExp {
  this: SpatialExp =>

  /** Infix methods **/
  case class Reg[T:Staged:Bits](s: Exp[Reg[T]]) {
    def value(implicit ctx: SrcCtx): T = wrap(reg_read(this.s))
    def :=(data: T)(implicit ctx: SrcCtx): Void = Void(reg_write(this.s, data.s, bool(true)))
  }

  /** Staged Type **/
  case class RegType[T:Bits](child: Staged[T]) extends Staged[Reg[T]] {
    override def unwrapped(x: Reg[T]) = x.s
    override def wrapped(x: Exp[Reg[T]]) = Reg(x)(child,bits[T])
    override def typeArguments = List(child)
    override def stagedClass = classOf[Reg[T]]
    override def isPrimitive = false
  }
  implicit def regType[T:Staged:Bits]: Staged[Reg[T]] = RegType(typ[T])

  /** IR Nodes **/
  case class ArgInNew[T:Staged:Bits](init: Exp[T]) extends Op[Reg[T]] {
    def mirror(f:Tx) = argin_alloc[T](f(init))
    val mT = typ[T]
    val bT = bits[T]
  }
  case class ArgOutNew[T:Staged:Bits](init: Exp[T]) extends Op[Reg[T]] {
    def mirror(f:Tx) = argout_alloc[T](f(init))
    val mT = typ[T]
    val bT = bits[T]
  }
  case class RegNew[T:Staged:Bits](init: Exp[T]) extends Op[Reg[T]] {
    def mirror(f:Tx) = reg_alloc[T](f(init))
    val mT = typ[T]
    val bT = bits[T]
  }
  case class RegRead[T:Staged:Bits](reg: Exp[Reg[T]]) extends Op[T] {
    def mirror(f:Tx) = reg_read(f(reg))
    val mT = typ[T]
    val bT = bits[T]
    override def aliases = Nil
  }
  case class RegWrite[T:Staged:Bits](reg: Exp[Reg[T]], data: Exp[T], en: Exp[Bool]) extends Op[Void] {
    def mirror(f:Tx) = reg_write(f(reg),f(data), f(en))
    val mT = typ[T]
    val bT = bits[T]
  }

  /** Constructors **/
  def argin_alloc[T:Staged:Bits](init: Exp[T])(implicit ctx: SrcCtx): Sym[Reg[T]] = {
    stageMutable( ArgInNew[T](init) )(ctx)
  }
  def argout_alloc[T:Staged:Bits](init: Exp[T])(implicit ctx: SrcCtx): Sym[Reg[T]] = {
    stageMutable( ArgOutNew[T](init) )(ctx)
  }

  def reg_alloc[T:Staged:Bits](init: Exp[T])(implicit ctx: SrcCtx): Sym[Reg[T]] = {
    stageMutable( RegNew[T](init) )(ctx)
  }

  def reg_read[T:Staged:Bits](reg: Exp[Reg[T]])(implicit ctx: SrcCtx): Sym[T] = stageCold( RegRead(reg) )(ctx)

  def reg_write[T:Staged:Bits](reg: Exp[Reg[T]], data: Exp[T], en: Exp[Bool])(implicit ctx: SrcCtx): Sym[Void] = {
    stageWrite(reg)( RegWrite(reg, data, en) )(ctx)
  }


  /** Internal methods **/
  private[spatial] def isArgIn(x: Exp[_]): Boolean = x match {
    case Op(ArgInNew(_)) => true
    case _ => false
  }
  private[spatial] def isArgOut(x: Exp[_]): Boolean = x match {
    case Op(ArgOutNew(_)) => true
    case _ => false
  }

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

