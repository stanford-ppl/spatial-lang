package spatial.codegen.simgen

import argon.codegen.FileDependencies
import spatial.lang.CounterExp
import spatial.{SpatialConfig, SpatialExp}

trait SimGenCounter extends SimCodegen with FileDependencies {
  val IR: SpatialExp
  import IR._

  dependencies ::= FileDep("scalagen", "Counter.scala")

  override protected def remap(tp: Type[_]): String = tp match {
    case CounterType      => src"Counterlike"
    case CounterChainType => src"Array[Counterlike]"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case CounterNew(start,end,step,par) => emit(src"val $lhs = Counter($start, $end, $step, $par)")
    case CounterChainNew(ctrs) => emit(src"""val $lhs = Array(${ctrs.map(quote).mkString(",")})""")
    case Forever() => emit(src"""val $lhs = Forever()""")
    case _ => super.emitNode(lhs, rhs)
  }

}
