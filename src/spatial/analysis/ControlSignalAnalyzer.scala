package spatial.analysis

import org.virtualized.SourceContext

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
  import IR._

  override val name = "Control Signal Analyzer"

  // --- State
  var level = 0
  var controller: Option[Ctrl] = None
  var pendingNodes: Map[Exp[_], List[Exp[_]]] = Map.empty
  var pendingExtReads: Set[Exp[_]] = Set.empty
  var unrollFactors: List[Const[Index]] = Nil

  var localMems: List[Exp[_]] = Nil
  var metapipes: List[Exp[_]] = Nil
  var top: Option[Exp[_]] = None

  override protected def preprocess[S:Staged](block: Block[S]) = {
    localMems = Nil
    metapipes = Nil
    top = None
    level = 0
    controller = None
    pendingExtReads = Set.empty
    pendingNodes = Map.empty
    unrollFactors = Nil
    metadata.clearAll[Writers]
    metadata.clearAll[Readers]
    metadata.clearAll[Children]
    metadata.clearAll[WrittenMems]
    metadata.clearAll[ReadUsers]
    metadata.clearAll[MShouldDuplicate]
    super.preprocess(block)
  }

  override protected def postprocess[S:Staged](block: Block[S]) = {
    top match {
      case Some(ctrl@Op(Hwblock(_))) =>
      case _ => new NoTopError(ctxOrHere(block.result))
    }
    if (top.isDefined) {
      // After setting all other readers/external uses, check to see if
      // any reads external to the accel block are unused. Parent for these is top
      pendingExtReads.foreach { case reader@LocalReader(reads) =>
        reads.foreach { case (mem, addr, en) =>
          if (!readersOf(mem).exists(_.node == reader)) {
            dbg(c"Adding external reader $reader to list of readers for $mem")
            readersOf(mem) = (reader, (top.get, false)) +: readersOf(mem)
          }
        }
      }
    }
    dbg("Local memories: ")
    localMems.foreach{mem => dbg(c"  $mem")}
    super.postprocess(block)
  }

  def visitCtrl(ctrl: Ctrl)(blk: => Unit): Unit = {
    level += 1
    val prevCtrl = controller
    val prevReads = pendingNodes

    controller = Some(ctrl)
    blk

    controller = prevCtrl
    pendingNodes = prevReads
    level -= 1
  }

  def visitCtrl(ctrl: Ctrl, inds: Seq[Bound[Index]], cchain: Exp[CounterChain])(blk: => Unit): Unit = {
    val prevUnrollFactors = unrollFactors
    val factors = parFactorsOf(cchain)

    // ASSUMPTION: Currently only parallelizes by innermost loop
    inds.zip(factors).foreach{case (i,f) => parFactorOf(i) = f }
    unrollFactors ++= factors.lastOption

    visitCtrl(ctrl)(blk)

    unrollFactors = prevUnrollFactors
  }

  /** Helper methods **/
  def appendReader(reader: Exp[_], ctrl: Ctrl) = {
    val LocalReader(reads) = reader
    reads.foreach{case (mem, addr, en) =>
      val access = (reader, ctrl)

      if (!readersOf(mem).contains(access))
        readersOf(mem) = access +: readersOf(mem)

      dbg(c"Added reader $reader of $mem in $ctrl")
    }
  }
  def addPendingNode(node: Exp[_]) = {
    dbg(c"Adding pending node $node")
    shouldDuplicate(node) = true
    if (!pendingNodes.contains(node)) pendingNodes += node -> List(node)
  }
  def addPendingExternalReader(reader: Exp[_]) = {
    dbg(c"Added pending external read: $reader")
    pendingExtReads += reader
  }

  def addReader(reader: Exp[_], ctrl: Ctrl) = {
    if (isInnerControl(ctrl))
      appendReader(reader, ctrl)
    else
      addPendingNode(reader)
  }

  def appendWriter(writer: Exp[_], ctrl: Ctrl) = {
    val LocalWriter(writes) = writer
    writes.foreach{case (mem,value,addr,en) =>
      writersOf(mem) = (writer,ctrl) +: writersOf(mem)      // (5)
      writtenIn(ctrl) = mem +: writtenIn(ctrl)              // (10)
      value.foreach{v => isAccum(mem) = isAccum(mem) || (v dependsOn mem)  }              // (6)
      addr.foreach{is => isAccum(mem) = isAccum(mem) || is.exists(i => i dependsOn mem) } // (6)
      en.foreach{e => isAccum(mem) = isAccum(mem) || (e dependsOn mem) }                  // (6)

      dbg(c"Added writer $writer of $mem in $ctrl")
    }
  }

  def addWriter(writer: Exp[_], ctrl: Ctrl) = {
    if (isInnerControl(ctrl))
      appendWriter(writer, ctrl)
    else {
      val mem = LocalWriter.unapply(writer).get.head._1
      throw new ExternalWriteError(mem, writer)(ctxOrHere(writer))
    }
  }

  // (1, 7)
  def addAllocation(alloc: Exp[_], ctrl: Exp[_]) = {
    dbg(c"Setting parent of $alloc to $ctrl")
    parentOf(alloc) = ctrl
    if (isLocalMemory(alloc)) {
      dbg(c"Registered local memory $alloc")
      localMems ::= alloc
    }
  }

  // (2, 3)
  def addChild(child: Exp[_], ctrl: Exp[_]) = {
    dbg(c"Setting parent of $child to $ctrl")
    parentOf(child) = ctrl
    childrenOf(ctrl) = child +: childrenOf(ctrl)
  }


  def addPendingUse(user: Exp[_], ctrl: Ctrl, pending: Seq[Exp[_]]): Unit = {
    dbg(c"Found user ${str(user)} of:")
    pending.foreach{s => dbg(c"  ${str(s)}")}

    pending.foreach{node =>
      usersOf(node) = (user,ctrl) +: usersOf(node)
      if (isRegisterRead(node)) appendReader(node, ctrl)

      // Also add stateless nodes that this node uses
      // Can't do this on the fly when the node was first reached, since the associated control was unknown
      pendingNodes.getOrElse(node, Nil).filter(_ != node).foreach{used =>
        usersOf(used) = (node,ctrl) +: usersOf(used)
      }
    }

  }

  /** Common method for all nodes **/
  def addCommonControlData(lhs: Sym[_], rhs: Op[_]) = {
    // Set total unrolling factors of this node's scope + internal unrolling factors in this node
    unrollFactorsOf(lhs) = unrollFactors ++ parFactorsOf(lhs) // (9)

    if (controller.isDefined) {
      val ctrl: Ctrl   = controller.get
      val parent: Ctrl = if (isControlNode(lhs)) (lhs, false) else ctrl

      val pending = rhs.inputs.flatMap{sym => pendingNodes.getOrElse(sym, Nil) }
      if (pending.nonEmpty) {
        // All nodes which could potentially use a reader outside of an inner control node
        if (isStateless(lhs) && !isInnerControl(parent)) {
          dbg(c"Found propagating reader ${str(lhs)} of:")
          pending.foreach{s => dbg(c"  ${str(s)}")}
          pendingNodes += lhs -> (lhs +: pending)
        }
        else {
          addPendingUse(lhs, parent, pending)
        }
      }


      if (isStateless(lhs) && isOuterControl(parent)) addPendingNode(lhs)

      if (isAllocation(lhs)) addAllocation(lhs, parent.node)  // (1, 7)
      if (isReader(lhs)) addReader(lhs, parent)               // (4)
      if (isWriter(lhs)) addWriter(lhs, parent)               // (5, 6, 10)

    }
    else {
      if (isReader(lhs)) addPendingExternalReader(lhs)
      if (isAllocation(lhs) && (isArgIn(lhs) || isArgOut(lhs))) localMems ::= lhs // (7)
    }

    if (isControlNode(lhs)) {
      if (controller.isDefined) addChild(lhs, controller.get.node) // (2, 3)
      else {
        top = Some(lhs) // (11)
        pendingExtReads.foreach{reader => addPendingNode(reader) }
      }
      if (isMetaPipe(lhs)) metapipes ::= lhs // (8)
    }
  }

  def addChildDependencyData(lhs: Sym[_], block: Block[_]): Unit = if (isOuterControl(lhs)) {
    withInnerStms(availStms diff block.inputs.map(stmOf)) {
      val children = childrenOf(lhs)
      dbg(c"parent: $lhs")
      val allDeps = Map(children.map { child =>
        dbg(c"  child: $child")
        val schedule = getCustomSchedule(availStms, List(child))
        schedule.foreach{stm => dbg(c"    $stm")}
        child -> schedule.flatMap(_.lhs).filter { e => children.contains(e) && e != child }
      }: _*)

      dbg(c"dependencies: ")
      allDeps.foreach { case (child, deps) =>
        val fringe = deps diff deps.flatMap(allDeps)
        ctrlDepsOf(child) = fringe.toSet
        dbg(c"  $child ($fringe)")
      }
    }
  }

  override protected def visit(lhs: Sym[_], rhs: Op[_]): Unit = {
    addCommonControlData(lhs, rhs)
    analyze(lhs, rhs)
  }

  protected def analyze(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case Hwblock(blk) =>
      visitCtrl((lhs,false)){ visitBlock(blk) }
      addChildDependencyData(lhs, blk)

    case UnitPipe(blk) =>
      visitCtrl((lhs,false)){ visitBlock(blk) }
      addChildDependencyData(lhs, blk)

    case ParallelPipe(blk) =>
      visitCtrl((lhs,false)){ visitBlock(blk) }
      addChildDependencyData(lhs, blk)

    case OpForeach(cchain,func,iters) =>
      visitCtrl((lhs,false),iters,cchain){ visitBlock(func) }
      addChildDependencyData(lhs, func)

    case OpReduce(cchain,accum,map,ld,reduce,store,rV,iters) =>
      visitCtrl((lhs,false), iters, cchain){
        visitBlock(map)

        // Handle the one case where we allow scalar communication between blocks
        if (isStateless(map.result)) {
          addPendingUse(lhs, (lhs,false), Seq(map.result))  // NOT the inner (lhs,true)
        }

        visitCtrl((lhs,true)) {
          visitBlock(ld)
          visitBlock(reduce)
          visitBlock(store)
        }
      }

      isAccum(accum) = true
      parentOf(accum) = lhs
      addChildDependencyData(lhs, map)
      isInnerAccum(accum) = isInnerControl(lhs)

    case OpMemReduce(cchainMap,cchainRed,accum,map,ldRes,ldAcc,reduce,store,rV,itersMap,itersRed) =>
      visitCtrl((lhs,false), itersMap, cchainMap) {
        visitBlock(map)
        visitCtrl((lhs,true), itersRed, cchainRed) {
          visitBlock(ldAcc)
          visitBlock(ldRes)
          visitBlock(reduce)
          visitBlock(store)
        }
      }
      isAccum(accum) = true
      parentOf(accum) = lhs
      addChildDependencyData(lhs, map)
      isInnerAccum(accum) = isInnerControl(lhs)

    case e: CoarseBurst[_,_] =>
      e.iters.foreach{i => parFactorOf(i) = int32(1) }
      parFactorsOf(lhs).headOption.foreach{p => parFactorOf(e.iters.last) = p }

    case e: Scatter[_] => parFactorOf(e.i) = parFactorsOf(lhs).head
    case e: Gather[_]  => parFactorOf(e.i) = parFactorsOf(lhs).head

    case _ => super.visit(lhs, rhs)
  }

}

