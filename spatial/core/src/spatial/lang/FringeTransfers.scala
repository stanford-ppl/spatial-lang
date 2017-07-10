package spatial.lang

import argon.core._
import forge._
import spatial.nodes._

object FringeTransfers {
  @internal def fringe_dense_load[T:Type:Bits](
    dram:       Exp[DRAM[T]],
    cmdStream:  Exp[StreamOut[BurstCmd]],
    dataStream: Exp[StreamIn[T]]
  ): Exp[MUnit] = {
    stageUnique(FringeDenseLoad(dram,cmdStream,dataStream))(ctx)
  }

  @internal def fringe_dense_store[T:Type:Bits](
    dram:       Exp[DRAM[T]],
    cmdStream:  Exp[StreamOut[BurstCmd]],
    dataStream: Exp[StreamOut[MTuple2[T,Bit]]],
    ackStream:  Exp[StreamIn[Bit]]
  ): Exp[MUnit] = {
    stageUnique(FringeDenseStore(dram,cmdStream,dataStream,ackStream))(ctx)
  }

  @internal def fringe_sparse_load[T:Type:Bits](
    dram:       Exp[DRAM[T]],
    addrStream: Exp[StreamOut[Int64]],
    dataStream: Exp[StreamIn[T]]
  ): Exp[MUnit] = {
    stageUnique(FringeSparseLoad(dram,addrStream,dataStream))(ctx)
  }

  @internal def fringe_sparse_store[T:Type:Bits](
    dram:       Exp[DRAM[T]],
    cmdStream: Exp[StreamOut[MTuple2[T,Int64]]],
    ackStream: Exp[StreamIn[Bit]]
  ): Exp[MUnit] = {
    stageUnique(FringeSparseStore(dram,cmdStream,ackStream))(ctx)
  }
}
