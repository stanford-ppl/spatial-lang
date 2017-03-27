package spatial.api

import argon.ops.{ArrayExtApi, ArrayExtExp, StructExp}
import spatial.SpatialExp

trait MatrixApi extends MatrixExp with RangeExp with ArrayExtApi {
  this: SpatialExp =>

  implicit class MatrixConstructor(ranges: (Range, Range) ) {
    def apply[A,T](func: (Index,Index) => A)(implicit ctx: SrcCtx, lft: Lift[A,T]): Matrix[T] = {
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

  implicit class MatrixOps[T:Type](a: Matrix[T]) {
    def foreach(func: T => Void)(implicit ctx: SrcCtx): Void = a.data.foreach(func)
    def map[R:Type](func: T => R)(implicit ctx: SrcCtx): Matrix[R] = matrix(a.data.map(func), a.rows, a.cols)
    def zip[S:Type,R:Type](b: Matrix[S])(func: (T,S) => R)(implicit ctx: SrcCtx): Matrix[R] = matrix(a.data.zip(b.data)(func), a.rows, a.cols)
    def reduce(rfunc: (T,T) => T)(implicit ctx: SrcCtx): T = a.data.reduce(rfunc)
  }

}

trait MatrixExp extends StructExp with ArrayExtExp {
  this: SpatialExp =>

  case class Matrix[T:Type](s: Exp[Matrix[T]]) extends MetaStruct[Matrix[T]] {
    def rows(implicit ctx: SrcCtx): Index = field[Index]("rows")
    def cols(implicit ctx: SrcCtx): Index = field[Index]("cols")
    private[spatial] def data(implicit ctx: SrcCtx): MetaArray[T] = field[MetaArray[T]]("data")

    def apply(i: Index, j: Index)(implicit ctx: SrcCtx): T = data.apply(i*cols + j)
    def update(i: Index, j: Index, elem: T)(implicit ctx: SrcCtx): Void = wrap(array_update(data.s, (i*cols + j).s, elem.s))
  }

  protected def matrix[T:Type](data: MetaArray[T], rows: Index, cols: Index)(implicit ctx: SrcCtx): Matrix[T] = {
    struct[Matrix[T]]("data" -> data.s, "rows" -> rows.s, "cols" -> cols.s)
  }

  /** Type classes **/
  case class MatrixType[T](child: Meta[T]) extends StructType[Matrix[T]] {
    override def wrapped(x: Exp[Matrix[T]]) = Matrix(x)(child)
    override def unwrapped(x: Matrix[T]) = x.s
    override def stagedClass = classOf[Matrix[T]]
    override def typeArguments = List(child)
    override def fields = Seq("data" -> ArrayType(child), "rows" -> IntType, "cols" -> IntType)
  }
  implicit def matrixType[T:Meta]: StructType[Matrix[T]] = MatrixType(meta[T])


}
