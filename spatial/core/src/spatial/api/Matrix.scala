package spatial.api

import spatial._
import forge._

trait MatrixApi extends MatrixExp { this: SpatialApi =>

  implicit class MatrixConstructor(ranges: (Range, Range) ) {
    @api def apply[A,T](func: (Index,Index) => A)(implicit lft: Lift[A,T]): Matrix[T] = {
      implicit val mT: Type[T] = lft.staged
      val rows = ranges._1.length
      val cols = ranges._2.length
      val data = Array.tabulate(rows*cols){x =>
        val i = (x / cols) * ranges._1.step.getOrElse(lift[Int,Index](1))
        val j = (x % cols) * ranges._2.step.getOrElse(lift[Int,Index](1))
        lft(func(i,j))
      }
      matrix(data, rows, cols)
    }
  }

  implicit class Tensor3Constructor(ranges: (Range, Range, Range) ) {
    @api def apply[A,T](func: (Index,Index,Index) => A)(implicit lft: Lift[A,T]): Tensor3[T] = {
      implicit val mT: Type[T] = lft.staged
      val dim0 = ranges._1.length
      val dim1 = ranges._2.length
      val dim2 = ranges._3.length
      val data = Array.tabulate(dim0*dim1*dim2){x =>
        val id0 = x / (dim1*dim2) * ranges._1.step.getOrElse(lift[Int,Index](1)) // Page
        val id1 = ((x / dim2) % dim1) * ranges._2.step.getOrElse(lift[Int,Index](1)) // Row
        val id2 = (x % dim2) * ranges._3.step.getOrElse(lift[Int,Index](1)) // Col
        lft(func(id0,id1,id2))
      }
      tensor3(data, dim0, dim1, dim2)
    }
  }

  implicit class Tensor4Constructor(ranges: (Range, Range, Range, Range) ) {
    @api def apply[A,T](func: (Index,Index,Index,Index) => A)(implicit lft: Lift[A,T]): Tensor4[T] = {
      implicit val mT: Type[T] = lft.staged
      val dim0 = ranges._1.length
      val dim1 = ranges._2.length
      val dim2 = ranges._3.length
      val dim3 = ranges._4.length
      val data = Array.tabulate(dim0*dim1*dim2*dim3){x =>
        val id0 = x / (dim1*dim2*dim3) * ranges._1.step.getOrElse(lift[Int,Index](1)) // Cube
        val id1 = ((x / (dim2*dim3)) % dim1 ) * ranges._2.step.getOrElse(lift[Int,Index](1)) // Page
        val id2 = ((x / dim3) % dim2) * ranges._3.step.getOrElse(lift[Int,Index](1)) // Row
        val id3 = (x % dim3) * ranges._4.step.getOrElse(lift[Int,Index](1)) // Col
        lft(func(id0,id1,id2,id3))
      }
      tensor4(data, dim0, dim1, dim2, dim3)
    }
  }

