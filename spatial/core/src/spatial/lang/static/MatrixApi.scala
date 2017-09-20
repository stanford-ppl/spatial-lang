package spatial.lang.static

import argon.core._
import forge._
import org.virtualized.virtualize

trait MatrixApi { this: SpatialApi =>

  implicit class VectorReshaper[T](a: MArray[T]) {
    private implicit val mT = a.s.tp.typeArguments.head.asInstanceOf[Type[T]]
    @virtualize
    @api def reshape(dim0: Index, dim1: Index): Matrix[T] = {
      assert(dim0*dim1 == a.length, "Number of elements in vector ("+a.length.toText+") must match number of elements in matrix ("+dim0.toText+"x"+dim1.toText+")")
      matrix(a, dim0, dim1)
    }
    @virtualize
    @api def reshape(dim0: Index, dim1: Index, dim2: Index): Tensor3[T] = {
      assert(dim0*dim1*dim2 == a.length, "Number of elements in vector ("+a.length.toText+") must match number of elements in matrix ("+dim0.toText+"x"+dim1.toText+"x"+dim2.toText+")")
      tensor3(a, dim0, dim1, dim2)
    }
    @virtualize
    @api def reshape(dim0: Index, dim1: Index, dim2: Index, dim3: Index): Tensor4[T] = {
      assert(dim0*dim1*dim2*dim3 == a.length, "Number of elements in vector ("+a.length.toText+") must match number of elements in matrix ("+dim0.toText+"x"+dim1.toText+"x"+dim2.toText+"x"+dim3.toText+")")
      tensor4(a, dim0, dim1, dim2, dim3)
    }
    @virtualize
    @api def reshape(dim0: Index, dim1: Index, dim2: Index, dim3: Index, dim4: Index): Tensor5[T] = {
      assert(dim0*dim1*dim2*dim3*dim4 == a.length, "Number of elements in vector ("+a.length.toText+") must match number of elements in matrix ("+dim0.toText+"x"+dim1.toText+"x"+dim2.toText+"x"+dim3.toText+"x"+dim4.toText+")")
      tensor5(a, dim0, dim1, dim2, dim3, dim4)
    }
  }

  implicit class FilterToeplitz[T<:MetaAny[T]:Type:Num](a: MArray[T]) {
    @virtualize
    @api def toeplitz(filterdim0: Index, filterdim1: Index, imgdim0: Index, imgdim1: Index, stride0: Index, stride1: Index): Matrix[T] = {
      // TODO: Incorporate stride
      val pad0 = filterdim0 - 1 - (stride0-1)
      val pad1 = filterdim1 - 1 - (stride1-1)
      val out_rows = ((imgdim0+pad0)-filterdim0+1) * ((imgdim1+pad1)-filterdim1+1) / (stride0 * stride1)
      val out_cols = (imgdim0+pad0)*(imgdim1+pad1)

      Console.println("toeplitz dims are " + out_rows +","+ out_cols)

      val data = MArray.tabulate(out_rows * out_cols){k => 
        val i = (k / out_cols)
        val j = (k % out_cols)
        val sliding_window_row_correction = (i / imgdim1) * pad1
        val stride0_correction = (i / imgdim1) * ((stride0-1)*imgdim1)
        val stride1_correction = (i % imgdim1) * (stride1-1)
        val filter_i_base = j - i - (sliding_window_row_correction + stride0_correction + stride1_correction) 
        val filter_i = (filter_i_base) / (imgdim1+pad1)
        val filter_j = ((j - i - sliding_window_row_correction) % (imgdim1+pad1))
        if (filter_i_base >= 0 && filter_j < filterdim1 && filter_j >= 0 && filter_i < filterdim0 && filter_i >= 0) a(filter_i * filterdim1 + filter_j) else 0.to[T]
      }
      matrix(data, out_rows, out_cols)
      // a.reshape(filterdim0, filterdim1) 
    }
  }

