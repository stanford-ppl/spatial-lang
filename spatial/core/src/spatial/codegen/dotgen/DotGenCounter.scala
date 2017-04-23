package spatial.codegen.dotgen

import argon.codegen.FileDependencies
import argon.codegen.dotgen.DotCodegen
import spatial.api.CounterExp
import spatial.SpatialExp

trait DotGenCounter extends DotCodegen with FileDependencies {
  val IR: CounterExp with SpatialExp
  import IR._

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case CounterNew(start,end,step,par) => 
    case CounterChainNew(ctrs) => 
    case Forever() => 
    case _ => super.emitNode(lhs, rhs)
  }

}
