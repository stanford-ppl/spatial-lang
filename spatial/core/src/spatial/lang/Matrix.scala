package spatial.lang

import argon.core._
import forge._
import org.virtualized._
import argon.nodes._
import spatial.nodes._

case class Matrix[T:Type](s: Exp[Matrix[T]]) extends Struct[Matrix[T]] {
  @internal def data: MArray[T] = field[MArray[T]]("data")
  /** Returns the number of rows in this Matrix. **/
  @api def rows: Index = field[Index]("rows")
  /** Returns the number of columns in this Matrix. **/
  @api def cols: Index = field[Index]("cols")
  /** Returns the element in this Matrix at the given 2-dimensional address (`i`, `j`). **/
  @api def apply(i: Index, j: Index): T = data.apply(i*cols + j)
  /** Updates the element at the given two dimensional address to `elem`. **/
  @api def update(i: Index, j: Index, elem: T): MUnit = data.update(i*cols + j, elem)

  /** Returns a flattened, immutable @Array view of this Matrix's data. **/
  @api def flatten: MArray[T] = data

  /** Returns the number of elements in the Matrix. **/
  @api def length: Index = rows * cols

  /** Applies the function `func` on each element in this Matrix. **/
  @api def foreach(func: T => MUnit): MUnit = data.foreach(func)
  /** Returns a new Matrix created using the mapping `func` over each element in this Matrix. **/
  @api def map[R:Type](func: T => R): Matrix[R] = matrix(data.map(func), rows, cols)
  /** Returns a new Matrix created using the pairwise mapping `func` over each element in this Matrix
    * and the corresponding element in `that`.
    */
  @api def zip[S:Type,R:Type](that: Matrix[S])(func: (T,S) => R): Matrix[R] = matrix(data.zip(that.data)(func), rows, cols)
  /** Reduces the elements in this Matrix into a single element using associative function `rfunc`. **/
  @api def reduce(rfunc: (T,T) => T): T = data.reduce(rfunc)
  /** Returns the transpose of this Matrix. **/
  @api def transpose(): Matrix[T] = (0::cols, 0::rows){(j, i) => apply(i,j) }
}
object Matrix {
  implicit def matrixType[T:Type]: StructType[Matrix[T]] = MatrixType(typ[T])

  /** Returns an immutable Matrix with the given `rows` and `cols` and elements defined by `func`. **/
  @api def tabulate[T:Type](rows: Index, cols: Index)(func: (Index, Index) => T): Matrix[T] = {
    val data = MArray.tabulate(rows*cols){x =>
      val i = x / cols
      val j = x % cols
      func(i,j)
    }
    matrix(data, rows, cols)
  }
  /**
    * Returns an immutable Matrix with the given `rows` and `cols` and elements defined by `func`.
    * Note that while `func` does not depend on the index, it is still executed `rows`*`cols` times.
    */
  @api def fill[T:Type](rows: Index, cols: Index)(func: => T): Matrix[T] = this.tabulate(rows, cols){(_,_) => func}
}

case class Tensor3[T:Type](s: Exp[Tensor3[T]]) extends Struct[Tensor3[T]] {
  @internal def data: MArray[T] = field[MArray[T]]("data")
  /** Returns the first dimension of this Tensor3. **/
  @api def dim0: Index = field[Index]("dim0")
  /** Returns the second dimension of this Tensor3. **/
  @api def dim1: Index = field[Index]("dim1")
  /** Returns the third dimension of this Tensor3. **/
  @api def dim2: Index = field[Index]("dim2")
  /** Returns the element in this Tensor3 at the given 3-dimensional address. **/
  @api def apply(i: Index, j: Index, k: Index): T = data.apply(i*dim1*dim2 + j*dim2 + k)
  /** Updates the element at the given 3-dimensional address to `elem`. **/
  @api def update(i: Index, j: Index, k: Index, elem: T): MUnit = data.update(i*dim1*dim2 + j*dim1 + k, elem)
  /** Returns a flattened, immutable @Array view of this Tensor3's data. **/
  @api def flatten: MArray[T] = data
  /** Returns the number of elements in the Tensor3. **/
  @api def length: Index = dim0 * dim1 * dim2

  /** Applies the function `func` on each element in this Tensor3. **/
  @api def foreach(func: T => MUnit): MUnit = data.foreach(func)
  /** Returns a new Tensor3 created using the mapping `func` over each element in this Tensor3. **/
  @api def map[R:Type](func: T => R): Tensor3[R] = tensor3(data.map(func), dim0, dim1, dim2)
  /** Returns a new Tensor3 created using the pairwise mapping `func` over each element in this Tensor3
    * and the corresponding element in `that`.
    */
  @api def zip[S:Type,R:Type](that: Tensor3[S])(func: (T,S) => R): Tensor3[R] = tensor3(data.zip(that.data)(func), dim0, dim1, dim2)
  /** Reduces the elements in this Tensor3 into a single element using associative function `rfunc`. **/
  @api def reduce(rfunc: (T,T) => T): T = data.reduce(rfunc)
}
object Tensor3 {
  implicit def tensor3Type[T:Type]: StructType[Tensor3[T]] = Tensor3Type(typ[T])

