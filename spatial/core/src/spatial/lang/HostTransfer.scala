package spatial.lang

import argon.internals._
import forge._
import spatial.SpatialApi
import spatial.nodes._
import spatial.utils._

object HostTransferOps {
  /** Smart Constructors **/
  @internal def set_arg[T:Type:Bits](reg: Exp[Reg[T]], value: Exp[T]): Exp[MUnit] = {
    stageWrite(reg)(SetArg(reg, value))(ctx)
  }
  @internal def get_arg[T:Type:Bits](reg: Exp[Reg[T]]): Exp[T] = {
    stage(GetArg(reg))(ctx)
  }
  @internal def set_mem[T:Type:Bits](dram: Exp[DRAM[T]], data: Exp[MArray[T]]): Exp[MUnit] = {
    stageWrite(dram)(SetMem(dram, data))(ctx)
  }
  @internal def get_mem[T:Type:Bits](dram: Exp[DRAM[T]], array: Exp[MArray[T]]): Exp[MUnit] = {
    stageWrite(array)(GetMem(dram, array))(ctx)
  }
}

trait HostTransferApi { this: SpatialApi =>
  import HostTransferOps._

  @api def setArg[A,T:Bits](reg: Reg[T], value: A)(implicit lift: Lift[A,T]): MUnit = {
    implicit val mT: Type[T] = lift.staged
    MUnit( set_arg(reg.s, lift.staged.unwrapped(lift(value))) )
  }
  @api def getArg[T:Type:Bits](reg: Reg[T]): T = wrap(get_arg(reg.s))
  @api def setMem[T:Type:Bits](dram: DRAM[T], data: MArray[T]): MUnit = MUnit(set_mem(dram.s, data.s))
  @api def getMem[T:Type:Bits](dram: DRAM[T]): MArray[T] = {
    val array = MArray.empty[T](Math.productTree(wrap(stagedDimsOf(dram.s))))
    get_mem(dram.s, array.s)
    array
  }

  @api def setMem[T:Type:Bits](dram: DRAM[T], matrix: Matrix[T]): MUnit = setMem(dram, matrix.data)
  @api def getMatrix[T:Type:Bits](dram: DRAM2[T])(implicit ctx: SrcCtx): Matrix[T] = {
    val dims = stagedDimsOf(dram.s)
    val data = getMem(dram)
    matrix(data, wrap(dims(0)), wrap(dims(1)))
  }

  @api def setMem[T:Type:Bits](dram: DRAM[T], tensor3: Tensor3[T]): MUnit = setMem(dram, tensor3.data)
  @api def getTensor3[T:Type:Bits](dram: DRAM3[T])(implicit ctx: SrcCtx): Tensor3[T] = {
    val dims = stagedDimsOf(dram.s)
    val data = getMem(dram)
    tensor3(data, wrap(dims(0)), wrap(dims(1)), wrap(dims(2)))
  }

  @api def setMem[T:Type:Bits](dram: DRAM[T], tensor4: Tensor4[T]): MUnit = setMem(dram, tensor4.data)
  @api def getTensor4[T:Type:Bits](dram: DRAM4[T])(implicit ctx: SrcCtx): Tensor4[T] = {
    val dims = stagedDimsOf(dram.s)
    val data = getMem(dram)
    tensor4(data, wrap(dims(0)), wrap(dims(1)), wrap(dims(2)), wrap(dims(3)))
  }

  @api def setMem[T:Type:Bits](dram: DRAM[T], tensor5: Tensor5[T]): MUnit = setMem(dram, tensor5.data)
  @api def getTensor5[T:Type:Bits](dram: DRAM5[T])(implicit ctx: SrcCtx): Tensor5[T] = {
    val dims = stagedDimsOf(dram.s)
    val data = getMem(dram)
    tensor5(data, wrap(dims(0)), wrap(dims(1)), wrap(dims(2)), wrap(dims(3)), wrap(dims(4)))
  }
}
