package spatial.codegen.chiselgen

import argon.core._
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._
import spatial.targets.DE1._
import spatial.SpatialConfig


trait ChiselGenController extends ChiselGenCounter{
  /* Set of control nodes which already have their enable signal emitted */
  var enDeclaredSet = Set.empty[Exp[Any]]

  /* Set of control nodes which already have their done signal emitted */
  var doneDeclaredSet = Set.empty[Exp[Any]]

  /* For every iter we generate, we track the children it may be used in.
     Given that we are quoting one of these, look up if it has a map entry,
     and keep getting parents of the currentController until we find a match or 
     get to the very top
  */

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
        emit(src"${is(0)}${suffix}.raw := ${counters(i)}${suffix}(0).r")
        val w = cchainWidth(counters(i))
        emitGlobalWire(src"val ${is(0)}${suffix} = Wire(new FixedPoint(true,$w,0))")
      } else { // This level IS parallelized, index into the counters correctly
        is.zipWithIndex.foreach{ case (iter, j) =>
          emit(src"${iter}${suffix}.raw := ${counters(i)}${suffix}($j).r")
          val w = cchainWidth(counters(i))
          emitGlobalWire(src"val ${iter}${suffix} = Wire(new FixedPoint(true,$w,0))")
        }
      }
    }
  }

  def emitValids(lhs: Exp[Any], cchain: Exp[CounterChain], iters: Seq[Seq[Bound[Index]]], valids: Seq[Seq[Bound[Bit]]], suffix: String = "") {
    valids.zip(iters).zipWithIndex.foreach{ case ((layer,count), i) =>
      layer.zip(count).foreach{ case (v, c) =>
        emit(src"val ${v}${suffix} = Mux(${cchain}${suffix}_strides($i) >= 0.S, ${c}${suffix} < ${cchain}${suffix}_stops(${i}), ${c}${suffix} > ${cchain}${suffix}_stops(${i})) // TODO: Generate these inside counter")
        if (styleOf(lhs) == MetaPipe & childrenOf(lhs).length > 1) {
          emitGlobalModule(src"""val ${v}${suffix}_chain = Module(new NBufFF(${childrenOf(lhs).size}, 1))""")
          childrenOf(lhs).indices.drop(1).foreach{i => emitGlobalModule(src"""val ${v}${suffix}_chain_read_$i = ${v}${suffix}_chain.read(${i}) === 1.U(1.W)""")}
          withStream(getStream("BufferControlCxns")) {
            childrenOf(lhs).zipWithIndex.foreach{ case (s, i) =>
              emit(src"""${v}${suffix}_chain.connectStageCtrl(${s}_done, ${s}_en, List($i))""")
            }
          }
          emit(src"""${v}${suffix}_chain.chain_pass(${v}${suffix}, ${lhs}_sm.io.output.ctr_inc)""")
          validPassMap += ((v, suffix) -> childrenOf(lhs))
        }
      }
    }
    // Console.println(s"map is $validPassMap")
  }
  def emitValidsDummy(iters: Seq[Seq[Bound[Index]]], valids: Seq[Seq[Bound[Bit]]], suffix: String = "") {
    valids.zip(iters).zipWithIndex.foreach{ case ((layer,count), i) =>
      layer.zip(count).foreach{ case (v, c) =>
        emit(src"val ${v}${suffix} = true.B")
      }
    }
  }

  def emitRegChains(controller: Sym[Any], inds:Seq[Bound[Index]], cchain:Exp[CounterChain]) = {
    val stages = childrenOf(controller)
    val Def(CounterChainNew(counters)) = cchain
    var maxw = 32 min counters.map(cchainWidth(_)).reduce{_*_}
    val par = counters.map{case Def(CounterNew(_,_,_,Exact(p))) => p}
    val ctrMapping = par.indices.map{i => par.dropRight(par.length - i).sum}
    inds.zipWithIndex.foreach { case (idx,index) =>
      val this_counter = ctrMapping.filter(_ <= index).length - 1
      val this_width = cchainWidth(counters(this_counter))
      emitGlobalModule(src"""val ${idx}_chain = Module(new NBufFF(${stages.size}, ${this_width}))""")
      stages.indices.foreach{i => emitGlobalModule(src"""val ${idx}_chain_read_$i = ${idx}_chain.read(${i})""")}
      withStream(getStream("BufferControlCxns")) {
        stages.zipWithIndex.foreach{ case (s, i) =>
          emit(src"""${idx}_chain.connectStageCtrl(${s}_done, ${s}_en, List($i))""")
        }
      }
      emit(src"""${idx}_chain.chain_pass(${idx}, ${controller}_sm.io.output.ctr_inc)""")
      // Associate bound sym with both ctrl node and that ctrl node's cchain
      stages.foreach{
        case stage @ Def(s:UnrolledForeach) => cchainPassMap += (s.cchain -> stage)
        case stage @ Def(s:UnrolledReduce[_,_]) => cchainPassMap += (s.cchain -> stage)
        case _ =>
      }
      itersMap += (idx -> stages.toList)
    }
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

  protected def findCtrlAncestor(lhs: Exp[_]): Option[Exp[_]] = {
    var nextLevel: Option[Exp[_]] = Some(lhs)
    var keep_looking = true
    while (keep_looking & nextLevel.isDefined) {
      nextLevel.get match {
        case Def(_:UnrolledReduce[_,_]) => nextLevel = None; keep_looking = false
        case Def(_:UnrolledForeach) => nextLevel = None; keep_looking = false
        case Def(_:Hwblock) => nextLevel = None; keep_looking = false
        case Def(_:UnitPipe) => nextLevel = None; keep_looking = false
        case Def(_:ParallelPipe) => nextLevel = None; keep_looking = false 
        case Def(_:StateMachine[_]) => keep_looking = false 
        case _ => nextLevel = parentOf(nextLevel.get)
      }
    }
    nextLevel
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

  protected def emitStandardSignals(lhs: Exp[_]): Unit = {
    emitGlobalWire(src"""val ${lhs}_done = Wire(Bool())""")
    emitGlobalWire(src"""val ${lhs}_en = Wire(Bool())""")
    emitGlobalWire(src"""val ${lhs}_base_en = Wire(Bool())""")
    emitGlobalWire(src"""val ${lhs}_mask = Wire(Bool())""")
    emitGlobalWire(src"""val ${lhs}_resetter = Wire(Bool())""")
    emitGlobalWire(src"""val ${lhs}_datapath_en = Wire(Bool())""")
    emitGlobalWire(src"""val ${lhs}_ctr_trivial = Wire(Bool())""")
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
            case Def(e: Switch[_]) =>
              s"x${lhs.id}_switch"
            case Def(e: SwitchCase[_]) =>
              s"x${lhs.id}_switchcase"
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
      // Add 1 to latency of fifo checks because SM takes one cycle to get into the done state
      val lat = bodyLatency.sum(c)
      val readiers = listensTo(c).distinct.map {
        case fifo @ Def(FIFONew(size)) => src"~$fifo.io.empty.D(${lat} + 1, rr)"
        case fifo @ Def(FILONew(size)) => src"~$fifo.io.empty.D(${lat} + 1, rr)"
        case fifo @ Def(StreamInNew(bus)) => bus match {
          case SliderSwitch => ""
          case _ => src"${fifo}_valid"
        }
        case fifo => src"${fifo}_en" // parent node
      }.filter(_ != "").mkString(" & ")
      val holders = pushesTo(c).distinct.map {
        case fifo @ Def(FIFONew(size)) => src"~$fifo.io.full.D(${lat} + 1, rr)"
        case fifo @ Def(FILONew(size)) => src"~$fifo.io.full.D(${lat} + 1, rr)"
        case fifo @ Def(StreamOutNew(bus)) => src"${fifo}_ready"
        case fifo @ Def(BufferedOutNew(_, bus)) => src"" //src"~${fifo}_waitrequest"
      }.filter(_ != "").mkString(" & ")

      val hasHolders = if (holders != "") "&" else ""
      val hasReadiers = if (readiers != "") "&" else ""

      src"${hasHolders} ${holders} ${hasReadiers} ${readiers}"

  }

  def emitController(sym:Sym[Any], cchain:Option[Exp[CounterChain]], iters:Option[Seq[Bound[Index]]], isFSM: Boolean = false) {

    val hasStreamIns = listensTo(sym).distinct.exists{
      case Def(StreamInNew(SliderSwitch)) => false
      case Def(StreamInNew(_))            => true
      case _ => false
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
        case InnerPipe => throw new spatial.OuterLevelInnerStyleException(src"$sym")
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
      var maxw = 32 min counters.map(cchainWidth(_)).reduce{_*_}
      counters.zipWithIndex.map {case (ctr,i) =>
        ctr match {
          case Def(CounterNew(start, end, step, par)) => 
            val w = cchainWidth(ctr)
            (start, end, step, par) match {
              /*
                  (e - s) / (st * p) + Mux( (e - s) % (st * p) === 0, 0, 1)
                     1          1              1          1    
                          1                         1
                          .                                     1
                          .            1
                                     1
                  Issue # 199           
              */
              case (Exact(s), Exact(e), Exact(st), Exact(p)) => 
                emit(src"val ${sym}${i}_range = ShiftRegister(${e}.S(${32 min 2*w}.W) - ${s}.S(${32 min 2*w}.W), 1)")
                emit(src"val ${sym}${i}_jump = ShiftRegister(${st}.S(${32 min 2*w}.W) *-* ${p}.S(${32 min 2*w}.W), 1)")
                emit(src"val ${sym}${i}_hops = (${sym}${i}_range /-/ ${sym}${i}_jump).asUInt")
                emit(src"val ${sym}${i}_leftover = ShiftRegister(${sym}${i}_range %-% ${sym}${i}_jump, 1)")
                emit(src"val ${sym}${i}_evenfit = ShiftRegister(${sym}${i}_leftover.asUInt === 0.U, 1)")
                emit(src"val ${sym}${i}_adjustment = ShiftRegister(Mux(${sym}${i}_evenfit, 0.U, 1.U), 1)")
              case (Exact(s), Exact(e), _, Exact(p)) => 
                emit("// TODO: Figure out how to make this one cheaper!")
                emit(src"val ${sym}${i}_range = ShiftRegister(${e}.S(${32 min 2*w}.W) - ${s}.S(${32 min 2*w}.W), 1)")
                emit(src"val ${sym}${i}_jump = ShiftRegister(${step} *-* ${p}.S(${w}.W), 1)")
                emit(src"val ${sym}${i}_hops = (${sym}${i}_range /-/ ${sym}${i}_jump).asUInt")
                emit(src"val ${sym}${i}_leftover = ShiftRegister(${sym}${i}_range %-% ${sym}${i}_jump, 1)")
                emit(src"val ${sym}${i}_evenfit = ShiftRegister(${sym}${i}_leftover.asUInt === 0.U, 1)")
                emit(src"val ${sym}${i}_adjustment = ShiftRegister(Mux(${sym}${i}_evenfit, 0.U, 1.U), 1)")
              case _ => 
                emit(src"val ${sym}${i}_range = ShiftRegister(${end} - ${start}, 1)")
                emit(src"val ${sym}${i}_jump = ShiftRegister(${step} *-* ${par}, 1)")
                emit(src"val ${sym}${i}_hops = ${sym}${i}_range /-/ ${sym}${i}_jump")
                emit(src"val ${sym}${i}_leftover = ShiftRegister(${sym}${i}_range %-% ${sym}${i}_jump, 1)")
                emit(src"val ${sym}${i}_evenfit = ShiftRegister(${sym}${i}_leftover === 0.U, 1)")
                emit(src"val ${sym}${i}_adjustment = ShiftRegister(Mux(${sym}${i}_evenfit, 0.U, 1.U), 1)")
            }
            emit(src"""val ${sym}_level${i}_iters = ShiftRegister(${sym}${i}_hops + ${sym}${i}_adjustment, 1)""")
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
      case _ =>;
    }

    val constrArg = if (isInner) {s"${isFSM}"} else {s"${childrenOf(sym).length}, isFSM = ${isFSM}"}

    val lat = bodyLatency.sum(sym)
    emitStandardSignals(sym)

    // Pass done signal upward and grab your own en signals if this is a switchcase child
    if (parentOf(sym).isDefined) {
      parentOf(sym).get match {
        case Def(SwitchCase(_)) => 
          emit(src"""${parentOf(sym).get}_done := ${sym}_done""")
          val streamAddition = getStreamEnablers(sym)
          emit(src"""${sym}_en := ${parentOf(sym).get}_en ${streamAddition}""")  
          emit(src"""${sym}_resetter := ${parentOf(sym).get}_resetter""")

        case _ =>
          // no sniffing to be done
      }
    }

    val stw = sym match{case Def(StateMachine(_,_,_,_,_,s)) => bitWidth(s.tp); case _ => 32}
    val ctrdepth = if (cchain.isDefined) {cchain.get match {case Def(CounterChainNew(ctrs)) => ctrs.length; case _ => 0}} else 0
    emit(s"""val ${quote(sym)}_retime = ${lat} // Inner loop? ${isInner}, II = ${iiOf(sym)}""")
    emit(src"val ${sym}_sm = Module(new ${smStr}(${constrArg.mkString}, ctrDepth = $ctrdepth, stateWidth = ${stw}, retime = ${sym}_retime))")
    emit(src"""${sym}_sm.io.input.enable := ${sym}_en;""")
    if (isFSM) {
      emit(src"""${sym}_done := (${sym}_sm.io.output.done & ~${sym}_inhibitor.D(2,rr)).D(${sym}_retime,rr)""")      
    } else {
      emit(src"""${sym}_done := ${sym}_sm.io.output.done.D(${sym}_retime,rr)""")
    }
    emit(src"""val ${sym}_rst_en = ${sym}_sm.io.output.rst_en // Generally used in inner pipes""")
    emit(src"""${sym}_sm.io.input.numIter := (${numIter.mkString(" *-* ")}).raw.asUInt // Unused for inner and parallel""")
    emit(src"""${sym}_sm.io.input.rst := ${sym}_resetter // generally set by parent""")

    if (isStreamChild(sym) & hasStreamIns & beneathForever(sym)) {
      emit(src"""${sym}_datapath_en := ${sym}_en & ~${sym}_ctr_trivial // Immediate parent has forever counter, so never mask out datapath_en""")    
    } else if ((isStreamChild(sym) & hasStreamIns & cchain.isDefined)) { // for FSM or hasStreamIns, tie en directly to datapath_en
      emit(src"""${sym}_datapath_en := ${sym}_en & ~${sym}_done & ~${sym}_ctr_trivial ${getNowValidLogic(sym)}""")  
    } else if (isFSM) { // for FSM or hasStreamIns, tie en directly to datapath_en
      emit(src"""${sym}_datapath_en := ${sym}_en & ~${sym}_done & ~${sym}_ctr_trivial & ~${sym}_sm.io.output.done ${getNowValidLogic(sym)}""")  
    } else if ((isStreamChild(sym) & hasStreamIns)) { // _done used to be commented out but I'm not sure why
      emit(src"""${sym}_datapath_en := ${sym}_en & ~${sym}_done & ~${sym}_ctr_trivial ${getNowValidLogic(sym)} """)  
    } else {
      emit(src"""${sym}_datapath_en := ${sym}_sm.io.output.ctr_inc & ~${sym}_done & ~${sym}_ctr_trivial""")
    }
    
    /* Counter Signals for controller (used for determining done) */
    if (smStr != "Parallel" & smStr != "Streampipe") {
      if (cchain.isDefined) {
        if (!ctrIsForever(cchain.get)) {
          emitGlobalWire(src"""val ${cchain.get}_en = Wire(Bool())""") 
          sym match { 
            case Def(n: UnrolledReduce[_,_]) => // These have II
              emit(src"""${cchain.get}_en := ${sym}_sm.io.output.ctr_inc & ${sym}_II_done""")
            case Def(n: UnrolledForeach) => 
              if (isStreamChild(sym) & hasStreamIns) {
                emit(src"${cchain.get}_en := ${sym}_datapath_en & ${sym}_II_done & ~${sym}_inhibitor ${getNowValidLogic(sym)}") 
              } else {
                emit(src"${cchain.get}_en := ${sym}_sm.io.output.ctr_inc & ${sym}_II_done// Should probably also add inhibitor")
              }             
            case _ => // If parent is stream, use the fine-grain enable, otherwise use ctr_inc from sm
              if (isStreamChild(sym) & hasStreamIns) {
                emit(src"${cchain.get}_en := ${sym}_datapath_en & ~${sym}_inhibitor ${getNowValidLogic(sym)}") 
              } else {
                emit(src"${cchain.get}_en := ${sym}_sm.io.output.ctr_inc // Should probably also add inhibitor")
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
            // val dlay = if (SpatialConfig.enableRetiming || SpatialConfig.enablePIRSim) {src"1 + ${sym}_retime"} else "1"
            emit(src"""${sym}_sm.io.input.ctr_done := Utils.delay(${ctr}_done, 1)""")
          }

        }
      } else {
        emit(src"""// ---- Single Iteration for $smStr ${sym} ----""")
        if (isInner) { 
          if (isStreamChild(sym) & hasStreamIns) {
            emit(src"""${sym}_sm.io.input.ctr_done := Utils.delay(${sym}_en & ~${sym}_done, 1) // Disallow consecutive dones from stream inner""")
            emit(src"""val ${sym}_ctr_en = ${sym}_done // stream kiddo""")
          } else {
            emit(src"""${sym}_sm.io.input.ctr_done := Utils.delay(Utils.risingEdge(${sym}_sm.io.output.ctr_inc), 1)""")
            emit(src"""val ${sym}_ctr_en = ${sym}_sm.io.output.ctr_inc""")            
          }
        } else {
          emit(s"// TODO: How to properly emit for non-innerpipe unit counter?  Probably doesn't matter")
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
        if (smStr == "Streampipe" & cchain.isDefined) {
          emit(src"""${sym}_sm.io.input.stageDone(${idx}) := ${cchain.get}_copy${c}_done;""")
        } else {
          emit(src"""${sym}_sm.io.input.stageDone(${idx}) := ${c}_done;""")
        }

        emit(src"""${sym}_sm.io.input.stageMask(${idx}) := ${c}_mask;""")

        val streamAddition = getStreamEnablers(c)

        emit(src"""${c}_base_en := ${sym}_sm.io.output.stageEnable(${idx})""")  
        emit(src"""${c}_en := ${c}_base_en ${streamAddition}""")  

        // If this is a stream controller, need to set up counter copy for children
        if (smStr == "Streampipe" & cchain.isDefined) {
          emitGlobalWire(src"""val ${cchain.get}_copy${c}_en = Wire(Bool())""") 
          val Def(CounterChainNew(ctrs)) = cchain.get
          // val stream_respeck = c match {case Def(UnitPipe(_,_)) => getNowValidLogic(c); case _ => ""}
          
          val unitKid = c match {case Def(UnitPipe(_,_)) => true; case _ => false}
          val snooping = getNowValidLogic(c).replace(" ", "") != ""
          val innerKid = levelOf(c) == InnerControl
          val signalHandle = if (unitKid & innerKid & snooping) { // If this is a unit pipe that listens, we just need to snoop the now_valid & _ready overlap
            src"true.B ${getReadyLogic(c)} ${getNowValidLogic(c)}"
          } else if (innerKid) { // Otherwise, use the done & ~inhibit
            src"${c}_done /* & ~${c}_inhibitor */"
          } else {
            src"${c}_done"
          }
          // val respecks_stream = if (unitKid) getNowValidLogic(c).replace(" ", "") != "" else false
          // val signalHandle = if (unitKid) src"${getReadyLogic(c)}" else src"${c}_done ${getNowValidLogic(c)}"
          emitCounterChain(cchain.get, ctrs, src"_copy$c")
          // val inhibit_respeck = if (levelOf(c) == InnerControl & respecks_stream) src"& ~${c}_inhibitor /*amazingly ugly hack for tensor5d*/" else ""
          emit(src"""${cchain.get}_copy${c}_en := ${signalHandle}""")
          emit(src"""${cchain.get}_copy${c}_resetter := ${sym}_sm.io.output.rst_en""")
        }
        emit(src"""${c}_resetter := ${sym}_sm.io.output.rst_en""")
      }
    }

    /* Emit reg chains */
    if (iters.isDefined) {
      if (smStr == "Metapipe" & childrenOf(sym).length > 1) {
        emitRegChains(sym, iters.get, cchain.get)
      }
    }

  }


  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case Hwblock(func,isForever) =>
      controllerStack.push(lhs)
      toggleEn() // turn on
      val streamAddition = getStreamEnablers(lhs)
      emit(s"""${quote(lhs)}_en := io.enable & !io.done ${streamAddition}""")
      emit(s"""${quote(lhs)}_resetter := reset""")
      emit(src"""${lhs}_ctr_trivial := false.B""")
      emitController(lhs, None, None)
      if (iiOf(lhs) <= 1) {
        emit(src"""val ${lhs}_II_done = true.B""")
      } else {
        emit(src"""val ${lhs}_IICtr = Module(new RedxnCtr(2 + Utils.log2Up(${lhs}_retime)));""")
        emit(src"""val ${lhs}_II_done = ${lhs}_IICtr.io.output.done | ${lhs}_ctr_trivial""")
        emit(src"""${lhs}_IICtr.io.input.enable := ${lhs}_en""")
        emit(src"""${lhs}_IICtr.io.input.stop := ${iiOf(lhs)}.S // ${lhs}_retime.S""")
        emit(src"""${lhs}_IICtr.io.input.reset := reset | ${lhs}_II_done.D(1)""")
        emit(src"""${lhs}_IICtr.io.input.saturate := false.B""")       
      }
      emit(src"""val retime_counter = Module(new SingleCounter(1)) // Counter for masking out the noise that comes out of ShiftRegister in the first few cycles of the app""")
      emit(src"""retime_counter.io.input.start := 0.S; retime_counter.io.input.stop := (max_retime.S); retime_counter.io.input.stride := 1.S; retime_counter.io.input.gap := 0.S""")
      emit(src"""retime_counter.io.input.saturate := true.B; retime_counter.io.input.reset := false.B; retime_counter.io.input.enable := true.B;""")
      emitGlobalWire(src"""val retime_released = Wire(Bool())""")
      emitGlobalWire(src"""val rr = retime_released // Shorthand""")
      emit(src"""retime_released := retime_counter.io.output.done """)
      topLayerTraits = childrenOf(lhs).map { c => src"$c" }
      if (levelOf(lhs) == InnerControl) emitInhibitor(lhs, None, None, None)
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
      emitGlobalWire(src"""val ${lhs}_II_done = true.B""")
      emit(src"""${lhs}_ctr_trivial := ${controllerStack.tail.head}_ctr_trivial | false.B""")
      emitController(lhs, None, None)
      if (levelOf(lhs) == InnerControl) emitInhibitor(lhs, None, None, None)
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
      emit(src"""${lhs}_ctr_trivial := ${controllerStack.tail.head}_ctr_trivial | false.B""")
      emitController(lhs, None, None)
      emit(src"""val ${lhs}_II_done = true.B""")
      withSubStream(src"${lhs}", src"${parent_kernel}", levelOf(lhs) == InnerControl) {
        emit(s"// Controller Stack: ${controllerStack.tail}")
        emitBlock(func)
      } 
      val en = if (ens.isEmpty) "true.B" else ens.map(quote).mkString(" && ")
      emit(src"${lhs}_mask := $en")
      controllerStack.pop()

    case op@Switch(body,selects,cases) =>
      // emitBlock(body)
      val parent_kernel = controllerStack.head 
      controllerStack.push(lhs)
      emitStandardSignals(lhs)
      emit(src"""val ${lhs}_II_done = ${parent_kernel}_II_done""")
      // emit(src"""//${lhs}_base_en := ${parent_kernel}_base_en // Set by parent""")
      emit(src"""${lhs}_mask := true.B // No enable associated with switch, never mask it""")
      emit(src"""//${lhs}_resetter := ${parent_kernel}_resetter // Set by parent""")
      emit(src"""${lhs}_datapath_en := ${parent_kernel}_datapath_en // Not really used probably""")
      emit(src"""${lhs}_ctr_trivial := ${parent_kernel}_ctr_trivial | false.B""")
      parentOf(lhs).get match {  // This switch is a condition of another switchcase
        case Def(SwitchCase(_)) => 
          emit(src"""${parentOf(lhs).get}_done := ${lhs}_done // Route through""")
          emit(src"""${lhs}_en := ${parent_kernel}_en""")
        // case Def(e: StateMachine[_]) =>
        //   if (levelOf(parentOf(lhs).get) == InnerControl) emit(src"""${lhs}_en := ${parent_kernel}_en""")
        case _ => 
          if (levelOf(parentOf(lhs).get) == InnerControl) {
            emit(src"""${lhs}_en := ${parent_kernel}_en // Parent is inner, so doesn't know about me :(""")
          } else {
            emit(src"""//${lhs}_en := ${parent_kernel}_en // Parent should have set me""")            
          }

      }

      if (levelOf(lhs) == InnerControl) { // If inner, don't worry about condition mutation
        selects.indices.foreach{i => 
          emitGlobalWire(src"""val ${cases(i)}_switch_select = Wire(Bool())""")
          emit(src"""${cases(i)}_switch_select := ${selects(i)}""")
        }
      } else { // If outer, latch in selects in case the body mutates the condition
        selects.indices.foreach{i => 
          emit(src"""val ${cases(i)}_switch_sel_reg = RegInit(false.B)""")
          emit(src"""${cases(i)}_switch_sel_reg := Mux(Utils.risingEdge(${lhs}_en), ${selects(i)}, ${cases(i)}_switch_sel_reg)""")
          emitGlobalWire(src"""val ${cases(i)}_switch_select = Wire(Bool())""")
          emit(src"""${cases(i)}_switch_select := Mux(Utils.risingEdge(${lhs}_en), ${selects(i)}, ${cases(i)}_switch_sel_reg)""")
        }
      }

      withSubStream(src"${lhs}", src"${parent_kernel}", levelOf(lhs) == InnerControl) {
        emit(s"// Controller Stack: ${controllerStack.tail}")
        if (Bits.unapply(op.mT).isDefined) {
          emit(src"val ${lhs}_onehot_selects = Wire(Vec(${selects.length}, Bool()))")
          emit(src"val ${lhs}_data_options = Wire(Vec(${selects.length}, ${newWire(lhs.tp)}))")
          selects.indices.foreach { i =>
            emit(src"${lhs}_onehot_selects($i) := ${cases(i)}_switch_select")
            emit(src"${lhs}_data_options($i) := ${cases(i)}")
          }
          emitGlobalWire(src"val $lhs = Wire(${newWire(lhs.tp)})")
          emit(src"$lhs := Mux1H(${lhs}_onehot_selects, ${lhs}_data_options).r")

          cases.collect{case s: Sym[_] => stmOf(s)}.foreach{ stm => 
            visitStm(stm)
            // Probably need to match on type of stm and grab the return values
          }
          if (levelOf(lhs) == InnerControl) {
            emit(src"""${lhs}_done := ${parent_kernel}_done""")
          } else {
            val anyCaseDone = cases.map{c => src"${c}_done"}.mkString(" | ")
            emit(src"""${lhs}_done := $anyCaseDone // Safe to assume Switch is done when ANY child is done?""")
          }

        } else {
          // If this is an innerpipe, we need to route the done of the parent downward.  If this is an outerpipe, we need to route the dones of children upward
          if (levelOf(lhs) == InnerControl) {
            emit(src"""${lhs}_done := ${parent_kernel}_done""")
            cases.collect{case s: Sym[_] => stmOf(s)}.foreach(visitStm)
          } else {
            val anyCaseDone = cases.map{c => src"${c}_done"}.mkString(" | ")
            emit(src"""${lhs}_done := $anyCaseDone // Safe to assume Switch is done when ANY child is done?""")
            cases.collect{case s: Sym[_] => stmOf(s)}.foreach(visitStm)
          }
        }
      }
      controllerStack.pop()


    case op@SwitchCase(body) =>
      // open(src"val $lhs = {")
      val parent_kernel = controllerStack.head 
      controllerStack.push(lhs)
      emitStandardSignals(lhs)
      emit(src"""${lhs}_en := ${parent_kernel}_en & ${lhs}_switch_select""")
      // emit(src"""${lhs}_base_en := ${parent_kernel}_base_en & ${lhs}_switch_select""")
      emit(src"""val ${lhs}_II_done = ${parent_kernel}_II_done""")
      emit(src"""${lhs}_mask := true.B // No enable associated with switch, never mask it""")
      emit(src"""${lhs}_resetter := ${parent_kernel}_resetter""")
      emit(src"""${lhs}_datapath_en := ${parent_kernel}_datapath_en // & ${lhs}_switch_select // Do not include switch_select because this signal is retimed""")
      emit(src"""${lhs}_ctr_trivial := ${parent_kernel}_ctr_trivial | false.B""")
      if (levelOf(lhs) == InnerControl) {
        val realctrl = findCtrlAncestor(lhs) // TODO: I don't think this search is needed anymore
        emitInhibitor(lhs, None, None, parentOf(parent_kernel))
      }
      withSubStream(src"${lhs}", src"${parent_kernel}", levelOf(lhs) == InnerControl) {
        emit(s"// Controller Stack: ${controllerStack.tail}")
        // if (blockContents(body).length > 0) {
        // if (childrenOf(lhs).count(isControlNode) == 1) { // This is an outer pipe
        if (childrenOf(lhs).count(isControlNode) > 1) {// More than one control node is children
          throw new Exception(s"Seems like something is messed up with switch cases ($lhs).  Please put a pipe around your multiple controllers inside the if statement, maybe?")
        } else if (levelOf(lhs) == OuterControl & childrenOf(lhs).count(isControlNode) == 1) { // This is an outer pipe
          emit(s"// Controller Stack: ${controllerStack.tail}")
          emitBlock(body)
        } else if (levelOf(lhs) == OuterControl & childrenOf(lhs).count(isControlNode) == 0) {
          emit(s"// Controller Stack: ${controllerStack.tail}")
          emitBlock(body)
          emit(src"""${lhs}_done := ${lhs}_en // Route through""")
        } else if (levelOf(lhs) == InnerControl) { // Body contains only primitives
          emitBlock(body)
          emit(src"""${lhs}_done := ${parent_kernel}_done // Route through""")
        }
        val returns_const = lhs match {
          case Const(_) => false
          case _ => true
        }
        if (Bits.unapply(op.mT).isDefined & returns_const) {
          emitGlobalWire(src"val $lhs = Wire(${newWire(lhs.tp)})")
          emit(src"$lhs.r := ${body.result}.r")
        }
      }
      // val en = if (ens.isEmpty) "true.B" else ens.map(quote).mkString(" && ")
      // emit(src"${lhs}_mask := $en")
      controllerStack.pop()

      // close("}")

    case _:OpForeach   => throw new Exception("Should not be emitting chisel for Op ctrl node")
    case _:OpReduce[_] => throw new Exception("Should not be emitting chisel for Op ctrl node")
    case _:OpMemReduce[_,_] => throw new Exception("Should not be emitting chisel for Op ctrl node")

    case _ => super.emitNode(lhs, rhs)
  }
}
