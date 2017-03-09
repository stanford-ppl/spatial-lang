package spatial.codegen.dotgen

import argon.codegen.dotgen.DotCodegen
import spatial.api.MathExp
import spatial.SpatialConfig
import spatial.analysis.SpatialMetadataExp

trait DotGenMath extends DotCodegen {
  val IR: MathExp with SpatialMetadataExp
  import IR._

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case FixAbs(x)  => 

    case FltAbs(x)  => 
    case FltLog(x)  => x.tp match {
      case DoubleType() => 
      case FloatType()  => 
    }
    case FltExp(x)  => x.tp match {
      case DoubleType() =>
      case FloatType()  => 
    }
    case FltSqrt(x) => x.tp match {
      case DoubleType() => 
      case FloatType()  => 
    }

    case Mux(sel, a, b) =>

    case Min(a, b) => 
    case Max(a, b) => 

    case _ => super.emitNode(lhs, rhs)
  }

}
