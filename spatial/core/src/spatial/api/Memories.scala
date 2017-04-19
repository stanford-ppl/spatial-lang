package spatial.api

import spatial._

trait MemoryApi extends MemoryExp { this: SpatialApi => }

trait MemoryExp { this: SpatialExp =>

  /** Addressable, potentially multi-dimensional **/
  trait Mem[T,C[_]] {
    def load(mem: C[T], is: Seq[Index], en: Bool)(implicit ctx: SrcCtx): T
    def store(mem: C[T], is: Seq[Index], v: T, en: Bool)(implicit ctx: SrcCtx): Void
    def iterators(mem: C[T])(implicit ctx: SrcCtx): Seq[Counter]
  }

  def isIndexType(x: Type[_]): Boolean = x == fixPtType[TRUE,_32,_0]
}