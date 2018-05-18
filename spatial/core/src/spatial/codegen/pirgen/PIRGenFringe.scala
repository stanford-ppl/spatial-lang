package spatial.codegen.pirgen

import argon.core._
import spatial.nodes._
import spatial.utils._
import spatial.metadata._

trait PIRGenFringe extends PIRCodegen {
  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case FringeDenseLoad(dram,cmdStream,dataStream) =>
      emit(lhs, s"FringeDenseLoad(dram=${decompose(dram)}, cmdStream=${decompose(cmdStream)}, dataStream=${decompose(dataStream)})", rhs)
    case FringeSparseLoad(dram,addrStream,dataStream) =>
      emit(lhs, s"FringeSparseLoad(dram=${decompose(dram)}, addrStream=${decompose(addrStream)}, dataStream=${decompose(dataStream)})", rhs)
    case FringeDenseStore(dram,cmdStream,dataStream,ackStream) =>
      emit(lhs, s"FringeDenseStore(dram=${decompose(dram)}, cmdStream=${decompose(cmdStream)}, dataStream=${decompose(dataStream)}, ackStream=${decompose(ackStream)})", rhs)
    case FringeSparseStore(dram,cmdStream,ackStream) =>
      emit(lhs, s"FringeSparseStore(dram=${decompose(dram)}, cmdStream=${decompose(cmdStream)}, ackStream=${decompose(ackStream)})", rhs)
    case _ => super.emitNode(lhs, rhs)
  }
}

