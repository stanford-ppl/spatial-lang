package spatial.api

import spatial.{SpatialApi, SpatialExp, SpatialOps}

trait RegOps extends MemoryOps {
  this: SpatialOps =>

  type Reg[T] <: RegOps[T]

  protected trait RegOps[T] {
    def value(implicit ctx: SrcCtx): T
    def :=(data: T)(implicit ctx: SrcCtx): Void
  }

  def ArgIn[T:Bits](implicit ctx: SrcCtx): Reg[T]
  def ArgOut[T:Bits](implicit ctx: SrcCtx): Reg[T]

  def Reg[T:Bits](implicit ctx: SrcCtx): Reg[T]
  def Reg[T:Bits](init: T)(implicit ctx: SrcCtx): Reg[T]

  implicit def regType[T:Bits]: Staged[Reg[T]]
  implicit def readReg[T](reg: Reg[T])(implicit ctx: SrcCtx): T = reg.value
}
trait RegApi extends RegOps with MemoryApi { this: SpatialApi => }


trait RegExp extends RegOps with MemoryExp {
  this: SpatialExp =>

  /** API **/
  case class Reg[T:Bits](s: Exp[Reg[T]]) extends RegOps[T] { self =>
    def value(implicit ctx: SrcCtx): T = wrap(reg_read(this.s))
    def :=(data: T)(implicit ctx: SrcCtx): Void = Void(reg_write(this.s, data.s, bool(true)))
  }

  def ArgIn[T:Bits](implicit ctx: SrcCtx): Reg[T] = Reg(argin_alloc[T](zero[T].s))
  def ArgOut[T:Bits](implicit ctx: SrcCtx): Reg[T] = Reg(argout_alloc[T](zero[T].s))

  def Reg[T:Bits](implicit ctx: SrcCtx): Reg[T] = Reg(reg_alloc[T](zero[T].s))
  def Reg[T:Bits](init: T)(implicit ctx: SrcCtx): Reg[T] = Reg(reg_alloc[T](init.s))


  /** Staged Type **/
  case class RegType[T](bits: Bits[T]) extends Staged[Reg[T]] {
    override def unwrapped(x: Reg[T]) = x.s
    override def wrapped(x: Exp[Reg[T]]) = Reg(x)(bits)
    override def typeArguments = List(bits)
    override def stagedClass = classOf[Reg[T]]
    override def isPrimitive = false
  }
  implicit def regType[T:Bits]: Staged[Reg[T]] = RegType[T](bits[T])

  /** IR Nodes **/
  case class ArgInNew[T:Bits](init: Exp[T]) extends Op[Reg[T]] {
    def mirror(f:Tx) = argin_alloc[T](f(init))
    val bT = bits[T]
  }
  case class ArgOutNew[T:Bits](init: Exp[T]) extends Op[Reg[T]] {
    def mirror(f:Tx) = argout_alloc[T](f(init))
    val bT = bits[T]
  }
  case class RegNew[T:Bits](init: Exp[T]) extends Op[Reg[T]] {
    def mirror(f:Tx) = reg_alloc[T](f(init))
    val bT = bits[T]
  }
  case class RegRead[T:Bits](reg: Exp[Reg[T]]) extends Op[T] {
    def mirror(f:Tx) = reg_read(f(reg))
    val bT = bits[T]
  }
  case class RegWrite[T:Bits](reg: Exp[Reg[T]], data: Exp[T], en: Exp[Bool]) extends Op[Void] {
    def mirror(f:Tx) = reg_write(f(reg),f(data), f(en))
    val bT = bits[T]
  }

  /** Smart Constructors **/
  def argin_alloc[T:Bits](init: Exp[T])(implicit ctx: SrcCtx): Sym[Reg[T]] = {
    init match { case Const(_) => ; case x => throw new Exception("Initial value of Reg must be constant") }
    stageMutable( ArgInNew[T](init) )(ctx)
  }
  def argout_alloc[T:Bits](init: Exp[T])(implicit ctx: SrcCtx): Sym[Reg[T]] = {
    init match { case Const(_) => ; case x => throw new Exception("Initial value of Reg must be constant") }
    stageMutable( ArgOutNew[T](init) )(ctx)
  }

  def reg_alloc[T:Bits](init: Exp[T])(implicit ctx: SrcCtx): Sym[Reg[T]] = {
    init match {case Const(_) => ; case x => throw new Exception("Initial value of Reg must be constant") }
    stageMutable( RegNew[T](init) )(ctx)
  }

  def reg_read[T:Bits](reg: Exp[Reg[T]])(implicit ctx: SrcCtx): Sym[T] = stage( RegRead(reg) )(ctx)

  def reg_write[T:Bits](reg: Exp[Reg[T]], data: Exp[T], en: Exp[Bool])(implicit ctx: SrcCtx): Sym[Void] = {
    stageWrite(reg)( RegWrite(reg, data, en) )(ctx)
  }


  /** Internal methods **/
  def isArgIn(x: Exp[_]): Boolean = x match {
    case Op(ArgInNew(_)) => true
    case _ => false
  }
  def isArgOut(x: Exp[_]): Boolean = x match {
    case Op(ArgOutNew(_)) => true
    case _ => false
  }
}