  /** Returns an immutable Tensor3 with the given dimensions and elements defined by `func`. **/
  @api def tabulate[T:Type](dim0: Index, dim1: Index, dim2: Index)(func: (Index, Index, Index) => T): Tensor3[T] = {
    val data = MArray.tabulate(dim0*dim1*dim2){x =>
      val i = x / (dim1*dim2)     // Page
      val j = (x / dim2) % dim1   // Row
      val k = x % dim2            // Col
      func(i,j,k)
    }
    tensor3(data, dim0, dim1, dim2)
  }
  /**
    * Returns an immutable Tensor3 with the given dimensions and elements defined by `func`.
    * Note that while `func` does not depend on the index, it is still executed multiple times.
    */
  @api def fill[T:Type](dim0: Index, dim1: Index, dim2: Index)(func: => T): Tensor3[T]
    = this.tabulate(dim0,dim1,dim2){(_,_,_) => func}
}

case class Tensor4[T:Type](s: Exp[Tensor4[T]]) extends Struct[Tensor4[T]] {
  @internal def data: MArray[T] = field[MArray[T]]("data")

  /** Returns the first dimension of this Tensor4. **/
  @api def dim0: Index = field[Index]("dim0")
  /** Returns the second dimension of this Tensor4. **/
  @api def dim1: Index = field[Index]("dim1")
  /** Returns the third dimension of this Tensor4. **/
  @api def dim2: Index = field[Index]("dim2")
  /** Returns the fourth dimension of this Tensor4. **/
  @api def dim3: Index = field[Index]("dim3")
  /** Returns the element in this Tensor4 at the given 4-dimensional address. **/
  @api def apply(i: Index, j: Index, k: Index, l: Index): T = data.apply(i*dim1*dim2*dim3 + j*dim2*dim3 + k*dim3 + l)
  /** Updates the element at the given 4-dimensional address to `elem`. **/
  @api def update(i: Index, j: Index, k: Index, l: Index, elem: T): MUnit = data.update(i*dim1*dim2*dim3 + j*dim2*dim3 + k*dim3 + l, elem)
  /** Returns a flattened, immutable @Array view of this Tensor4's data. **/
  @api def flatten: MArray[T] = data
  /** Returns the number of elements in the Tensor4. **/
  @api def length: Index = dim0 * dim1 * dim2 * dim3

  /** Applies the function `func` on each element in this Tensor4. **/
  @api def foreach(func: T => MUnit): MUnit = data.foreach(func)
  /** Returns a new Tensor4 created using the mapping `func` over each element in this Tensor4. **/
  @api def map[R:Type](func: T => R): Tensor4[R] = tensor4(data.map(func), dim0, dim1, dim2, dim3)
  /** Returns a new Tensor4 created using the pairwise mapping `func` over each element in this Tensor4
    * and the corresponding element in `that`.
    */
  @api def zip[S:Type,R:Type](b: Tensor4[S])(func: (T,S) => R): Tensor4[R] = tensor4(data.zip(b.data)(func), dim0, dim1, dim2, dim3)
  /** Reduces the elements in this Tensor4 into a single element using associative function `rfunc`. **/
  @api def reduce(rfunc: (T,T) => T): T = data.reduce(rfunc)
}
object Tensor4 {
  implicit def tensor4Type[T:Type]: StructType[Tensor4[T]] = Tensor4Type(typ[T])

