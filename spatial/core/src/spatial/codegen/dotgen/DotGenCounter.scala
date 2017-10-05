package spatial.codegen.dotgen

import argon.codegen.FileDependencies
import argon.codegen.dotgen.DotCodegen
import argon.core._
import spatial.aliases._
import spatial.nodes._

trait DotGenCounter extends DotCodegen with FileDependencies {

  override def attr(n: Exp[_]) = n match {
    case lhs: Sym[_] => lhs match {
      case Def(CounterChainNew(ctrs)) => super.attr(n).label(src"""cchain_${lhs.id}""").shape(box)
      case _ => super.attr(n)
    }
    case _ => super.attr(n)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case CounterNew(start,end,step,par) => 
    case CounterChainNew(ctrs) => if (config.dotDetail > 0) emitVert(lhs)
    case Forever() => 
    case _ => super.emitNode(lhs, rhs)
  }

}
