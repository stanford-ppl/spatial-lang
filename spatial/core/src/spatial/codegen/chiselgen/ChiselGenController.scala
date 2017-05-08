package spatial.codegen.chiselgen

import argon.codegen.chiselgen.ChiselCodegen
import spatial.api.{ControllerExp, CounterExp, UnrolledExp}
import spatial.SpatialConfig
import spatial.analysis.SpatialMetadataExp
import spatial.SpatialExp

trait ChiselGenController extends ChiselGenCounter{
  val IR: SpatialExp
  import IR._


  /* Set of control nodes which already have their enable signal emitted */
  var enDeclaredSet = Set.empty[Exp[Any]]

  /* Set of control nodes which already have their done signal emitted */
  var doneDeclaredSet = Set.empty[Exp[Any]]

  /* For every iter we generate, we track the children it may be used in.
     Given that we are quoting one of these, look up if it has a map entry,
     and keep getting parents of the currentController until we find a match or 
     get to the very top
  */
  var itersMap = new scala.collection.mutable.HashMap[Bound[_], List[Exp[_]]]

  private def emitNestedLoop(cchain: Exp[CounterChain], iters: Seq[Bound[Index]])(func: => Unit): Unit = {
    for (i <- iters.indices)
      open(src"$cchain($i).foreach{case (is,vs) => is.zip(vs).foreach{case (${iters(i)},v) => if (v) {")

    func

    iters.indices.foreach{_ => close("}}}") }
  }

  def emitParallelizedLoop(iters: Seq[Seq[Bound[Index]]], cchain: Exp[CounterChain], suffix: String = "") = {
    val Def(CounterChainNew(counters)) = cchain

    iters.zipWithIndex.foreach{ case (is, i) =>
      if (is.size == 1) { // This level is not parallelized, so assign the iter as-is
        emit(src"${is(0)}${suffix}.raw := ${counters(i)}${suffix}(0)")
        emitGlobalWire(src"val ${is(0)}${suffix} = Wire(new FixedPoint(true,32,0))")
      } else { // This level IS parallelized, index into the counters correctly
        is.zipWithIndex.foreach{ case (iter, j) =>
          emit(src"${iter}${suffix}.raw := ${counters(i)}${suffix}($j)")
          emitGlobalWire(src"val ${iter}${suffix} = Wire(new FixedPoint(true,32,0))")
        }
      }
    }
  }

  def emitValids(cchain: Exp[CounterChain], iters: Seq[Seq[Bound[Index]]], valids: Seq[Seq[Bound[Bool]]], suffix: String = "") {
    valids.zip(iters).zipWithIndex.foreach{ case ((layer,count), i) =>
      layer.zip(count).foreach{ case (v, c) =>
        emit(src"val ${v}${suffix} = ${c}${suffix} < ${cchain}${suffix}_maxes(${i})")
      }
    }
  }
  def emitValidsDummy(iters: Seq[Seq[Bound[Index]]], valids: Seq[Seq[Bound[Bool]]], suffix: String = "") {
    valids.zip(iters).zipWithIndex.foreach{ case ((layer,count), i) =>
      layer.zip(count).foreach{ case (v, c) =>
        emit(src"val ${v}${suffix} = true.B")
      }
    }
  }

  protected def computeSuffix(s: Bound[_]): String = {
    var result = super.quote(s)
    if (itersMap.contains(s)) {
      val siblings = itersMap(s)
      var nextLevel: Option[Exp[_]] = Some(controllerStack.head)
      while (nextLevel.isDefined) {
        if (siblings.contains(nextLevel.get)) {
          if (siblings.indexOf(nextLevel.get) > 0) {result = result + s"_chain.read(${siblings.indexOf(nextLevel.get)})"}
          nextLevel = None
        } else {
          nextLevel = parentOf(nextLevel.get)
        }
      }
    } 
    result
  }

  protected def isStreamChild(lhs: Exp[_]): Boolean = {
    var nextLevel: Option[Exp[_]] = Some(lhs)
    var result = false
    while (nextLevel.isDefined) {
      if (styleOf(nextLevel.get) == StreamPipe) {
        result = true
        nextLevel = None
      } else {
        nextLevel = parentOf(nextLevel.get)
      }
    }
    result

  }

