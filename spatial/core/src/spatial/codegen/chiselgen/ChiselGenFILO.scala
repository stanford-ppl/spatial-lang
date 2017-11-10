package spatial.codegen.chiselgen

import argon.core._
import argon.nodes._
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._


trait ChiselGenFILO extends ChiselGenSRAM {

  override protected def spatialNeedsFPType(tp: Type[_]): Boolean = tp match { // FIXME: Why doesn't overriding needsFPType work here?!?!
    case FixPtType(s,d,f) => if (s) true else if (f == 0) false else true
    case IntType()  => false
    case LongType() => false
    case FloatType() => true
    case DoubleType() => true
    case _ => super.needsFPType(tp)
  }

  override protected def bitWidth(tp: Type[_]): Int = tp match {
    case Bits(bitEv) => bitEv.length
    // case x: StructType[_] => x.fields.head._2 match {
    //   case _: IssuedCmd => 96
    //   case _ => super.bitWidth(tp)
    // }
    case _ => super.bitWidth(tp)
  }

  override protected def name(s: Dyn[_]): String = s match {
    case Def(_: FILONew[_])                => s"""${s}_${s.name.getOrElse("filo").replace("$","")}"""
    case Def(FILOPush(fifo:Sym[_],_,_))    => s"${s}_pushTo${fifo.id}"
    case Def(FILOPop(fifo:Sym[_],_))       => s"${s}_popFrom${fifo.id}"
    case Def(FILOEmpty(fifo:Sym[_]))       => s"${s}_isEmpty${fifo.id}"
    case Def(FILOFull(fifo:Sym[_]))        => s"${s}_isFull${fifo.id}"
    case Def(FILOAlmostEmpty(fifo:Sym[_])) => s"${s}_isAlmostEmpty${fifo.id}"
    case Def(FILOAlmostFull(fifo:Sym[_]))  => s"${s}_isAlmostFull${fifo.id}"
    case Def(FILONumel(fifo:Sym[_]))       => s"${s}_numel${fifo.id}"
    case _ => super.name(s)
  } 

  override protected def remap(tp: Type[_]): String = tp match {
    case tp: FILOType[_] => src"chisel.collection.mutable.Queue[${tp.child}]"
    case _ => super.remap(tp)
  }

  // override protected def vecSize(tp: Type[_]): Int = tp.typeArguments.head match {
  //   case tp: Vector[_] => 1
  //   case _ => 1
  // }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op@FILONew(_) =>
      val size = constSizeOf(lhs)
      val width = bitWidth(op.mT)
      val rPars = readWidths(lhs)
      val wPars = writeWidths(lhs)

      if (wPars.distinct.length != 1 || rPars.distinct.length != 1) {
        error(lhs.ctx, u"FILO $lhs has differing port widths.")
        error(u"FILOs with differing port widths not yet supported in Chisel.")
        error(lhs.ctx)
      }
      val rPar = rPars.headOption.getOrElse(1)
      val wPar = wPars.headOption.getOrElse(1)

      emitGlobalModule(src"""val $lhs = Module(new FILO($rPar, $wPar, $size, ${writersOf(lhs).length}, ${readersOf(lhs).length}, $width)) // ${lhs.name.getOrElse("")}""")

    case FILOPush(filo,v,en) =>
      val writer = ctrlOf(lhs).node
      val enabler = src"${swap(writer, DatapathEn)}"
      emit(src"""$filo.connectPushPort(Vec(List($v.r)), ${swap(writer, En)} & ($enabler & ~${swap(writer, Inhibitor)} & ${swap(writer, IIDone)}).D(${symDelay(lhs)}) & $en)""")

    case FILOPop(filo,en) =>
      val reader = ctrlOf(lhs).node
      val bug202delay = reader match {
        case Def(op@SwitchCase(_)) => 
          if (Bits.unapply(op.mT).isDefined & listensTo(reader).isEmpty) src"${symDelay(parentOf(reader).get)}" else src"${symDelay(lhs)}"
        case _ => src"${symDelay(lhs)}" 
      }
      val enabler = src"${swap(reader, DatapathEn)} & ~${swap(reader, Inhibitor)} & ${swap(reader, IIDone)}"
      emit(src"val $lhs = Wire(${newWire(lhs.tp)})")
      emit(src"""$lhs.r := $filo.connectPopPort(${swap(reader, En)} & ($enabler).D(${bug202delay}) & $en & ~${swap(reader, Inhibitor)}).apply(0)""")

    case BankedFILOPush(filo,data,ens) =>
      //val par = ens.length
      val en = ens.map(quote).mkString("&")
      val writer = writersOf(filo).find{_.node == lhs}.get.ctrlNode
      val enabler = src"${swap(writer, DatapathEn)}"
      val datacsv = data.map{d => src"$d.r"}.mkString(",")
      emit(src"""$filo.connectPushPort(Vec(List($datacsv)), ($enabler & ~${swap(writer, Inhibitor)} & ${swap(writer, IIDone)}).D(${symDelay(lhs)}) & $en)""")

    case BankedFILOPop(filo,ens) =>
      //val par = ens.length
      val en = ens.map(quote).mkString("&")
      val reader = readersOf(filo).find{_.node == lhs}.get.ctrlNode
      emit(src"val $lhs = Wire(${newWire(lhs.tp)})")
      emit(src"""val ${lhs}_vec = ${quote(filo)}.connectPopPort((${swap(reader, DatapathEn)} & ~${swap(reader, Inhibitor)} & ${swap(reader, IIDone)}).D(${symDelay(lhs)}) & $en).reverse""")
      emit(src"""(0 until ${ens.length}).foreach{ i => $lhs(i).r := ${lhs}_vec(i) }""")

    case FILOPeek(filo) => emit(src"val $lhs = Wire(${newWire(lhs.tp)}); $lhs.r := $filo.io.out(0).r")
    case FILOEmpty(filo) => emit(src"val $lhs = $filo.io.empty")
    case FILOFull(filo) => emit(src"val $lhs = $filo.io.full")
    case FILOAlmostEmpty(filo) => emit(src"val $lhs = $filo.io.almostEmpty")
    case FILOAlmostFull(filo) => emit(src"val $lhs = $filo.io.almostFull")
    case FILONumel(filo) => emit(src"val $lhs = $filo.io.numel")

    case _ => super.emitNode(lhs, rhs)
  }
}
