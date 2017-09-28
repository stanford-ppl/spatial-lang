package spatial.lang.static

import argon.core._
import forge._
import org.virtualized.virtualize

trait MatrixApi { this: SpatialApi =>

  implicit class VectorReshaper[T<:MetaAny[T]:Type:Num](a: MArray[T]) {
    /** Returns an immutable view of the data in this Array as a @Matrix with given `rows` and `cols`. **/
    @virtualize
    @api def reshape(rows: Index, cols: Index): Matrix[T] = {
      assert(rows*cols == a.length, "Number of elements in vector ("+a.length.toText+") must match number of elements in matrix ("+rows.toText+"x"+cols.toText+")")
      matrix(a, rows, cols)
    }
    /** Returns an immutable view of the data in this Array as a @Tensor3 with given dimensions. **/
    @virtualize
    @api def reshape(dim0: Index, dim1: Index, dim2: Index): Tensor3[T] = {
      assert(dim0*dim1*dim2 == a.length, "Number of elements in vector ("+a.length.toText+") must match number of elements in matrix ("+dim0.toText+"x"+dim1.toText+"x"+dim2.toText+")")
      tensor3(a, dim0, dim1, dim2)
    }
    /** Returns an immutable view of the data in this Array as a @Tensor4 with given dimensions. **/
    @virtualize
    @api def reshape(dim0: Index, dim1: Index, dim2: Index, dim3: Index): Tensor4[T] = {
      assert(dim0*dim1*dim2*dim3 == a.length, "Number of elements in vector ("+a.length.toText+") must match number of elements in matrix ("+dim0.toText+"x"+dim1.toText+"x"+dim2.toText+"x"+dim3.toText+")")
      tensor4(a, dim0, dim1, dim2, dim3)
    }
    /** Returns an immutable view of the data in this Array as a @Tensor5 with given dimensions. **/
    @virtualize
    @api def reshape(dim0: Index, dim1: Index, dim2: Index, dim3: Index, dim4: Index): Tensor5[T] = {
      assert(dim0*dim1*dim2*dim3*dim4 == a.length, "Number of elements in vector ("+a.length.toText+") must match number of elements in matrix ("+dim0.toText+"x"+dim1.toText+"x"+dim2.toText+"x"+dim3.toText+"x"+dim4.toText+")")
      tensor5(a, dim0, dim1, dim2, dim3, dim4)
    }
    @virtualize
    @api def toeplitz(filterdim0: Index, filterdim1: Index, imgdim0: Index, imgdim1: Index, stride0: Index, stride1: Index): Matrix[T] = {
      // TODO: Incorporate stride
      val pad0 = filterdim0 - 1 - (stride0-1)
      val pad1 = filterdim1 - 1 - (stride1-1)
      val out_rows = ((imgdim0+pad0-filterdim0+stride0) * (imgdim1+pad1-filterdim1+stride1)) / (stride0 * stride1)
      val out_cols = (imgdim0+pad0)*(imgdim1+pad1)

      val data = MArray.tabulate(out_rows * out_cols){k =>
        val i = (k / out_cols)
        val j = (k % out_cols)
        // val sliding_window_row_correction = (i / (imgdim1/stride1)) * pad1 // Jump an extra pad1 for each time the sliding window drops a row
        val current_slide_row = i / (imgdim1/stride1)
        val current_slide_col = i % (imgdim1/stride1)
        val rows_correction = current_slide_row * stride0 * (imgdim1+pad1)
        val cols_correction = current_slide_col * stride1
        // val stride0_correction = stride0 * (i / (imgdim1/stride1)) * ((stride0-1)*(imgdim1+pad1)) // Jump an extra (stride0-1)*(pad1+dim1) for each time the sliding window drops a row
        // val stride1_correction = (i % (imgdim1/stride1)) * (stride1-1)  // Jump an extra (stride1-1) for each time the sliding window moves -> by 1
        val filter_base = j - rows_correction - cols_correction //i - (sliding_window_row_correction + stride0_correction + stride1_correction) 
        val filter_i = (filter_base) / (imgdim1+pad1)
        val filter_j = (filter_base) % (imgdim1+pad1)
        // println("at " + i + "," + j +", " + current_slide_row + " row, correct " + rows_correction + ", " + cols_correction + " filter stuff " + filter_base + ", " + filter_i + ", " + filter_j + " == " + {filter_base >= 0 && filter_j < filterdim1 && filter_j >= 0 && filter_i < filterdim0 && filter_i >= 0})
      
        // println("at " + i + "," + j + " slidwind " + sliding_window_row_correction + ", stride0 " + stride0_correction + " stride1 " +  stride1_correction + ", filter numbers " + filter_base + " - " + filter_i + ", " + filter_j + " = " + {filter_base >= 0 && filter_j < filterdim1 && filter_j >= 0 && filter_i < filterdim0 && filter_i >= 0})
        if (filter_base >= 0 && filter_j < filterdim1 && filter_j >= 0 && filter_i < filterdim0 && filter_i >= 0) a(filter_i * filterdim1 + filter_j) else 0.to[T]

        // val i = (k / out_cols)
        // val j = (k % out_cols)
        // val sliding_window_row_correction = (i / imgdim1) * pad1
        // val stride0_correction = (i / imgdim1) * ((stride0-1)*imgdim1)
        // val stride1_correction = (i % imgdim1) * (stride1-1)
        // val filter_i_base = j - i - (sliding_window_row_correction + stride0_correction + stride1_correction) 
        // val filter_i = (filter_i_base) / (imgdim1+pad1)
        // val filter_j = ((j - i - sliding_window_row_correction) % (imgdim1+pad1))
        // if (filter_i_base >= 0 && filter_j < filterdim1 && filter_j >= 0 && filter_i < filterdim0 && filter_i >= 0) a(filter_i * filterdim1 + filter_j) else 0.to[T]
      }
      matrix(data, out_rows, out_cols)
    }

  }

