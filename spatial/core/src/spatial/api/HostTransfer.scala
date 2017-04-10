package spatial.api

import argon.core.Staging
import argon.ops.ArrayExtApi
import spatial.SpatialExp
import forge._

trait HostTransferApi extends HostTransferExp with ArrayExtApi {
  this: SpatialExp =>

  @api def setArg[A,T:Bits](reg: Reg[T], value: A)(implicit ctx: SrcCtx, lift: Lift[A,T]): Void = {
    Void( set_arg(reg.s, lift.staged.unwrapped(lift(value)))(lift.staged,bits[T],ctx) )
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
}

trait HostTransferExp extends Staging with DRAMExp with RegExp {
  this: SpatialExp =>

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
  }
  case class GetMem[T:Type:Bits](dram: Exp[DRAM[T]], array: Exp[MetaArray[T]]) extends Op[Void] {
    def mirror(f:Tx) = get_mem(f(dram),f(array))
    override def aliases = Nil
  }

  /** Smart Constructors **/
  def set_arg[T:Type:Bits](reg: Exp[Reg[T]], value: Exp[T])(implicit ctx: SrcCtx): Exp[Void] = {
    stageWrite(reg)(SetArg(reg, value))(ctx)
  }
  def get_arg[T:Type:Bits](reg: Exp[Reg[T]])(implicit ctx: SrcCtx): Exp[T] = {
    stage(GetArg(reg))(ctx)
  }
  def set_mem[T:Type:Bits](dram: Exp[DRAM[T]], data: Exp[MetaArray[T]])(implicit ctx: SrcCtx): Exp[Void] = {
    stageWrite(dram)(SetMem(dram, data))(ctx)
  }
  def get_mem[T:Type:Bits](dram: Exp[DRAM[T]], array: Exp[MetaArray[T]])(implicit ctx: SrcCtx): Exp[Void] = {
    stageWrite(array)(GetMem(dram, array))(ctx)
  }
}
