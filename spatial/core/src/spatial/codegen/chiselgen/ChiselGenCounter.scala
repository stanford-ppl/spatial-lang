package spatial.codegen.chiselgen

import argon.codegen.FileDependencies
import argon.codegen.chiselgen.ChiselCodegen
import spatial.api.CounterExp
import spatial.SpatialConfig
import spatial.SpatialExp


trait ChiselGenCounter extends ChiselGenSRAM with FileDependencies {
  val IR: SpatialExp
  import IR._

  var streamCtrCopy = List[Bound[_]]()

  // dependencies ::= AlwaysDep("chiselgen", "resources/Counter.chisel")

  def emitCounterChain(lhs: Exp[_], ctrs: Seq[Exp[Counter]], suffix: String = ""): Unit = {
    var isForever = false
    val counter_data = ctrs.map{ c => c match {
      case Def(CounterNew(start, end, step, par)) => (src"$start", src"$end", src"$step", {s"$par"}.split('.').take(1)(0))
      case Def(Forever()) => 
        isForever = true
        ("0.U", "999.U", "1.U", "1") 
    }}

    emitGlobalWire(src"""val ${lhs}${suffix}_done = Wire(Bool())""")
    // emitGlobalWire(src"""val ${lhs}${suffix}_en = Wire(Bool())""")
    emitGlobalWire(src"""val ${lhs}${suffix}_resetter = Wire(Bool())""")
    emit(src"""val ${lhs}${suffix}_strides = List(${counter_data.map(_._3).mkString(",")}) // TODO: Safe to get rid of this and connect directly?""")
    emit(src"""val ${lhs}${suffix}_maxes = List(${counter_data.map(_._2).mkString(",")}) // TODO: Safe to get rid of this and connect directly?""")
    emit(src"""val ${lhs}${suffix}_starts = List(${counter_data.map{_._1}.mkString(",")}) """)
    emit(src"""val ${lhs}${suffix} = Module(new templates.Counter(List(${counter_data.map(_._4).mkString(",")}))) // Par of 0 creates forever counter""")
    val ctrl = usersOf(lhs).head._1
    if (suffix != "") {
      emit(src"// this trivial signal will be assigned multiple times but each should be the same")
      emit(src"""${ctrl}_ctr_trivial := ${controllerStack.tail.head}_ctr_trivial | ${lhs}${suffix}_maxes.zip(${lhs}${suffix}_starts).map{case (max,start) => max-start}.reduce{_*_} === 0.U""")
    } else {
      emit(src"""${ctrl}_ctr_trivial := ${controllerStack.head}_ctr_trivial | ${lhs}${suffix}_maxes.zip(${lhs}${suffix}_starts).map{case (max,start) => max-start}.reduce{_*_} === 0.U""")
    }
    emit(src"""${lhs}${suffix}.io.input.maxes.zip(${lhs}${suffix}_maxes).foreach { case (port,max) => port := max.number }""")
    emit(src"""${lhs}${suffix}.io.input.strides.zip(${lhs}${suffix}_strides).foreach { case (port,stride) => port := stride.number }""")
    emit(src"""${lhs}${suffix}.io.input.starts.zip(${lhs}${suffix}_starts).foreach { case (port,start) => port := start.number }""")
    emit(src"""${lhs}${suffix}.io.input.gaps.foreach { gap => gap := 0.U }""")
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

  override def quote(s: Exp[_]): String = {
    if (SpatialConfig.enableNaming) {
      s match {
        case lhs: Sym[_] =>
          val Op(rhs) = lhs
          rhs match {
            case CounterNew(s,e,st,p)=> 
              s"x${lhs.id}_ctr"
            case CounterChainNew(ctrs) =>
              s"x${lhs.id}_ctrchain"
            case _ =>
              super.quote(s)
          }
        case b: Bound[_] =>
          if (streamCtrCopy.contains(b)) { 
            super.quote(s) + getCtrSuffix(controllerStack.head)
          } else {
            super.quote(s)
          }
        case _ =>
          super.quote(s)
      }
    } else {
      s match {
        case b: Bound[_] =>
          if (streamCtrCopy.contains(b)) { 
            super.quote(s) + getCtrSuffix(controllerStack.head)
          } else {
            super.quote(s)
          }
        case _ =>
          super.quote(s)
      }
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

      if (controllerStack.length > 0) {
        val ctrl = usersOf(lhs).head._1
        emit(src"val ${lhs}_ctr_trivial = ${controllerStack.head}_ctr_trivial | false.B")
      }    

    case _ => super.emitNode(lhs, rhs)
  }

}
