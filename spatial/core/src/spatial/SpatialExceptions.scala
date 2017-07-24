package spatial

import argon._
import argon.core._
import spatial.aliases._

// --- Compiler exceptions

class EmptyReductionTreeLevelException(implicit ctx: SrcCtx, state: State) extends
CompilerException(1000, u"Cannot create reduction tree for empty list."){
  bug(ctx, "Cannot create reduction tree for empty list.")
  bug(ctx)
}

class UndefinedDimensionsException(s: Exp[_], d: Option[Exp[_]])(implicit ctx: SrcCtx, state: State) extends
CompilerException(1001, u"Cannot find dimensions for symbol ${str(s)}."){
  bug(ctx, s"Cannot locate dimensions for symbol ${str(s)} used here.")
  if (d.isDefined) bug(c"  Dimension: $d")
  bug(ctx)
}

class UndefinedZeroException(s: Exp[_], tp: Type[_])(implicit ctx: SrcCtx, state: State) extends
CompilerException(1002, c"Unit Pipe Transformer could not create zero for type $tp for escaping value $s"){
  bug(ctx, c"Unit Pipe Transformer could not create zero for type $tp for escaping value $s")
}

class ExternalWriteException(mem: Exp[_], write: Exp[_], ctrl: Ctrl)(implicit ctx: SrcCtx, state: State) extends
CompilerException(1003, c"Found illegal write to memory $mem defined outside an inner controller"){
  bug(ctx, c"Found illegal write to memory $mem defined outside an inner controller")
  bug(c"${str(write)}")
  bug(c"Current control $ctrl")
}

class UndefinedBankingException(tp: Type[_])(implicit ctx: SrcCtx, state: State) extends
CompilerException(1004, c"Don't know how to bank memory type $tp"){
  bug(ctx, c"Don't know how to bank memory type $tp")
}

class UndefinedDispatchException(access: Exp[_], mem: Exp[_])(implicit state: State) extends
CompilerException(1005, c"""Access $access had no dispatch information for memory $mem (${mem.name.getOrElse("noname")}}"""){
  bug(c"Access $access had no dispatch information for memory $mem")
}

class UndefinedPortsException(access: Exp[_], mem: Exp[_], idx: Option[Int])(implicit state: State) extends
CompilerException(1006, c"Access $access had no ports for memory $mem" + idx.map{i => c", index $i"}.getOrElse("")){
  bug(c"Access $access had no ports for memory $mem" + idx.map{i => c", index $i"}.getOrElse(""))
}

class NoCommonParentException(a: Ctrl, b: Ctrl)(implicit state: State) extends
CompilerException(1007, c"Controllers $a and $b had no common parent while finding LCA with distance"){
  bug(c"Controllers $a and $b had no common parent while finding LCA with distance")
}

class UndefinedChildException(parent: Ctrl, access: Access)(implicit state: State) extends
CompilerException(1008, c"Parent $parent does not appear to contain $access while running childContaining"){
  bug(c"Parent $parent does not appear to contain $access while running childContaining")
}

class AmbiguousMetaPipeException(mem: Exp[_], metapipes: Map[Ctrl, Seq[Access]])(implicit state: State) extends
CompilerException(1009, c"Ambiguous metapipes for readers/writers of $mem: ${metapipes.keySet}"){
  bug(c"Ambiguous metapipes for readers/writers of $mem:")
  metapipes.foreach{case (pipe,accesses) => bug(c"  $pipe: $accesses")}
}

class UndefinedPipeDistanceException(a: Ctrl, b: Ctrl)(implicit state: State) extends
CompilerException(1010, c"Controllers $a and $b have an undefined pipe distance because they occur in parallel"){
  bug(c"Controllers $a and $b have an undefined pipe distance because they occur in parallel")
}

class UndefinedControlStyleException(ctrl: Exp[_])(implicit state: State) extends
CompilerException(1011, c"Controller ${str(ctrl)} does not have a control style defined"){
  bug(c"Controller ${str(ctrl)} does not have a control style defined")
}

class UndefinedControlLevelException(ctrl: Exp[_])(implicit state: State) extends
CompilerException(1012, c"Controller ${str(ctrl)} does not have a control level defined"){
  bug(c"Controller ${str(ctrl)} does not have a control level defined")
}


