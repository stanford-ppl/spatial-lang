package spatial.lang

import spatial._
import forge._

trait HostTransferApi extends HostTransferExp { this: SpatialApi =>

  @api def setArg[A,T:Bits](reg: Reg[T], value: A)(implicit lift: Lift[A,T]): Void = {
    implicit val mT: Meta[T] = lift.staged
    Void( set_arg(reg.s, lift.staged.unwrapped(lift(value))) )
  }
  @api def getArg[T:Meta:Bits](reg: Reg[T]): T = wrap(get_arg(reg.s))
  @api def setMem[T:Meta:Bits](dram: DRAM[T], data: Array[T]): Void = Void(set_mem(dram.s, data.s))
  @api def getMem[T:Meta:Bits](dram: DRAM[T]): Array[T] = {
    val array = Array.empty[T](productTree(wrap(stagedDimsOf(dram.s))))
    get_mem(dram.s, array.s)
    array
  }

  @api def setMem[T:Meta:Bits](dram: DRAM[T], matrix: Matrix[T]): Void = setMem(dram, matrix.data)
  @api def getMatrix[T:Meta:Bits](dram: DRAM2[T])(implicit ctx: SrcCtx): Matrix[T] = {
    val dims = stagedDimsOf(dram.s)
    val data = getMem(dram)
    matrix(data, wrap(dims(0)), wrap(dims(1)))
  }

  @api def setMem[T:Meta:Bits](dram: DRAM[T], tensor3: Tensor3[T]): Void = setMem(dram, tensor3.data)
  @api def getTensor3[T:Meta:Bits](dram: DRAM3[T])(implicit ctx: SrcCtx): Tensor3[T] = {
    val dims = stagedDimsOf(dram.s)
    val data = getMem(dram)
    tensor3(data, wrap(dims(0)), wrap(dims(1)), wrap(dims(2)))
  }

  @api def setMem[T:Meta:Bits](dram: DRAM[T], tensor4: Tensor4[T]): Void = setMem(dram, tensor4.data)
  @api def getTensor4[T:Meta:Bits](dram: DRAM4[T])(implicit ctx: SrcCtx): Tensor4[T] = {
    val dims = stagedDimsOf(dram.s)
    val data = getMem(dram)
    tensor4(data, wrap(dims(0)), wrap(dims(1)), wrap(dims(2)), wrap(dims(3)))
  }

  @api def setMem[T:Meta:Bits](dram: DRAM[T], tensor5: Tensor5[T]): Void = setMem(dram, tensor5.data)
  @api def getTensor5[T:Meta:Bits](dram: DRAM5[T])(implicit ctx: SrcCtx): Tensor5[T] = {
    val dims = stagedDimsOf(dram.s)
    val data = getMem(dram)
    tensor5(data, wrap(dims(0)), wrap(dims(1)), wrap(dims(2)), wrap(dims(3)), wrap(dims(4)))
  }
}

trait HostTransferExp { this: SpatialExp =>

  /** IR Nodes **/
  case class SetArg[T:Type:Bits](reg: Exp[Reg[T]], value: Exp[T]) extends Op[Void] {
    def mirror(f:Tx) = set_arg(f(reg),f(value))
  }
  case class GetArg[T:Type:Bits](reg: Exp[Reg[T]]) extends Op[T] {
    def mirror(f:Tx) = get_arg(f(reg))
  }
  case class SetMem[T:Type:Bits](dram: Exp[DRAM[T]], data: Exp[MetaArray[T]]) extends Op[Void] {
    def mirror(f:Tx) = set_mem(f(dram),f(data))
    override def aliases = Nil
    val mT = typ[T]
  }
  case class GetMem[T:Type:Bits](dram: Exp[DRAM[T]], array: Exp[MetaArray[T]]) extends Op[Void] {
    def mirror(f:Tx) = get_mem(f(dram),f(array))
    override def aliases = Nil
    val mT = typ[T]
  }

  /** Smart Constructors **/
  @internal def set_arg[T:Type:Bits](reg: Exp[Reg[T]], value: Exp[T]): Exp[Void] = {
    stageWrite(reg)(SetArg(reg, value))(ctx)
  }
  @internal def get_arg[T:Type:Bits](reg: Exp[Reg[T]]): Exp[T] = {
    stage(GetArg(reg))(ctx)
  }
  @internal def set_mem[T:Type:Bits](dram: Exp[DRAM[T]], data: Exp[MetaArray[T]]): Exp[Void] = {
    stageWrite(dram)(SetMem(dram, data))(ctx)
  }
  @internal def get_mem[T:Type:Bits](dram: Exp[DRAM[T]], array: Exp[MetaArray[T]]): Exp[Void] = {
    stageWrite(array)(GetMem(dram, array))(ctx)
  }
}
