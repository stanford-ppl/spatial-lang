package spatial.dse

import argon.core._
import argon.nodes._
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._
import spatial.analysis.SpatialTraversal

trait HeuristicAnalyzer extends SpatialTraversal {
  override val name = "Heuristic Analyzer"
  override val recurse = Always

  var restrict = Set[Restrict]()

  object Parameter {
    def unapply(x: Exp[_]): Option[Param[Index]] = x match {
      case x: Param[_] if x.tp == IntType && !x.isFinal => Some(x.asInstanceOf[Param[Index]])
      case _ => None
    }
  }
  object Num {
    def unapply(x: Exp[_]): Option[Int] = x match {
      case Exact(c) => Some(c.toInt)
      case _ => None
    }
  }
  object Expect {
    def unapply(x: Exp[_]): Option[Int] = x match {
      case Bound(c) => Some(c.toInt)
      case _ => None
    }
  }

  def setRange(x: Param[Index], min: Int, max: Int, stride: Int = 1): Unit = domainOf.get(x) match {
    case Some((start,end,step)) =>
      domainOf(x) = (Math.max(min,start), Math.min(max,end), Math.max(stride,step))
    case None =>
      domainOf(x) = (min,max,stride)
  }

  override protected def visit(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case SRAMNew(_) => // TODO

    case CounterNew(start,end,step,Parameter(par)) =>
      //var max = 192 // ???
      dbg("")
      dbg("")
      dbg(u"Found counter with start=$start, end=$end, step=$step, par=$par")
      dbg(u"  bound($start) = " + boundOf.get(start).map(_.toString).getOrElse("???"))
      dbg(u"  bound($end) = " + boundOf.get(end).map(_.toString).getOrElse("???"))
      dbg(u"  bound($step) = " + boundOf.get(step).map(_.toString).getOrElse("???"))

      // Set constraints on par factor
      (start,end,step) match {
        case (Num(0),Parameter(end),Num(1)) =>
          val r1 = RLessEqual(par, end)
          val r2 = RDivides(par, end)
          dbg(s"  Case #1: Adding restriction $r1")
          dbg(s"  Case #2: Adding restriction $r2")
          restrict += r1
          restrict += r2

        case (_,Param(_),Param(_)) => // ???

        case (Num(0), Expect(e), Parameter(p)) =>
          val r = RDividesQuotient(par, e.toInt, p)
          dbg(s"  Case #3: Adding restriction $r")
          restrict += r

        case (Bound(s),Bound(e), Parameter(p)) =>
          val r = RDividesQuotient(par, (e-s).toInt, p)
          dbg(s"  Case #4: Adding restriction $r")
          restrict += r

        case (Bound(s),Bound(e),Bound(t)) =>
          val nIters = (e - s)/t
          //if (nIters < max) max = nIters.toInt
          val r = RDividesConst(par, nIters.toInt)  // HACK: par factor divides bounded loop size (avoid edge case)
          dbg(s"  Case #5: Adding restriction $r")
          restrict += r

        case _ => // No restrictions
      }

      // Set constraints on step size
      (start,end,step) match {
        case (Parameter(s),Parameter(p),Parameter(_)) => // ???

        case (Num(0),Parameter(p),Parameter(s)) =>
          val r1 = RLessEqual(s, p)
          val r2 = RDivides(s, p)   // HACK: avoid edge case
          dbg(s"  Case #6: Adding restriction $r1")
          dbg(s"  Case #7: Adding restriction $r2")
          restrict += r1
          restrict += r2

        case (Bound(s),Bound(b),Parameter(p)) =>
          val l = b - s
          setRange(p, 1, l.toInt, 1)
          val r = RDividesConst(p, l.toInt) // HACK: avoid edge case
          dbg(s"  Case #8: Adding restriction $r")
          restrict += r

        case _ => // No restrictions
      }

    case CounterNew(start,end,step,Const(c)) =>
      dbg("")
      dbg("")
      dbg(u"Found counter with start=$start, end=$end, step=$step, par=$c")
      dbg(u"  bound($start) = " + boundOf(start))
      dbg(u"  bound($end) = " + boundOf(end))
      dbg(u"  bound($step) = " + boundOf(step))

      // Set constraints on step size
      (start,end,step) match {
        case (Parameter(s),Parameter(p),Parameter(_)) => // ???

        case (Num(0),Parameter(p),Parameter(s)) =>
          val r1 = RLessEqual(s, p)
          val r2 = RDivides(s, p)   // HACK: avoid edge case
          dbg(s"  Case #6: Adding restriction $r1")
          dbg(s"  Case #7: Adding restriction $r2")
          restrict += r1
          restrict += r2

        case (Bound(s),Bound(b),Parameter(p)) =>
          val l = b - s
          setRange(p, 1, l.toInt, 1)
          val r = RDividesConst(p, l.toInt) // HACK: avoid edge case
          dbg(s"  Case #8: Adding restriction $r")
          restrict += r

        case _ => // No restrictions
      }

    case _ => super.visit(lhs,rhs)
  }

}
