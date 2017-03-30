package spatial.api

import argon.core.Staging
import argon.transform.SubstTransformer
import spatial.SpatialExp

trait EnabledPrimitivesApi extends EnabledPrimitivesExp {
  this: SpatialExp =>
}


trait EnabledPrimitivesExp extends Staging {
  this: SpatialExp =>

  abstract class EnabledOp[T:Staged](ens: Exp[Bool]*) extends Op[T] {
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
