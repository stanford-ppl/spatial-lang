package spatial.banking

import argon.core._
import forge._
import spatial.aliases._
import spatial.metadata._
import spatial.utils._

abstract class AccessVector {
  def is: Seq[Exp[Index]]
  def str(i: Int): String
}

case class RandomVector(x: Option[Exp[Index]], uroll: Map[Seq[Int],Exp[Index]], vecId: Option[Int]) extends AccessVector {
  def is: Seq[Exp[Index]] = uroll.values.toSeq // TODO: Should this be Nil if vecId is undefined?
  def str(i: Int): String = "*"
}

case class AffineVector(as: Array[Int], is: Seq[Exp[Index]], b: Int) extends AccessVector {
  def remap(indices: Seq[Exp[Index]]): AffineVector = {
    val as2 = indices.map{ind =>
      val pos = is.indexOf(ind)
      if (pos >= 0) as(pos) else 0
    }.toArray
    AffineVector(as2, indices, b)
  }
  def str(i: Int): String = if (i < as.length) as(i).toString else b.toString
  override def toString: String = {
    val plus = if (as.length > 0) " + " else " "
    "Affine(" + as.zip(is).map{case (a,i) => s"$a$i" }.mkString(" + ") + plus + b + ")"
  }
}

case class CompactMatrix(vectors: Array[AccessVector], access: Access, vecId: Option[Int] = None) {
  def indices: Seq[Exp[Index]] = vectors.flatMap(_.is)

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
      val a = Matrix(affineVectors.map{row => row.as})
      val c = affineVectors.map{row => row.b}
      val pair = (a,c)
      //pair.printWithTab("      ")
      Option(pair)
    }
    else {
      //dbg(s"NOT AFFINE (RANDOM ACCESS?)")
      None
    }
  }

  // TODO
  /**
    * Returns true if the space of addresses in a is statically known to include all of the addresses in b
    */
  def containsSpace(b: AccessMatrix, domain: IndexDomain): Boolean = {
    false
  }

  /**
    * Returns true if the space of addresses in a and b may have at least one element in common
    */
  def intersectsSpace(b: AccessMatrix, domain: IndexDomain): Boolean = {
    true
  }

  /**
    * Returns true if there exists a reachable multi-dimensional index I such that addr_a(I) = addr_b(I)
    */
  def intersects(b: AccessMatrix, domain: IndexDomain): Boolean = {
    true
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
  data: Array[Array[Int]],
  rows: Int,
  cols: Int
) {
  def apply(r: Int, c: Int): Int = data(r)(c)
}

object Matrix {
  def apply(data: Array[Array[Int]]) = new Matrix(data, data.length, data.head.length)
}