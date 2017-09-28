package spatial.metadata

import argon.core._
import argon.util.recursive.collectSet
import forge._
import spatial.aliases._


sealed abstract class Restrict {this: Product =>
  @stateful def evaluate(): Boolean
  def deps: Set[Param[_]] = collectSet{case p: Param[_] => p}(productIterator)
  def dependsOnlyOn(x: Param[_]*): Boolean = (deps diff x.toSet).isEmpty
}
object Restrict {
  implicit class ParamValue(x: Param[Index]) {
    @stateful def v: Int = x.value match {
      case c: FloatPoint => c.toInt
      case c: FixedPoint => c.toInt
      case c: Int        => c
    }
  }
}

import Restrict._

case class RLess(a: Param[Index], b: Param[Index]) extends Restrict {
  @stateful def evaluate(): Boolean = a.v < b.v
  override def toString = u"$a < $b"
}
case class RLessEqual(a: Param[Index], b: Param[Index]) extends Restrict {
  @stateful def evaluate(): Boolean = a.v <= b.v
  override def toString = u"$a <= $b"
}
case class RDivides(a: Param[Index], b: Param[Index]) extends Restrict {
  @stateful def evaluate(): Boolean = b.v % a.v == 0
  override def toString = u"$a divides $b"
}
case class RDividesConst(a: Param[Index], b: Int) extends Restrict {
  @stateful def evaluate(): Boolean = b % a.v == 0
  override def toString = u"$a divides $b"
}
case class RDividesQuotient(a: Param[Index], n: Int, d: Param[Index]) extends Restrict {
  @stateful def evaluate(): Boolean = {
    val q = Math.ceil(n.toDouble / d.v.toDouble).toInt
    a.v < q && (q % a.v == 0)
  }
  override def toString = u"$a divides ($n/$d)"
}
case class RProductLessThan(ps: Seq[Param[Index]], y: Int) extends Restrict {
  @stateful def evaluate(): Boolean = ps.map(_.v).product < y
  override def toString = u"product($ps) < $y"
}
case class REqualOrOne(ps: Seq[Param[Index]]) extends Restrict {
  @stateful def evaluate(): Boolean = {
    val values = ps.map(_.value).distinct
    values.length == 1 || (values.length == 2 && values.contains(1))
  }
  override def toString = u"$ps equal or one"
}


case class Domain[T](options: Seq[T], setter: (T,State) => Unit, getter: State => T) {
  def apply(i: Int): T = options(i)
  @stateful def value: T = getter(state)
  @stateful def set(i: Int): Unit = setter(options(i), state)
  @stateful def setValue(v: T): Unit = setter(v, state)
  def len: Int = options.length

  @stateful def filter(cond: State => Boolean): Domain[T] = {
    val values = options.filter{i => setter(i, state); cond(state) }
    new Domain[T](values, setter, getter)
  }

  override def toString: String = {
    if (len <= 10) "Domain(" + options.mkString(",") + ")"
    else "Domain(" + options.take(10).mkString(", ") + "... [" + (len-10) + " more])"
  }

  @stateful def filter(cond: => Boolean) = new Domain(options.filter{t => setValue(t); cond}, setter, getter)
}
object Domain {
  def apply(range: Range, setter: (Int,State) => Unit, getter: State => Int): Domain[Int] = {
    if (range.start % range.step != 0) {
      val start = range.step*(range.start/range.step + 1)
      new Domain[Int]((start to range.end by range.step) :+ range.start, setter, getter)
    }
    else new Domain[Int](range, setter, getter)
  }
  @stateful def restricted(range: Range, setter: (Int,State) => Unit, getter: State => Int, cond: State => Boolean): Domain[Int] = {
    val (start, first) = if (range.start % range.step != 0) {
      val start = range.step*((range.start/range.step) + 1)
      setter(range.start, state)
      val first = if (cond(state)) Some(range.start) else None
      (start, first)
    }
    else (range.start, None)

    val values = (start to range.end by range.step).filter{i => setter(i, state); cond(state) } ++ first
    new Domain[Int](values, setter, getter)
  }
}


