package spatial.codegen.dotgen

import argon.codegen.dotgen.DotCodegen
import spatial.SpatialExp

trait DotGenMath extends DotCodegen {
  val IR: SpatialExp
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
