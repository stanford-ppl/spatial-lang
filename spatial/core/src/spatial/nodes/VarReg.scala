package spatial.nodes

import argon.core._
import spatial.aliases._

case class VarRegType[T](child: Type[T]) extends Type[VarReg[T]] {
  override def wrapped(x: Exp[VarReg[T]]) = new VarReg(x)(child)
  override def typeArguments = List(child)
  override def stagedClass = classOf[VarReg[T]]
  override def isPrimitive = false
}

case class VarRegNew[T:Type](tp: Type[T]) extends Alloc[VarReg[T]] {
  def mirror(f:Tx) = VarReg.alloc[T](tp)
  val mT = typ[T]
}
case class VarRegRead[T:Type](reg: Exp[VarReg[T]]) extends Op[T] {
  def mirror(f:Tx) = VarReg.read(f(reg))
  val mT = typ[T]
  override def aliases = Nil
}
case class VarRegWrite[T:Type](reg: Exp[VarReg[T]], data: Exp[T], en: Exp[Bit]) extends EnabledOp[MUnit](en) {
  def mirror(f:Tx) = VarReg.write(f(reg),f(data), f(en))
  val mT = typ[T]
}
