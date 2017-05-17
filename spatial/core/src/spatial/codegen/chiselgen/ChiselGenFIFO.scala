package spatial.codegen.chiselgen

import argon.codegen.chiselgen.ChiselCodegen
import spatial.api.FIFOExp
import spatial.api.DRAMTransferExp
import spatial.SpatialConfig
import spatial.SpatialExp

trait ChiselGenFIFO extends ChiselCodegen {
  val IR: SpatialExp
  import IR._

  override protected def spatialNeedsFPType(tp: Type[_]): Boolean = tp match { // FIXME: Why doesn't overriding needsFPType work here?!?!
      case FixPtType(s,d,f) => if (s) true else if (f == 0) false else true
      case IntType()  => false
      case LongType() => false
      case FloatType() => true
      case DoubleType() => true
      case _ => super.needsFPType(tp)
  }

  override protected def bitWidth(tp: Type[_]): Int = {
    tp match { 
      case Bits(bitEv) => bitEv.length
      // case x: StructType[_] => x.fields.head._2 match {
      //   case _: IssuedCmd => 96
      //   case _ => super.bitWidth(tp)
      // }
      case _ => super.bitWidth(tp)
    }
  }

  override def quote(s: Exp[_]): String = {
    if (SpatialConfig.enableNaming) {
      s match {
        case lhs: Sym[_] =>
          lhs match {
            case Def(e: FIFONew[_]) =>
              s"""x${lhs.id}_${nameOf(lhs).getOrElse("fifo").replace("$","")}"""
            case Def(FIFOEnq(fifo:Sym[_],_,_)) =>
              s"x${lhs.id}_enqTo${fifo.id}"
            case Def(FIFODeq(fifo:Sym[_],_)) =>
              s"x${lhs.id}_deqFrom${fifo.id}"
            case Def(FIFOEmpty(fifo:Sym[_])) =>
              s"x${lhs.id}_isEmpty${fifo.id}"
            case Def(FIFOFull(fifo:Sym[_])) =>
              s"x${lhs.id}_isFull${fifo.id}"
            case Def(FIFOAlmostEmpty(fifo:Sym[_])) =>
              s"x${lhs.id}_isAlmostEmpty${fifo.id}"
            case Def(FIFOAlmostFull(fifo:Sym[_])) =>
              s"x${lhs.id}_isAlmostFull${fifo.id}"
            case Def(FIFONumel(fifo:Sym[_])) =>
              s"x${lhs.id}_numel${fifo.id}"
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
    case tp: FIFOType[_] => src"chisel.collection.mutable.Queue[${tp.child}]"
    case _ => super.remap(tp)
  }

  // override protected def vecSize(tp: Type[_]): Int = tp.typeArguments.head match {
  //   case tp: Vector[_] => 1
  //   case _ => 1
  // }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op@FIFONew(size)   => 
      val rPar = readersOf(lhs).map { r => 
        r.node match {
          case Def(_: FIFODeq[_]) => 1
          case Def(a@ParFIFODeq(q,ens)) => ens.length
        }
      }.max
      val wPar = writersOf(lhs).map { w =>
        w.node match {
          case Def(_: FIFOEnq[_]) => 1
          case Def(a@ParFIFOEnq(q,_,ens)) => ens.length
        }
      }.max
      val width = bitWidth(lhs.tp.typeArguments.head)
      emitGlobalModule(s"""val ${quote(lhs)} = Module(new FIFO($rPar, $wPar, $size, $width)) // ${nameOf(lhs).getOrElse("")}""")

    case FIFOEnq(fifo,v,en) => 
      val writer = writersOf(fifo).head.ctrlNode  
      // val enabler = if (loadCtrlOf(fifo).contains(writer)) src"${writer}_datapath_en" else src"${writer}_sm.io.output.ctr_inc"
      val enabler = src"${writer}_datapath_en"
      emit(src"""${fifo}.io.enq := ShiftRegister($enabler & ~${writer}_inhibitor, ${symDelay(lhs)}) & $en """)
      emit(src"""${fifo}.io.in := Vec(List(${v}.raw))""")


    case FIFODeq(fifo,en) =>
      val reader = readersOf(fifo).head.ctrlNode  // Assuming that each fifo has a unique reader
      emit(src"""${fifo}.io.deq := ShiftRegister(${reader}_datapath_en & ~${reader}_inhibitor, ${symDelay(lhs)}) & $en & ~${reader}_inhibitor""")
      fifo.tp.typeArguments.head match { 
        case FixPtType(s,d,f) => if (spatialNeedsFPType(fifo.tp.typeArguments.head)) {
            emit(s"""val ${quote(lhs)} = Utils.FixedPoint($s,$d,$f,${quote(fifo)}.io.out(0))""")
          } else {
            emit(src"""val ${lhs} = ${fifo}.io.out(0)""")
          }
        case _ => emit(src"""val ${lhs} = ${fifo}.io.out(0)""")
      }

    case FIFOEmpty(fifo) => emit(src"val $lhs = ${fifo}.io.empty")
    case FIFOFull(fifo) => emit(src"val $lhs = ${fifo}.io.full")
    case FIFOAlmostEmpty(fifo) => emit(src"val $lhs = ${fifo}.io.almostEmpty")
    case FIFOAlmostFull(fifo) => emit(src"val $lhs = ${fifo}.io.almostFull")
    case FIFONumel(fifo) => emit(src"val $lhs = ${fifo}.io.numel")

    case _ => super.emitNode(lhs, rhs)
  }
}
