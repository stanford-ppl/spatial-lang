package spatial.api

import argon.core.Staging
import argon.ops.ArrayApi
import spatial.SpatialExp

trait HostTransferApi extends HostTransferExp with ArrayApi {
  this: SpatialExp =>

  def setArg[A,T:Bits](reg: Reg[T], value: A)(implicit ctx: SrcCtx, lft: Lift[A,T]): Void = {
    Void( set_arg(reg.s, lft.staged.unwrapped(lft.lift(value)))(lft.staged,bits[T],ctx) )
  }
  def getArg[T:Staged:Bits](reg: Reg[T])(implicit ctx: SrcCtx): T = wrap(get_arg(reg.s))
  def setMem[T:Staged:Bits](dram: DRAM[T], data: Array[T])(implicit ctx: SrcCtx): Void = Void(set_mem(dram.s, data.s))
  def getMem[T:Staged:Bits](dram: DRAM[T])(implicit ctx: SrcCtx): Array[T] = {
    val array = Array[T](productTree(wrap(dimsOf(dram.s))))
    get_mem(dram.s, array.s)
    array
  }
}

trait HostTransferExp extends Staging with DRAMExp with RegExp {
  this: SpatialExp =>

  /** IR Nodes **/
  case class SetArg[T:Staged:Bits](reg: Exp[Reg[T]], value: Exp[T]) extends Op[Void] {
    def mirror(f:Tx) = set_arg(f(reg),f(value))
  }
  case class GetArg[T:Staged:Bits](reg: Exp[Reg[T]]) extends Op[T] {
    def mirror(f:Tx) = get_arg(f(reg))
  }
  case class SetMem[T:Staged:Bits](dram: Exp[DRAM[T]], data: Exp[ArgonArray[T]]) extends Op[Void] {
    def mirror(f:Tx) = set_mem(f(dram),f(data))
    override def aliases = Nil
  }
  case class GetMem[T:Staged:Bits](dram: Exp[DRAM[T]], array: Exp[ArgonArray[T]]) extends Op[Void] {
    def mirror(f:Tx) = get_mem(f(dram),f(array))
    override def aliases = Nil
  }

  /** Smart Constructors **/
  def set_arg[T:Staged:Bits](reg: Exp[Reg[T]], value: Exp[T])(implicit ctx: SrcCtx): Exp[Void] = {
    stageWrite(reg)(SetArg(reg, value))(ctx)
  }
  def get_arg[T:Staged:Bits](reg: Exp[Reg[T]])(implicit ctx: SrcCtx): Exp[T] = {
    stage(GetArg(reg))(ctx)
  }
  def set_mem[T:Staged:Bits](dram: Exp[DRAM[T]], data: Exp[ArgonArray[T]])(implicit ctx: SrcCtx): Exp[Void] = {
    stageWrite(dram)(SetMem(dram, data))(ctx)
  }
  def get_mem[T:Staged:Bits](dram: Exp[DRAM[T]], array: Exp[ArgonArray[T]])(implicit ctx: SrcCtx): Exp[Void] = {
    stageWrite(array)(GetMem(dram, array))(ctx)
  }
}