  implicit class MatrixConstructor(ranges: (MRange, MRange) ) {
    @api def apply[A,T](func: (Index,Index) => A)(implicit lft: Lift[A,T]): Matrix[T] = {
      implicit val mT: Type[T] = lft.staged
      val rows = ranges._1.length
      val cols = ranges._2.length
      val data = MArray.tabulate(rows*cols){x =>
        val i = (x / cols) * ranges._1.step.getOrElse(lift[Int,Index](1))
        val j = (x % cols) * ranges._2.step.getOrElse(lift[Int,Index](1))
        lft(func(i,j))
      }
      matrix(data, rows, cols)
    }
  }

  implicit class Tensor3Constructor(ranges: (MRange, MRange, MRange) ) {
    @api def apply[A,T](func: (Index,Index,Index) => A)(implicit lft: Lift[A,T]): Tensor3[T] = {
      implicit val mT: Type[T] = lft.staged
      val dim0 = ranges._1.length
      val dim1 = ranges._2.length
      val dim2 = ranges._3.length
      val data = MArray.tabulate(dim0*dim1*dim2){x =>
        val id0 = x / (dim1*dim2) * ranges._1.step.getOrElse(lift[Int,Index](1)) // Page
      val id1 = ((x / dim2) % dim1) * ranges._2.step.getOrElse(lift[Int,Index](1)) // Row
      val id2 = (x % dim2) * ranges._3.step.getOrElse(lift[Int,Index](1)) // Col
        lft(func(id0,id1,id2))
      }
      tensor3(data, dim0, dim1, dim2)
    }
  }

  implicit class Tensor4Constructor(ranges: (MRange, MRange, MRange, MRange) ) {
    @api def apply[A,T](func: (Index,Index,Index,Index) => A)(implicit lft: Lift[A,T]): Tensor4[T] = {
      implicit val mT: Type[T] = lft.staged
      val dim0 = ranges._1.length
      val dim1 = ranges._2.length
      val dim2 = ranges._3.length
      val dim3 = ranges._4.length
      val data = MArray.tabulate(dim0*dim1*dim2*dim3){x =>
        val id0 = x / (dim1*dim2*dim3) * ranges._1.step.getOrElse(lift[Int,Index](1)) // Cube
      val id1 = ((x / (dim2*dim3)) % dim1 ) * ranges._2.step.getOrElse(lift[Int,Index](1)) // Page
      val id2 = ((x / dim3) % dim2) * ranges._3.step.getOrElse(lift[Int,Index](1)) // Row
      val id3 = (x % dim3) * ranges._4.step.getOrElse(lift[Int,Index](1)) // Col
        lft(func(id0,id1,id2,id3))
      }
      tensor4(data, dim0, dim1, dim2, dim3)
    }
  }

  implicit class Tensor5Constructor(ranges: (MRange, MRange, MRange, MRange, MRange) ) {
    @api def apply[A,T](func: (Index,Index,Index,Index,Index) => A)(implicit lft: Lift[A,T]): Tensor5[T] = {
      implicit val mT: Type[T] = lft.staged
      val dim0 = ranges._1.length
      val dim1 = ranges._2.length
      val dim2 = ranges._3.length
      val dim3 = ranges._4.length
      val dim4 = ranges._5.length
      val data = MArray.tabulate(dim0*dim1*dim2*dim3*dim4){x =>
        val id0 = x / (dim1*dim2*dim3*dim4) * ranges._1.step.getOrElse(lift[Int,Index](1))
        val id1 = ((x / (dim2*dim3*dim4)) % dim1 ) * ranges._2.step.getOrElse(lift[Int,Index](1))
        val id2 = ((x / (dim3*dim4)) % dim2) * ranges._3.step.getOrElse(lift[Int,Index](1))
        val id3 = ((x / (dim4)) % dim3) * ranges._4.step.getOrElse(lift[Int,Index](1))
        val id4 = (x % dim4) * ranges._5.step.getOrElse(lift[Int,Index](1))
        lft(func(id0,id1,id2,id3,id4))
      }
      tensor5(data, dim0, dim1, dim2, dim3, dim4)
    }
  }

