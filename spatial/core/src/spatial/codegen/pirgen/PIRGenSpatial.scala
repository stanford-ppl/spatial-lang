package spatial.codegen.pirgen

import argon.core._

trait PIRGenSpatial extends PIRCodegen with PIRFileGen with PIRGenController with PIRGenFringe with PIRGenCounter with PIRGenOp with PIRGenMem with PIRGenAccess with PIRGenDummy with PIRLogger with PIRMultiMethodCodegen {

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = {
    dbgblk(s"emitNode ${qdef(lhs)}") {
      super.emitNode(lhs, rhs)
    }
  }
}
