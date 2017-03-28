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

  override protected def remap(tp: Staged[_]): String = tp match {
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
      // TODO: Examine writers and see if all are from Shift or from Store. In that case,
      // can specialize below to templates.ParallelShiftReg or templates.RegFile
      emitGlobal(s"val ${quote(lhs)} = Array.tabulate(${dims(0)}) { i => Module(new templates.ParallelShiftRegFile(${dims(1)}, /* stride = */ 1)) }")
      // rows = dims(0).toInt
      // cols = dims(1).toInt
      
    case op@RegFileLoad(rf,inds,en) =>
      // TODO: Right now both indices are potentially not constants, so we add muxes
      // Can specialize if all selects are constants
      emit(s"val ${quote(lhs)}_tmp = Array.tabulate(${quote(rf)}.length) { i =>")
      emit(s"  (i.U -> chisel3.util.MuxLookup(${quote(inds(1))}, 0.U, Array.tabulate(${quote(rf)}(0).size){j => (j.U -> ${quote(rf)}(i).io.data_out(j))}))")
      emit(s"}")
      if (needsFPType(lhs.tp)) { lhs.tp match {
        case FixPtType(s,d,f) => 
          emit(src"""val ${lhs} = Wire(new FixedPoint($s, $d, $f))""")
          emit(src"""${lhs}.number := chisel3.util.MuxLookup(${quote(inds(0))}, 0.U, ${quote(lhs)}_tmp)""")
        case _ =>
          emit(s"val ${quote(lhs)} = chisel3.util.MuxLookup(${quote(inds(0))}, 0.U, ${quote(lhs)}_tmp)")
      }} else {
        emit(s"val ${quote(lhs)} = chisel3.util.MuxLookup(${quote(inds(0))}, 0.U, ${quote(lhs)}_tmp)")
      }

    case op@RegFileStore(rf,inds,data,en) =>
      emit(s"${quote(rf)}(${inds(0)}).io.data_in(0) := ${quote(data)}")
      emit(s"${quote(rf)}(${inds(0)}).io.w_en := ${quote(en)}")
      emit(s"${quote(rf)}(${inds(0)}).io.w_addr := ${inds(1)}.U")
      // TODO: finish this using inds like in Load

    case RegFileShiftIn(rf,i,d,data,en)    => 
      // (copied from ScalaGen) shiftIn(lhs, rf, i, d, data, isVec = false)
      
    case ParRegFileShiftIn(rf,i,d,data,en) => 
      // (copied from ScalaGen) shiftIn(lhs, rf, i, d, data, isVec = true)

    case _ => super.emitNode(lhs, rhs)
  }

}
