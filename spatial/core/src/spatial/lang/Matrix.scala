package spatial.lang

import argon.core._
import forge._
import org.virtualized._
import argon.nodes._
import spatial.nodes._

case class Matrix[T:Type](s: Exp[Matrix[T]]) extends Struct[Matrix[T]] {
  @internal def data: MArray[T] = field[MArray[T]]("data")

  @api def rows: Index = field[Index]("rows")
  @api def cols: Index = field[Index]("cols")
  @api def flatten: MArray[T] = data
  @api def apply(i: Index, j: Index): T = data.apply(i*cols + j)
  @api def update(i: Index, j: Index, elem: T): MUnit = data.update(i*cols + j, elem)
}
object Matrix {
  implicit def matrixType[T:Type]: StructType[Matrix[T]] = MatrixType(typ[T])
}

case class Tensor3[T:Type](s: Exp[Tensor3[T]]) extends Struct[Tensor3[T]] {
  @internal def data: MArray[T] = field[MArray[T]]("data")

  @api def dim0: Index = field[Index]("dim0")
  @api def dim1: Index = field[Index]("dim1")
  @api def dim2: Index = field[Index]("dim2")
  @api def flatten: MArray[T] = data
  @api def apply(i: Index, j: Index, k: Index): T = data.apply(i*dim1*dim2 + j*dim2 + k)
  @api def update(i: Index, j: Index, k: Index, elem: T): MUnit = data.update(i*dim1*dim2 + j*dim1 + k, elem)
}
object Tensor3 {
  implicit def tensor3Type[T:Type]: StructType[Tensor3[T]] = Tensor3Type(typ[T])
}

case class Tensor4[T:Type](s: Exp[Tensor4[T]]) extends Struct[Tensor4[T]] {
  @internal def data: MArray[T] = field[MArray[T]]("data")

  @api def dim0: Index = field[Index]("dim0")
  @api def dim1: Index = field[Index]("dim1")
  @api def dim2: Index = field[Index]("dim2")
  @api def dim3: Index = field[Index]("dim3")
  @api def flatten: MArray[T] = data
  @api def apply(i: Index, j: Index, k: Index, l: Index): T = data.apply(i*dim1*dim2*dim3 + j*dim2*dim3 + k*dim3 + l)
  @api def update(i: Index, j: Index, k: Index, l: Index, elem: T): MUnit = data.update(i*dim1*dim2*dim3 + j*dim2*dim3 + k*dim3 + l, elem)
}
object Tensor4 {
  implicit def tensor4Type[T:Type]: StructType[Tensor4[T]] = Tensor4Type(typ[T])
}

case class Tensor5[T:Type](s: Exp[Tensor5[T]]) extends Struct[Tensor5[T]] {
  @internal def data: MArray[T] = field[MArray[T]]("data")

  @api def dim0: Index = field[Index]("dim0")
  @api def dim1: Index = field[Index]("dim1")
  @api def dim2: Index = field[Index]("dim2")
  @api def dim3: Index = field[Index]("dim3")
  @api def dim4: Index = field[Index]("dim4")
  @api def flatten: MArray[T] = data
  @api def apply(i: Index, j: Index, k: Index, l: Index, m: Index): T = data.apply(i*dim1*dim2*dim3*dim4 + j*dim2*dim3*dim4 + k*dim3*dim4 + l*dim4 + m)
  @api def update(i: Index, j: Index, k: Index, l: Index, m: Index, elem: T): MUnit = data.update(i*dim1*dim2*dim3*dim4 + j*dim2*dim3*dim4 + k*dim3*dim4 + l*dim4 + m, elem)
}
object Tensor5 {
  implicit def tensor5Type[T:Type]: StructType[Tensor5[T]] = Tensor5Type(typ[T])
}
