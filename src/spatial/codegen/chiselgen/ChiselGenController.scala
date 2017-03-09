package spatial.codegen.chiselgen

import argon.codegen.chiselgen.ChiselCodegen
import spatial.api.{ControllerExp, CounterExp, UnrolledExp}
import spatial.SpatialConfig
import spatial.analysis.SpatialMetadataExp
import spatial.SpatialExp

trait ChiselGenController extends ChiselCodegen with ChiselGenCounter{
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
  var controllerStack = scala.collection.mutable.Stack[Exp[_]]()

  private def emitNestedLoop(cchain: Exp[CounterChain], iters: Seq[Bound[Index]])(func: => Unit): Unit = {
    for (i <- iters.indices)
      open(src"$cchain($i).foreach{case (is,vs) => is.zip(vs).foreach{case (${iters(i)},v) => if (v) {")

    func

    iters.indices.foreach{_ => close("}}}") }
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

  override def quote(s: Exp[_]): String = {
    s match {
      case b: Bound[_] => computeSuffix(b)
      case _ =>
        if (SpatialConfig.enableNaming) {
          s match {
            case lhs: Sym[_] =>
              lhs match {
                case Def(e: Hwblock) =>
                  s"AccelController"
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
          super.quote(s)
        }
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

  def emitController(sym:Sym[Any], cchain:Option[Exp[CounterChain]], iters:Option[Seq[Bound[Index]]]) {

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

    sym match {
      case Def(n: UnrolledForeach) =>
        emit(src"""val ${sym}_datapath_en = ${sym}_sm.io.output.ctr_inc // TODO: Make sure this is a safe assignment""")
      case _ =>
        emit(src"""val ${sym}_datapath_en = ${sym}_en & ~${sym}_rst_en // TODO: Phase out this assignment and make it ctr_inc""") 
    }
    
    /* Counter Signals for controller (used for determining done) */
    if (smStr != "Parallel" & smStr != "Streampipe") {
      if (cchain.isDefined) {
        emitGlobal(src"""val ${cchain.get}_en = Wire(Bool())""") 
        sym match { 
          case Def(n: UnrolledReduce[_,_]) => // Emit handles by emitNode
          case _ => emit(src"${cchain.get}_en := ${sym}_sm.io.output.ctr_inc")
        }
        emit(src"""// ---- Begin $smStr ${sym} Counter Signals ----""")
        val ctr = cchain.get
        emit(src"""${ctr}_resetter := ${sym}_rst_en""")
        if (smStr == "Innerpipe") {
          emit(src"""${sym}_sm.io.input.ctr_done := Utils.delay(${ctr}_done, 1 + ${sym}_offset)""")
        }
      } else {
        emit(src"""// ---- Begin $smStr ${sym} Unit Counter ----""")
        if (smStr == "Innerpipe") {
          emit(src"""${sym}_sm.io.input.ctr_done := Utils.delay(${sym}_sm.io.output.ctr_en, 1 + ${sym}_offset)""")
          emit(src"""val ${sym}_ctr_en = ${sym}_sm.io.output.ctr_inc""")
        } else {
          emit(s"// How to emit for non-innerpipe unit counter?")
        }
      }
    }

    if (hasForever) {
      emit(src"""${sym}_sm.io.input.forever := true.B""")
    } else {
      emit(src"""${sym}_sm.io.input.forever := false.B""")
    }

        
    /* Control Signals to Children Controllers */
    if (smStr == "Innerpipe") {
      emit(src"""// ---- No children for $sym ----""")
    } else {
      emit(src"""// ---- Begin $smStr ${sym} Children Signals ----""")
      childrenOf(sym).zipWithIndex.foreach { case (c, idx) =>
        emitGlobal(src"""val ${c}_done = Wire(Bool())""")
        emitGlobal(src"""val ${c}_en = Wire(Bool())""")
        emitGlobal(src"""val ${c}_resetter = Wire(Bool())""")
        emit(src"""${sym}_sm.io.input.stageDone(${idx}) := ${c}_done;""")
        // If we are inside a stream pipe, the following may be set
        val readiers = listensTo(c).distinct.map { fifo => 
          fifo match {
            case Def(FIFONew(size)) => src"~${fifo}.io.empty"
            case Def(StreamInNew(bus)) => src"${fifo}_ready"
            case _ => src"${fifo}_en" // parent node
          }
        }.mkString(" & ")
        val holders = (pushesTo(c)).distinct.map { fifo => 
          fifo match {
            case Def(FIFONew(size)) => src"~${fifo}.io.full"
            case Def(StreamOutNew(bus)) => src"${fifo}_ready /*not sure if this sig exists*/"
          }
        }.mkString(" & ")

        val hasHolders = if (holders != "") "&" else ""
        val hasReadiers = if (readiers != "") "&" else ""

        emit(src"""${c}_en := ${sym}_sm.io.output.stageEnable(${idx}) ${hasHolders} ${holders} ${hasReadiers} ${readiers}""")  

        // If this is a stream controller, need to set up counter copy for children

        if (smStr == "Streampipe" & cchain.isDefined) {
          val Def(CounterChainNew(ctrs)) = cchain.get
          emitCounterChain(cchain.get, ctrs, src"_copy$c")
          emit(src"""${cchain.get}_copy${c}_en := ${c}_done""")
        }

        //   // Collect info about the fifos this child listens to
        //   val readiers = (listensTo(c) :+ sym).distinct.map { fifo => 
        //     fifo match {
        //       case Def(FIFONew(size)) => src"~${fifo}.io.empty"
        //       case Def(StreamInNew(bus)) => src"${fifo}_ready"
        //       case _ => src"${fifo}_en" // parent node
        //     }
        //   }.mkString(" & ")
        //   val holders = (pushesTo(c)).distinct.map { fifo => 
        //     fifo match {
        //       case Def(FIFONew(size)) => src"~${fifo}.io.full"
        //       case Def(StreamOutNew(bus)) => src"${fifo}_ready /*not sure if this sig exists*/"
        //     }
        //   }.mkString(" & ")
        //   val enablers = List(readiers, holders).mkString(" & ")
        //   emit(src"""${c}_en := ${enablers}""")
        // } else {
        //   emit(src"""${c}_en := ${sym}_sm.io.output.stageEnable(${idx})""")  
        // }
        emit(src"""${c}_resetter := ${sym}_sm.io.output.rst_en""")
      }
    }

    /* Emit reg chains */
    if (iters.isDefined) {
      if (smStr == "Metapipe" & childrenOf(sym).length > 1) {
        emitRegChains(sym, iters.get)
      }
    }

  //   // emit(s"""// debug.simPrintf(${quote(sym)}_en, "pipe ${quote(sym)}: ${percentDSet.toList.mkString(",   ")}\\n", ${childrenSet.toList.mkString(",")});""")
  }


  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case Hwblock(func,isForever) =>
      controllerStack.push(lhs)
      toggleEn() // turn on
      emit(s"""val ${quote(lhs)}_en = io.enable & !io.done;""")
      emit(s"""val ${quote(lhs)}_resetter = false.B // TODO: top level reset""")
      emitGlobal(s"""val ${quote(lhs)}_done = Wire(Bool())""")
      emitController(lhs, None, None)
      topLayerTraits = childrenOf(lhs).map { c => src"$c" }
      // Emit unit counter for this
      emit(s"""val done_latch = Module(new SRFF())""")
      emit(s"""done_latch.io.input.set := ${quote(lhs)}_sm.io.output.done""")
      emit(s"""done_latch.io.input.reset := ${quote(lhs)}_resetter""")
      emit(s"""io.done := done_latch.io.output.data""")

      emitBlock(func)
      toggleEn() // turn off
      controllerStack.pop()

    case UnitPipe(en,func) =>
      val parent_kernel = controllerStack.head 
      controllerStack.push(lhs)
      emitController(lhs, None, None)
      withSubStream(src"${lhs}", src"${parent_kernel}", styleOf(lhs) == InnerPipe) {
        emitBlock(func)
      }
      controllerStack.pop()

    case ParallelPipe(en,func) =>
      val parent_kernel = controllerStack.head 
      controllerStack.push(lhs)
      emitController(lhs, None, None)
      withSubStream(src"${lhs}", src"${parent_kernel}", styleOf(lhs) == InnerPipe) {
        emitBlock(func)
      } 
      controllerStack.pop()

    case OpForeach(cchain, func, iters) =>
      val parent_kernel = controllerStack.head 
      controllerStack.push(lhs)
      emitController(lhs, Some(cchain), Some(iters))
      withSubStream(src"${lhs}", src"${parent_kernel}", styleOf(lhs) == InnerPipe) {
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
