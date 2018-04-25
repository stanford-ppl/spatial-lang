package spatial.codegen.pirgen

import argon.core._
import spatial.nodes._
import spatial.utils._
import spatial.metadata._

trait PIRGenFringe extends PIRCodegen {
  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = {
    lhs match {
      case _ if isFringe(lhs) =>
        val children = rhs.allInputs.filter { e => isDRAM(e) || isStream(e) || isStreamOut(e) }.flatMap { e => decompose(e) }
        emit(lhs, s"FringeContainer(${children.mkString(s",")})", rhs)
      case _ => super.emitNode(lhs, rhs)
    }
  }
}

