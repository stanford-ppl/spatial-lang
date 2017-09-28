package spatial.codegen.scalagen

import argon.core._
import org.virtualized.SourceContext
import spatial.aliases._
import spatial.nodes._
import spatial.utils._

trait ScalaGenLUTs extends ScalaGenMemories {

  override protected def remap(tp: Type[_]): String = tp match {
    case tp: LUTType[_] => src"Array[${tp.child}]"
    case _ => super.remap(tp)
  }
  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op@LUTNew(dims,elems) => emitMem(lhs, src"""$lhs = Array[${op.mT}]($elems)""")
    case op@LUTLoad(rf,inds,en) =>
      val dims = constDimsOf(rf).map(int32s(_))
      open(src"val $lhs = {")
      oobApply(op.mT, rf, lhs, inds){ emit(src"if ($en) $rf.apply(${flattenAddress(dims,inds,None)}) else ${invalid(op.mT)}") }
      close("}")

    case _ => super.emitNode(lhs, rhs)
  }

}
