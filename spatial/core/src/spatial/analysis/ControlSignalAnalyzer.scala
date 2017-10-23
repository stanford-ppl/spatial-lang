package spatial.analysis

import argon.core._
import org.virtualized.SourceContext
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

/**
  * (1)  Sets parent control nodes of local memories
  * (2)  Sets parent control nodes of controllers
  * (3)  Sets children control nodes of controllers
  * (4)  Sets reader control nodes of locally read memories
  * (5)  Sets writer control nodes of locally written memories
  * (6)  Flags accumulators
  * (7)  Records list of local memories
  * (8)  Records set of metapipes
  * (9)  Set parallelization factors of memory readers and writers relative to memory
  * (10) Sets written set of controllers
  * (11) Determines the top controller
  */
trait ControlSignalAnalyzer extends SpatialTraversal {
  override val name = "Control Signal Analyzer"

  // --- State
  var level = 0
  // These two are related, but the indices may be different (e.g. OpReduce has 4 blocks but only 2 logical stages)
  // Readers/writers use the logical controller, while users use the compiler block (e.g. for reg duplication)
  var controller: Option[Ctrl] = None // The current logical controller stage
  var curBlock: Option[Blk] = None    // The current block being navigated by the compiler

  var pendingNodes: Map[Exp[_], Seq[Exp[_]]] = Map.empty
  var unrollFactors: List[List[Const[Index]]] = Nil       // Parallel loop unrolling factors (head = innermost)
  var loopIterators: List[Bound[Index]] = Nil             // Loop iterators (last = innermost)
  var inInnerLoop: Boolean = false                        // Is the innermost loop iterator an inner controller?

  var localMems: List[Exp[_]] = Nil
  var metapipes: List[Exp[_]] = Nil
  var streampipes: List[Exp[_]] = Nil
  var streamLoadCtrls: List[Exp[_]] = Nil // Pops
  var tileTransferCtrls: List[Exp[_]] = Nil // Pops
  var streamParEnqs: List[Exp[_]] = Nil // Pops
  var streamEnablers: List[Exp[_]] = Nil // Pops
  var streamHolders: List[Exp[_]] = Nil // Pushes
  var top: Option[Exp[_]] = None

  val instrument = new argon.util.NoInstrument("total")

  override protected def preprocess[S:Type](block: Block[S]): Block[S] = instrument("preprocess"){
    instrument.reset()

    localMems = Nil
    metapipes = Nil
    streamLoadCtrls = Nil
    tileTransferCtrls = Nil
    streamParEnqs = Nil
    streampipes = Nil
    top = None
    level = 0
    controller = None
    pendingNodes = Map.empty
    unrollFactors = Nil
    metadata.clearAll[Writers]
    metadata.clearAll[Readers]
    metadata.clearAll[Children]
    metadata.clearAll[WrittenMems]
    metadata.clearAll[ReadUsers]
    metadata.clearAll[Resetters]
    metadata.clearAll[MShouldDuplicate]

    super.preprocess(block)
  }

  override protected def postprocess[S:Type](block: Block[S]): Block[S] = {
    val result = instrument("postprocess") {
      top match {
        case Some(ctrl@Op(Hwblock(_, _))) =>
        case _ => new spatial.NoTopError(block.result.ctx)
      }
      dbg("Local memories: ")
      localMems.foreach { mem => dbg(c"  $mem") }
      super.postprocess(block)
    }
    instrument.dump(s"#${state.pass-1} $name: ")
    result
  }

  def visitBlk(e: Exp[_])(func: => Unit): Unit = visitBlk((e,0))(func)
  def visitBlk(blk: Blk)(func: => Unit): Unit = {
    level += 1
    val prevCtrl = controller
    val prevBlock = curBlock
    val prevReads = pendingNodes

    curBlock = Some(blk)
    controller = Some(blkToCtrl(blk))
    dbgs(c"  Setting controller to ${str(blk.node)} [block #${blk.block}, child #${controller.get.block}, isInner: ${controller.get.isInner}]")
    func

    controller = prevCtrl
    curBlock = prevBlock
    pendingNodes = prevReads
    level -= 1
  }

