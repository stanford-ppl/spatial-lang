package spatial.lang

import argon.core._
import forge._
import spatial.nodes._

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

