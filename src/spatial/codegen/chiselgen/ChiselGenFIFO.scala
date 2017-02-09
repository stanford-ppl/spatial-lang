package spatial.codegen.chiselgen

import argon.codegen.chiselgen.ChiselCodegen
import spatial.api.FIFOExp
import spatial.SpatialConfig
import spatial.SpatialExp

trait ChiselGenFIFO extends ChiselCodegen {
  val IR: SpatialExp
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
    case tp: FIFOType[_] => src"chisel.collection.mutable.Queue[${tp.child}]"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op@FIFONew(size)   => 
      val par = duplicatesOf(lhs).head match {
        case BankedMemory(dims,_) => dims.map{_.banks}.head
        case _ => 1
      }
      emit(src"""val ${lhs}_wdata = Wire(Vec($par, UInt(32.W)))""")
      emit(src"""val ${lhs}_readEn = Wire(Bool())""")
      emit(src"""val ${lhs}_writeEn = Wire(Bool())""")
      emit(src"""val ${lhs} = Module(new FIFO($par, $par, $size)) // ${nameOf(lhs).getOrElse("")}""".replace(".U",""))
      emit(src"""val ${lhs}_rdata = ${lhs}.io.out""")
      emit(src"""${lhs}.io.in := ${lhs}_wdata""")
      emit(src"""${lhs}.io.pop := ${lhs}_readEn""")
      emit(src"""${lhs}.io.push := ${lhs}_writeEn""")

    case FIFOEnq(fifo,v,en) => 
      val writer = writersOf(fifo).head.ctrlNode  // Not using 'en' or 'shuffle'
      emit(src"""${fifo}_writeEn := ${writer}_ctr_en // not using $en """)
      emit(src"""${fifo}_wdata := ${v}""")

    case FIFODeq(fifo,en,z) => 
      val reader = readersOf(fifo).head.ctrlNode  // Assuming that each fifo has a unique reader
      emit(src"""${fifo}_readEn := ${reader}_ctr_en // not using $en""")
      emit(src"""val ${lhs} = ${fifo}_rdata(0)""")

    case _ => super.emitNode(lhs, rhs)
  }
}