  def visitBlk(e: Exp[_], inds: Seq[Bound[Index]], cchain: Exp[CounterChain])(blk: => Unit): Unit = {
    visitBlk((e,0), inds, cchain)(blk)
  }
  def visitBlk(blk: Blk, inds: Seq[Bound[Index]], cchain: Exp[CounterChain])(func: => Unit): Unit = {
    val prevUnrollFactors = unrollFactors
    val prevLoopIterators = loopIterators
    val prevIsInnerLoop = inInnerLoop
    val factors = parFactorsOf(cchain)

    // ASSUMPTION: Currently only parallelizes by innermost loop
    inds.zip(factors).foreach{case (i,f) => parFactorOf(i) = f }
    inds.zip(countersOf(cchain)).foreach{case (i,c) => ctrOf(i) = c }
    controller.foreach{ctrl => inds.foreach{i => ctrlOf(i) = ctrl }}
    unrollFactors = factors.lastOption.toList +: unrollFactors
    loopIterators = loopIterators ++ inds
    inInnerLoop = isInnerControl(blkToCtrl(blk)) // This version of the method is only called for loops

    visitBlk(blk)(func)

    unrollFactors = prevUnrollFactors
    loopIterators = prevLoopIterators
    inInnerLoop = prevIsInnerLoop
  }

  /** Helper methods **/
  def appendReader(reader: Exp[_], ctrl: Ctrl) = instrument("appendReader"){
    val LocalReader(reads) = reader
    reads.foreach{case (mem, addr, en) =>
      val access = (reader, ctrl)

      if (!readersOf(mem).contains(access))
        readersOf(mem) = access +: readersOf(mem)

      dbgs(c"  Added reader $reader of $mem in $ctrl")
    }
  }

  def addReader(reader: Exp[_], ctrl: Ctrl) = instrument("addReader"){
    if (isInnerControl(ctrl))
      appendReader(reader, ctrl)
    else
      addPendingNode(reader)
  }

  def appendWriter(writer: Exp[_], ctrl: Ctrl) = instrument("appendWriter"){
    val LocalWriter(writes) = writer
    val Def(writeDef) = writer
    writes.foreach{case (mem,value,addr,en) =>
      writersOf(mem) = (writer,ctrl) +: writersOf(mem)      // (5)
      writtenIn(ctrl) = mem +: writtenIn(ctrl)              // (10)
      //val isAccumulatingWrite = writeDef.inputs.filterNot(_ == mem).exists{_.dependsOn(mem, curScope) }
      //isAccum(mem) = isAccum(mem) || isAccumulatingWrite
      //isAccum(writer) = isAccumulatingWrite

      val isAccumulatingWrite = writeDef.expInputs.filterNot(_ == mem).exists{in => memDepsOf(in).contains(mem) }
      isAccum(mem) = isAccum(mem) || isAccumulatingWrite
      isAccum(writer) = isAccumulatingWrite

      dbgs(c"  Added writer $writer of $mem in $ctrl")
    }
  }

  def addWriter(writer: Exp[_], ctrl: Ctrl) = instrument("addWriter"){
    if (isInnerControl(ctrl))
      appendWriter(writer, ctrl)
    else {
      val mem = LocalWriter.unapply(writer).get.head._1
      throw new spatial.ExternalWriteException(mem, writer, ctrl)(writer.ctx, state)
    }
  }

  def appendResetter(resetter: Exp[_], ctrl: Ctrl) = instrument("appendResetter"){
    val LocalResetter(resetters) = resetter
    resetters.foreach{case (mem,en) =>
      val access = (resetter, ctrl)

      if (!resettersOf(mem).contains(access))
        resettersOf(mem) = access +: resettersOf(mem)

      dbgs(c"  Added resetter $resetter of $mem in $ctrl")
    }
  }

  def addResetter(resetter: Exp[_], ctrl: Ctrl) = instrument("addResetter"){
    if (isInnerControl(ctrl))
      appendResetter(resetter, ctrl)
    else {
      throw new Exception("Cannot have resetter outside of an inner pipe!")
    }
  }

  // (1, 7)
  def addAllocation(alloc: Exp[_], ctrl: Exp[_]) = instrument("addAllocation"){
    dbgs(c"  Setting parent of $alloc to $ctrl")
    parentOf(alloc) = ctrl
    if (isLocalMemory(alloc)) {
      dbgs(c"  Registered local memory $alloc")
      localMems ::= alloc
    }
  }

