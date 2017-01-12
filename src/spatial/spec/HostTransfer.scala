package spatial.spec

import argon.ops.ArrayOps

trait HostTransferOps extends DRAMOps with RegOps with ArrayOps {
  this: SpatialOps =>

  def setArg[T:Bits](reg: Reg[T], value: T)(implicit ctx: SrcCtx): Void
  def getArg[T:Bits](reg: Reg[T])(implicit ctx: SrcCtx): T
  def setMem[T:Bits](dram: DRAM[T], data: MArray[T])(implicit ctx: SrcCtx): Void
  def getMem[T:Bits](dram: DRAM[T])(implicit ctx: SrcCtx): MArray[T]
}

trait HostTransferApi extends HostTransferOps with DRAMApi with RegApi { this: SpatialApi => }

trait HostTransferExp extends HostTransferOps with DRAMExp with RegExp {
  this: SpatialExp =>

  /** API **/
  def setArg[T:Bits](reg: Reg[T], value: T)(implicit ctx: SrcCtx): Void = Void(set_arg(reg.s, value.s))
  def getArg[T:Bits](reg: Reg[T])(implicit ctx: SrcCtx): T = wrap(get_arg(reg.s))
  def setMem[T:Bits](dram: DRAM[T], data: MArray[T])(implicit ctx: SrcCtx): Void = Void(set_mem(dram.s, data.s))
  def getMem[T:Bits](dram: DRAM[T])(implicit ctx: SrcCtx): MArray[T] = {
    val array = Array[T](productTree(wrap(dimsOf(dram.s))))
    get_mem(dram.s, array.s)
    array
  }

  /** IR Nodes **/
  case class SetArg[T:Bits](reg: Sym[Reg[T]], value: Sym[T]) extends Op[Void] {
    def mirror(f:Tx) = set_arg(f(reg),f(value))
  }
  case class GetArg[T:Bits](reg: Sym[Reg[T]]) extends Op[T] {
    def mirror(f:Tx) = get_arg(f(reg))
  }
  case class SetMem[T:Bits](dram: Sym[DRAM[T]], data: Sym[MArray[T]]) extends Op[Void] {
    def mirror(f:Tx) = set_mem(f(dram),f(data))
    override def aliases = Nil
  }
  case class GetMem[T:Bits](dram: Sym[DRAM[T]], array: Sym[MArray[T]]) extends Op[Void] {
    def mirror(f:Tx) = get_mem(f(dram),f(array))
    override def aliases = Nil
  }

  /** Smart Constructors **/
  def set_arg[T:Bits](reg: Sym[Reg[T]], value: Sym[T])(implicit ctx: SrcCtx): Sym[Void] = {
    stageWrite(reg)(SetArg(reg, value))(ctx)
  }
  def get_arg[T:Bits](reg: Sym[Reg[T]])(implicit ctx: SrcCtx): Sym[T] = {
    stage(GetArg(reg))(ctx)
  }
  def set_mem[T:Bits](dram: Sym[DRAM[T]], data: Sym[MArray[T]])(implicit ctx: SrcCtx): Sym[Void] = {
    stageWrite(dram)(SetMem(dram, data))(ctx)
  }
  def get_mem[T:Bits](dram: Sym[DRAM[T]], array: Sym[MArray[T]])(implicit ctx: SrcCtx): Sym[Void] = {
    stageWrite(array)(GetMem(dram, array))(ctx)
  }
}
