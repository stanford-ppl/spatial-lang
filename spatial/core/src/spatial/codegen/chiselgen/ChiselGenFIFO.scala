package spatial.codegen.chiselgen

import argon.core._
import argon.nodes._
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._
import spatial.SpatialConfig

trait ChiselGenFIFO extends ChiselGenSRAM {

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
              s"""x${lhs.id}_${lhs.name.getOrElse("fifo").replace("$","")}"""
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
    case op@FIFONew(_)   =>
      if (SpatialConfig.useCheapFifos) {
        val size = constSizeOf(lhs)
        // ASSERT that all pars are the same!
        // Console.println(s"Working on $lhs, readers ${readersOf(lhs)}")
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
        emitGlobalModule(src"""val $lhs = Module(new FIFO($rPar, $wPar, $size, ${writersOf(lhs).length}, ${readersOf(lhs).length}, $width)) // ${lhs.name.getOrElse("")}""")
      } else {
        val size = constSizeOf(lhs)
        // ASSERT that all pars are the same!
        // Console.println(s"Working on $lhs, readers ${readersOf(lhs)}")
        val rPar = readersOf(lhs).map { r => 
          r.node match {
            case Def(_: FIFODeq[_]) => 1
            case Def(a@ParFIFODeq(q,ens)) => ens.length
          }
        }
        val wPar = writersOf(lhs).map { w =>
          w.node match {
            case Def(_: FIFOEnq[_]) => 1
            case Def(a@ParFIFOEnq(q,_,ens)) => ens.length
          }
        }
        val width = bitWidth(lhs.tp.typeArguments.head)
        emitGlobalModule(src"""val $lhs = Module(new GeneralFIFO(List($rPar), List($wPar), $size, $width)) // ${lhs.name.getOrElse("")}""")
      }

    case FIFOEnq(fifo,v,en) => 
      val writer = writersOf(fifo).find{_.node == lhs}.get.ctrlNode
      // val enabler = if (loadCtrlOf(fifo).contains(writer)) src"${swap(writer, DatapathEn)}" else src"${writer}_sm.io.output.ctr_inc"
      val enabler = src"${swap(writer, DatapathEn)}"
      if (SpatialConfig.useCheapFifos) {
        emit(src"""${fifo}.connectEnqPort(Vec(List(${v}.r)), /*${writer}_en & seems like we don't want this for retime to work*/ ($enabler & ~${writer}_inhibitor & ${swap(writer, IIDone)}).D(${symDelay(lhs)}) & $en)""")  
      } else {
        emit(src"""${fifo}.connectEnqPort(Vec(List(${v}.r)), Vec(List(($enabler & ~${writer}_inhibitor & ${swap(writer, IIDone)}).D(${symDelay(lhs)}) & $en)))""")
      }
      


    case FIFODeq(fifo,en) =>
      val reader = readersOf(fifo).find{_.node == lhs}.get.ctrlNode
      val bug202delay = reader match {
        case Def(op@SwitchCase(_)) => 
          if (Bits.unapply(op.mT).isDefined & listensTo(reader).distinct.length == 0) src"${symDelay(parentOf(reader).get)}" else src"${symDelay(lhs)}" 
        case _ => src"${symDelay(lhs)}" 
      }
      val enabler = src"${swap(reader, DatapathEn)} & ~${reader}_inhibitor & ${swap(reader, IIDone)}"
      emit(src"val $lhs = Wire(${newWire(lhs.tp)})")
      if (SpatialConfig.useCheapFifos) {
        emit(src"""${lhs}.r := ${fifo}.connectDeqPort(${reader}_en & ($enabler).D(${bug202delay}) & $en).apply(0)""")  
      } else {
        emit(src"""${lhs}.r := ${fifo}.connectDeqPort(Vec(List(${reader}_en & ($enabler).D(${bug202delay}) & $en))).apply(0)""")
      }
      
    case FIFOPeek(fifo) => emit(src"val $lhs = Wire(${newWire(lhs.tp)}); ${lhs}.r := ${fifo}.io.out(0).r")
    case FIFOEmpty(fifo) => emitGlobalWire(src"val $lhs = Wire(Bool())"); emit(src"$lhs := ${fifo}.io.empty")
    case FIFOFull(fifo) => emitGlobalWire(src"val $lhs = Wire(Bool())"); emit(src"$lhs := ${fifo}.io.full")
    case FIFOAlmostEmpty(fifo) => emitGlobalWire(src"val $lhs = Wire(Bool())"); emit(src"$lhs := ${fifo}.io.almostEmpty")
    case FIFOAlmostFull(fifo) => emitGlobalWire(src"val $lhs = Wire(Bool())"); emit(src"$lhs := ${fifo}.io.almostFull")
    case FIFONumel(fifo) => emit(src"val $lhs = ${fifo}.io.numel")

    case _ => super.emitNode(lhs, rhs)
  }
}
