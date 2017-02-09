package spatial.api

import argon.core.Staging
import argon.ops.{BoolExp,VoidExp,FixPtExp}
import argon.typeclasses.NumExp
import spatial.SpatialExp

trait MemoryApi extends MemoryExp {this: SpatialExp => }

trait MemoryExp extends Staging with VoidExp with BoolExp with NumExp with FixPtExp {
  this: SpatialExp =>

  /** Addressable, potentially multi-dimensional **/
  trait Mem[T,C[_]] {
    def load(mem: C[T], is: Seq[Index], en: Bool)(implicit ctx: SrcCtx): T
    def store(mem: C[T], is: Seq[Index], v: T, en: Bool)(implicit ctx: SrcCtx): Void
    def iterators(mem: C[T])(implicit ctx: SrcCtx): Seq[Counter]
  }

  def isIndexType(x: Staged[_]): Boolean = x == fixPtType[TRUE,_32,_0]
}