  def addStreamLoadMem(ctrl: Exp[_]) = instrument("addStreamLoadMem"){
    dbgs(c"  Registered stream load $ctrl")
    streamLoadCtrls ::= ctrl
  }

  def addTileTransferCtrl(ctrl: Exp[_]) = instrument("addTileTransferCtrl"){
    dbgs(c"  Registered tile transfer $ctrl")
    tileTransferCtrls ::= ctrl
  }

  def addParEnq(ctrl: Exp[_]) = instrument("addParEnq"){
    dbgs(c"  Registered par enq $ctrl")
    streamParEnqs ::= ctrl
  }


  def addStreamDeq(stream: Exp[_], ctrl: Exp[_]) = instrument("addStreamDeq"){
    parentOf(stream) = ctrl
    dbgs(c"  Registered stream enabler $stream")
    streamEnablers ::= stream
  }

  def addStreamEnq(stream: Exp[_], ctrl: Exp[_]) = instrument("addStreamEnq"){
    parentOf(stream) = ctrl
    dbgs(c"  Registered stream holder $stream")
    streamHolders ::= stream
  }

  // (2, 3)
  def addChild(child: Exp[_], ctrl: Exp[_]) = instrument("addChild"){
    dbgs(c"  Setting parent of $child to $ctrl")
    parentOf(child) = ctrl
    childrenOf(ctrl) = childrenOf(ctrl) :+ child
  }


  def addPendingUse(user: Exp[_], ctrl: Ctrl, blk: Blk, pending: Seq[Exp[_]], isBlockResult: Boolean = false): Unit = instrument("addPendingUse"){
    dbgs(c"  Node is user of:")
    pending.foreach{s => dbgs(c"  ${str(s)}")}

    // Bit of a hack: When the node being used is added as the result of a block (e.g. reduction)
    // which is used in an inner controller, the usersOf list should still see the outer controller as the user
    // rather than the inner controller. The readersOf list should see the inner controller.
    // val ctrlUser = if (isBlockResult && ctrl != null) (ctrl.node,-1) else ctrl

    pending.foreach{node =>
      usersOf(node) = usersOf(node) + ((user,blk))
      if (isRegisterRead(node) && ctrl != null) appendReader(node, ctrl)

      // Also add stateless nodes that this node uses
      // Can't do this on the fly when the node was first reached, since the associated control was unknown
      pendingNodes.getOrElse(node, Nil).filter(_ != node).foreach{used =>
        usersOf(used) = usersOf(used) + ((node,blk))
      }
    }
  }

  def checkPendingNodes(lhs: Sym[_], rhs: Op[_], ctrl: Option[Ctrl], blk: Option[Blk]) = instrument("checkPendingNodes"){
    val pending = rhs.nonBlockInputs.flatMap{sym => pendingNodes.getOrElse(sym, Nil) }
    if (pending.nonEmpty) {
      // All nodes which could potentially use a reader outside of an inner control node
      if (isStateless(lhs) && !ctrl.exists(isInnerControl)) { // Ctrl is either outer or outside Accel
        addPropagatingNode(lhs, pending)
      }
      else {
        addPendingUse(lhs, ctrl.orNull, blk.orNull, pending)
      }
    }
  }

  def addPropagatingNode(node: Exp[_], pending: Seq[Exp[_]]) = instrument("addPropagatingNode"){
    dbgs(c"  Node is propagating reader of:")
    pending.foreach{s => dbgs(c"  ${str(s)}")}
    pendingNodes += node -> (node +: pending)
  }

  def addPendingNode(node: Exp[_]) = instrument("addPendingNode"){
    dbgs(c"  Adding pending node $node")
    shouldDuplicate(node) = true
    if (!pendingNodes.contains(node)) pendingNodes += node -> List(node)
  }

  def addMemoryDeps(node: Exp[_]) = instrument("addMemoryDeps"){
    getDef(node).foreach{d =>
      val inputs = d.expInputs
      memDepsOf(node) = (inputs intersect localMems).toSet ++ inputs.flatMap(in => memDepsOf(in))
      dbgs(s"memory dependencies of $node: " + memDepsOf(node).mkString(","))
    }
  }

