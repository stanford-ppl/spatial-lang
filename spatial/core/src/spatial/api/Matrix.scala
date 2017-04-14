package spatial.api
import spatial._

import argon.ops.{ArrayExtApi, ArrayExtExp, StructExp}
import forge._

trait MatrixApi extends MatrixExp {
  this: SpatialApi =>

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

  implicit class MatrixOps[T](a: Matrix[T]) {
    private implicit val mT = a.s.tp.typeArguments.head.asInstanceOf[Meta[T]]
    @api def foreach(func: T => Void): Void = a.data.foreach(func)
    @api def map[R:Type](func: T => R): Matrix[R] = matrix(a.data.map(func), a.rows, a.cols)
    @api def zip[S:Type,R:Type](b: Matrix[S])(func: (T,S) => R): Matrix[R] = matrix(a.data.zip(b.data)(func), a.rows, a.cols)
    @api def reduce(rfunc: (T,T) => T): T = a.data.reduce(rfunc)
  }

}

trait MatrixExp {
  this: SpatialExp =>

  case class Matrix[T:Type](s: Exp[Matrix[T]]) extends MetaStruct[Matrix[T]] {
    private[spatial] def data(implicit ctx: SrcCtx): MetaArray[T] = field[MetaArray[T]]("data")

    @api def rows: Index = field[Index]("rows")
    @api def cols: Index = field[Index]("cols")
    @api def apply(i: Index, j: Index): T = data.apply(i*cols + j)
    @api def update(i: Index, j: Index, elem: T): Void = wrap(array_update(data.s, (i*cols + j).s, elem.s))
  }

  @internal def matrix[T:Type](data: MetaArray[T], rows: Index, cols: Index): Matrix[T] = {
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
