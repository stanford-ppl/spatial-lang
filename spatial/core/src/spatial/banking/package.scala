package spatial

import argon.core._
import spatial.aliases._

package object banking {
  type AccessMatrix = Array[UnrolledVector]
  type AccessPair = (Matrix, Array[Int])


  implicit class AccessMatrixOps(a: AccessMatrix) {
    def access: Access = a.head.access
    def takeDims(dims: Seq[Int]): AccessMatrix = Array.tabulate(dims.length){i => a(dims(i)) }

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

}
