package spatial.codegen.pirgen

import argon.core._
import spatial.nodes._
import spatial.utils._
import spatial.metadata._

trait PIRGenFringe extends PIRCodegen {
  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case FringeDenseLoad(dram,cmdStream,dataStream) =>
      emit(DefRhs(lhs, s"FringeDenseLoad", "dram"->decompose(dram), "cmdStream"->decompose(cmdStream), "dataStream"->decompose(dataStream)))
    case FringeSparseLoad(dram,addrStream,dataStream) =>
      emit(DefRhs(lhs, s"FringeSparseLoad", "dram"->decompose(dram), "addrStream"->decompose(addrStream), "dataStream"->decompose(dataStream)))
    case FringeDenseStore(dram,cmdStream,dataStream,ackStream) =>
      emit(DefRhs(lhs, s"FringeDenseStore","dram"->decompose(dram),"cmdStream"->decompose(cmdStream), "dataStream"->decompose(dataStream), "ackStream"->decompose(ackStream)))
    case FringeSparseStore(dram,cmdStream,ackStream) =>
      emit(DefRhs(lhs, s"FringeSparseStore", "dram"->decompose(dram), "cmdStream"->decompose(cmdStream), "ackStream"->decompose(ackStream)))
    case _ => super.emitNode(lhs, rhs)
  }
}

