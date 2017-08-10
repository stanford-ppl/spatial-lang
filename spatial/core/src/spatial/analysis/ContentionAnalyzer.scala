package spatial.analysis

import argon.traversal.CompilerPass
import argon.core._
import org.virtualized.SourceContext

import spatial.metadata._
import spatial.nodes._
import spatial.utils._

import scala.collection.mutable

trait ContentionAnalyzer extends CompilerPass {
  override val name = "Contention Analyzer"

  def top: Exp[_]
  val isolatedContention = mutable.HashMap[Exp[_],List[Int]]()

  def outerContention(x: Exp[_], P: => Int): Int = {
    if (!isInnerPipe(x) && childrenOf(x).nonEmpty) {
      val ics = childrenOf(x).map{c => calcContention(c) * P}
      isolatedContention(x) = ics
      if (isMetaPipe(x) || isStreamPipe(x)) ics.sum else ics.max
    }
    else 0
  }

  def calcContention(x: Exp[_]): Int = x match {
    case Def(_:Hwblock)            => outerContention(x, 1)
    case Def(_:ParallelPipe)       => childrenOf(x).map(calcContention).sum
    case Def(_:UnitPipe)           => outerContention(x, 1)
    case Def(e:OpForeach)          => outerContention(x, parsOf(e.cchain).product)
    case Def(e:OpReduce[_])        => outerContention(x, parsOf(e.cchain).product)
    case Def(e:OpMemReduce[_,_])   => outerContention(x, parsOf(e.cchainMap).product)
    case Def(_:DenseTransfer[_,_]) => 1
    case Def(_:SparseTransfer[_])  => 1
    case _ => 0
  }

  def markPipe(x: Exp[_], parent: Int) {
    if (isMetaPipe(x) || isStreamPipe(x)) {
      childrenOf(x).foreach{child => markContention(child,parent) }
    }
    else if (isSeqPipe(x) && childrenOf(x).nonEmpty) {
      val ics = isolatedContention(x)
      val mx = ics.max
      // Can just skip case where mx = 0 - no offchip memory accesses in this sequential anyway
      if (mx > 0) childrenOf(x).zip(ics).foreach{case (child,c) => markContention(child, (parent/mx)*c) }
    }
  }

  def markContention(x: Exp[_], parent: Int): Unit = x match {
    case Def(_:Hwblock)            => markPipe(x, parent)
    case Def(_:ParallelPipe)       => childrenOf(x).foreach{child => markContention(child,parent)}
    case Def(_:UnitPipe)           => markPipe(x, parent)
    case Def(_:OpForeach)          => markPipe(x, parent)
    case Def(_:OpReduce[_])        => markPipe(x, parent)
    case Def(_:OpMemReduce[_,_])   => markPipe(x, parent)
    case Def(_:DenseTransfer[_,_]) => contentionOf(x) = parent
    case Def(_:SparseTransfer[_])  => contentionOf(x) = parent
    case _ => // do nothing
  }

  def run(): Unit = {
    val c = calcContention(top)
    markContention(top, c)
  }

  protected def process[S:Type](block: Block[S]): Block[S] = {
    run()
    block
  }

}
