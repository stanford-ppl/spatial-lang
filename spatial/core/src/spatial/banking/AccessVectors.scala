package spatial.banking

import argon.core._
import forge._
import spatial.aliases._
import spatial.metadata._
import spatial.utils._

abstract class AccessVector { def is: Seq[Exp[Index]] }

case class RandomVector(x: Option[Exp[Index]]) extends AccessVector { def is: Seq[Exp[Index]] = Nil }

case class AffineVector(as: Array[Int], is: Seq[Exp[Index]], b: Int) extends AccessVector {
  def remap(indices: Seq[Exp[Index]]): AffineVector = {
    val as2 = indices.map{ind =>
      val pos = is.indexOf(ind)
      if (pos >= 0) as(pos) else 0
    }.toArray
    AffineVector(as2, indices, b)
  }
}

case class CompactMatrix(vectors: Array[AccessVector], access: Access, vecId: Int = -1) {
  def indices: Seq[Exp[Index]] = vectors.flatMap(_.is)

  /**
    * Convert this compact access matrix to multiple unrolled access matrices
    * by simulating loop parallelization/unrolling
    */
  @stateful def unroll(mem: Exp[_], indices: Seq[Exp[Index]]): Seq[AccessMatrix] = {
    val is = iteratorsBetween(access, (parentOf(mem).get,-1))
    val ps = is.map{i => parFactorOf(i).toInt }

    val ndims = is.length
    val prods = List.tabulate(ndims){i => ps.slice(i+1,ndims).product }
    val total = ps.product

    def expand(vector: AccessVector, id: Seq[Int]): AccessVector = vector match {
      case vec: RandomVector => vec

      // Note that there's three sets of iterators here:
      //  is      - iterators defined between the memory and this access
      //  inds    - iterators used by this affine access
      //  indices - iterators used by ALL accesses to this memory
      case AffineVector(as,inds,b) =>
        val unrolled = indices.map{i =>
          val idxAccess = inds.indexOf(i)
          val idxHierarchy = is.indexOf(i)
          val a_orig = if (idxAccess >= 0) as(idxAccess) else 0
          val p = if (idxHierarchy >= 0) ps(idxHierarchy) else 0
          val n = if (idxHierarchy >= 0) id(idxHierarchy) else 0
          val a = a_orig*p
          val b_i = a_orig*n
          (a, b_i)
        }
        val as2 = unrolled.map(_._1).toArray
        val b2 = unrolled.map(_._2).sum + b
        AffineVector(as2,inds,b2)
    }

    // Fake unrolling
    // e.g. change 2i + 3 with par(i) = 2 into
    // 4i + 0*2 + 3 = 4i + 3
    // 4i + 1*2 + 3 = 4i + 5
    Seq.tabulate(total){x =>
      val id = Seq.tabulate(ndims){d => (x / prods(d)) % ps(d) }
      val uvectors = vectors.map{vector => expand(vector, id) }
      val unrollId = if (vecId >= 0) id :+ vecId else id
      AccessMatrix(uvectors, access, indices, unrollId)
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