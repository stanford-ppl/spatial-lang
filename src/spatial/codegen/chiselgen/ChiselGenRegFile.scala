package spatial.codegen.chiselgen

import argon.codegen.chiselgen.{ChiselCodegen}
import spatial.api.RegisterFileExp
import spatial.SpatialConfig
import spatial.SpatialExp

trait ChiselGenRegFile extends ChiselGenSRAM {
  val IR: SpatialExp
  import IR._

  // private var rows: Int = 0
  // private var cols: Int = 0

  override def quote(s: Exp[_]): String = {
    if (SpatialConfig.enableNaming) {
      s match {
        case lhs: Sym[_] =>
          lhs match {
            case Def(e: RegFileNew[_]) =>
              s"""x${lhs.id}_${nameOf(lhs).getOrElse("regfile")}"""
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

  override protected def remap(tp: Type[_]): String = tp match {
    case tp: RegFileType[_] => src"Array[${tp.child}]"
    case _ => super.remap(tp)
  }

  /* Copied from Scala Gen
  
  private def shiftIn(lhs: Exp[_], rf: Exp[_], inds: Seq[Exp[Index]], d: Int, data: Exp[_], isVec: Boolean): Unit = {
    val len = 1//if (isVec) lenOf(data) else 1
    val dims = stagedDimsOf(rf)
    val size = dims(d)
    val stride = (dims.drop(d+1).map(quote) :+ "1").mkString("*")

    open(src"val $lhs = {")
      // emit(src"val ofs = ${flattenAddress(dims,inds,None)}")
      emit(src"val stride = $stride")
      open(src"for (j <- $size-1 to 0 by - 1) {")
        if (isVec) emit(src"if (j < $len) $rf.update(ofs+j*stride, $data(j)) else $rf.update(ofs + j*stride, $rf.apply(ofs + (j - $len)*stride))")
        else       emit(src"if (j < $len) $rf.update(ofs+j*stride, $data) else $rf.update(ofs + j*stride, $rf.apply(ofs + (j - $len)*stride))")
      close("}")
    close("}")
  }
  */

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op@RegFileNew(dims) =>
      val par = writersOf(lhs).length
      emitGlobal(s"val ${quote(lhs)} = Module(new templates.ParallelShiftRegFile(${dims(0)}, ${dims(1)}, 1, ${par}/${dims(0)}))")
      // cols = dims(1).toInt
      
    case op@RegFileLoad(rf,inds,en) =>
      if (needsFPType(lhs.tp)) { lhs.tp match {
        case FixPtType(s,d,f) => 
          emit(src"""val ${lhs} = Wire(new FixedPoint($s, $d, $f))""")
          emit(src"""${lhs}.number := ${rf}.readValue(${inds(0)}.number, ${inds(1)}.number)""")
        case _ =>
          emit(src"""${lhs} := ${rf}.readValue(${inds(0)}.number, ${inds(1)}.number)""")
      }} else {
          emit(src"""${lhs} := ${rf}.readValue(${inds(0)}.number, ${inds(1)}.number)""")
      }

    case op@RegFileStore(rf,inds,data,en) =>
      val parent = writersOf(rf).find{_.node == lhs}.get.ctrlNode
      emit(s"${quote(rf)}.connectWPort(${quote(data)}.number, ${quote(inds(0))}.number, ${quote(inds(1))}.number, ${quote(en)} & ${quote(parent)}_datapath_en)")
      // TODO: finish this using inds like in Load

    case RegFileShiftIn(rf,inds,d,data,en)    => 
      val parent = writersOf(rf).find{_.node == lhs}.get.ctrlNode
      emit(s"${quote(rf)}.connectShiftPort(${quote(data)}.number, ${quote(inds(0))}.number, ${quote(en)} & ${quote(parent)}_datapath_en)")
      
    case ParRegFileShiftIn(rf,i,d,data,en) => 
      emit("ParRegFileShiftIn not implemented!")
      // (copied from ScalaGen) shiftIn(lhs, rf, i, d, data, isVec = true)

    case _ => super.emitNode(lhs, rhs)
  }

}