  /** Common method for all nodes **/
  def addCommonControlData(lhs: Sym[_], rhs: Op[_]) = instrument("addCommonControlData"){
    if (controller.isDefined) {
      // Set total unrolling factors of this node's scope + internal unrolling factors in this node

      if (isPrimitiveNode(lhs) && inInnerLoop) {
        isLoopInvariant(lhs) = !lhs.dependsOn(loopIterators.last) && !isAccessWithoutAddress(lhs)
      }

      if (isPrimitiveNode(lhs) && isLoopInvariant(lhs)) {
        unrollFactorsOf(lhs) = parFactorsOf(lhs) +: unrollFactors.drop(1) // Everything except innermost loop
      }
      else {
        unrollFactorsOf(lhs) = parFactorsOf(lhs) +: unrollFactors // (9)
      }

      val ctrl: Ctrl   = controller.get
      val parent: Ctrl = if (isControlNode(lhs)) (lhs, -1) else ctrl
      val blk: Blk     = if (isControlNode(lhs)) (lhs, -1) else curBlock.get
      dbgs(c"  parent: $parent, ctrl: $ctrl")

      if (parent.node != lhs) parentOf(lhs) = parent.node else parentOf(lhs) = ctrl.node

      checkPendingNodes(lhs, rhs, Some(parent), Some(blk))

      if (isStateless(lhs) && isOuterControl(parent)) addPendingNode(lhs)

      if (isAllocation(lhs)) addAllocation(lhs, parent.node)  // (1, 7)
      if (isStreamLoad(lhs)) addStreamLoadMem(lhs)
      if (isTileTransfer(lhs)) addTileTransferCtrl(lhs)
      if (isParEnq(lhs)) addParEnq(lhs)
      if (isStreamStageEnabler(lhs)) addStreamDeq(lhs, parent.node)
      if (isStreamStageHolder(lhs)) addStreamEnq(lhs, parent.node)
      if (isReader(lhs)) addReader(lhs, parent)               // (4)
      if (isWriter(lhs)) addWriter(lhs, parent)               // (5, 6, 10)
      if (isResetter(lhs)) addResetter(lhs, parent)

      if (isInnerControl(ctrl) && !isControlNode(lhs)) addMemoryDeps(lhs)
    }
    else {
      checkPendingNodes(lhs, rhs, None, None)
      if (isStateless(lhs)) addPendingNode(lhs)

      if (isLocalMemory(lhs)) localMems ::= lhs // (7)
    }

    if (isControlNode(lhs)) {
      if (controller.isDefined) addChild(lhs, controller.get.node) // (2, 3)
      else {
        top = Some(lhs) // (11)
      }
      if (isMetaPipe(lhs)) metapipes ::= lhs // (8)
      if (isStreamPipe(lhs)) streampipes ::= lhs
    }
  }

  def addChildDependencyData(lhs: Sym[_], block: Block[_]): Unit = instrument("addChildDependencyData"){
    // TODO: This is expensive and doesn't work yet
    /*if (isOuterControl(lhs)) {
      withInnerStms(availStms diff block.inputs.flatMap(getStm)) {
        val children = childrenOf(lhs)
        dbgs(c"  parent: $lhs")
        val allDeps = Map(children.map { child =>
          dbgs(c"    child: $child")
          val schedule = getCustomSchedule(availStms, List(child))
          schedule.foreach{stm => dbgs(c"      $stm")}
          child -> schedule.flatMap(_.lhs).filter { e => children.contains(e) && e != child }
        }: _*)

        dbgs(c"  dependencies: ")
        allDeps.foreach { case (child, deps) =>
          val fringe = deps diff deps.flatMap(allDeps)
          ctrlDepsOf(child) = fringe.toSet
          dbgs(c"    $child ($fringe)")
        }
      }
    }*/
  }

  override protected def visit(lhs: Sym[_], rhs: Op[_]): Unit = instrument("visit"){
    dbgs(c"$lhs = $rhs")
    addCommonControlData(lhs, rhs)
    analyze(lhs, rhs)
  }