  implicit class MatrixOps[T](a: Matrix[T]) {
    private implicit val mT = a.s.tp.typeArguments.head.asInstanceOf[Type[T]]
    @api def foreach(func: T => MUnit): MUnit = a.data.foreach(func)
    @api def map[R:Type](func: T => R): Matrix[R] = matrix(a.data.map(func), a.rows, a.cols)
    @api def zip[S:Type,R:Type](b: Matrix[S])(func: (T,S) => R): Matrix[R] = matrix(a.data.zip(b.data)(func), a.rows, a.cols)
    @api def reduce(rfunc: (T,T) => T): T = a.data.reduce(rfunc)
    @api def transpose(): Matrix[T] = (0::a.cols, 0::a.rows){(j, i) => a(i,j) }
  }
  implicit class Tensor3Ops[T](a: Tensor3[T]) {
    private implicit val mT = a.s.tp.typeArguments.head.asInstanceOf[Type[T]]
    @api def foreach(func: T => MUnit): MUnit = a.data.foreach(func)
    @api def map[R:Type](func: T => R): Tensor3[R] = tensor3(a.data.map(func), a.dim0, a.dim1, a.dim2)
    @api def zip[S:Type,R:Type](b: Tensor3[S])(func: (T,S) => R): Tensor3[R] = tensor3(a.data.zip(b.data)(func), a.dim0, a.dim1, a.dim2)
    @api def reduce(rfunc: (T,T) => T): T = a.data.reduce(rfunc)
  }
  implicit class Tensor4Ops[T](a: Tensor4[T]) {
    private implicit val mT = a.s.tp.typeArguments.head.asInstanceOf[Type[T]]
    @api def foreach(func: T => MUnit): MUnit = a.data.foreach(func)
    @api def map[R:Type](func: T => R): Tensor4[R] = tensor4(a.data.map(func), a.dim0, a.dim1, a.dim2, a.dim3)
    @api def zip[S:Type,R:Type](b: Tensor4[S])(func: (T,S) => R): Tensor4[R] = tensor4(a.data.zip(b.data)(func), a.dim0, a.dim1, a.dim2, a.dim3)
    @api def reduce(rfunc: (T,T) => T): T = a.data.reduce(rfunc)
  }
  implicit class Tensor5Ops[T](a: Tensor5[T]) {
    private implicit val mT = a.s.tp.typeArguments.head.asInstanceOf[Type[T]]
    @api def foreach(func: T => MUnit): MUnit = a.data.foreach(func)
    @api def map[R:Type](func: T => R): Tensor5[R] = tensor5(a.data.map(func), a.dim0, a.dim1, a.dim2, a.dim3, a.dim4)
    @api def zip[S:Type,R:Type](b: Tensor5[S])(func: (T,S) => R): Tensor5[R] = tensor5(a.data.zip(b.data)(func), a.dim0, a.dim1, a.dim2, a.dim3, a.dim4)
    @api def reduce(rfunc: (T,T) => T): T = a.data.reduce(rfunc)
  }


  @internal def matrix[T:Type](data: MArray[T], rows: Index, cols: Index): Matrix[T] = {
    struct[Matrix[T]]("data" -> data.s, "rows" -> rows.s, "cols" -> cols.s)
  }
  @internal def tensor3[T:Type](data: MArray[T], dim0: Index, dim1: Index, dim2: Index): Tensor3[T] = {
    struct[Tensor3[T]]("data" -> data.s, "dim0" -> dim0.s, "dim1" -> dim1.s, "dim2" -> dim2.s)
  }
  @internal def tensor4[T:Type](data: MArray[T], dim0: Index, dim1: Index, dim2: Index, dim3: Index): Tensor4[T] = {
    struct[Tensor4[T]]("data" -> data.s, "dim0" -> dim0.s, "dim1" -> dim1.s, "dim2" -> dim2.s, "dim3" -> dim3.s)
  }
  @internal def tensor5[T:Type](data: MArray[T], dim0: Index, dim1: Index, dim2: Index, dim3: Index, dim4: Index): Tensor5[T] = {
    struct[Tensor5[T]]("data" -> data.s, "dim0" -> dim0.s, "dim1" -> dim1.s, "dim2" -> dim2.s, "dim3" -> dim3.s, "dim4" -> dim4.s)
  }
}