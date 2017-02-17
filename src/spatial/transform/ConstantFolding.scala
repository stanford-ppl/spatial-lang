package spatial.transform

import argon.transform.ForwardTransformer
import spatial.SpatialExp

/**
  * Replaces all fixed and floating point, pure expressions with "final" bounds with corresponding constant values
  * TODO: Is this transformer still needed? Now largely superseded by rewrite rules
  */
trait ConstantFolding extends ForwardTransformer {
  val IR: SpatialExp
  import IR._

  override val name = "Constant Folding"

  override def transform[T:Staged](lhs: Sym[T], rhs: Op[T])(implicit ctx: SrcCtx): Exp[T] = lhs match {
    // Don't constant fold effectful statements
    case Effectful(effects,deps) => super.transform(lhs, rhs)

    case Final(c) => (lhs.tp match {
      case tp: FixPtType[_,_,_] => fixpt(BigDecimal(c))(tp.mS,tp.mI,tp.mF,ctx)
      case tp: FltPtType[_,_]   => fltpt(BigDecimal(c))(tp.mG, tp.mE, ctx)
      case _ => super.transform(lhs, rhs)
    }).asInstanceOf[Exp[T]]

    case _ => super.transform(lhs, rhs)
  }

}