class UnusedDRAMException(dram: Exp[_], name: String)(implicit state: State) extends
CompilerException(1020, c"DRAM $dram ($name) was declared as a DRAM in app but is not used by the accel"){
  bug(c"DRAM $dram ($name) was declared as a DRAM in app but is not used by the accel")
}

class OuterLevelInnerStyleException(name: String)(implicit state: State) extends
CompilerException(1022, c"Controller $name claims to be an outer level controller but has style of an innerpipe"){
  bug(c"Controller $name claims to be an outer level controller but has style of an innerpipe")
}

class DoublyUsedDRAMException(dram: Exp[_], name: String)(implicit state: State) extends
CompilerException(1023, c"DRAM $dram is used twice as a $name.  Please only load from a DRAM once, or else stream signals will interfere"){
  bug(c"DRAM $dram is used twice as a $name.  Please only load from a DRAM once, or else stream signals will interfere")
}

class TrigInAccelException(lhs: Exp[_])(implicit state: State) extends
CompilerException(1024, c"""Cannot handle trig functions inside of accel block! ${lhs.name.getOrElse("")}"""){
  bug(c"""Cannot handle trig functions inside of accel block! ${lhs.name.getOrElse("")}""")
}


class EmptyDispatchException(lhs: Exp[_])(implicit state: State) extends
CompilerException(1025, c"$lhs had empty dispatch information") {
  bug(s"Access:")
  bug(c"  ${str(lhs)}")
  bug(c"had empty dispatch information.")
}


// --- User exceptions
object Nth {
  def apply(x: Int) = x match {
    case 0 => "first"
    case 1 => "second"
    case 2 => "third"
    case n => s"${n}th"
  }
}

class InvalidOnchipDimensionError(mem: Exp[_], dim: Int)(implicit state: State) extends UserError(mem.ctx){ def console() = {
  error(mem.ctx, u"Memory $mem defined here has invalid ${Nth(dim)} dimension.")
  error("Only functions of constants and DSE parameters are allowed as dimensions of on-chip memories")
}}

class InvalidParallelFactorError(par: Exp[_])(implicit ctx: SrcCtx, state: State) extends UserError(ctx){ def console() = {
  error(ctx, u"Invalid parallelization factor: ${str(par)}")
}}

class DimensionMismatchError(mem: Exp[_], dims: Int, inds: Int)(implicit ctx: SrcCtx, state: State) extends UserError(ctx){ def console() = {
  error(ctx, u"Invalid number of indices used to access $mem: Expected $dims, got $inds")
}}

class SparseAddressDimensionError(dram: Exp[_], d: Int)(implicit ctx: SrcCtx, state: State) extends UserError(ctx){ def console() = {
  error(ctx, u"Creation of multi-dimensional sparse DRAM tiles is currently unsupported.")
  error(u"Expected 1D SRAM tile, found ${d}D tile")
}}
class SparseDataDimensionError(isLoad: Boolean, d: Int)(implicit ctx: SrcCtx, state: State) extends UserError(ctx){ def console() = {
  error(ctx, u"""Multi-dimensional ${if (isLoad) "gather" else "scatter"} is currently unsupported.""")
}}
class UnsupportedStridedDRAMError(isLoad: Boolean)(implicit ctx: SrcCtx, state: State) extends UserError(ctx){ def console() = {
  error(ctx, u"""Strided tile ${if (isLoad) "load" else "store"} is currently unsupported""")
}}

class UnsupportedUnalignedTileStore(implicit ctx: SrcCtx, state: State) extends UserError(ctx){ def console() = {
  error(ctx, u"Unaligned tile store is currently unsupported.")
}}

class ControlInReductionError(ctx: SrcCtx)(implicit state: State) extends UserError(ctx){ def console() = {
  error(ctx, u"Reduction functions cannot have inner control nodes")
}}

class ControlInNotDoneError(ctx: SrcCtx)(implicit state: State) extends UserError(ctx){ def console() = {
  error(ctx, "While condition of FSMs cannot have inner control nodes")
}}

class ControlInNextStateError(ctx: SrcCtx)(implicit state: State) extends UserError(ctx){ def console() = {
  error(ctx, "Next state function of FSMs cannot have inner control nodes")
}}


class InvalidOffchipDimensionError(offchip: Exp[_], dim: Int)(implicit ctx: SrcCtx, state: State) extends UserError(ctx){ def console() = {
  error(ctx, u"Offchip memory defined here has invalid ${Nth(dim)} dimension")
  error("Only functions of input arguments, parameters, and constants can be used as DRAM dimensions.")
}}

