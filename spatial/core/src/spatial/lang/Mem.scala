package spatial.lang

import argon.core._
import forge._

/** Addressable, potentially multi-dimensional hardware memories **/
trait Mem[T,C[_]] {
  /** Loads an element from `mem` at the given multi-dimensional address `indices` with enable `en`. **/
  @api def load(mem: C[T], indices: Seq[Index], en: Bit): T
  /** Stores `data` into `mem` at the given multi-dimensional address `indices` with enable `en`. **/
  @api def store(mem: C[T], indices: Seq[Index], data: T, en: Bit): MUnit
  /** Returns a `Seq` of counters which define the iteration space of the given memory `mem`. **/
  @api def iterators(mem: C[T]): Seq[Counter]

  /** Returns the parallelization annotation for this memory. **/
  def par(mem: C[T]): Option[Index]
}
