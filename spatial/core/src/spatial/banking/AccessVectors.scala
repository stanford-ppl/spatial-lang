package spatial.banking

import argon.core._
import spatial.aliases._

abstract class CompactVector {
  def is: Seq[Exp[Index]]
  def access: Access
}

case class CompactRandomVector(access: Access) extends CompactVector {
  def is: Seq[Exp[Index]] = Nil
}
case class CompactAffineVector(as: Array[Int], is: Seq[Exp[Index]], b: Int, access: Access) extends CompactVector



abstract class UnrolledVector {
  def is: Seq[Exp[Index]]
  def access: Access
  def id: Seq[Int]
}

case class UnrolledRandomVector(is: Seq[Exp[Index]], access: Access, id: Seq[Int]) extends UnrolledVector
case class UnrolledAffineVector(as: Array[Int], is: Seq[Exp[Index]], b: Int, access: Access, id: Seq[Int]) extends UnrolledVector
