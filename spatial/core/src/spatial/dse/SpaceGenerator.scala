package spatial.dse

import argon.core._
import forge._
import spatial.aliases._
import spatial.metadata._

trait SpaceGenerator {
  final val PRUNE: Boolean = false

  implicit class ToRange(x: (Int,Int,Int)) {
    def toRange: Range = x._1 until x._3 by x._2
  }

  def domain(p: Param[Index], restricts: Iterable[Restrict])(implicit ir: State): Domain[Int] = {
    if (restricts.nonEmpty) {
      Domain.restricted(domainOf(p).toRange, {(v: Int, state: State) => p.setValue(BigDecimal(v))(state) }, {state => restricts.forall(_.evaluate()(state)) })
    }
    else {
      Domain(domainOf(p).toRange, { (v: Int, state: State) => p.setValue(BigDecimal(v))(state) })
    }
  }

  def createIntSpace(params: Seq[Param[Index]], restrict: Set[Restrict])(implicit ir: State): Seq[Domain[Int]] = {
    if (PRUNE) {
      val pruneSingle = params.map { p =>
        val restricts = restrict.filter(_.dependsOnlyOn(p))
        p -> domain(p, restricts)
      }
      pruneSingle.map(_._2)
    }
    else {
      params.map{p => domain(p, Nil) }
    }
  }

  def createCtrlSpace(metapipes: Seq[Exp[_]])(implicit ir: State): Seq[Domain[Boolean]] = {
    metapipes.map{mp =>
      Domain(List(true,false), {(c: Boolean, state:State) =>
        if (c) styleOf.set(mp, MetaPipe)(state)
        else   styleOf.set(mp, SeqPipe)(state)
      })
    }
  }
}

