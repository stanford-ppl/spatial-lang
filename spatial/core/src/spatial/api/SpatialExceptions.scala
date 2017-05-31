package spatial.api

import spatial._

trait SpatialExceptions {this: SpatialExp =>
  // --- Compiler exceptions

  class EmptyReductionTreeLevelException(implicit ctx: SrcCtx) extends
  CompilerException(1000, u"Cannot create reduction tree for empty list.", {
    error(ctx, "Cannot create reduction tree for empty list.")
    error(ctx)
  })

  class UndefinedDimensionsError(s: Exp[_], d: Option[Exp[_]])(implicit ctx: SrcCtx) extends
  CompilerException(1001, u"Cannot find dimensions for symbol ${str(s)}.", {
    error(ctx, s"Cannot locate dimensions for symbol ${str(s)} used here.")
    if (d.isDefined) error(c"  Dimension: $d")
    error(ctx)
  })

  class UndefinedZeroException(s: Exp[_], tp: Type[_])(implicit ctx: SrcCtx) extends
  CompilerException(1002, c"Unit Pipe Transformer could not create zero for type $tp for escaping value $s", {
    error(ctx, c"Unit Pipe Transformer could not create zero for type $tp for escaping value $s")
  })

  class ExternalWriteError(mem: Exp[_], write: Exp[_], ctrl: Ctrl)(implicit ctx: SrcCtx) extends
  CompilerException(1003, c"Found illegal write to memory $mem defined outside an inner controller", {
    error(ctx, c"Found illegal write to memory $mem defined outside an inner controller")
    error(c"${str(write)}")
    error(c"Current control $ctrl")
  })

  class UndefinedBankingException(tp: Type[_])(implicit ctx: SrcCtx) extends
  CompilerException(1004, c"Don't know how to bank memory type $tp", {
    error(ctx, c"Don't know how to bank memory type $tp")
  })

  class UndefinedDispatchException(access: Exp[_], mem: Exp[_]) extends
  CompilerException(1005, c"""Access $access had no dispatch information for memory $mem (${nameOf(mem).getOrElse("noname")})""", {
    error(c"Access $access had no dispatch information for memory $mem")
  })

  class UndefinedPortsException(access: Exp[_], mem: Exp[_], idx: Option[Int]) extends
  CompilerException(1006, c"Access $access had no ports for memory $mem" + idx.map{i => c", index $i"}.getOrElse(""), {
    error(c"Access $access had no ports for memory $mem" + idx.map{i => c", index $i"}.getOrElse(""))
  })

  class NoCommonParentException(a: Ctrl, b: Ctrl) extends
  CompilerException(1007, c"Controllers $a and $b had no common parent while finding LCA with distance", {
    error(c"Controllers $a and $b had no common parent while finding LCA with distance")
  })

  class UndefinedChildException(parent: Ctrl, access: Access) extends
  CompilerException(1008, c"Parent $parent does not appear to contain $access while running childContaining", {
    error(c"Parent $parent does not appear to contain $access while running childContaining")
  })

  class UndefinedPipeDistanceException(a: Ctrl, b: Ctrl) extends
  CompilerException(1010, c"Controllers $a and $b have an undefined pipe distance because they occur in parallel", {
    error(c"Controllers $a and $b have an undefined pipe distance because they occur in parallel")
  })

  class UndefinedControlStyleException(ctrl: Exp[_]) extends
  CompilerException(1011, c"Controller ${str(ctrl)} does not have a control style defined", {
    error(c"Controller ${str(ctrl)} does not have a control style defined")
  })

  class UndefinedControlLevelException(ctrl: Exp[_]) extends
  CompilerException(1012, c"Controller ${str(ctrl)} does not have a control level defined", {
    error(c"Controller ${str(ctrl)} does not have a control level defined")
  })


  // --- User exceptions
  def nth(x: Int) = x match {
    case 0 => "first"
    case 1 => "second"
    case 2 => "third"
    case n => s"${n}th"
  }

  class InvalidOnchipDimensionError(mem: Exp[_], dim: Int) extends UserError(mem.ctx, {
    error(mem.ctx, u"Memory $mem defined here has invalid ${nth(dim)} dimension.")
    error("Only functions of constants and DSE parameters are allowed as dimensions of on-chip memories")
  })

  class InvalidParallelFactorError(par: Exp[_])(implicit ctx: SrcCtx) extends UserError(ctx, {
    error(ctx, u"Invalid parallelization factor: ${str(par)}")
  })

  class DimensionMismatchError(mem: Exp[_], dims: Int, inds: Int)(implicit ctx: SrcCtx) extends UserError(ctx, {
    error(ctx, u"Invalid number of indices used to access $mem: Expected $dims, got $inds")
  })

  class SparseAddressDimensionError(dram: Exp[_], d: Int)(implicit ctx: SrcCtx) extends UserError(ctx, {
    error(ctx, u"Creation of multi-dimensional sparse DRAM tiles is currently unsupported.")
    error(u"Expected 1D SRAM tile, found ${d}D tile")
  })
  class SparseDataDimensionError(isLoad: Boolean, d: Int)(implicit ctx: SrcCtx) extends UserError(ctx, {
    error(ctx, u"""Multi-dimensional ${if (isLoad) "gather" else "scatter"} is currently unsupported.""")
  })
  class UnsupportedStridedDRAMError(isLoad: Boolean)(implicit ctx: SrcCtx) extends UserError(ctx, {
    error(ctx, u"""Strided tile ${if (isLoad) "load" else "store"} is currently unsupported""")
  })

