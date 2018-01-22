package spatial.banking

import argon.core._
import forge._
import spatial.aliases
import spatial.aliases._
import spatial.metadata._
import spatial.poly.Polytope
import spatial.utils._

case class IndexDomain(domain: Array[Array[Int]]) {
  lazy val str: String = domain.map{vec => s"""1 ${vec.mkString(" ")}""" }.mkString("\n") + "\n"
  def map[T](func: Array[Int] => T): Seq[T] = domain.map(func)
  def headOption: Option[Array[Int]] = domain.headOption
  def length: Int = domain.length
}

abstract class AccessVector {
  def indices: Seq[Exp[Index]]
  def unrollIndices: Seq[Exp[Index]]
  def str(i: Int): String
}

case class RandomVector(x: Option[Exp[Index]], uroll: Map[Seq[Int],Exp[Index]], vecId: Option[Int]) extends AccessVector {
  def indices: Seq[Exp[Index]] = x.toSeq // TODO: Should this be Nil if vecId is undefined?
  def unrollIndices: Seq[Exp[Index]] = uroll.values.toSeq
  def str(i: Int): String = "*"
}

case class AffineVector(as: Array[Int], is: Seq[Exp[Index]], b: Int, uroll: Map[(Exp[Index],Seq[Int]),Exp[Index]]) extends AccessVector {
  def indices: Seq[Exp[Index]] = is
  def unrollIndices: Seq[Exp[Index]] = is.filterNot(x => uroll.values.exists{y => x == y}) ++ uroll.keys.map(_._1)

  def remap(indices: Seq[Exp[Index]]): AffineVector = {
    val as2 = indices.map{ind =>
      val pos = is.indexOf(ind)
      if (pos >= 0) as(pos) else 0
    }.toArray
    AffineVector(as2, indices, b, uroll)
  }
  def str(i: Int): String = if (i < as.length) as(i).toString else b.toString
  override def toString: String = {
    val plus = if (as.length > 0) " + " else " "
    "Affine(" + as.zip(is).map{case (a,i) => s"$a$i" }.mkString(" + ") + plus + b + ")"
  }
}

case class CompactMatrix(vectors: Array[AccessVector], access: Access, vecId: Option[Int] = None) {
  def indices: Seq[Exp[Index]] = vectors.flatMap(_.indices)
  def unrollIndices: Seq[Exp[Index]] = vectors.flatMap(_.unrollIndices)

  @stateful def printWithTab(tab: String): Unit = {
    dbg(tab + s"""${str(access.node)}""")
    val inds = indices.distinct
    val heading = inds.distinct.map(i => u"$i") :+ "1"
    val entries = heading +: vectors.map{
      case v: RandomVector => Seq.tabulate(inds.length+1){i => v.str(i) }
      case v: AffineVector =>
        val rvec = v.remap(inds)
        Seq.tabulate(inds.length+1){i => rvec.str(i) }
    }
    val maxCol = entries.flatten.map{x: String => x.length }.max
    entries.foreach{row =>
      dbg(tab + row.map{x => " "*(maxCol - x.length + 1) + x }.mkString(" "))
    }
  }
}

case class AccessPair(a: Matrix, c: Seq[Int]) {
  @stateful def printWithTab(tab: String): Unit = {
    val data = a.data

    val entries = data.map{x => x.map(_.toString) }
    val maxCol  = entries.map{_.map(_.length).fold(0){Math.max}}.fold(0){Math.max}
    entries.zip(c).foreach{case (row,rc) =>
      dbg(tab + row.map{x => " "*(maxCol - x.length + 1) + x}.mkString(" ") + " " + rc)
    }
  }

  /*override def equals(o: Any): Boolean = o match {
    case AccessPair(a1,c1) => true
      //c.zip(c1).forall{case (b0,b1) => b0 == b1 } &&
      //  a.data.zip(a1.data).forall{case (v0,v1) => v0.zip(v1).forall{case (x0,x1) => x0 == x1 }}
    case _ => false
  }*/
}


case class AccessMatrix(
  vectors: Array[AccessVector],
  access:  Access,
  is:      Seq[Exp[Index]],
  id:      Seq[Int]
) {

  def unrolledAccess: UnrolledAccess = (access.node, access.ctrl, id)

  /*def remap(indices: Seq[Exp[Index]]): AccessMatrix = {
    val vecs = vectors.map{_.remap(indices)}
    AccessMatrix(vecs,access,indices,id)
  }*/

  // Stateful is only needed for debugging
  @stateful def getAccessPairsIfAffine(dims: Seq[Int]): Option[AccessPair] = {
    val vecs = Array.tabulate(dims.length){i => vectors(dims(i)) }
    if (vecs.forall(_.isInstanceOf[AffineVector])) {
      val affineVectors = vecs.map(_.asInstanceOf[AffineVector])
      val a = Matrix(affineVectors.map{row => row.as.toSeq })
      val c = affineVectors.map{row => row.b}
      Some(AccessPair(a,c))
    }
    else {
      //dbg(s"NOT AFFINE (RANDOM ACCESS?)")
      None
    }
  }

  /**
    * Returns true if the space of addresses in a is statically known to include all of the addresses in b
    * TODO: Used for reaching write calculation
    */
  @stateful def containsSpace(b: AccessMatrix, domain: IndexDomain): Boolean = {
    false
  }

  /**
    * Returns true if the space of addresses in a and b may have at least one element in common
    * TODO: Used for reaching write calculation
    */
  @stateful def intersectsSpace(b: AccessMatrix, domain: IndexDomain): Boolean = {
    true
  }

  /**
    * Returns true if there exists a reachable multi-dimensional index I such that addr_a(I) = addr_b(I)
    *
    * True if all given dimensions may intersect. Trivially true for random accesses (at least for now)
    */
  @stateful def intersects(b: AccessMatrix, domain: IndexDomain): Boolean = {
    val vecs = this.vectors.zip(b.vectors).collect{
      case (AffineVector(a0,_,c0,_), AffineVector(a1,_,c1,_)) =>
        val dA = Array.tabulate(a0.length){i => a0(i) - a1(i) }
        val dC = c0 - c1
        s"""0 ${dA.mkString(" ")} $dC"""

      // Other cases are trivially true (for now)
    }
    if (vecs.isEmpty) true
    else {
      val nCols = domain.headOption.map(_.length).getOrElse(0) + 1
      val nRows = vecs.length + domain.length

      val mat = s"$nRows $nCols\n" + domain.str + vecs.mkString("\n")
      !Polytope.isEmpty(mat)
    }
  }

  @stateful def printWithTab(tab: String): Unit = {
    dbg(tab + s"""${str(access.node)} [id: ${id.mkString(", ")}]""")
    val heading = is.map(i => u"$i") :+ "c"
    val entries = heading +: vectors.map{vec =>
      Seq.tabulate(is.length+1){i => vec.str(i) }
    }
    val maxCol = entries.flatten.map{x: String => x.length }.fold(0){Math.max}
    entries.foreach{row =>
      dbg(tab + row.map{x => " "*(maxCol - x.length + 1) + x }.mkString(" "))
    }
  }
}


case class Matrix(
  data: Seq[Seq[Int]],
  rows: Int,
  cols: Int
) {
  def apply(r: Int, c: Int): Int = data(r)(c)
}

object Matrix {
  def apply(data: Seq[Seq[Int]]) = new Matrix(data, data.length, data.head.length)
}