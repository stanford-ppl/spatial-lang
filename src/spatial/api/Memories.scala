package spatial.api
import argon.ops._
import spatial.{SpatialApi, SpatialExp, SpatialOps}

trait MemoryOps extends VoidOps with BoolOps with NumOps with FixPtOps {
  this: SpatialOps =>

  type Range
  type Mem[T,C[_]]
}
trait MemoryApi extends MemoryOps with VoidApi with BoolApi with NumApi with FixPtApi { this: SpatialApi => }
trait MemoryExp extends MemoryOps with VoidExp with BoolExp with NumExp with FixPtExp {
  this: SpatialExp =>

  /** Addressable, potentially multi-dimensional **/
  trait Mem[T,C[_]] {
    def load(mem: C[T], is: Seq[Index], en: Bool)(implicit ctx: SrcCtx): T
    def store(mem: C[T], is: Seq[Index], v: T, en: Bool)(implicit ctx: SrcCtx): Void
    //def empty(dims: Seq[Index])(implicit ctx: SrcCtx, nT: Bits[T]): C[T]
    //def ranges(mem: C[T])(implicit ctx: SrcCtx): Seq[Range]
    def iterators(mem: C[T])(implicit ctx: SrcCtx): Seq[Counter]
  }

  def isIndexType(x: Staged[_]): Boolean = x == fixPtType[TRUE,_32,_0]
}