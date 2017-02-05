package spatial.codegen.chiselgen

import argon.codegen.chiselgen.ChiselCodegen
import spatial.api.{ControllerExp, CounterExp, UnrolledExp}
import spatial.SpatialConfig
import spatial.analysis.SpatialMetadataExp
import spatial.SpatialExp

trait ChiselGenController extends ChiselCodegen {
  val IR: SpatialExp
  import IR._


  /* Set of control nodes which already have their enable signal emitted */
  var enDeclaredSet = Set.empty[Exp[Any]]

  /* Set of control nodes which already have their done signal emitted */
  var doneDeclaredSet = Set.empty[Exp[Any]]


  private def emitNestedLoop(cchain: Exp[CounterChain], iters: Seq[Bound[Index]])(func: => Unit): Unit = {
    for (i <- iters.indices)
      open(src"$cchain($i).foreach{case (is,vs) => is.zip(vs).foreach{case (${iters(i)},v) => if (v) {")

    func

    iters.indices.foreach{_ => close("}}}") }
  }


  override def quote(s: Exp[_]): String = {
    if (SpatialConfig.enableNaming) {
      s match {
        case lhs: Sym[_] =>
          lhs match {
            case Def(Hwblock(_)) =>
              s"AccelController"
            case Def(UnitPipe(_)) =>
              s"x${lhs.id}_UnitPipe"
            case Def(e: OpForeach) =>
              s"x${lhs.id}_ForEach"
            case Def(e: OpReduce[_]) =>
              s"x${lhs.id}_Reduce"
            case Def(e: OpMemReduce[_,_]) =>
              s"x${lhs.id}_MemReduce"
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

  def emitController(sym:Sym[Any], cchain:Option[Exp[CounterChain]]) {

    val smStr = styleOf(sym) match {
      case MetaPipe => s"Metapipe"
      case StreamPipe => "Streampipe"
      case InnerPipe => "Innerpipe"
      case SeqPipe => s"Seqpipe"
      case ForkJoin => s"Parallel"
    }

    emit(src"""//  ---- Begin ${smStr} $sym Controller ----""")

    /* State Machine Instatiation */
    // IO
    val numIter = if (cchain.isDefined) {
      val Def(CounterChainNew(counters)) = cchain.get
      counters.zipWithIndex.map {case (ctr,i) =>
        val Def(CounterNew(start, end, step, par)) = ctr
        emit(src"""val ${sym}_level${i}_iters = (${end} - ${start}) / (${step} * ${par}) + Mux(((${end} - ${start}) % (${step} * ${par}) === 0.U), 0.U, 1.U)""")
        src"${sym}_level${i}_iters"
      }
    } else { 
      List("1.U") // Unit pipe
    }

    val constrArg = smStr match {
      case "Innerpipe" => s"${numIter.length} /*probably don't need*/"
      // case "Parallel" => ""
      case _ => childrenOf(sym).length
    }

    emit(src"""val ${sym}_offset = 0 // TODO: Compute real delays""")
    emitModule(src"${sym}_sm", s"${smStr}", s"${constrArg}")
    emit(src"""${sym}_sm.io.input.enable := ${sym}_en;""")
    emit(src"""${sym}_done := Utils.delay(${sym}_sm.io.output.done, ${sym}_offset)""")
    emit(src"""val ${sym}_rst_en = ${sym}_sm.io.output.rst_en // Generally used in inner pipes""")

    smStr match {
      case s @ ("Metapipe" | "Seqpipe") =>
        emit(src"""${sym}_sm.io.input.numIter := (${numIter.mkString(" * ")})""")
        emit(src"""${sym}_sm.io.input.rst := ${sym}_resetter // generally set by parent""")
      case _ =>
    }

    emit(src"""val ${sym}_datapath_en = ${sym}_en & ~${sym}_rst_en;""")
    if (cchain.isDefined) {
      emitGlobal(src"""val ${cchain.get}_ctr_en = Wire(Bool())""") 
      sym match { 
        case Def(n: UnrolledReduce[_,_]) => // Emit handles by emitNode
        case _ => emit(src"${cchain.get}_ctr_en := ${sym}_datapath_en")
      }
      emit(src"""// ---- Begin $smStr ${sym} Counter Signals ----""")
      val ctr = cchain.get
      emit(src"""${ctr}_en := ${sym}_en""")
      emit(src"""${ctr}_resetter := ${sym}_rst_en""")
      if (smStr == "Innerpipe") {
        emit(src"""${sym}_sm.io.input.ctr_done := Utils.delay(${ctr}.io.output.done, 1 + ${sym}_offset)""")
      }
    } else {
      emit(src"""// ---- Begin $smStr ${sym} Unit Counter ----""")
      if (smStr == "Innerpipe") {
        emit(src"""${sym}_sm.io.input.ctr_done := Utils.delay(${sym}_sm.io.output.ctr_en, 1 + ${sym}_offset)""")
      } else {
        emit(s"// How to emit for non-innerpipe unit counter?")
      }
    }

        
    /* Control Signals to Children Controllers */
    if (smStr != "Innerpipe") {
      emit(src"""// ---- Begin $smStr ${sym} Children Signals ----""")
      childrenOf(sym).zipWithIndex.foreach { case (c, idx) =>
        emitGlobal(src"""val ${c}_done = Wire(Bool())""")
        emitGlobal(src"""val ${c}_en = Wire(Bool())""")
        emitGlobal(src"""val ${c}_resetter = Wire(Bool())""")
        emit(src"""${sym}_sm.io.input.stageDone(${idx}) := ${c}_done;""")
        emit(src"""${c}_en := ${sym}_sm.io.output.stageEnable(${idx})""")
        emit(src"""${c}_resetter := ${sym}_sm.io.output.rst_en""")
      }
    }

  //   // emit(s"""// debug.simPrintf(${quote(sym)}_en, "pipe ${quote(sym)}: ${percentDSet.toList.mkString(",   ")}\\n", ${childrenSet.toList.mkString(",")});""")
  }


  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case Hwblock(func) =>
      toggleEn() // turn on
      emit(s"""val ${quote(lhs)}_en = io.top_en & !io.top_done;""")
      emit(s"""val ${quote(lhs)}_resetter = false.B // TODO: top level reset""")
      emitGlobal(s"""val ${quote(lhs)}_done = Wire(Bool())""")
      emitController(lhs, None)
      // Emit unit counter for this
      emit(s"""val done_latch = Module(new SRFF())""")
      emit(s"""done_latch.io.input.set := ${quote(lhs)}_sm.io.output.done""")
      emit(s"""done_latch.io.input.reset := ${quote(lhs)}_resetter""")
      emit(s"""io.top_done := done_latch.io.output.data""")

      emitBlock(func)
      toggleEn() // turn off

    case UnitPipe(func) =>
      emitController(lhs, None)
      withSubStream(src"${lhs}", styleOf(lhs) == InnerPipe) {
        emitBlock(func)
      }

    case OpForeach(cchain, func, iters) =>
      emitController(lhs, Some(cchain))
      withSubStream(src"${lhs}", styleOf(lhs) == InnerPipe) {
        emitNestedLoop(cchain, iters){ emitBlock(func) }
      }

    case OpReduce(cchain, accum, map, load, reduce, store, rV, iters) =>
      emitController(lhs, Some(cchain))
      open(src"val $lhs = {")
      emitNestedLoop(cchain, iters){
        visitBlock(map)
        visitBlock(load)
        emit(src"val ${rV._1} = ${load.result}")
        emit(src"val ${rV._2} = ${map.result}")
        visitBlock(reduce)
        emitBlock(store)
      }
      close("}")

    case OpMemReduce(cchainMap,cchainRed,accum,map,loadRes,loadAcc,reduce,storeAcc,rV,itersMap,itersRed) =>
      open(src"val $lhs = { mem op reduce what do i do aaaah")
      emitNestedLoop(cchainMap, itersMap){
        visitBlock(map)
        emitNestedLoop(cchainRed, itersRed){
          visitBlock(loadRes)
          visitBlock(loadAcc)
          emit(src"val ${rV._1} = ${loadRes.result}")
          emit(src"val ${rV._2} = ${loadAcc.result}")
          visitBlock(reduce)
          visitBlock(storeAcc)
        }
      }
      close("}")
      emit("/** END MEM REDUCE **/")

    case _ => super.emitNode(lhs, rhs)
  }
}