  /** Returns an immutable Tensor4 with the given dimensions and elements defined by `func`. **/
  @api def tabulate[T:Type](dim0: Index, dim1: Index, dim2: Index, dim3: Index)(func: (Index, Index, Index, Index) => T): Tensor4[T] = {
    val data = MArray.tabulate(dim0*dim1*dim2*dim3){x =>
      val i0 = x / (dim1*dim2*dim3)       // Cube
      val i1 = (x / (dim2*dim3)) % dim1   // Page
      val i2 = (x / dim3) % dim2          // Row
      val i3 = x % dim3                   // Col

      func(i0,i1,i2,i3)
    }
    tensor4(data, dim0, dim1, dim2, dim3)
  }
  /**
    * Returns an immutable Tensor4 with the given dimensions and elements defined by `func`.
    * Note that while `func` does not depend on the index, it is still executed multiple times.
    */
  @api def fill[T:Type](dim0: Index, dim1: Index, dim2: Index, dim3: Index)(func: => T): Tensor4[T]
  = this.tabulate(dim0,dim1,dim2,dim3){(_,_,_,_) => func}
}

case class Tensor5[T:Type](s: Exp[Tensor5[T]]) extends Struct[Tensor5[T]] {
  @internal def data: MArray[T] = field[MArray[T]]("data")

  /** Returns the first dimension of this Tensor5. **/
  @api def dim0: Index = field[Index]("dim0")
  /** Returns the second dimension of this Tensor5. **/
  @api def dim1: Index = field[Index]("dim1")
  /** Returns the third dimension of this Tensor5. **/
  @api def dim2: Index = field[Index]("dim2")
  /** Returns the fourth dimension of this Tensor5. **/
  @api def dim3: Index = field[Index]("dim3")
  /** Returns the fifth dimension of this Tensor5. **/
  @api def dim4: Index = field[Index]("dim4")
  /** Returns the element in this Tensor5 at the given 5-dimensional addreess. **/
  @api def apply(i: Index, j: Index, k: Index, l: Index, m: Index): T = data.apply(i*dim1*dim2*dim3*dim4 + j*dim2*dim3*dim4 + k*dim3*dim4 + l*dim4 + m)
  /** Updates the element at the given 5-dimensional address to `elem`. **/
  @api def update(i: Index, j: Index, k: Index, l: Index, m: Index, elem: T): MUnit = data.update(i*dim1*dim2*dim3*dim4 + j*dim2*dim3*dim4 + k*dim3*dim4 + l*dim4 + m, elem)
  /** Returns a flattened, immutable @Array view of this Tensor5's data. **/
  @api def flatten: MArray[T] = data
  /** Returns the number of elements in the Tensor5. **/
  @api def length: Index = dim0 * dim1 * dim2 * dim3 * dim4

  /** Applies the function `func` on each element in this Tensor5. **/
  @api def foreach(func: T => MUnit): MUnit = data.foreach(func)
  /** Returns a new Tensor5 created using the mapping `func` over each element in this Tensor5. **/
  @api def map[R:Type](func: T => R): Tensor5[R] = tensor5(data.map(func), dim0, dim1, dim2, dim3, dim4)
  /** Returns a new Tensor5 created using the pairwise mapping `func` over each element in this Tensor5
    * and the corresponding element in `that`.
    */
  @api def zip[S:Type,R:Type](b: Tensor5[S])(func: (T,S) => R): Tensor5[R] = tensor5(data.zip(b.data)(func), dim0, dim1, dim2, dim3, dim4)
  /** Reduces the elements in this Tensor5 into a single element using associative function `rfunc`. **/
  @api def reduce(rfunc: (T,T) => T): T = data.reduce(rfunc)
}
object Tensor5 {
  implicit def tensor5Type[T:Type]: StructType[Tensor5[T]] = Tensor5Type(typ[T])

  /** Returns an immutable Tensor5 with the given dimensions and elements defined by `func`. **/
  @api def tabulate[T:Type](dim0: Index, dim1: Index, dim2: Index, dim3: Index, dim4: Index)(func: (Index, Index, Index, Index, Index) => T): Tensor5[T] = {
    val data = MArray.tabulate(dim0*dim1*dim2*dim3*dim4){x =>
      val i0 = x / (dim1*dim2*dim3*dim4)
      val i1 = (x / (dim2*dim3*dim4)) % dim1
      val i2 = (x / (dim3*dim4)) % dim2
      val i3 = (x / (dim4)) % dim3
      val i4 = x % dim4

      func(i0,i1,i2,i3,i4)
    }
    tensor5(data, dim0, dim1, dim2, dim3, dim4)
  }
  /**
    * Returns an immutable Tensor5 with the given dimensions and elements defined by `func`.
    * Note that while `func` does not depend on the index, it is still executed multiple times.
    */
  @api def fill[T:Type](dim0: Index, dim1: Index, dim2: Index, dim3: Index, dim4: Index)(func: => T): Tensor5[T]
    = this.tabulate(dim0,dim1,dim2,dim3,dim4){(_,_,_,_,_) => func}

}
