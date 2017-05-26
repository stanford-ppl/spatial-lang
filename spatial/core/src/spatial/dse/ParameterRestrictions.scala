package spatial.dse

import spatial.SpatialExp
import argon.util.recursive.collectSet

trait ParameterRestrictions { this: SpatialExp =>
  private type RRange = scala.collection.immutable.Range

  sealed abstract class Restrict {this: Product =>
    def evaluate: Boolean
    def deps: Set[Param[_]] = collectSet{case p: Param[_] => p}(productIterator)
    def dependsOnlyOn(x: Param[_]*) = (deps diff x.toSet).isEmpty
  }

  private implicit class ParamValue(x: Param[Index]) {
    def value: Int = x.c match {
      case c: BigDecimal => c.toInt
      case c: Int        => c
    }
  }

  case class RLess(a: Param[Index], b: Param[Index]) extends Restrict {
    def evaluate = a.value < b.value
    override def toString = u"$a < $b"
  }
  case class RLessEqual(a: Param[Index], b: Param[Index]) extends Restrict {
    def evaluate = a.value <= b.value
    override def toString = u"$a <= $b"
  }
  case class RDivides(a: Param[Index], b: Param[Index]) extends Restrict {
    def evaluate = b.value % a.value == 0
    override def toString = u"$a divides $b"
  }
  case class RDividesConst(a: Param[Index], b: Int) extends Restrict {
    def evaluate = b % a.value == 0
    override def toString = u"$a divides $b"
  }
  case class RDividesQuotient(a: Param[Index], n: Int, d: Param[Index]) extends Restrict {
    def evaluate = {
      val q = Math.ceil(n.toDouble / d.value.toDouble).toInt
      a.value < q && (q % a.value == 0)
    }
    override def toString = u"$a divides ($n/$d)"
  }
  case class RProductLessThan(ps: Seq[Param[Index]], y: Int) extends Restrict {
    def evaluate = ps.map(_.value).product < y
    override def toString = u"product($ps) < $y"
  }
  case class REqualOrOne(ps: Seq[Param[Index]]) extends Restrict {
    def evaluate = {
      val values = ps.map(_.value).distinct
      values.length == 1 || (values.length == 2 && values.contains(1))
    }
    override def toString = u"$ps equal or one"
  }


  case class Domain[T](options: Seq[T], setter: T => Unit) {
    def apply(i: Int) = options(i)
    def set(i: Int) = setter(options(i))
    def setValue(v: T) = setter(v)
    def len = options.length

    override def toString = {
      if (len <= 10) "Domain(" + options.mkString(",") + ")"
      else "Domain(" + options.take(10).mkString(", ") + "... [" + (len-10) + " more])"
    }

    def filter(cond: => Boolean) = new Domain(options.filter{t => setValue(t); cond}, setter)
  }
  object Domain {
    def apply(range: RRange, setter: Int => Unit) = {
      if (range.start % range.step != 0) {
        val start = range.step*(range.start/range.step + 1)
        new Domain[Int]((start to range.end by range.step) :+ range.start, setter)
      }
      else new Domain[Int](range, setter)
    }
    def restricted(range: RRange, setter: Int => Unit, cond: => Boolean) {
      val (start, first) = if (range.start % range.step != 0) {
        val start = range.step*((range.start/range.step) + 1)
        setter(range.start);
        val first = if (cond) Some(range.start) else None
        (start, first)
      }
      else (range.start, None)

      val values = (start to range.end by range.step).filter{i => setter(i); cond } ++ first
      new Domain[Int](values, setter)
    }
  }

  def prune(params: Seq[Param[Index]], ranges: Map[Param[Index], RRange], restrict: Set[Restrict]) = {
    val pruneSingle = params.map{p =>
      val restricts = restrict.filter(_.dependsOnlyOn(p))
      if (restricts.nonEmpty)
        p -> Domain.restricted(ranges(p), {v: Int => p.c = BigDecimal(v) }, { restricts.forall(_.evaluate) })
      else
        p -> Domain[Int](ranges(p), {v: Int => p.c = BigDecimal(v)})
    }
    pruneSingle.map(_._2)
  }
}
