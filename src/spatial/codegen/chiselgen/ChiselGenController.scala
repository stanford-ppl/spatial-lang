package spatial.codegen.chiselgen

import argon.codegen.chiselgen.ChiselCodegen
import spatial.api.{ControllerExp, CounterExp, UnrolledExp}
import spatial.SpatialConfig
import spatial.analysis.SpatialMetadataExp

trait ChiselGenController extends ChiselCodegen {
  val IR: ControllerExp with SpatialMetadataExp with CounterExp with UnrolledExp
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

    open("")
    emit(s"""//  ---- Begin ${smStr} $sym Controller ----""")

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
    emit(src"""val ${sym}_ctr_en = ${sym}_datapath_en // Not sure if this is right""") 
    if (cchain.isDefined) {
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
    close("")
  }


  def emitCChainCtrl(sym: Sym[Any], cchain: Exp[CounterChain]) {
    val Def(CounterChainNew(counters)) = cchain

    emit(s"""//      ---- Begin counter for ${quote(sym)} ----""")
    /* Reset CounterChain */
    //TODO: support reset of counterchain to sequential and metapipe in templete
    val maxes = counters.map {case ctr =>
      val Def(CounterNew(start, end, step, par)) = ctr
      styleOf(sym) match {
        // case InnerPipe =>
        //   emit(s"""${quote(sym)}_sm.connectInput("sm_maxIn_$i", ${quote(end)});""")
        //   // emit(s"""${quote(sym)}_sm.connectInput("sm_trashCnt", constant.var(dfeUInt(32), ${trashCount(bound(end).get.toInt, sym)}));""")
        //   emit(s"""DFEVar ${quote(sym)}_trash_en = ${quote(sym)}_sm.getOutput("trashEn");""")
        //   emit(s"""val ${quote(ctr)}_max_$i = ${quote(sym)}_sm.getOutput("ctr_maxOut_$i");""")
        case ForkJoin => 
          throw new Exception("Cannot have counter chain control logic for fork-join (parallel) controller!")
          "error"
        case _ =>
          quote(end)
      }
    }

    // emit(s"""val ${quote(sym)}_maxes = List(${maxes.map(quote).mkString(",")}) // TODO: Is this variable ever used??""")

    styleOf(sym) match {
      case InnerPipe =>
        emitGlobal(s"""val ${quote(cchain).replace("t.","")}_done = Wire(Bool())""")
        doneDeclaredSet += cchain
        emit(s"""${quote(sym)}_sm.io.input.ctr_done := ${quote(cchain)}_done;""")
        emit(s"""val ${quote(sym)}_datapath_en = ${quote(sym)}_sm.io.output.ctr_en;""")
      case ForkJoin => throw new Exception("Cannot have counter chain control logic for fork-join (parallel) controller!")
      case _ => emit(s"""val ${quote(sym)}_datapath_en = ${quote(sym)}_en;""")
    }


    /* Emit CounterChain */
    styleOf(sym) match {
      case InnerPipe =>
        val Def(d) = sym; d match {
          case n:OpForeach =>
            val writesToAccumRam = writtenIn(sym).exists {s => s match {
                case Def(SRAMNew(_)) => isAccum(sym)
                case _ => false
              }
            }

            if (writesToAccumRam) {
              val ctrEn = s"${quote(sym)}_datapath_en | ${quote(sym)}_rst_en"
              emit(s"""var ${quote(sym)}_ctr_en = $ctrEn; // TODO: Not migrated from maxj yet!!!""")
              val rstStr = s"${quote(sym)}_done"
              emitCustomCounterChain(cchain, Some(ctrEn), Some(rstStr), sym,
                    Some(s"stream.offset(${quote(sym)}_datapath_en & ${quote(cchain)}_chain.getCounterWrap(${quote(counters.head)}), -${quote(sym)}_offset-1)"))
            } else {
              val ctrEn = s"${quote(sym)}_datapath_en"
              emit(s"""val ${quote(sym)}_ctr_en = ${quote(sym)}_datapath_en""")
              val rstStr = s"${quote(sym)}_done"
              emitCustomCounterChain(cchain, Some(ctrEn), Some(rstStr), sym)
            }


          case n:UnrolledForeach =>
            val writesToAccumRam = writtenIn(sym).exists {s => s match {
                case Def(SRAMNew(_)) => isAccum(sym)
                case _ => false
              }
            }
            if (writesToAccumRam) {
              val ctrEn = s"${quote(sym)}_datapath_en | ${quote(sym)}_rst_en"
              emit(s"""var ${quote(sym)}_ctr_en = $ctrEn; // TODO: Not migrated from maxj yet!!!""")
              val rstStr = s"${quote(sym)}_done"
              emitCustomCounterChain(cchain, Some(ctrEn), Some(rstStr), sym,
                    Some(s"stream.offset(${quote(sym)}_datapath_en & ${quote(cchain)}_chain.getCounterWrap(${quote(counters.head)}), -${quote(sym)}_offset-1)"))
            } else {
              val ctrEn = s"${quote(sym)}_datapath_en"
              emit(s"""val ${quote(sym)}_ctr_en = ${quote(sym)}_datapath_en""")
              val rstStr = s"${quote(sym)}_done"
              emitCustomCounterChain(cchain, Some(ctrEn), Some(rstStr), sym)
            }

          case n@UnrolledReduce(cchain, _, _, _, inds, ens, _) =>
            emit(s"""val ${quote(sym)}_loopLengthVal = 1.U // TODO: fix this;""")
            emit(s"""val ${quote(sym)}_redLoopCtr = Module(new RedxnCtr());""")
            emit(s"""${quote(sym)}_redLoopCtr.io.input.enable := ${quote(sym)}_datapath_en""")
            emit(s"""${quote(sym)}_redLoopCtr.io.input.max := 5.U //TODO: Really calculate this""")
            // // emit(s"""DFEVar ${quote(sym)}_redLoopCtr = ${quote(sym)}_redLoopCtr.addCounter(${stream_offset_guess+1}, 1);""")
            // emit(s"""var ${quote(sym)}_redLoopCtr = ${quote(sym)}_redLoopCtr.addCounter(${quote(sym)}_loopLengthVal, 1);""")
            emit(s"""var ${quote(sym)}_redLoop_done = ${quote(sym)}_redLoopCtr.io.output.done;""")
            val ctrEn = s"${quote(sym)}_datapath_en & ${quote(sym)}_redLoop_done"
            emit(s"""var ${quote(sym)}_ctr_en = $ctrEn; // TODO: Not migrated from maxj yet!!!""")
            val rstStr = s"${quote(sym)}_done"
            emitCustomCounterChain(cchain, Some(ctrEn), Some(rstStr), sym)


          case n:OpReduce[_] =>
            //TODO : what is this? seems like all reduce supported are specialized
            //  def specializeReduce(r: ReduceTree) = {
            //  val lastGraph = r.graph.takeRight(1)(0)
            //  (lastGraph.nodes.size == 1) & (r.accum.input match {
            //    case in:Add => true
            //    case in:MinWithMetadata => true
            //    case in:MaxWithMetadata => true
            //    case in:Max => true
            //    case _ => false
            //  })
            val specializeReduce = true;
            if (specializeReduce) {
              val ctrEn = s"${quote(sym)}_datapath_en"
              emit(s"""var ${quote(sym)}_ctr_en = $ctrEn; // TODO: Not migrated from maxj yet!!!""")
              val rstStr = s"${quote(sym)}_done"
              emitCustomCounterChain(cchain, Some(ctrEn), Some(rstStr), sym)
            } else {
              emit(s"""var ${quote(sym)}_loopLengthVal = ${quote(sym)}_offset.getDFEVar(this, dfeUInt(9));""")
              emit(s"""CounterChain ${quote(sym)}_redLoopChain =
                control.count.makeCounterChain(${quote(sym)}_datapath_en);""")
              // emit(s"""DFEVar ${quote(sym)}_redLoopCtr = ${quote(sym)}_redLoopChain.addCounter(${stream_offset_guess+1}, 1);""")
              emit(s"""var ${quote(sym)}_redLoopCtr = ${quote(sym)}_redLoopChain.addCounter(${quote(sym)}_loopLengthVal, 1);""")
              emit(s"""var ${quote(sym)}_redLoop_done = stream.offset(${quote(sym)}_redLoopChain.getCounterWrap(${quote(sym)}_redLoopCtr), -1);""")
              val ctrEn = s"${quote(sym)}_datapath_en & ${quote(sym)}_redLoop_done"
              emit(s"""var ${quote(sym)}_ctr_en = $ctrEn; // TODO: Not migrated from maxj yet!!!""")
              val rstStr = s"${quote(sym)}_done"
              emitCustomCounterChain(cchain, Some(ctrEn), Some(rstStr), sym)
            }
        }
      case MetaPipe =>
        val ctrEn = s"${quote(childrenOf(sym).head)}_done"
        emit(s"""var ${quote(sym)}_ctr_en = $ctrEn;""")
        val rstStr = s"${quote(sym)}_done"
        emitCustomCounterChain(cchain, Some(ctrEn), Some(rstStr), sym)
      case SeqPipe =>
        val ctrEn = s"${quote(childrenOf(sym).last)}_done"
        emit(s"""var ${quote(sym)}_ctr_en = $ctrEn;""")
        val rstStr = s"${quote(sym)}_done"
        emitCustomCounterChain(cchain, Some(ctrEn), Some(rstStr), sym)
      case _ => 
        emit(s"// Not sure what to emit")
    }

  }

