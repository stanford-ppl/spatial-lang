package spatial.codegen.chiselgen

import argon.codegen.chiselgen.ChiselCodegen
import spatial.api.ControllerExp
import spatial.SpatialConfig
import spatial.analysis.SpatialMetadataExp

trait ChiselGenController extends ChiselCodegen {
  val IR: ControllerExp with SpatialMetadataExp
  import IR._

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

    emit(s"""//  ---- Begin ${smStr} $sym Controller ----""")
    open("")

    /* State Machine Instatiation */
    // IO
    val numIter = if (cchain.isDefined) {
      val Def(CounterChainNew(counters)) = cchain.get
      counters.zipWithIndex.map {case (ctr,i) =>
        val Def(CounterNew(start, end, step, par)) = ctr
        emit(s"""val ${quote(sym)}_level${i}_iters = (${end} - ${start}) / (${step} * ${par}.U) + Mux(((${end} - ${start}) % (${step} * ${par}.U) === 0.U), 0.U, 1.U)""")
        s"${quote(sym)}_level${i}_iters"
      }
    } else { 
      List("1.U") // Unit pipe
    }

    val constrArg = smStr match {
      case "Innerpipe" => s"${numIter.length} /*probably don't need*/"
      // case "Parallel" => ""
      case _ => childrenOf(sym).length
    }

    emit(s"""val ${quote(sym)}_offset = 0 // TODO: Compute real delays""")
    emit(s"""val ${quote(sym)}_sm = Module(new ${smStr}(${constrArg}))""")
    emit(s"""${quote(sym)}_sm.io.input.enable := ${quote(sym)}_en;""")
    emit(s"""${quote(sym)}_done := Utils.delay(${quote(sym)}_sm.io.output.done, ${quote(sym)}_offset)""")
    emit(s"""val ${quote(sym)}_rst_en = ${quote(sym)}_sm.io.output.rst_en // Generally used in inner pipes""")

    smStr match {
      case s @ ("Metapipe" | "Seqpipe") =>
        emit(s"""  ${quote(sym)}_sm.io.input.numIter := (${numIter.mkString(" * ")})""")
        emit(s"""  ${quote(sym)}_sm.io.input.rst := ${quote(sym)}_resetter // generally set by parent""")
      case _ =>
    }

  //   if (styleOf(sym)!=ForkJoin) {
      if (cchain.isDefined) {
        // emitCChainCtrl(sym, cchain.get)
      } else {
        emit(s"""val ${quote(sym)}_datapath_en = ${quote(sym)}_en & ~${quote(sym)}_rst_en;""")
        emit(s"""val ${quote(sym)}_ctr_en = ${quote(sym)}_datapath_en """)
        // emit(s"""val ${quote(sym)}_ctr_en = ${quote(sym)}_sm.io.output.ctr_en // TODO: Why did we originally generate the 2 lines above??""")
      }
  //   }

    /* Control Signals to Children Controllers */
    if (smStr != "Innerpipe") {
      emit(s"""//      ---- Begin $smStr ${quote(sym)} Children Signals ----""")
      childrenOf(sym).zipWithIndex.foreach { case (c, idx) =>
        emitGlobal(s"""${c}_done = Wire(Bool())""")
        emitGlobal(s"""${c}_en = Wire(Bool())""")
        emitGlobal(s"""${c}_resetter = Wire(Bool())""")
        emit(s"""    ${quote(sym)}_sm.io.input.stageDone(${idx}) := ${c}_done;""")
        emit(s"""    ${c}_en := ${quote(sym)}_sm.io.output.stageEnable(${idx})""")
        emit(s"""    ${c}_resetter := ${quote(sym)}_sm.io.output.rst_en""")
      }
    }

  //   // emit(s"""// debug.simPrintf(${quote(sym)}_en, "pipe ${quote(sym)}: ${percentDSet.toList.mkString(",   ")}\\n", ${childrenSet.toList.mkString(",")});""")
    close("")
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case Hwblock(func) =>
      emitEn = true
      emit(s"""val ${quote(lhs)}_en = io.top_en & !io.top_done;""")
      emit(s"""val ${quote(lhs)}_resetter = false.B // TODO: top level reset""")
      emitGlobal(s"""${quote(lhs)}_done = Wire(Bool())""")
      emitController(lhs, None)
      emit(s"""val done_latch = Module(new SRFF())""")
      emit(s"""done_latch.io.input.set := ${quote(lhs)}_sm.io.output.done""")
      emit(s"""done_latch.io.input.reset := ${quote(lhs)}_resetter""")
      emit(s"""io.top_done := done_latch.io.output.data""")

      emitBlock(func)
      emitEn = false

    case UnitPipe(func) =>
      withSubStream("${quote(lhs)}UnitPipe") {
        emitBlock(func)
      }

    case OpForeach(cchain, func, iters) =>
      withSubStream("${quote(lhs)}OpForeach") {
        emitNestedLoop(cchain, iters){ emitBlock(func) }
      }

    case OpReduce(cchain, accum, map, load, reduce, store, rV, iters) =>
      emit("/** BEGIN REDUCE **/")
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
      emit("/** END REDUCE **/")

    case OpMemReduce(cchainMap,cchainRed,accum,map,loadRes,loadAcc,reduce,storeAcc,rV,itersMap,itersRed) =>
      emit("/** BEGIN MEM REDUCE **/")
      open(src"val $lhs = {")
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
