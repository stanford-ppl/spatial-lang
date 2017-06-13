package spatial.nodes

import argon.transform.SubstTransformer
import spatial.compiler._

abstract class Alloc[T:Type] extends Op[T]
abstract class DynamicAlloc[T:Type] extends Alloc[T]
abstract class PrimitiveAlloc[T:Type] extends DynamicAlloc[T]

abstract class FringeNode[T] extends Op[T]

abstract class ControlNode[T] extends Op[T]
abstract class DRAMTransfer extends ControlNode[MUnit]

abstract class EnabledControlNode extends ControlNode[Controller] {
  def en: Seq[Exp[Bit]]
  final def mirror(f:Tx): Exp[Controller] = mirrorWithEn(f, Nil)
  def mirrorWithEn(f:Tx, addEn: Seq[Exp[Bit]]): Exp[Controller]
}

abstract class Pipeline extends EnabledControlNode
abstract class Loop extends Pipeline



abstract class EnabledOp[T:Type](ens: Exp[Bit]*) extends Op[T] {
  def enables: Seq[Exp[Bit]] = ens.toSeq
  // HACK: Only works if the transformer is a substitution-based transformer (but what else is there?)
  def mirrorWithEn(f:Tx, addEn:Exp[Bit]) = f match {
    case sub: SubstTransformer =>
      val newEns = f(enables).map{Bit.and(_,addEn)}
      sub.withSubstScope(enables.zip(newEns):_*){ this.mirror(f) }

    case _ => throw new Exception("Cannot mirrorWithEn in non-subst based transformer")
  }
}