  class UnsupportedUnalignedTileStore(implicit ctx: SrcCtx) extends UserError(ctx, {
    error(ctx, u"Unaligned tile store is currently unsupported.")
  })

  class ControlInReductionError(ctx: SrcCtx) extends UserError(ctx, {
    error(ctx, u"Reduction functions cannot have inner control nodes")
  })

  class ControlInNotDoneError(ctx: SrcCtx) extends UserError(ctx, {
    error(ctx, "While condition of FSMs cannot have inner control nodes")
  })

  class ControlInNextStateError(ctx: SrcCtx) extends UserError(ctx, {
    error(ctx, "Next state function of FSMs cannot have inner control nodes")
  })


  class InvalidOffchipDimensionError(offchip: Exp[_], dim: Int)(implicit ctx: SrcCtx) extends UserError(ctx, {
    error(ctx, u"Offchip memory defined here has invalid ${nth(dim)} dimension")
    error("Only functions of input arguments, parameters, and constants can be used as DRAM dimensions.")
  })

  class ConcurrentReadersError(mem: Exp[_], a: Exp[_], b: Exp[_])(implicit ctx: SrcCtx) extends UserError(mem.ctxOrElse(ctx), {
    error(mem.ctxOrElse(ctx), u"${mem.tp} defined here has illegal concurrent readers: ")
    error(a.ctxOrElse(ctx), u"  The first read occurs here")
    error(a.ctxOrElse(ctx))
    error(b.ctxOrElse(ctx), u"  The second read occurs here")
    error(b.ctxOrElse(ctx))
  })

  class ConcurrentWritersError(mem: Exp[_], a: Exp[_], b: Exp[_])(implicit ctx: SrcCtx) extends UserError(mem.ctxOrElse(ctx), {
    error(mem.ctxOrElse(ctx), u"${mem.tp} defined here has illegal concurrent writers: ")
    error(a.ctxOrElse(ctx), u"  The first write occurs here")
    error(a.ctxOrElse(ctx))
    error(b.ctxOrElse(ctx), u"  The second write occurs here")
    error(b.ctxOrElse(ctx))
  })

  class PipelinedReadersError(mem: Exp[_], a: Exp[_], b: Exp[_])(implicit ctx: SrcCtx) extends UserError(mem.ctxOrElse(ctx), {
    error(mem.ctxOrElse(ctx), u"${mem.tp} defined here has illegal pipelined readers: ")
    error(a.ctxOrElse(ctx), u"  The first read occurs here")
    error(a.ctxOrElse(ctx))
    error(b.ctxOrElse(ctx), u"  The second read occurs here")
    error(b.ctxOrElse(ctx))
  })

  class PipelinedWritersError(mem: Exp[_], a: Exp[_], b: Exp[_])(implicit ctx: SrcCtx) extends UserError(mem.ctxOrElse(ctx), {
    error(mem.ctxOrElse(ctx), u"${mem.tp} defined here has illegal pipelined writers: ")
    error(a.ctxOrElse(ctx), u"  The first write occurs here")
    error(a.ctxOrElse(ctx))
    error(b.ctxOrElse(ctx), u"  The second write occurs here")
    error(b.ctxOrElse(ctx))
  })

  class MultipleReadersError(mem: Exp[_], readers: List[Exp[_]])(implicit ctx: SrcCtx) extends UserError(mem.ctxOrElse(ctx), {
    error(mem.ctxOrElse(ctx), u"${mem.tp} defined here has illegal multiple readers: ")
    readers.foreach{read =>
      error(read.ctxOrElse(ctx), u"  Read defined here")
      error(read.ctxOrElse(ctx))
    }
  })

  class MultipleWritersError(mem: Exp[_], writers: List[Exp[_]])(implicit ctx: SrcCtx) extends UserError(mem.ctxOrElse(ctx), {
    error(mem.ctxOrElse(ctx), u"${mem.tp} defined here has illegal multiple writers: ")
    writers.foreach{write =>
      error(write.ctxOrElse(ctx), u"  Write defined here")
      error(write.ctxOrElse(ctx))
    }
  })

  class NoTopError(ctx: SrcCtx) extends ProgramError({
    error("An Accel block is required to specify the area of code to optimize for the FPGA.")
    error("No Accel block was specified for this program.")
  })

  class EmptyVectorException(ctx: SrcCtx) extends UserError(ctx, {
    error("Attempted to create an empty vector. Empty vectors are currently disallowed.")
  })

  class InvalidVectorApplyIndex(vector: Exp[_], index: Int)(implicit ctx: SrcCtx) extends UserError(ctx, {
    error(u"Attempted to address vector $vector at invalid index $index.")
  })

  class InvalidVectorSlice(vector: Exp[_], start: Int, end: Int)(implicit ctx: SrcCtx) extends UserError(ctx, {
    error(u"Attempted to slice vector $vector $end::$start, creating an empty vector")
    error("Note that Vectors in Spatial are little-endian (last element in Vector is index 0).")
  })

  class NonConstantInitError(ctx: SrcCtx) extends UserError(ctx, {
    error(ctx, u"Register must have constant initialization value.")
  })
}
