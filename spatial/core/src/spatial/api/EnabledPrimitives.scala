package spatial.api

import argon.transform.SubstTransformer
import spatial._

trait EnabledPrimitivesApi extends EnabledPrimitivesExp { this: SpatialApi => }

trait EnabledPrimitivesExp { this: SpatialExp =>

  abstract class EnabledOp[T:Type](ens: Exp[Bool]*) extends Op[T] {
    def enables: Seq[Exp[Bool]] = ens.toSeq
    // HACK: Only works if the transformer is a substitution-based transformer (but what else is there?)
    def mirrorWithEn(f:Tx, addEn:Exp[Bool]) = f match {
      case sub: SubstTransformer =>
        val newEns = f(enables).map{bool_and(_,addEn)}
        sub.withSubstScope(enables.zip(newEns):_*){ this.mirror(f) }

      case _ => throw new Exception("Cannot mirrorWithEn in non-subst based transformer")
    }
  }

}
