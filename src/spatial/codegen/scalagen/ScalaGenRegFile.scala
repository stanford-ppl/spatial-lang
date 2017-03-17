package spatial.codegen.scalagen

import spatial.api.{RegisterFileExp, SRAMExp, VectorExp}
import org.virtualized.SourceContext

trait ScalaGenRegFile extends ScalaGenSRAM {
  val IR: SRAMExp with RegisterFileExp with VectorExp
  import IR._

  override protected def remap(tp: Staged[_]): String = tp match {
    case tp: RegFileType[_] => src"Array[${tp.child}]"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op@RegFileNew(dims) => emit(src"val $lhs = Array.fill(${dims.map(quote).mkString("*")})(${invalid(op.mT)})")
    case op@RegFileLoad(rf,inds) =>
      val dims = stagedDimsOf(rf)
      open(src"val $lhs = {")
        oobApply(op.mT, rf, lhs, inds){ emit(src"$rf.apply(${flattenAddress(dims,inds,None)})") }
      close("}")

    case op@RegFileStore(rf,inds,data) =>
      val dims = stagedDimsOf(rf)
      open(src"val $lhs = {")
        oobUpdate(op.mT, rf, lhs, inds){ emit(src"$rf.update(${flattenAddress(dims,inds,None)}, $data)") }
      close("}")

    case RegFileShiftIn(rf,data) =>
      val len = lenOf(data)
      val dims = stagedDimsOf(rf)
      val size = dims.map(quote).mkString("*")
      open(src"val lhs = {")
        open(src"for (i <- 0 until $size) {")
          emit(src"if (i < $len) $rf.update(i, $data(i)) else $rf.update(i, $rf.apply(i - $len))")
        close("}")
      close("}")

    case _ => super.emitNode(lhs, rhs)
  }

}
