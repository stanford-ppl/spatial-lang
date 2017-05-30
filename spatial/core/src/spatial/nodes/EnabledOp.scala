package spatial.nodes

import argon.transform.SubstTransformer
import spatial.compiler._

abstract class EnabledOp[T:Type](ens: Exp[Bit]*) extends Op[T] {
  def enables: Seq[Exp[Bit]] = ens.toSeq
  // HACK: Only works if the transformer is a substitution-based transformer (but what else is there?)
  def mirrorWithEn(f:Tx, addEn:Exp[Bit]) = f match {
    case sub: SubstTransformer =>
      val newEns = f(enables).map{MBoolean.and(_,addEn)}
      sub.withSubstScope(enables.zip(newEns):_*){ this.mirror(f) }

    case _ => throw new Exception("Cannot mirrorWithEn in non-subst based transformer")
  }
}