  implicit class Tensor5Constructor(ranges: (Range, Range, Range, Range, Range) ) {
    @api def apply[A,T](func: (Index,Index,Index,Index,Index) => A)(implicit lft: Lift[A,T]): Tensor5[T] = {
      implicit val mT: Type[T] = lft.staged
      val dim0 = ranges._1.length
      val dim1 = ranges._2.length
      val dim2 = ranges._3.length
      val dim3 = ranges._4.length
      val dim4 = ranges._5.length
      val data = Array.tabulate(dim0*dim1*dim2*dim3*dim4){x =>
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
    private implicit val mT = a.s.tp.typeArguments.head.asInstanceOf[Meta[T]]
    @api def foreach(func: T => Void): Void = a.data.foreach(func)
    @api def map[R:Type](func: T => R): Matrix[R] = matrix(a.data.map(func), a.rows, a.cols)
    @api def zip[S:Type,R:Type](b: Matrix[S])(func: (T,S) => R): Matrix[R] = matrix(a.data.zip(b.data)(func), a.rows, a.cols)
    @api def reduce(rfunc: (T,T) => T): T = a.data.reduce(rfunc)
  }
  implicit class Tensor3Ops[T](a: Tensor3[T]) {
    private implicit val mT = a.s.tp.typeArguments.head.asInstanceOf[Meta[T]]
    @api def foreach(func: T => Void): Void = a.data.foreach(func)
    @api def map[R:Type](func: T => R): Tensor3[R] = tensor3(a.data.map(func), a.dim0, a.dim1, a.dim2)
    @api def zip[S:Type,R:Type](b: Tensor3[S])(func: (T,S) => R): Tensor3[R] = tensor3(a.data.zip(b.data)(func), a.dim0, a.dim1, a.dim2)
    @api def reduce(rfunc: (T,T) => T): T = a.data.reduce(rfunc)
  }
  implicit class Tensor4Ops[T](a: Tensor4[T]) {
    private implicit val mT = a.s.tp.typeArguments.head.asInstanceOf[Meta[T]]
    @api def foreach(func: T => Void): Void = a.data.foreach(func)
    @api def map[R:Type](func: T => R): Tensor4[R] = tensor4(a.data.map(func), a.dim0, a.dim1, a.dim2, a.dim3)
    @api def zip[S:Type,R:Type](b: Tensor4[S])(func: (T,S) => R): Tensor4[R] = tensor4(a.data.zip(b.data)(func), a.dim0, a.dim1, a.dim2, a.dim3)
    @api def reduce(rfunc: (T,T) => T): T = a.data.reduce(rfunc)
  }
  implicit class Tensor5Ops[T](a: Tensor5[T]) {
    private implicit val mT = a.s.tp.typeArguments.head.asInstanceOf[Meta[T]]
    @api def foreach(func: T => Void): Void = a.data.foreach(func)
    @api def map[R:Type](func: T => R): Tensor5[R] = tensor5(a.data.map(func), a.dim0, a.dim1, a.dim2, a.dim3, a.dim4)
    @api def zip[S:Type,R:Type](b: Tensor5[S])(func: (T,S) => R): Tensor5[R] = tensor5(a.data.zip(b.data)(func), a.dim0, a.dim1, a.dim2, a.dim3, a.dim4)
    @api def reduce(rfunc: (T,T) => T): T = a.data.reduce(rfunc)
  }

}

trait MatrixExp { this: SpatialExp =>

  case class Matrix[T:Type](s: Exp[Matrix[T]]) extends MetaStruct[Matrix[T]] {
    private[spatial] def data(implicit ctx: SrcCtx): MetaArray[T] = field[MetaArray[T]]("data")

    @api def rows: Index = field[Index]("rows")
    @api def cols: Index = field[Index]("cols")
    @api def apply(i: Index, j: Index): T = data.apply(i*cols + j)
    @api def update(i: Index, j: Index, elem: T): Void = wrap(array_update(data.s, (i*cols + j).s, elem.s))
  }

  case class Tensor3[T:Type](s: Exp[Tensor3[T]]) extends MetaStruct[Tensor3[T]] {
    private[spatial] def data(implicit ctx: SrcCtx): MetaArray[T] = field[MetaArray[T]]("data")

    @api def dim0: Index = field[Index]("dim0")
    @api def dim1: Index = field[Index]("dim1")
    @api def dim2: Index = field[Index]("dim2")
    @api def apply(i: Index, j: Index, k: Index): T = data.apply(i*dim1*dim2 + j*dim2 + k)
    @api def update(i: Index, j: Index, k: Index, elem: T): Void = wrap(array_update(data.s, (i*dim1*dim2 + j*dim1 + k).s, elem.s))
  }
  case class Tensor4[T:Type](s: Exp[Tensor4[T]]) extends MetaStruct[Tensor4[T]] {
    private[spatial] def data(implicit ctx: SrcCtx): MetaArray[T] = field[MetaArray[T]]("data")

    @api def dim0: Index = field[Index]("dim0")
    @api def dim1: Index = field[Index]("dim1")
    @api def dim2: Index = field[Index]("dim2")
    @api def dim3: Index = field[Index]("dim3")
    @api def apply(i: Index, j: Index, k: Index, l: Index): T = data.apply(i*dim1*dim2*dim3 + j*dim2*dim3 + k*dim3 + l)
    @api def update(i: Index, j: Index, k: Index, l: Index, elem: T): Void = wrap(array_update(data.s, (i*dim1*dim2*dim3 + j*dim2*dim3 + k*dim3 + l).s, elem.s))
  }
  case class Tensor5[T:Type](s: Exp[Tensor5[T]]) extends MetaStruct[Tensor5[T]] {
    private[spatial] def data(implicit ctx: SrcCtx): MetaArray[T] = field[MetaArray[T]]("data")

    @api def dim0: Index = field[Index]("dim0")
    @api def dim1: Index = field[Index]("dim1")
    @api def dim2: Index = field[Index]("dim2")
    @api def dim3: Index = field[Index]("dim3")
    @api def dim4: Index = field[Index]("dim4")
    @api def apply(i: Index, j: Index, k: Index, l: Index, m: Index): T = data.apply(i*dim1*dim2*dim3*dim4 + j*dim2*dim3*dim4 + k*dim3*dim4 + l*dim4 + m)
    @api def update(i: Index, j: Index, k: Index, l: Index, m: Index, elem: T): Void = wrap(array_update(data.s, (i*dim1*dim2*dim3*dim4 + j*dim2*dim3*dim4 + k*dim3*dim4 + l*dim4 + m).s, elem.s))
  }

  @internal def matrix[T:Type](data: MetaArray[T], rows: Index, cols: Index): Matrix[T] = {
    struct[Matrix[T]]("data" -> data.s, "rows" -> rows.s, "cols" -> cols.s)
  }
  @internal def tensor3[T:Type](data: MetaArray[T], dim0: Index, dim1: Index, dim2: Index): Tensor3[T] = {
    struct[Tensor3[T]]("data" -> data.s, "dim0" -> dim0.s, "dim1" -> dim1.s, "dim2" -> dim2.s)
  }
  @internal def tensor4[T:Type](data: MetaArray[T], dim0: Index, dim1: Index, dim2: Index, dim3: Index): Tensor4[T] = {
    struct[Tensor4[T]]("data" -> data.s, "dim0" -> dim0.s, "dim1" -> dim1.s, "dim2" -> dim2.s, "dim3" -> dim3.s)
  }
  @internal def tensor5[T:Type](data: MetaArray[T], dim0: Index, dim1: Index, dim2: Index, dim3: Index, dim4: Index): Tensor5[T] = {
    struct[Tensor5[T]]("data" -> data.s, "dim0" -> dim0.s, "dim1" -> dim1.s, "dim2" -> dim2.s, "dim3" -> dim3.s, "dim4" -> dim4.s)
  }
  // @internal def tensor[T:Type](data: MetaArray[T], dims: Index*): Tensor[T] = {
  //   struct[Tensor[T]]("data" -> data.s, )
  // }

  /** Type classes **/
  case class MatrixType[T](child: Meta[T]) extends StructType[Matrix[T]] {
    override def wrapped(x: Exp[Matrix[T]]) = Matrix(x)(child)
    override def unwrapped(x: Matrix[T]) = x.s
    override def stagedClass = classOf[Matrix[T]]
    override def typeArguments = List(child)
    override def fields = Seq("data" -> ArrayType(child), "rows" -> IntType, "cols" -> IntType)
  }
  implicit def matrixType[T:Meta]: StructType[Matrix[T]] = MatrixType(meta[T])
  case class Tensor3Type[T](child: Meta[T]) extends StructType[Tensor3[T]] {
    override def wrapped(x: Exp[Tensor3[T]]) = Tensor3(x)(child)
    override def unwrapped(x: Tensor3[T]) = x.s
    override def stagedClass = classOf[Tensor3[T]]
    override def typeArguments = List(child)
    override def fields = Seq("data" -> ArrayType(child), "dim0" -> IntType, "dim1" -> IntType, "dim2" -> IntType)
  }
  implicit def tensor3Type[T:Meta]: StructType[Tensor3[T]] = Tensor3Type(meta[T])
  case class Tensor4Type[T](child: Meta[T]) extends StructType[Tensor4[T]] {
    override def wrapped(x: Exp[Tensor4[T]]) = Tensor4(x)(child)
    override def unwrapped(x: Tensor4[T]) = x.s
    override def stagedClass = classOf[Tensor4[T]]
    override def typeArguments = List(child)
    override def fields = Seq("data" -> ArrayType(child), "dim0" -> IntType, "dim1" -> IntType, "dim2" -> IntType, "dim3" -> IntType)
  }
  implicit def tensor4Type[T:Meta]: StructType[Tensor4[T]] = Tensor4Type(meta[T])
  case class Tensor5Type[T](child: Meta[T]) extends StructType[Tensor5[T]] {
    override def wrapped(x: Exp[Tensor5[T]]) = Tensor5(x)(child)
    override def unwrapped(x: Tensor5[T]) = x.s
    override def stagedClass = classOf[Tensor5[T]]
    override def typeArguments = List(child)
    override def fields = Seq("data" -> ArrayType(child), "dim0" -> IntType, "dim1" -> IntType, "dim2" -> IntType, "dim3" -> IntType, "dim4" -> IntType)
  }
  implicit def tensor5Type[T:Meta]: StructType[Tensor5[T]] = Tensor5Type(meta[T])
}
