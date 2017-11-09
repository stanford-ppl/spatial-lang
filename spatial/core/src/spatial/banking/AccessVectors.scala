package spatial.banking

import argon.core._
import forge._
import spatial.aliases._
import spatial.metadata._
import spatial.utils._

abstract class AccessVector {
  def is: Seq[Exp[Index]]
}

case class RandomVector(x: Option[Exp[Index]], uroll: Map[Seq[Int],Exp[Index]], len: Int) extends AccessVector {
  def is: Seq[Exp[Index]] = uroll.values.toSeq
}

case class AffineVector(as: Array[Int], is: Seq[Exp[Index]], b: Int) extends AccessVector {
  def remap(indices: Seq[Exp[Index]]): AffineVector = {
    val as2 = indices.map{ind =>
      val pos = is.indexOf(ind)
      if (pos >= 0) as(pos) else 0
    }.toArray
    AffineVector(as2, indices, b)
  }
}

case class CompactMatrix(vectors: Array[AccessVector], access: Access, vecId: Option[Int]) {
  def indices: Seq[Exp[Index]] = vectors.flatMap(_.is)
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

  def getAccessPairsIfAffine(dims: Seq[Int]): Option[AccessPair] = {
    val vecs = Array.tabulate(dims.length){i => vectors(dims(i)) }
    if (vecs.forall(_.isInstanceOf[AffineVector])) {
      val affineVectors = vecs.asInstanceOf[Array[AffineVector]]
      val a = Matrix(affineVectors.map{row => row.as})
      val c = affineVectors.map{row => row.b}
      Option((a, c))
    }
    else None
  }

  /**
    * Returns true if the space of addresses in a is statically known to include all of the addresses in b
    */
  def containsSpace(b: AccessMatrix): Boolean = {

  }

  /**
    * Returns true if the space of addresses in a and b may have at least one element in common
    */
  def intersectsSpace(b: AccessMatrix): Boolean = {

  }

  /**
    * Returns true if there exists a reachable multi-dimensional index I such that addr_a(I) = addr_b(I)
    */
  def intersects(b: AccessMatrix): Boolean = {

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