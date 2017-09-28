package spatial.transform

import argon.core._
import argon.transform.ForwardTransformer
import argon.nodes._
import spatial.aliases._
import spatial.metadata._

/**
  * Replaces all fixed and floating point, pure expressions with "final" bounds with corresponding constant values
  * TODO: Is this transformer still needed? Now largely superseded by rewrite rules
  */
trait ConstantFolding extends ForwardTransformer {
  override val name = "Constant Folding"

  override def transform[T:Type](lhs: Sym[T], rhs: Op[T])(implicit ctx: SrcCtx): Exp[T] = lhs match {
    // Don't constant fold effectful statements
    case Effectful(effects,deps) => super.transform(lhs, rhs)

    case Final(c) => (lhs.tp match {
      case tp: FixPtType[s,i,f] =>
        implicit val mS = tp.mS.asInstanceOf[BOOL[s]]
        implicit val mI = tp.mI.asInstanceOf[INT[i]]
        implicit val mF = tp.mF.asInstanceOf[INT[f]]
        FixPt.const[s,i,f](c)

      case tp: FltPtType[g,e]   =>
        implicit val mG = tp.mG.asInstanceOf[INT[g]]
        implicit val mE = tp.mE.asInstanceOf[INT[e]]
        FltPt.const[g,e](c)

      case _ => super.transform(lhs, rhs)
    }).asInstanceOf[Exp[T]]

    case _ => super.transform(lhs, rhs)
  }

}
