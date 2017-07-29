package spatial.codegen.chiselgen

import argon.codegen.FileDependencies
import argon.core._
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._
import spatial.SpatialConfig
import scala.math._

trait ChiselGenCounter extends ChiselGenSRAM with FileDependencies {
  var streamCtrCopy = List[Bound[_]]()

  // dependencies ::= AlwaysDep("chiselgen", "resources/Counter.chisel")



  def emitCounterChain(lhs: Exp[_], ctrs: Seq[Exp[Counter]], suffix: String = ""): Unit = {
    var isForever = false
    // Temporarily shove ctrl node onto stack so the following is quoted properly
    if (cchainPassMap.contains(lhs)) {controllerStack.push(cchainPassMap(lhs))}
    var maxw = 32 min ctrs.map(cchainWidth(_)).reduce{_*_}
    val counter_data = ctrs.map{ ctr => ctr match {
      case Def(CounterNew(start, end, step, par)) => 
        val w = cchainWidth(ctr)
        (start,end) match { 
          case (Exact(s), Exact(e)) => (src"${s}.FP(true, $w, 0)", src"${e}.FP(true, $w, 0)", src"$step", {src"$par"}.split('.').take(1)(0), src"$w")
          case _ => (src"$start", src"$end", src"$step", {src"$par"}.split('.').take(1)(0), src"$w")
        }
      case Def(Forever()) => 
        isForever = true
        ("0.S", "999.S", "1.S", "1", "32") 
    }}
    if (cchainPassMap.contains(lhs)) {controllerStack.pop()}

    emitGlobalWire(src"""val ${lhs}${suffix}_done = Wire(Bool())""")
    // emitGlobalWire(src"""val ${lhs}${suffix}_en = Wire(Bool())""")
    emitGlobalWire(src"""val ${lhs}${suffix}_resetter = Wire(Bool())""")
    emit(src"""val ${lhs}${suffix}_strides = List(${counter_data.map(_._3)}) // TODO: Safe to get rid of this and connect directly?""")
    emit(src"""val ${lhs}${suffix}_stops = List(${counter_data.map(_._2)}) // TODO: Safe to get rid of this and connect directly?""")
    emit(src"""val ${lhs}${suffix}_starts = List(${counter_data.map{_._1}}) """)
    emit(src"""val ${lhs}${suffix} = Module(new templates.Counter(List(${counter_data.map(_._4)}), List(${counter_data.map(_._5)}))) // Par of 0 creates forever counter""")
    val ctrl = usersOf(lhs).head._1
    if (suffix != "") {
      emit(src"// this trivial signal will be assigned multiple times but each should be the same")
      emit(src"""${ctrl}_ctr_trivial := ${controllerStack.tail.head}_ctr_trivial | ${lhs}${suffix}_stops.zip(${lhs}${suffix}_starts).map{case (stop,start) => (stop-start).asUInt.apply($maxw,0)}.reduce{_*_}.asUInt === 0.U""")
    } else {
      emit(src"""${ctrl}_ctr_trivial := ${controllerStack.head}_ctr_trivial | ${lhs}${suffix}_stops.zip(${lhs}${suffix}_starts).map{case (stop,start) => (stop-start).asUInt.apply($maxw,0)}.reduce{_*_}.asUInt === 0.U""")
    }
    emit(src"""${lhs}${suffix}.io.input.stops.zip(${lhs}${suffix}_stops).foreach { case (port,stop) => port := stop.r.asSInt }""")
    emit(src"""${lhs}${suffix}.io.input.strides.zip(${lhs}${suffix}_strides).foreach { case (port,stride) => port := stride.r.asSInt }""")
    emit(src"""${lhs}${suffix}.io.input.starts.zip(${lhs}${suffix}_starts).foreach { case (port,start) => port := start.r.asSInt }""")
    emit(src"""${lhs}${suffix}.io.input.gaps.foreach { gap => gap := 0.S }""")
    emit(src"""${lhs}${suffix}.io.input.saturate := false.B""")
    emit(src"""${lhs}${suffix}.io.input.enable := ${lhs}${suffix}_en""")
    emit(src"""${lhs}${suffix}_done := ${lhs}${suffix}.io.output.done""")
    emit(src"""${lhs}${suffix}.io.input.reset := ${lhs}${suffix}_resetter""")
    if (suffix != "") {
      emit(src"""${lhs}${suffix}.io.input.isStream := true.B""")
    } else {
      emit(src"""${lhs}${suffix}.io.input.isStream := false.B""")      
    }
    emit(src"""val ${lhs}${suffix}_maxed = ${lhs}${suffix}.io.output.saturated""")
    ctrs.zipWithIndex.foreach { case (c, i) =>
      val x = c match {
        case Def(CounterNew(_,_,_,p)) => 
          val Const(xx: BigDecimal) = p
          xx
        case Def(Forever()) => 1
      }
      emit(s"""val ${quote(c)}${suffix} = (0 until $x).map{ j => ${quote(lhs)}${suffix}.io.output.counts($i + j) }""")
    }

  }

  private def getCtrSuffix(head: Exp[_]): String = {
    if (parentOf(head).isDefined) {
      if (styleOf(parentOf(head).get) == StreamPipe) {src"_copy${head}"} else {getCtrSuffix(parentOf(head).get)}  
    } else {
      "NO_SUFFIX_ERROR"
    }
    
  }

  private def getValidSuffix(head: Exp[_], candidates: Seq[Exp[_]]): String = {
    if (candidates.contains(head)) {
      val id = candidates.toList.indexOf(head)
      if (id > 0) src"_chain_read_${id}" else ""
    } else {
      if (parentOf(head).isDefined) {
        getValidSuffix(parentOf(head).get, candidates)
      } else {
        "NO_SUFFIX_ERROR"
      }
    }
  }

  override def quote(s: Exp[_]): String = {
    s match {
      case lhs: Sym[_] => 
        val Def(rhs) = lhs
        rhs match {
          case CounterNew(_,e,st,p)=> 
            if (SpatialConfig.enableNaming) {s"x${lhs.id}_ctr"} else super.quote(s)
          case CounterChainNew(ctrs) =>
            if (SpatialConfig.enableNaming) {s"x${lhs.id}_ctrchain"} else super.quote(s)
          case _ =>
            super.quote(s)
        }
      case b: Bound[_] =>
          if (streamCtrCopy.contains(b)) {
            if (validPassMap.contains((s, getCtrSuffix(controllerStack.head)) )) {
              super.quote(s) + getCtrSuffix(controllerStack.head) +  getValidSuffix(controllerStack.head, validPassMap(s, getCtrSuffix(controllerStack.head)))
            } else {
              super.quote(s) + getCtrSuffix(controllerStack.head)  
            }
          } else {
            if (validPassMap.contains((s, "") )) {
              super.quote(s) + getValidSuffix(controllerStack.head, validPassMap(s, ""))
            } else {
              super.quote(s)
            }
          }
      case _ =>
        super.quote(s)
    }
  } 

  override protected def remap(tp: Type[_]): String = tp match {
    case CounterType      => src"Counter"
    case CounterChainType => src"Array[Counter]"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case CounterNew(start,end,step,par) => 
      emit(s"// $lhs = ($start to $end by $step par $par")
    case CounterChainNew(ctrs) => 
      val user = usersOf(lhs).head._1
      if (styleOf(user) != StreamPipe) emitCounterChain(lhs, ctrs)
    case Forever() => 
      emit("// $lhs = Forever")

    case _ => super.emitNode(lhs, rhs)
  }

}
