package spatial.codegen.cppgen

import argon.codegen.cppgen.CppCodegen
import spatial.api.FIFOExp
import spatial.SpatialConfig

trait CppGenFIFO extends CppCodegen {
  val IR: FIFOExp
  import IR._

  override def quote(s: Exp[_]): String = {
    if (SpatialConfig.enableNaming) {
      s match {
        case lhs: Sym[_] =>
          val Op(rhs) = lhs
          rhs match {
            case e: FIFONew[_] =>
              s"x${lhs.id}_Fifo"
            case FIFOEnq(fifo:Sym[_],_,_) =>
              s"x${lhs.id}_enqTo${fifo.id}"
            case FIFODeq(fifo:Sym[_],_,_) =>
              s"x${lhs.id}_deqFrom${fifo.id}"
            case _ =>
              super.quote(s)
          }
        case _ =>
          super.quote(s)
      }
    } else {
      super.quote(s)
    }
  } 

  override protected def remap(tp: Staged[_]): String = tp match {
    case tp: FIFOType[_] => src"cpp.collection.mutable.Queue[${tp.bits}]"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case _ => super.emitNode(lhs, rhs)
  }
}
