package spatial.lang

import argon.core._
import forge._
import org.virtualized._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

/** Specialized buses **/
@struct case class BurstCmd(offset: Int64, size: Index, isLoad: Bit)
@struct case class IssuedCmd(size: Index, start: Index, end: Index)

abstract class DRAMBus[T:Type:Bits] extends Bus { def length = bits[T].length }

case object BurstCmdBus extends DRAMBus[BurstCmd]
case object BurstAckBus extends DRAMBus[Bit]
case class BurstDataBus[T:Type:Bits]() extends DRAMBus[T]
case class BurstFullDataBus[T:Type:Bits]() extends DRAMBus[MTuple2[T,Bit]]

case object GatherAddrBus extends DRAMBus[Int64]
case class GatherDataBus[T:Type:Bits]() extends DRAMBus[T]

case class ScatterCmdBus[T:Type:Bits]() extends DRAMBus[MTuple2[T, Int64]]
case object ScatterAckBus extends DRAMBus[Bit]


object DRAMTransfers {
  /** Internal **/
  @internal def dense_transfer[T:Type:Bits,C[_]](
    tile:   DRAMDenseTile[T],
    local:  C[T],
    isLoad: Boolean,
    isAlign: Boolean = false
  )(implicit mem: Mem[T,C], mC: Type[C[T]]): MUnit = {
    implicit val mD: Type[DRAM[T]] = tile.dram.tp

    // Extract range lengths early to avoid unit pipe insertion eliminating rewrite opportunities
    val dram    = tile.dram
    val ofs     = tile.ranges.map(_.start.map(_.s).getOrElse(int32s(0)))
    val lens    = tile.ranges.map(_.length.s)
    val strides = tile.ranges.map(_.step.map(_.s).getOrElse(int32s(1)))
    val units   = tile.ranges.map(_.isUnit)
    val p       = extractParFactor(tile.ranges.last.p)

    // UNSUPPORTED: Strided ranges for DRAM in burst load/store
    if (strides.exists{case Exact(c) if (c == 1 || isLoad) => false ; case _ => true}) {
      new spatial.UnsupportedStridedDRAMError(isLoad)(ctx, state)
    } else if (strides.last match{case Exact(c) if (c == 1) => false ; case _ => true}) {
      new spatial.UnsupportedStridedDRAMError(isLoad)(ctx, state)
    }

    val localRank = mem.iterators(local).length // TODO: Replace with something else here (this creates counters)

    val iters = List.tabulate(localRank){_ => fresh[Index]}

    MUnit(op_dense_transfer(dram,local.s,ofs,lens,strides,units,p,isLoad,isAlign,iters))
  }

  @internal def sparse_transfer[T:Type:Bits](
    tile:   DRAMSparseTile[T],
    local:  SRAM1[T],
    isLoad: Boolean
  ): MUnit = {
    implicit val mD: Type[DRAM[T]] = tile.dram.tp

    val p = extractParFactor(tile.addrs.p)
    val size = tile.len.s //stagedDimsOf(local.s).head
    val i = fresh[Index]
    MUnit(op_sparse_transfer(tile.dram, local.s, tile.addrs.s, size, p, isLoad, i))
  }

  @internal def sparse_transfer_mem[T,C[T],A[_]](
    tile:   DRAMSparseTileMem[T,A],
    local:  C[T],
    isLoad: Boolean
  )(implicit mT: Type[T], bT: Bits[T], memC: Mem[T,C], mC: Type[C[T]]): MUnit = {
    implicit val mD: Type[DRAM[T]] = tile.dram.tp
    implicit val memA: Mem[Index,A] = tile.memA
    implicit val mA: Type[A[Index]] = tile.mA

    val p = extractParFactor(memA.par(tile.addrs))
    val size = tile.len.s
    val i = fresh[Index]
    MUnit(op_sparse_transfer_mem(tile.dram, local.s, tile.addrs.s, size, p, isLoad, i))
  }

  /** Constructors **/
  @internal def op_dense_transfer[T:Type:Bits,C[T]](
    dram:   Exp[DRAM[T]],
    local:  Exp[C[T]],
    ofs:    Seq[Exp[Index]],
    lens:   Seq[Exp[Index]],
    strides:Seq[Exp[Index]],
    units:  Seq[Boolean],
    p:      Const[Index],
    isLoad: Boolean,
    isAlign: Boolean,
    iters:  List[Bound[Index]]
  )(implicit mem: Mem[T,C], mC: Type[C[T]], mD: Type[DRAM[T]]): Exp[MUnit] = {

    val node = DenseTransfer(dram,local,ofs,lens,strides,units,p,isLoad,iters)
    node.isAlign = isAlign

    val out = if (isLoad) stageWrite(local)(node)(ctx) else stageWrite(dram)(node)(ctx)
    styleOf(out) = InnerPipe
    out
  }

  @internal def op_sparse_transfer[T:Type:Bits](
    dram:   Exp[DRAM[T]],
    local:  Exp[SRAM1[T]],
    addrs:  Exp[SRAM1[Index]],
    size:   Exp[Index],
    p:      Const[Index],
    isLoad: Boolean,
    i:      Bound[Index]
  )(implicit mD: Type[DRAM[T]]): Exp[MUnit] = {

    val node = SparseTransfer(dram,local,addrs,size,p,isLoad,i)

    val out = if (isLoad) stageWrite(local)(node)(ctx) else stageWrite(dram)(node)(ctx)
    styleOf(out) = InnerPipe
    out
  }

  @internal def op_sparse_transfer_mem[T:Type:Bits,C[T],A[_]](
    dram:   Exp[DRAM[T]],
    local:  Exp[C[T]],
    addrs:  Exp[A[Index]],
    size:   Exp[Index],
    p:      Const[Index],
    isLoad: Boolean,
    i:      Bound[Index]
  )(implicit memC: Mem[T,C], mC: Type[C[T]], memA: Mem[Index,A], mA: Type[A[Index]], mD: Type[DRAM[T]]) = {
    val node = SparseTransferMem(dram,local,addrs,size,p,isLoad,i)

    val out = if (isLoad) stageWrite(local)(node)(ctx) else stageWrite(dram)(node)(ctx)
    styleOf(out) = InnerPipe
    out
  }
}