  implicit class MatrixConstructor(ranges: (MRange, MRange) ) {
    @api def apply[A,T](func: (Index,Index) => A)(implicit lft: Lift[A,T]): Matrix[T] = {
      implicit val mT: Type[T] = lft.staged
      val rows = ranges._1.length
      val cols = ranges._2.length
      Matrix.tabulate(rows, cols){(i,j) => lft(func(ranges._1(i), ranges._2(j))) }
    }
  }

  implicit class Tensor3Constructor(ranges: (MRange, MRange, MRange) ) {
    @api def apply[A,T](func: (Index,Index,Index) => A)(implicit lft: Lift[A,T]): Tensor3[T] = {
      implicit val mT: Type[T] = lft.staged
      val dim0 = ranges._1.length
      val dim1 = ranges._2.length
      val dim2 = ranges._3.length
      Tensor3.tabulate(dim0,dim1,dim2){(i,j,k) => lft(func(ranges._1(i), ranges._2(j), ranges._3(k))) }
    }
  }

  implicit class Tensor4Constructor(ranges: (MRange, MRange, MRange, MRange) ) {
    @api def apply[A,T](func: (Index,Index,Index,Index) => A)(implicit lft: Lift[A,T]): Tensor4[T] = {
      implicit val mT: Type[T] = lft.staged
      val dim0 = ranges._1.length
      val dim1 = ranges._2.length
      val dim2 = ranges._3.length
      val dim3 = ranges._4.length
      Tensor4.tabulate(dim0,dim1,dim2,dim3){(i0,i1,i2,i3) =>
        lft(func(ranges._1(i0), ranges._2(i1), ranges._3(i2), ranges._4(i3)))
      }
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
      Tensor5.tabulate(dim0,dim1,dim2,dim3,dim4){(i0,i1,i2,i3,i4) =>
        lft(func(ranges._1(i0), ranges._2(i1), ranges._3(i2), ranges._4(i3), ranges._5(i4)))
      }
    }
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