package spatial.codegen.scalagen

import argon.codegen.FileDependencies
import argon.codegen.scalagen.ScalaCodegen
import argon.core._
import spatial.aliases._
import spatial.nodes._

trait ScalaGenCounter extends ScalaCodegen with FileDependencies {
  dependencies ::= FileDep("scalagen", "Counter.scala")

  override protected def remap(tp: Type[_]): String = tp match {
    case CounterType      => src"Counterlike"
    case CounterChainType => src"Array[Counterlike]"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case Forever() => emit(src"""val $lhs: ${lhs.tp} = Forever()""")
    case CounterNew(start,end,step,par) => emit(src"val $lhs: ${lhs.tp} = Counter($start, $end, $step, $par)")
    case CounterChainNew(ctrs) => emit(src"""val $lhs: ${lhs.tp} = Array[Counterlike]($ctrs)""")
    case _ => super.emitNode(lhs, rhs)
  }

}
