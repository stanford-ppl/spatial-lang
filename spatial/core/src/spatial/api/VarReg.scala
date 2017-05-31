package spatial.api

import spatial._
import forge._

trait VarRegExp { this: SpatialExp =>

  case class VarReg[T:Type](s: Exp[VarReg[T]]) extends Template[VarReg[T]]

  /** Staged Type **/
  case class VarRegType[T](child: Type[T]) extends Type[VarReg[T]] {
    override def wrapped(x: Exp[VarReg[T]]) = VarReg(x)(child)
    override def typeArguments = List(child)
    override def stagedClass = classOf[VarReg[T]]
    override def isPrimitive = false
  }
  implicit def varRegType[T:Type]: Type[VarReg[T]] = VarRegType(typ[T])

  private[spatial] def var_reg_alloc[T:Type](tp: Type[T])(implicit ctx: SrcCtx): Sym[VarReg[T]] = {
    stageMutable( VarRegNew[T](tp) )(ctx)
  }

  private[spatial] def var_reg_read[T:Type](reg: Exp[VarReg[T]])(implicit ctx: SrcCtx): Sym[T] = {
    stageCold( VarRegRead(reg) )(ctx)
  }

  private[spatial] def var_reg_write[T:Type](reg: Exp[VarReg[T]], data: Exp[T], en: Exp[Bool])(implicit ctx: SrcCtx): Sym[Void] = {
    stageWrite(reg)( VarRegWrite(reg, data, en) )(ctx)
  }

  case class VarRegNew[T:Type](tp: Type[T]) extends Op[VarReg[T]] {
    def mirror(f:Tx) = var_reg_alloc[T](tp)
    val mT = typ[T]
  }
  case class VarRegRead[T:Type](reg: Exp[VarReg[T]]) extends Op[T] {
    def mirror(f:Tx) = var_reg_read(f(reg))
    val mT = typ[T]
    override def aliases = Nil
  }
  case class VarRegWrite[T:Type](reg: Exp[VarReg[T]], data: Exp[T], en: Exp[Bool]) extends EnabledOp[Void](en) {
    def mirror(f:Tx) = var_reg_write(f(reg),f(data), f(en))
    val mT = typ[T]
  }

}

trait VarRegApi { /** None **/ }