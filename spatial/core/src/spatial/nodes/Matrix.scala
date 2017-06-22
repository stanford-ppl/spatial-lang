package spatial.nodes

import argon.core._
import argon.nodes._
import spatial.aliases._

case class MatrixType[T](child: Type[T]) extends StructType[Matrix[T]] {
  override def wrapped(x: Exp[Matrix[T]]) = new Matrix(x)(child)
  override def unwrapped(x: Matrix[T]) = x.s
  override def stagedClass = classOf[Matrix[T]]
  override def typeArguments = List(child)
  override def fields = Seq("data" -> ArrayType(child), "rows" -> IntType, "cols" -> IntType)
}

case class Tensor3Type[T](child: Type[T]) extends StructType[Tensor3[T]] {
  override def wrapped(x: Exp[Tensor3[T]]) = new Tensor3(x)(child)
  override def unwrapped(x: Tensor3[T]) = x.s
  override def stagedClass = classOf[Tensor3[T]]
  override def typeArguments = List(child)
  override def fields = Seq("data" -> ArrayType(child), "dim0" -> IntType, "dim1" -> IntType, "dim2" -> IntType)
}

case class Tensor4Type[T](child: Type[T]) extends StructType[Tensor4[T]] {
  override def wrapped(x: Exp[Tensor4[T]]) = new Tensor4(x)(child)
  override def unwrapped(x: Tensor4[T]) = x.s
  override def stagedClass = classOf[Tensor4[T]]
  override def typeArguments = List(child)
  override def fields = Seq("data" -> ArrayType(child), "dim0" -> IntType, "dim1" -> IntType, "dim2" -> IntType, "dim3" -> IntType)
}

case class Tensor5Type[T](child: Type[T]) extends StructType[Tensor5[T]] {
  override def wrapped(x: Exp[Tensor5[T]]) = new Tensor5(x)(child)
  override def unwrapped(x: Tensor5[T]) = x.s
  override def stagedClass = classOf[Tensor5[T]]
  override def typeArguments = List(child)
  override def fields = Seq("data" -> ArrayType(child), "dim0" -> IntType, "dim1" -> IntType, "dim2" -> IntType, "dim3" -> IntType, "dim4" -> IntType)
}