  def emitCustomCounterChain(cchain: Exp[CounterChain], en: Option[String], rstStr: Option[String], parent: Exp[Any], done: Option[String]=None) = {
    val sym = cchain
    if (!enDeclaredSet.contains(sym)) {
      emit(s"""val ${quote(sym)}_en = ${en.get};""")
      enDeclaredSet += sym
    }



    // For Pipes, max must be derived from PipeSM
    // For everyone else, max is as mentioned in the ctr
    val Def(CounterChainNew(counters)) = cchain

    // Connect maxes
    val maxes = counters.zipWithIndex.map { case (ctr, i) =>
      val Def(CounterNew(start, end, step, _)) = ctr
      quote(end)
    }

    emit(s"""val ${quote(sym)}_maxes = List(${maxes.map(m=>s"${m}").mkString(",")});""")

    // Connect strides
    val strides = counters.zipWithIndex.map { case (ctr, i) =>
      val Def(CounterNew(start, end, step, _)) = ctr
      step
      // val Def(d) = step
      // d match {
      //   case n@Const(value) => value
      //   case n@Tpes_Int_to_fix(v) => v match {
      //     case c@Const(value) => value
      //     case c@Param(value) => value
      //     case _ => throw new Exception(s"""Step is of unhandled node $n, $v""")
      //   }
      //   case _ => throw new Exception(s"""Step is of unhandled node $d""")
      // }
    }
    emit(s"""val ${quote(sym)}_strides = List(${strides.map(s=>s"${quote(s)}").mkString(",")})""")

    val gap = 0 // Power-of-2 upcasting not supported yet
    val pars = counters.map{ c => 1 // TODO: FIX THIS
      // c match {
      //   case n: Counter => n.par.getOrElse(1)
      // }
    } 
    val parsList = s"""List(${pars.mkString(",")})"""
    // emit(s"""OffsetExpr ${quote(sym)}_offset = stream.makeOffsetAutoLoop(\"${quote(sym)}_offset\");""")
    emit(s"""val ${quote(sym)} = Module(new Counter(${parsList}))""")
    emit(s"""${quote(sym)}.io.input.enable := ${quote(sym)}_en
${quote(sym)}.io.input.reset := ${rstStr.get}
val ${quote(sym)}_maxed = ${quote(sym)}.io.output.saturated""")

    if (!doneDeclaredSet.contains(sym)) {
      emit(s"""val ${quote(sym)}_done = ${quote(sym)}.io.output.done""")
      doneDeclaredSet += sym
    } else {
      emit(s"""${quote(sym)}_done := ${quote(sym)}.io.output.done""")
    }

    emit(s"""  ${quote(sym)}.io.input.maxes.zip(${quote(sym)}_maxes).foreach { case (port,max) => port := max }""")
    emit(s"""  ${quote(sym)}.io.input.strides.zip(${quote(sym)}_strides).foreach { case (port,stride) => port := stride.U }""")
    // emit(s"""  ${quote(sym)}.io.input.starts.zip(${quote(sym)}_starts).foreach { (port,start) => port := start }""")
    counters.zipWithIndex.map { case (ctr, i) =>
      val drop = pars.take(i+1).reduce{_+_} - pars(i)
      val take = pars(i)
      emit(s"""val ${quote(ctr)} = ${quote(sym)}.io.output.counts.drop(${drop}).take(${take})""")      
    }

    // Emit the valid bit calculations that don't exist in the IR
    parent match {
      case Def(UnrolledForeach(_,_,counts,vs)) =>
        vs.zip(counts).zipWithIndex.map { case ((layer,count), i) =>
          layer.zip(count).map { case (v, c) =>
            emit(s"val ${quote(v)} = ${quote(c)} < ${quote(sym)}_maxes(${i})")
          }
        }
      case Def(UnrolledReduce(_,_,_,_,counts,vs,_)) =>
        vs.zip(counts).zipWithIndex.map { case ((layer,count), i) =>
          layer.zip(count).map { case (v, c) =>
            emit(s"val ${quote(v)} = ${quote(c)} < ${quote(sym)}_maxes(${i})")
          }
        }
      case _ =>
    }

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
      withSubStream(src"${lhs}") {
        emitBlock(func)
      }

    case OpForeach(cchain, func, iters) =>
      emitController(lhs, Some(cchain))
      withSubStream(src"${lhs}") {
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