class ConcurrentReadersError(mem: Exp[_], a: Exp[_], b: Exp[_])(implicit ctx: SrcCtx, state: State) extends UserError(mem.ctxOrElse(ctx)){ def console() = {
  error(mem.ctxOrElse(ctx), u"${mem.tp} defined here has illegal concurrent readers: ")
  error(a.ctxOrElse(ctx), u"  The first read occurs here")
  error(a.ctxOrElse(ctx))
  error(b.ctxOrElse(ctx), u"  The second read occurs here")
  error(b.ctxOrElse(ctx))
}}

class ConcurrentWritersError(mem: Exp[_], a: Exp[_], b: Exp[_])(implicit ctx: SrcCtx, state: State) extends UserError(mem.ctxOrElse(ctx)){ def console() = {
  error(mem.ctxOrElse(ctx), u"${mem.tp} defined here has illegal concurrent writers: ")
  error(a.ctxOrElse(ctx), u"  The first write occurs here")
  error(a.ctxOrElse(ctx))
  error(b.ctxOrElse(ctx), u"  The second write occurs here")
  error(b.ctxOrElse(ctx))
}}

class PipelinedReadersError(mem: Exp[_], a: Exp[_], b: Exp[_])(implicit ctx: SrcCtx, state: State) extends UserError(mem.ctxOrElse(ctx)){ def console() = {
  error(mem.ctxOrElse(ctx), u"${mem.tp} defined here has illegal pipelined readers: ")
  error(a.ctxOrElse(ctx), u"  The first read occurs here")
  error(a.ctxOrElse(ctx))
  error(b.ctxOrElse(ctx), u"  The second read occurs here")
  error(b.ctxOrElse(ctx))
}}

class PipelinedWritersError(mem: Exp[_], a: Exp[_], b: Exp[_])(implicit ctx: SrcCtx, state: State) extends UserError(mem.ctxOrElse(ctx)){ def console() = {
  error(mem.ctxOrElse(ctx), u"${mem.tp} defined here has illegal pipelined writers: ")
  error(a.ctxOrElse(ctx), u"  The first write occurs here")
  error(a.ctxOrElse(ctx))
  error(b.ctxOrElse(ctx), u"  The second write occurs here")
  error(b.ctxOrElse(ctx))
}}

class MultipleReadersError(mem: Exp[_], readers: List[Exp[_]])(implicit ctx: SrcCtx, state: State) extends UserError(mem.ctxOrElse(ctx)){ def console() = {
  error(mem.ctxOrElse(ctx), u"${mem.tp} defined here has illegal multiple readers: ")
  readers.foreach { read =>
    error(read.ctxOrElse(ctx), u"  Read defined here")
    error(read.ctxOrElse(ctx))
  }
}}

class MultipleWritersError(mem: Exp[_], writers: List[Exp[_]])(implicit ctx: SrcCtx, state: State) extends UserError(mem.ctxOrElse(ctx)){ def console() = {
  error(mem.ctxOrElse(ctx), u"${mem.tp} defined here has illegal multiple writers: ")
  writers.foreach { write =>
    error(write.ctxOrElse(ctx), u"  Write defined here")
    error(write.ctxOrElse(ctx))
  }
}}

class NoTopError(ctx: SrcCtx)(implicit state: State) extends ProgramError {
  error("An Accel block is required to specify the area of code to optimize for the FPGA.")
  error("No Accel block was specified for this program.")
}

class EmptyVectorException(ctx: SrcCtx)(implicit state: State) extends UserError(ctx){ def console() = {
  error("Attempted to create an empty vector. Empty vectors are currently disallowed.")
}}

class InvalidVectorApplyIndex(vector: Exp[_], index: Int)(implicit ctx: SrcCtx, state: State) extends UserError(ctx){ def console() = {
  error(u"Attempted to address vector $vector at invalid index $index.")
}}

class InvalidVectorSlice(vector: Exp[_], start: Int, end: Int)(implicit ctx: SrcCtx, state: State) extends UserError(ctx){ def console() = {
  error(u"Attempted to slice vector $vector $end::$start, creating an empty vector")
  error("Note that Vectors in Spatial are little-endian (last element in Vector is index 0).")
}}

class NonConstantInitError(ctx: SrcCtx)(implicit state: State) extends UserError(ctx){ def console() = {
  error(ctx, u"Register must have constant initialization value.")
}}