  protected def hasCopiedCounter(lhs: Exp[_]): Boolean = {
    if (parentOf(lhs).isDefined) {
      val parent = parentOf(lhs).get
      val hasCChain = parent match {
        case Def(UnitPipe(_,_)) => false
        case _ => true
      }
      if (styleOf(parent) == StreamPipe & hasCChain) {
        true
      } else {
        false
      }
    } else {
      false
    }
  }

  protected def isImmediateStreamChild(lhs: Exp[_]): Boolean = {
    var result = false
    if (parentOf(lhs).isDefined) {
      if (styleOf(parentOf(lhs).get) == StreamPipe) {
        result = true
      } else {
        result = false
      }
    } else {
      result = false
    }
    result

  }

  override def quote(s: Exp[_]): String = {
    s match {
      case b: Bound[_] => computeSuffix(b)
      case _ =>
        if (SpatialConfig.enableNaming) {
          s match {
            case lhs: Sym[_] =>
              lhs match {
                case Def(e: Hwblock) =>
                  s"RootController"
                case Def(e: UnitPipe) =>
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
          // Always need to remap root controller
          s match {
            case lhs: Sym[_] =>
              lhs match {
                case Def(e: Hwblock) => s"RootController"
                case _ => super.quote(s)
              }
            case _ => super.quote(s)
          }
        }
    }
  } 

  private def beneathForever(lhs: Sym[Any]):Boolean = { // TODO: Make a counterOf() method that will just grab me Some(counter) that I can check
    if (parentOf(lhs).isDefined) {
      val parent = parentOf(lhs).get
      parent match {
        case Def(Hwblock(_,isFrvr)) => true
        case Def(e: ParallelPipe) => false
        case Def(e: UnitPipe) => false
        case Def(e: OpForeach) => e.cchain match {
          case Def(Forever()) => true
          case _ => false
        }
        case Def(e: OpReduce[_]) => e.cchain match {
          case Def(Forever()) => true
          case _ => false
        }
        case Def(e: OpMemReduce[_,_]) => false
        case Def(e: UnrolledForeach) => e.cchain match {
          case Def(Forever()) => true
          case _ => false
        }
        case Def(e: UnrolledReduce[_,_]) => e.cchain match {
          case Def(Forever()) => true
          case _ => false
        }
        case _ => false 
      }
    } else {
      false
    }
  }

  def emitRegChains(controller: Sym[Any], inds:Seq[Bound[Index]]) = {
    val stages = childrenOf(controller)
    inds.foreach { idx =>
      emit(src"""val ${idx}_chain = Module(new NBufFF(${stages.size}, 32))""")
      stages.zipWithIndex.foreach{ case (s, i) =>
        emit(src"""${idx}_chain.connectStageCtrl(${s}_done, ${s}_en, List($i))""")
      }
      emit(src"""${idx}_chain.chain_pass(${idx}, ${controller}_sm.io.output.ctr_inc)""")
      itersMap += (idx -> stages.toList)
    }
  }

  def ctrIsForever(cchain: Exp[_]):Boolean = {
    var isForever = false
    cchain match {
      case Def(CounterChainNew(ctrs)) => 
        ctrs.foreach{c => c match {
          case Def(Forever()) => 
            isForever = true
          case _ => 
        }}
      }
    isForever

  }

  def getStreamEnablers(c: Exp[Any]): String = {
      // If we are inside a stream pipe, the following may be set
      val readiers = listensTo(c).distinct.map { fifo => 
        fifo match {
          case Def(FIFONew(size)) => src"~${fifo}.io.empty"
          case Def(FILONew(size)) => src"~${fifo}.io.empty"
          case Def(StreamInNew(bus)) => src"${fifo}_valid"
          case _ => src"${fifo}_en" // parent node
        }
      }.mkString(" & ")
      val holders = (pushesTo(c)).distinct.map { fifo => 
        fifo match {
          case Def(FIFONew(size)) => src"~${fifo}.io.full"
          case Def(FILONew(size)) => src"~${fifo}.io.full"
          case Def(StreamOutNew(bus)) => src"${fifo}_ready"
        }
      }.mkString(" & ")

      val hasHolders = if (holders != "") "&" else ""
      val hasReadiers = if (readiers != "") "&" else ""

      src"${hasHolders} ${holders} ${hasReadiers} ${readiers}"

  }

  def emitController(sym:Sym[Any], cchain:Option[Exp[CounterChain]], iters:Option[Seq[Bound[Index]]], isFSM: Boolean = false) {

    val hasStreamIns = if (listensTo(sym).length > 0) { // Please simplify this mess
      true
    } else { 
      false
    }

    val isInner = levelOf(sym) match {
      case InnerControl => true
      case OuterControl => false
      case _ => false
    }
    val smStr = if (isInner) {
      if (isStreamChild(sym) & hasStreamIns ) {
        "Streaminner"
      } else {
        "Innerpipe"
      }
    } else {
      styleOf(sym) match {
        case MetaPipe => s"Metapipe"
        case StreamPipe => "Streampipe"
        case InnerPipe => throw new OuterLevelInnerStyleException(src"$sym")
        case SeqPipe => s"Seqpipe"
        case ForkJoin => s"Parallel"
        case ForkSwitch => s"Match"
      }
    }

    emit(src"""//  ---- ${if (isInner) {"INNER: "} else {"OUTER: "}}Begin ${smStr} $sym Controller ----""")

    /* State Machine Instatiation */
    // IO
    var hasForever = false
    val numIter = if (cchain.isDefined) {
      val Def(CounterChainNew(counters)) = cchain.get
      counters.zipWithIndex.map {case (ctr,i) =>
        ctr match {
          case Def(CounterNew(start, end, step, par)) => 
            emit(src"""val ${sym}_level${i}_iters = (${end} - ${start}) / (${step} * ${par}) + Mux(((${end} - ${start}) % (${step} * ${par}) === 0.U), 0.U, 1.U)""")
            src"${sym}_level${i}_iters"
          case Def(Forever()) =>
            hasForever = true
            // need to change the outer pipe counter interface!!
            emit(src"""val ${sym}_level${i}_iters = 0.U // Count forever""")
            src"${sym}_level${i}_iters"
        }
      }
    } else { 
      List("1.U") // Unit pipe:
    }

    // Special match if this is HWblock, there is no forever cchain, just the forever flag
    sym match {
      case Def(Hwblock(_,isFrvr)) => if (isFrvr) hasForever = true
      case _ =>
    }

    val constrArg = if (isInner) {s"${isFSM}"} else {s"${childrenOf(sym).length}, ${isFSM}"}

    val lat = bodyLatency.sum(sym)
    emit(s"""val ${quote(sym)}_retime = ${lat} // Inner loop? ${isInner}""")
    emitModule(src"${sym}_sm", s"${smStr}", s"${constrArg}")
    emit(src"""${sym}_sm.io.input.enable := ${sym}_en;""")
    emit(src"""${sym}_done := ${sym}_sm.io.output.done""")
    emit(src"""val ${sym}_rst_en = ${sym}_sm.io.output.rst_en // Generally used in inner pipes""")
    emit(src"""${sym}_sm.io.input.numIter := (${numIter.mkString(" * ")}).raw // Unused for inner and parallel""")
    emit(src"""${sym}_sm.io.input.rst := ${sym}_resetter // generally set by parent""")
    emitGlobalWire(src"""val ${sym}_datapath_en = Wire(Bool())""")

    if (isStreamChild(sym) & hasStreamIns & beneathForever(sym)) {
      emit(src"""${sym}_datapath_en := ${sym}_en // Immediate parent has forever counter, so never mask out datapath_en""")    
    } else if ((isStreamChild(sym) & hasStreamIns) | isFSM) { // for FSM or hasStreamIns, tie en directly to datapath_en
      emit(src"""${sym}_datapath_en := ${sym}_en & ~${sym}_done""")  
    } else {
      emit(src"""${sym}_datapath_en := ${sym}_sm.io.output.ctr_inc""")
    }
    
    /* Counter Signals for controller (used for determining done) */
    if (smStr != "Parallel" & smStr != "Streampipe") {
      if (cchain.isDefined) {
        if (!ctrIsForever(cchain.get)) {
          emitGlobalWire(src"""val ${cchain.get}_en = Wire(Bool())""") 
          sym match { 
            case Def(n: UnrolledReduce[_,_]) => // Emit handles by emitNode
            case _ => // If parent is stream, use the fine-grain enable, otherwise use ctr_inc from sm
              if (isStreamChild(sym) & hasStreamIns) {
                emit(src"${cchain.get}_en := ${sym}_datapath_en // Stream kiddo, so only inc when _enq is ready (may be wrong)")
              } else {
                emit(src"${cchain.get}_en := ${sym}_sm.io.output.ctr_inc")
              } 
          }
          emit(src"""// ---- Counter Connections for $smStr ${sym} (${cchain.get}) ----""")
          val ctr = cchain.get
          if (isStreamChild(sym) & hasStreamIns) {
            emit(src"""${ctr}_resetter := ${sym}_done // Do not use rst_en for stream kiddo""")
          } else {
            emit(src"""${ctr}_resetter := ${sym}_rst_en""")
          }
          if (isInner) { 
            val dlay = if (SpatialConfig.enableRetiming || SpatialConfig.enablePIRSim) {src"1 + ${sym}_retime"} else "1"
            emit(src"""${sym}_sm.io.input.ctr_done := Utils.delay(${ctr}_done, $dlay)""")
          }

        }
      } else {
        emit(src"""// ---- Single Iteration for $smStr ${sym} ----""")
        if (isInner) { 
          if (isStreamChild(sym) & hasStreamIns) {
            emit(src"""${sym}_sm.io.input.ctr_done := Utils.delay(${sym}_en & ~${sym}_done, 1 + ${sym}_retime) // Disallow consecutive dones from stream inner""")
            emit(src"""val ${sym}_ctr_en = ${sym}_done // stream kiddo""")
          } else {
            emit(src"""${sym}_sm.io.input.ctr_done := Utils.delay(Utils.risingEdge(${sym}_sm.io.output.ctr_inc), 1 + ${sym}_retime)""")
            emit(src"""val ${sym}_ctr_en = ${sym}_sm.io.output.ctr_inc""")            
          }
        } else {
          emit(s"// TODO: How to properly emit for non-innerpipe unit counter?")
        }
      }
    }

    val hsi = if (isInner & hasStreamIns) "true.B" else "false.B"
    val hf = if (hasForever) "true.B" else "false.B"
    emit(src"${sym}_sm.io.input.hasStreamIns := $hsi")
    emit(src"${sym}_sm.io.input.forever := $hf")

    /* Control Signals to Children Controllers */
    if (!isInner) {
      emit(src"""// ---- Begin $smStr ${sym} Children Signals ----""")
      childrenOf(sym).zipWithIndex.foreach { case (c, idx) =>
        emitGlobalWire(src"""val ${c}_done = Wire(Bool())""")
        emitGlobalWire(src"""val ${c}_en = Wire(Bool())""")
        emitGlobalWire(src"""val ${c}_mask = Wire(Bool())""")
        emitGlobalWire(src"""val ${c}_resetter = Wire(Bool())""")
        if (smStr == "Streampipe" & cchain.isDefined) {
          emit(src"""${sym}_sm.io.input.stageDone(${idx}) := ${cchain.get}_copy${c}_done;""")
        } else {
          emit(src"""${sym}_sm.io.input.stageDone(${idx}) := ${c}_done;""")
        }

        emit(src"""${sym}_sm.io.input.stageMask(${idx}) := ${c}_mask;""")

        val streamAddition = getStreamEnablers(c)

        emit(src"""${c}_en := ${sym}_sm.io.output.stageEnable(${idx}) ${streamAddition}""")  

        // If this is a stream controller, need to set up counter copy for children
        if (smStr == "Streampipe" & cchain.isDefined) {
          emitGlobalWire(src"""val ${cchain.get}_copy${c}_en = Wire(Bool())""") 
          val Def(CounterChainNew(ctrs)) = cchain.get
          emitCounterChain(cchain.get, ctrs, src"_copy$c")
          emit(src"""${cchain.get}_copy${c}_en := ${c}_done""")
          emit(src"""${cchain.get}_copy${c}_resetter := ${sym}_sm.io.output.rst_en""")
        }
        emit(src"""${c}_resetter := ${sym}_sm.io.output.rst_en""")
      }
    }

    /* Emit reg chains */
    if (iters.isDefined) {
      if (smStr == "Metapipe" & childrenOf(sym).length > 1) {
        emitRegChains(sym, iters.get)
      }
    }

  }


  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case Hwblock(func,isForever) =>
      controllerStack.push(lhs)
      toggleEn() // turn on
      val streamAddition = getStreamEnablers(lhs)
      emit(s"""val ${quote(lhs)}_en = io.enable & !io.done ${streamAddition}""")
      emit(s"""val ${quote(lhs)}_resetter = reset""")
      emitGlobalWire(s"""val ${quote(lhs)}_done = Wire(Bool())""")
      emitController(lhs, None, None)
      topLayerTraits = childrenOf(lhs).map { c => src"$c" }
      if (levelOf(lhs) == InnerControl) emitInhibitor(lhs, None)
      // Emit unit counter for this
      emit(s"""val done_latch = Module(new SRFF())""")
      emit(s"""done_latch.io.input.set := ${quote(lhs)}_done""")
      emit(s"""done_latch.io.input.reset := ${quote(lhs)}_resetter""")
      emit(s"""done_latch.io.input.asyn_reset := ${quote(lhs)}_resetter""")
      emit(s"""io.done := done_latch.io.output.data""")
      // if (isForever) emit(s"""${quote(lhs)}_sm.io.input.forever := true.B""")

      emitBlock(func)
      toggleEn() // turn off
      controllerStack.pop()

    case UnitPipe(ens,func) =>
      val parent_kernel = controllerStack.head 
      controllerStack.push(lhs)
      emitController(lhs, None, None)
      if (levelOf(lhs) == InnerControl) emitInhibitor(lhs, None)
      withSubStream(src"${lhs}", src"${parent_kernel}", levelOf(lhs) == InnerControl) {
        emit(s"// Controller Stack: ${controllerStack.tail}")
        emitBlock(func)
      }
      val en = if (ens.isEmpty) "true.B" else ens.map(quote).mkString(" && ")
      emit(src"${lhs}_mask := $en")
      controllerStack.pop()

    case ParallelPipe(ens,func) =>
      val parent_kernel = controllerStack.head 
      controllerStack.push(lhs)
      emitController(lhs, None, None)
      withSubStream(src"${lhs}", src"${parent_kernel}", levelOf(lhs) == InnerControl) {
        emit(s"// Controller Stack: ${controllerStack.tail}")
        emitBlock(func)
      } 
      val en = if (ens.isEmpty) "true.B" else ens.map(quote).mkString(" && ")
      emit(src"${lhs}_mask := $en")
      controllerStack.pop()

    case OpForeach(cchain, func, iters) =>
      val parent_kernel = controllerStack.head 
      controllerStack.push(lhs)
      emitController(lhs, Some(cchain), Some(iters))
      withSubStream(src"${lhs}", src"${parent_kernel}", levelOf(lhs) == InnerControl) {
        emit(s"// Controller Stack: ${controllerStack.tail}")
        emitNestedLoop(cchain, iters){ emitBlock(func) }
      }
      controllerStack.pop()

    case OpReduce(cchain, accum, map, load, reduce, store, fold, zero, rV, iters) =>
      val parent_kernel = controllerStack.head 
      controllerStack.push(lhs)
      emitController(lhs, Some(cchain), Some(iters))
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
      controllerStack.pop()

    case OpMemReduce(cchainMap,cchainRed,accum,map,loadRes,loadAcc,reduce,storeAcc,fold,zero,rV,itersMap,itersRed) =>
      val parent_kernel = controllerStack.head 
      controllerStack.push(lhs)
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
      controllerStack.pop()

    case _ => super.emitNode(lhs, rhs)
  }
}