  protected def analyze(lhs: Sym[_], rhs: Op[_]): Unit = instrument("analyze"){ rhs match {
    case Hwblock(blk,_) =>
      visitBlk(lhs){ visitBlock(blk) }
      addChildDependencyData(lhs, blk)

    case UnitPipe(_,blk) =>
      visitBlk(lhs){ visitBlock(blk) }
      addChildDependencyData(lhs, blk)

    case ParallelPipe(_,blk) =>
      visitBlk(lhs){ visitBlock(blk) }
      addChildDependencyData(lhs, blk)

    case SwitchCase(blk) =>
      visitBlk(lhs){
        visitBlock(blk)
        pendingNodes.get(blk.result).foreach{nodes =>
          addPendingUse(lhs, blkToCtrl((lhs,0)), (lhs,0), nodes, isBlockResult = true)
        }
      }
      addChildDependencyData(lhs, blk)

    case Switch(blk,selects,cases) =>
      visitBlk(lhs){ visitBlock(blk) }
      addChildDependencyData(lhs, blk)
      addPropagatingNode(lhs, blockContents(blk).flatMap(_.lhs).filter(pendingNodes contains _))

    case StateMachine(_,_,notDone,action,nextState,_) =>
      visitBlk((lhs,0)){
        visitBlock(notDone)
        pendingNodes.get(notDone.result).foreach{nodes =>
          addPendingUse(lhs, blkToCtrl((lhs,0)), (lhs,0), nodes, isBlockResult = true)
        }
      }

      visitBlk((lhs,1)){ visitBlock(action) }

      visitBlk((lhs,2)){
        visitBlock(nextState)
        pendingNodes.get(nextState.result).foreach{nodes =>
          addPendingUse(lhs, blkToCtrl((lhs,2)), (lhs,2), nodes, isBlockResult = true)
        }
      }

      addChildDependencyData(lhs, notDone)
      addChildDependencyData(lhs, action)
      addChildDependencyData(lhs, nextState)

    case OpForeach(en,cchain,func,iters) =>
      visitBlk(lhs,iters,cchain){ visitBlock(func) }
      addChildDependencyData(lhs, func)
      iters.foreach { iter => parentOf(iter) = lhs }

    case OpReduce(en,cchain,accum,map,ld,reduce,store,_,_,rV,iters) =>
      // Child 0 - the map part
      visitBlk((lhs,0), iters, cchain){
        visitBlock(map)

        // Handle case where we allow scalar communication between blocks
        pendingNodes.get(map.result).foreach{nodes =>
          // Logically, the map result is used by the reduction stage of the reduce
          // But in the compiler, the read occurs in the first block
          addPendingUse(lhs, blkToCtrl((lhs,1)), (lhs,0), nodes, isBlockResult = true)
        }
      }

      // Child 1 - the reduction part
      visitBlk((lhs,1)) { visitBlock(ld) }
      visitBlk((lhs,2)) { visitBlock(reduce) }
      visitBlk((lhs,3)) { visitBlock(store) }

      isAccum(accum) = true
      parentOf(accum) = lhs
      iters.foreach { iter => parentOf(iter) = lhs }
      addChildDependencyData(lhs, map)
      isInnerAccum(accum) = isInnerControl(lhs)

    case OpMemReduce(en,cchainMap,cchainRed,accum,map,ldRes,ldAcc,reduce,store,_,_,rV,itersMap,itersRed) =>
      visitBlk((lhs,0), itersMap, cchainMap) { visitBlock(map) }
      visitBlk((lhs,1), itersRed, cchainRed) { visitBlock(ldAcc) }
      visitBlk((lhs,2), itersRed, cchainRed) { visitBlock(ldRes) }
      visitBlk((lhs,3), itersRed, cchainRed) { visitBlock(reduce) }
      visitBlk((lhs,4), itersRed, cchainRed) { visitBlock(store) }

      isAccum(accum) = true
      parentOf(accum) = lhs
      itersMap.foreach { iter => parentOf(iter) = lhs }
      itersRed.foreach { iter => parentOf(iter) = lhs }
      addChildDependencyData(lhs, map)
      isInnerAccum(accum) = isInnerControl(lhs)

    case e: DenseTransfer[_,_] =>
      e.iters.foreach{i => parFactorOf(i) = int32s(1) }
      e.iters.foreach { iter => parentOf(iter) = lhs }
      parFactorOf(e.iters.last) = e.p

    case e: SparseTransfer[_] =>
      parFactorOf(e.i) = e.p

    case e if isFringe(lhs) =>
      rhs.allInputs.filter(isStream).foreach { stream => fringeOf(stream) = lhs }

    case _ => super.visit(lhs, rhs)
  }}

}

