package spatial.spec

import argon.core.{Staging, ArgonExceptions}

trait SpatialExceptions extends ArgonExceptions { self: Staging =>
  // --- Compiler exceptions

  class EmptyReductionTreeLevelException(implicit ctx: SrcCtx) extends
  CompilerException(1000, c"Cannot create reduction tree for empty list.", {
    error(ctx, "Cannot create reduction tree for empty list.")
    error(ctx)
  })

  class UndefinedDimensionsError(s: Sym[_], d: Option[Sym[_]])(implicit ctx: SrcCtx) extends
  CompilerException(1001, c"Cannot find dimensions for symbol ${str(s)}.", {
    error(ctx, s"Cannot locate dimensions for symbol ${str(s)} used here.")
    if (d.isDefined) error(c"  Dimension: $d")
    error(ctx)
  })

  // --- User exceptions
  class InvalidDimensionError(dim: Sym[_])(implicit ctx: SrcCtx) extends UserError(ctx, {
    error(ctx, c"Invalid memory dimension ${str(dim)}.")
    error("Only constants and DSE parameters are allowed as dimensions of on-chip memories")
  })

  class InvalidParallelFactorError(par: Sym[_])(implicit ctx: SrcCtx) extends UserError(ctx, {
    error(ctx, c"Invalid parallelization factor: ${str(par)}")
  })

  class DimensionMismatchError(mem: Sym[_], dims: Int, inds: Int)(implicit ctx: SrcCtx) extends UserError(ctx, {
    error(ctx, c"Invalid number of indices used to access $mem: Expected $dims, got $inds")
  })

  class SparseAddressDimensionError(dram: Sym[_], d: Int)(implicit ctx: SrcCtx) extends UserError(ctx, {
    error(ctx, c"Creation of multi-dimensional sparse DRAM tiles is currently unsupported.")
    error(c"Expected 1D SRAM tile, found ${d}D tile")
  })
  class SparseDataDimensionError(isLoad: Boolean, d: Int)(implicit ctx: SrcCtx) extends UserError(ctx, {
    error(ctx, c"""Multi-dimensional ${if (isLoad) "gather" else "scatter"} is currently unsupported.""")
  })
  class UnsupportedStridedDRAMError(isLoad: Boolean)(implicit ctx: SrcCtx) extends UserError(ctx, {
    error(ctx, c"""Strided tile ${if (isLoad) "load" else "store"} is currently unsupported""")
  })

  class UnsupportedUnalignedTileStore(implicit ctx: SrcCtx) extends UserError(ctx, {
    error(ctx, c"Unaligned tile store is currently unsupported.")
  })
}
