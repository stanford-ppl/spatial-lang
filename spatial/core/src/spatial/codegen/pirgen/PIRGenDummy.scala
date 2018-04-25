package spatial.codegen.pirgen

import argon.core._
import argon.nodes._
import spatial.nodes._
import spatial.utils._
import spatial.metadata._

trait PIRGenDummy extends PIRCodegen {
  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = {
    rhs match {
      case _:ArrayNew[_] =>
      case _:ArrayApply[_] =>
      case _:ArrayZip[_, _, _] =>
      case _:ArrayReduce[_] =>
      case _:ArrayLength[_] =>
      case _:ArrayFromSeq[_] =>
      case _:ArrayMap[_,_] =>
      case _:ArrayFlatMap[_,_] =>
      case _:StringToFixPt[_, _, _] =>
      case _:MapIndices[_] =>
      case _:FixRandom[_, _, _] =>
      case _:SetArg[_] =>
      case _:GetArg[_] =>
      case _:SetMem[_] =>
      case _:GetMem[_] =>
      case _:InputArguments =>
      case _:PrintlnIf =>
      case _:PrintIf =>
      case _:StringConcat =>
      case _:ToString[_] =>
      case _:RangeForeach =>
      case _:OpenFile =>
      case _:CloseFile =>
      case _:ReadTokens =>
      case _:AssertIf =>
      case _:FixPtToFltPt[_,_,_,_,_] =>
      case _:FltPtToFixPt[_,_,_,_,_] =>
      case _:VarRegNew[_] =>
      case _:VarRegRead[_] =>
      case _:VarRegWrite[_] =>
      case _ => super.emitNode(lhs, rhs)
    }
  }
}
