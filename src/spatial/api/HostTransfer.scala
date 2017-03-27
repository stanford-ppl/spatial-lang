package spatial.api

import argon.core.Staging
import argon.ops.ArrayExtApi
import spatial.SpatialExp

trait HostTransferApi extends HostTransferExp with ArrayExtApi {
  this: SpatialExp =>

  def setArg[A,T:Bits](reg: Reg[T], value: A)(implicit ctx: SrcCtx, lift: Lift[A,T]): Void = {
    Void( set_arg(reg.s, lift.staged.unwrapped(lift(value)))(lift.staged,bits[T],ctx) )
  }
  def getArg[T:Meta:Bits](reg: Reg[T])(implicit ctx: SrcCtx): T = wrap(get_arg(reg.s))
  def setMem[T:Meta:Bits](dram: DRAM[T], data: Array[T])(implicit ctx: SrcCtx): Void = Void(set_mem(dram.s, data.s))
  def getMem[T:Meta:Bits](dram: DRAM[T])(implicit ctx: SrcCtx): Array[T] = {
    val array = Array.empty[T](productTree(wrap(dimsOf(dram.s))))
    get_mem(dram.s, array.s)
    array
  }

  def setMem[T:Meta:Bits](dram: DRAM[T], matrix: Matrix[T])(implicit ctx: SrcCtx): Void = setMem(dram, matrix.data)
  def getMatrix[T:Meta:Bits](dram: DRAM[T])(implicit ctx: SrcCtx): Matrix[T] = {
    val dims = dimsOf(dram.s)
    if (dims.length != 2) {
      error(ctx, u"Cannot get matrix for ${dram.s} since it is ${dims}-dimensional.")
      wrap(fresh[Matrix[T]])
    }
    else {
      val data = getMem(dram)
      matrix(data, wrap(dims(0)), wrap(dims(1)))
    }
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
