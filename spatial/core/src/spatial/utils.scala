package spatial

import argon.core._
import argon.nodes._
import argon.transform.Transformer
import forge._
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.lang.Math

import scala.io.Source

object utils {
  /**
    * Return the number of bits of data the given symbol represents
    */
  def nbits(e: Exp[_]): Int = e.tp match {case Bits(bT) => bT.length; case _ => 0 }

  /**
    * Least common multiple of two integers (smallest integer which has integer divisors a and b)
    */
  def lcm(a: Int, b: Int) = {
    val bigA = BigInt(a)
    val bigB = BigInt(b)
    (bigA*bigB / bigA.gcd(bigB)).intValue()
  }

  /**
    * Returns the list of parents of x, ordered outermost to innermost.
    */
  def allParents[T](x: T, parent: T => Option[T]): List[Option[T]] = {
    var path: List[Option[T]] = List(Some(x))
    var cur: Option[T] = Some(x)
    while (cur.isDefined) { cur = parent(cur.get); path ::= cur } // prepend
    path
  }

  /**
    * Returns the least common ancestor of two nodes in some directed, acyclic graph.
    * If the nodes share no common parent at any point in the tree, the LCA is undefined (None).
    * Also returns the paths from the least common ancestor to each node.
    * The paths do not contain the LCA, as it may be undefined.
    */
  def leastCommonAncestorWithPaths[T](x: T, y: T, parent: T => Option[T]): (Option[T], List[T], List[T]) = {
    val pathX = allParents(x, parent)
    val pathY = allParents(y, parent)
    // Choose last node where paths are the same
    val lca = pathX.zip(pathY).filter{case (x,y) => x == y}.lastOption.flatMap(_._1)
    val pathToX = pathX.drop(pathX.indexOf(lca)+1).map(_.get)
    val pathToY = pathY.drop(pathY.indexOf(lca)+1).map(_.get)
    (lca,pathToX,pathToY)
  }

  def leastCommonAncestor[T](x: T, y: T, parent: T => Option[T]): Option[T] = {
    leastCommonAncestorWithPaths(x,y,parent)._1
  }


  @internal def flatIndex(indices: Seq[Index], dims: Seq[Index]): Index = {
    val strides = List.tabulate(dims.length){d => Math.productTree(dims.drop(d+1)) }
    Math.sumTree(indices.zip(strides).map{case (a,b) => a.to[Index]*b })
  }

  def constDimsToStrides(dims: Seq[Int]): Seq[Int] = List.tabulate(dims.length){d => dims.drop(d + 1).product}

  @internal def dimsToStrides(dims: Seq[Index]): Seq[Index] = {
    List.tabulate(dims.length){d => Math.productTree(dims.drop(d + 1)) }
  }

  // Assumes stride of outermost dimension is first
  @stateful def stridesToDims(mem: Exp[_], strides: Seq[Int]): Seq[Int] = {
    val size = constDimsOf(mem).product
    val allStrides = size +: strides
    List.tabulate(allStrides.length-1){i => allStrides(i)/allStrides(i+ 1) }
  }

  @stateful def remapDispatches(access: Exp[_], mem: Exp[_], mapping: Map[Int,Int]): Unit = {
    dispatchOf(access, mem) = dispatchOf(access, mem).flatMap { o => mapping.get(o) }
    portsOf.set(access, mem, {
      portsOf(access, mem).flatMap { case (i, ps) => mapping.get(i).map { i2 => i2 -> ps } }
    })
  }


  // TODO: This uses the pointer-chasing version of scheduling - could possibly make faster?
  implicit class ExpOps(x: Exp[_]) {


    @stateful def getNodesBetween(y: Exp[_], scope: Set[Exp[_]]): Set[Exp[_]] = {
      def dfs(frontier: Seq[Exp[_]], nodes: Set[Exp[_]]): Set[Exp[_]] = frontier.toSet.flatMap{x: Exp[_] =>
        if (scope.contains(x)) {
          if (x == y) nodes + x
          else getDef(x).map{d => dfs(d.allInputs, nodes + x) }.getOrElse(Set.empty[Exp[_]])
        }
        else Set.empty[Exp[_]]

        /*var fullPath: Option[Set[Exp[_]]] = None
        val iter = frontier.iterator
        while (iter.hasNext && fullPath.isEmpty) {
          val x = iter.next()
          if (scp.isEmpty || scp.contains(x)) {
            if (x == y) {
              fullPath = Some(nodes + x)
            }
            else {
              fullPath = getDef(x).flatMap{d => dfs(d.inputs, nodes + x) }
            }
          }
        }
        fullPath*/
      }
      dfs(Seq(x),Set(x))
    }

    /**
      * Checks to see if x depends on y (dataflow only, no scheduling dependencies)
      */
    @stateful def dependsOn(y: Exp[_], scope: Seq[Stm] = Nil): Boolean = {
      val scp = scope.flatMap(_.lhs.asInstanceOf[Seq[Exp[_]]]).toSet

      def dfs(frontier: Seq[Exp[_]]): Boolean = frontier.exists{x =>
        (scp.isEmpty || scp.contains(x)) && (x == y || getDef(x).exists{d => dfs(d.inputs) })
      }
      dfs(Seq(x))
    }
    @stateful def dependsOnType(y: PartialFunction[Exp[_],Boolean]): Boolean = {
      def dfs(frontier: Seq[Exp[_]]): Boolean = frontier.exists{
        case s if y.isDefinedAt(s) && y(s) => true
        case Def(d) => dfs(d.inputs)
        case _ => false
      }
      dfs(Seq(x))
    }
    @stateful def dependsOnlyOnType(y: PartialFunction[Exp[_],Boolean]): Boolean = {
      def dfs(frontier: Seq[Exp[_]]): Boolean = frontier.forall{
        case s if y.isDefinedAt(s) && y(s) => true
        case Def(d) => dfs(d.inputs)
        case _ => false
      }
      dfs(Seq(x))
    }

    @stateful def collectDeps(y: PartialFunction[Exp[_],Exp[_]]): Seq[Exp[_]] = {
      def dfs(frontier: Seq[Exp[_]]): Seq[Exp[_]] = frontier.flatMap{
        case s @ Def(d) if y.isDefinedAt(s) => y(s) +: dfs(d.inputs)
        case s if y.isDefinedAt(s) => Seq(y(s))
        case Def(d) => dfs(d.inputs)
        case _ => Nil
      }
      dfs(Seq(x))
    }
    // x: StreamOut, y: StreamOutWrite
    // y.collectDeps{case Def(StreamInRead(strm)) => strm}
    // writersOf(x).head.node.collectDeps{case Def(StreamInRead(strm)) => strm }
  }

  implicit class IndexRangeInternalOps(x: Index) {
    @api def toRange: MRange = MRange.fromIndex(x)
  }
  implicit class Int64RangeInternalOps(x: Int64) {
    @api def toRange64: Range64 = Range64.fromInt64(x)
  }


  @stateful def lca(a: Ctrl, b: Ctrl): Option[Ctrl] = leastCommonAncestor[Ctrl](a, b, {x => parentOf(x)})

  @stateful def dependenciesOf(a: Ctrl): Set[Ctrl] = {
    if (a.block > 0) {
      val parent = parentOf(a).get
      val children = childrenOf(parent) filterNot (_ == a)
      val leaves = children.filter{x => !children.exists{child => dependenciesOf(child) contains x}}
      leaves.toSet
    }
    else ctrlDepsOf(a.node).map{node => (node,-1) }
  }

  /**
    * Pipeline distance between controllers a and b:
    * If a and b have a least common ancestor which is neither a nor b,
    * this is defined as the dataflow distance between the LCA's children which contain a and b
    * When a and b are equal, the distance is defined as zero.
    *
    * The distance is undefined when the LCA is a xor b, or if a and b occur in parallel
    * The distance is positive if a comes before b, negative otherwise
    *
    * @return The LCA of a and b and the pipeline distance.
    */
  @stateful def lcaWithDistance(a: Ctrl, b: Ctrl): (Ctrl, Int) = {
    if (a == b) (a, 0)
    else {
      val (lca, pathA, pathB) = leastCommonAncestorWithPaths[Ctrl](a, b, {node => parentOf(node)})
      if (lca.isEmpty) throw new NoCommonParentException(a,b)

      val parent = lca.get

      if (isOuterControl(parent) && a != parent && b != parent) {
        val topA = pathA.head
        val topB = pathB.head

        // Account for fork-join behavior - return the LONGEST path possible
        /*def dfs(start: Ctrl, end: Ctrl, i: Int): Int = {
          if (start == end) i
          else {
            val deps = dependenciesOf(start)
            val dists = deps.map{dep => dfs(dep, end, i+1) }

            dbg(c"$start ==> $end: ")
            deps.zip(dists).foreach{case (dep,dist) => dbg(c"  $dep: $dist") }

            dists.fold(-1){(a,b) => Math.max(a,b)}
          }
        }
        val aToB = dfs(topA, topB, 0)
        val bToA = dfs(topB, topA, 0)
        val dist  = if (aToB >= 0) aToB
                    else if (bToA >= 0) -bToA
                    else throw new UndefinedPipeDistanceException(a, b)*/
        log(c"    LCA: " + parent)
        log(c"    LCA children: " + childrenOf(parent).mkString(", "))
        log(c"    Path A (from $a): " + pathA.mkString(", ") + s": topA = $topA")
        log(c"    Path B (from $b): " + pathB.mkString(", ") + s": topB = $topB")


        // Linear version (using for now)
        val indexA = childrenOf(parent).indexOf(topA)
        val indexB = childrenOf(parent).indexOf(topB)
        if (indexA < 0 || indexB < 0) {
          throw new UndefinedPipeDistanceException(a, b)
        }
        val dist = indexB - indexA
        log(c"    distance: $dist")

        (parent, dist)
      }
      else (parent, 0)
    }
  }

  /**
    * Coarse-grained pipeline distance between accesses a and b.
    * If the LCA controller of a and b is a metapipeline, the pipeline distance
    * of the respective controllers for a and b. Otherwise zero.
    *
    * @return The LCA of a and b and the coarse-grained pipeline distance
    **/
  @stateful def lcaWithCoarseDistance(a: Access, b: Access): (Ctrl, Int) = {
    val (lca, dist) = lcaWithDistance(a.ctrl, b.ctrl)
    val coarseDistance = if (isMetaPipe(lca) || isStreamPipe(lca)) dist else 0
    (lca, coarseDistance)
  }

  /**
    * @return The child of the top controller which contains the given access.
    * Undefined if the access is not contained within a child of the top controller
    **/
  @stateful def childContaining(top: Ctrl, access: Access): Ctrl = {
    val child = access.ctrl
    val (lca, pathA, pathB) = leastCommonAncestorWithPaths[Ctrl](top,child, {node => parentOf(node)})

    if (pathB.isEmpty || lca.isEmpty || top != lca.get)
      throw new UndefinedChildException(top, access)

    pathB.head
  }

  /**
    * Returns metapipe controller for given accesses
    **/
  @stateful def findMetaPipe(mem: Exp[_], readers: Seq[Access], writers: Seq[Access]): (Option[Ctrl], Map[Access,Int]) = {

    def ambiguousMetapipesError(lcas: Map[Ctrl,Seq[(Access,Access)]]): Unit = {
      error(u"Ambiguous metapipes for readers/writers of $mem defined here:")
      error(str(mem))
      error(mem.ctx)
      lcas.foreach{case (pipe,accs) =>
        error(c"  metapipe: $pipe ")
        error(c"  accesses: " + accs.map(x => c"${x._1} / ${x._2}").mkString(","))
        error(str(pipe.node))
        error(pipe.node.ctx)
        error("")
      }
      error(c"  readers:")
      readers.foreach{rd => error(c"    $rd") }
      error(c"  writers:")
      writers.foreach{wr => error(c"    $wr") }
      state.logError()
    }

    val accesses = readers ++ writers
    assert(accesses.nonEmpty)

    val lcas = accesses.indices.flatMap{i =>
      (i + 1 until accesses.length).map{j =>
        val (lca,dist) = lcaWithCoarseDistance(accesses(i), accesses(j))
        (lca,dist,(accesses(i),accesses(j)))
      }
    }

    // Find accesses which require n-buffering, group by their controller
    val metapipeLCAs = lcas.filter(_._2 != 0).groupBy(_._1).mapValues(_.map(_._3))

    // Hierarchical metapipelining is currently disallowed
    if (metapipeLCAs.keys.size > 1) ambiguousMetapipesError(metapipeLCAs)
    val metapipe = metapipeLCAs.keys.headOption

    val ports = if (metapipe.isDefined) {
      val mpgroup = metapipeLCAs(metapipe.get)
      val anchor = mpgroup.head._1
      val dists = accesses.map{access =>
        val (lca,dist) = lcaWithCoarseDistance(anchor, access)
        dbg(c"LCA of $anchor and $access: $lca")
        // Time multiplexed actually becomes ALL ports
        if (lca == metapipe.get || access == anchor) access -> dist else access -> 0
      }
      val minDist = dists.map(_._2).min
      dists.map{case (access, dist) => access -> (dist - minDist) }.toMap
    }
    else accesses.map{access => access -> 0}.toMap

    // Port 0: First stage to write/read
    // Port X: X stage(s) after first stage
    // val ports = Map(lcas.map{grp => grp._3 -> (grp._2 - minDist)}:_*)

    dbg("")
    dbg(c"  accesses: $accesses")
    lcas.foreach{case (lca,dist,access) => dbg(c"    lca(${access._1}, ${access._2}) = $lca ($dist)") }
    dbg(s"  metapipe: $metapipe")
    ports.foreach{case (access, port) => dbg(s"    - $access : port #$port")}

    (metapipe, ports)
  }



  /** Error checking methods **/
  @stateful def areConcurrent(a: Access, b: Access): Boolean = {
    val (top,dist) = lcaWithDistance(a.ctrl, b.ctrl)
    isInnerPipe(top) || isParallel(top.node)
  }
  @stateful def arePipelined(a: Access, b: Access): Boolean = {
    val top = lca(a.ctrl, b.ctrl).get
    isInnerPipe(top) || isMetaPipe(top) || isStreamPipe(top)
  }

  // O(N^2), but number of accesses is typically small
  @stateful def checkAccesses(access: List[Access])(func: (Access, Access) => Boolean): Boolean = {
    access.indices.exists {i =>
      (i+1 until access.length).exists{j =>
        access(i) != access(j) && func(access(i), access(j))
      }
    }
  }
  @stateful def findAccesses(access: List[Access])(func: (Access, Access) => Boolean): Seq[(Access,Access)] = {
    access.indices.flatMap{i =>
      (i+1 until access.length).flatMap{j =>
        if (access(i) != access(j) && func(access(i), access(j))) Some((access(i),access(j))) else None
      }
    }
  }

  @internal def checkConcurrentReaders(mem: Exp[_]): Boolean = checkAccesses(readersOf(mem)){(a,b) =>
    if (areConcurrent(a,b)) {new ConcurrentReadersError(mem, a.node, b.node); true } else false
  }
  @internal def checkConcurrentWriters(mem: Exp[_]): Boolean = checkAccesses(writersOf(mem)){(a,b) =>
    if (areConcurrent(a,b)) {new ConcurrentWritersError(mem, a.node, b.node); true } else false
  }
  @internal def checkPipelinedReaders(mem: Exp[_]): Boolean = checkAccesses(readersOf(mem)){(a,b) =>
    if (arePipelined(a,b)) {new PipelinedReadersError(mem, a.node, b.node); true } else false
  }
  @internal def checkPipelinedWriters(mem: Exp[_]): Boolean = checkAccesses(writersOf(mem)){(a,b) =>
    if (arePipelined(a,b)) {new PipelinedWritersError(mem, a.node, b.node); true } else false
  }
  @internal def checkMultipleReaders(mem: Exp[_]): Boolean = if (readersOf(mem).length > 1) {
    new MultipleReadersError(mem, readersOf(mem).map(_.node)); true
  } else false
  @internal def checkMultipleWriters(mem: Exp[_]): Boolean = if (writersOf(mem).length > 1) {
    new MultipleWritersError(mem, writersOf(mem).map(_.node)); true
  } else false

  /*def checkConcurrentReadWrite(mem: Exp[_]): Boolean = {
    val hasConcurrent = writersOf(mem).exists{writer =>
      readersOf(mem).exists{reader =>
        if (areConcurrent(writer, reader)) {
          warn(mem.ctx, u"Memory $mem appears to have a concurrent read and write")
          warn(reader.ctx, u"Read defined here")
          warn(writer.ctx, u"Write defined here")
        }
      }
    }
  }*/

  /**
    * Calculate delay line costs:
    * a. Determine time (in cycles) any given input or internal signal needs to be delayed
    * b. Distinguish each delay line as a separate entity
    *
    * Is there a concise equation that can capture this? Haven't been able to come up with one.
    * E.g.
    *   8 inputs => perfectly balanced binary tree, no delay paths
    *   9 inputs => 1 path of length 3
    *   85 inputs => 3 paths with lengths 2, 1, and 1
    **/
  def reductionTreeDelays(nLeaves: Int): List[Long] = {
    if ( (nLeaves & (nLeaves - 1)) == 0) Nil // Specialize for powers of 2
    // Could also have 2^k + 1 case (delay = 1 path of length k)
    else {
      def reduceLevel(nNodes: Int, completePaths: List[Long], currentPath: Long): List[Long] = {
        if (nNodes <= 1) completePaths  // Stop when 1 node is remaining
        else if (nNodes % 2 == 0) {
          // For an even number of nodes, we don't need any delays - all current delay paths end
          val allPaths = completePaths ++ (if (currentPath > 0) List(currentPath) else Nil)
          reduceLevel(nNodes/2, allPaths, 0L)
        }
        // For odd number of nodes, always delay exactly one signal, and keep delaying that signal until it can be used
        else reduceLevel((nNodes-1)/2 + 1, completePaths, currentPath+1)
      }

      reduceLevel(nLeaves, Nil, 0L)
    }
  }

  @stateful def delayLineTrace(x: Exp[_]): Exp[_] = x match {
    case Def(DelayLine(_,xx)) => delayLineTrace(xx)
    case _ => x
  }

  def reductionTreeHeight(nLeaves: Int): Int = {
    def treeLevel(nNodes: Int, curHeight: Int): Int = {
      if (nNodes <= 1) curHeight
      else if (nNodes % 2 == 0) treeLevel(nNodes/2, curHeight + 1)
      else treeLevel((nNodes - 1)/2 + 1, curHeight + 1)
    }
    treeLevel(nLeaves, 0)
  }

  def mirrorCtrl(x: Ctrl, f: Transformer): Ctrl = (f(x.node), x.block)
  def mirrorAccess(x: Access, f: Transformer): Access = (f(x.node), mirrorCtrl(x.ctrl, f))
  def mirrorStreamInfo(x: StreamInfo, f: Transformer): StreamInfo = (f(x.memory), f(x.access))

  /** Parallelization factors **/
  @internal def parFactorsOf(x: Exp[_]): Seq[Const[Index]] = x match {
    case Op(CounterNew(start,end,step,par)) => List(par)
    case Op(Forever())             => List(int32s(1))
    case Op(CounterChainNew(ctrs)) => ctrs.flatMap{ctr => parFactorsOf(ctr) }
    case Op(e: DenseTransfer[_,_]) => Seq(e.p)
    case Op(e: SparseTransfer[_])  => Seq(e.p)
    case _ => Nil
  }
  @internal def parsOf(x: Exp[_]): Seq[Int] = parFactorsOf(x).map{case Exact(p: BigInt) => p.toInt }

  @internal def extractParFactor(par: Option[Index]): Const[Index] = par.map(_.s) match {
    case Some(x: Const[_]) if isIndexType(x.tp) => x.asInstanceOf[Const[Index]]
    case None => intParam(1)
    case Some(x) =>
      new spatial.InvalidParallelFactorError(x)(ctx, state)
      intParam(1)
  }

  /** Control Nodes **/
  implicit class CtrlOps(x: Ctrl) {
    def node: Exp[_] = if (x == null) null else x._1
    def block: Int = if (x == null) -1 else x._2
    @stateful def isInner: Boolean = if (x == null || block < 0) false else node match {
      case Op(OpReduce(_,_,_,map,ld,reduce,store,_,_,_,_)) if isInnerControl(node) => true
      case Op(_:OpReduce[_]) if isOuterControl(node) => true
      case Op(OpMemReduce(_,_,_,_,map,ldRes,ldAcc,reduce,stAcc,_,_,_,_,_)) if isInnerControl(node) => true
      case Op(_:OpMemReduce[_,_]) if isOuterControl(node) => block == 0
      case Op(StateMachine(_,_,notdone,action,nextState,_)) if isInnerControl(node) => true
      case Op(_:StateMachine[_]) => block == 0 || block == 1
      case _ => isInnerControl(node)
    }
  }

  /**
    * Map a given compiler block number to the logical child number
    *
    *   Inner Reduce: Everything eventually becomes one logical stage
    *   Outer Reduce: The map is part of the initialization (and has other children inside), the reduce is a separate stage
    *   Inner MemReduce: Doesn't usually happen, but would be two stages
    *   Outer MemReduce: Map is part of initialization (and has other children), the reduce is a separate stage
    *
    *   When -1 is used, this is an outer scope block which shouldn't contain primitives (only stateless logic)
    **/
  @stateful def blockCountRemap(e: Exp[_], blockNum: Int): Int = if (blockNum < 0) blockNum else e match {
    case Op(_:OpReduce[_]) if isInnerControl(e) => blockNum match {
      case 0 | 1 | 2 | 3 => 0
    }
    case Op(_:OpReduce[_]) if isOuterControl(e) => blockNum match {
      case 0 => -1
      case 1 | 2 | 3 => 0
    }
    case Op(_:OpMemReduce[_,_]) if isInnerControl(e) => blockNum match {
      case 0 => 0
      case 1 | 2 | 3 | 4 => 1
    }
    case Op(_:OpMemReduce[_,_]) if isOuterControl(e) => blockNum match {
      case 0 => -1
      case 1 | 2 | 3 | 4 => 0
    }
    case Op(_:StateMachine[_]) if isInnerControl(e) => blockNum
    case Op(_:StateMachine[_]) if isOuterControl(e) => blockNum match {
      case 0 => 0
      case 1 => -1
      case 2 => 1
    }
    case _ if isInnerControl(e) => blockNum
    case _ if isOuterControl(e) => -1
  }
  @stateful def blkToCtrl(block: Blk): Ctrl = (block.node, blockCountRemap(block.node, block.block))



  @stateful def addImplicitChildren(x: Ctrl, children: List[Ctrl]): List[Ctrl] = x.node match {
    case Op(_:OpReduce[_]) if isInnerControl(x) => children ++ List((x.node,0)) // children should be Nil
    case Op(_:OpReduce[_]) if isOuterControl(x) => children ++ List((x.node,0))
    case Op(_:OpMemReduce[_,_]) if isInnerControl(x) => children ++ List((x.node,0), (x.node,1)) // children should be Nil
    case Op(_:OpMemReduce[_,_]) if isOuterControl(x) => children ++ List((x.node,0))
    case Op(_:StateMachine[_])  if isInnerControl(x) => List((x.node,0), (x.node,1), (x.node,2)) // children should be Nil
    case Op(_:StateMachine[_])  if isOuterControl(x) => List((x.node,0)) ++ children ++ List((x.node,1))
    case _ if isInnerControl(x) => children ++ List((x.node,0)) // children should be Nil
    case _ if isOuterControl(x) => children
  }

  @stateful def loopCounters(e: Exp[_]): Seq[Exp[CounterChain]] = getDef(e).map{d => d.nonBlockInputs.collect{
    case e: Exp[_] if e.tp == CounterChainType => e.asInstanceOf[Exp[CounterChain]]
  }}.getOrElse(Nil)

  @stateful def willBeFullyUnrolled(e: Exp[_]): Boolean = e match {
    case Def(d:OpReduce[_]) => canFullyUnroll(d.cchain)
    case Def(d:OpForeach) => canFullyUnroll(d.cchain)
    case _ => false
  }

  @stateful def isOuterControl(e: Exp[_]): Boolean = isControlNode(e) && levelOf(e) == OuterControl
  @stateful def isInnerControl(e: Exp[_]): Boolean = isControlNode(e) && levelOf(e) == InnerControl
  @stateful def isPrimitiveControl(e: Exp[_]): Boolean = (isSwitch(e) || isSwitchCase(e)) && levelOf(e) == InnerControl

  @stateful def isOuterPipeline(e: Exp[_]): Boolean = isOuterControl(e) && isPipeline(e)
  @stateful def isInnerPipeline(e: Exp[_]): Boolean = isInnerControl(e) && isPipeline(e)

  @stateful def isOuterControl(e: Ctrl): Boolean = !e.isInner && isOuterControl(e.node)
  @stateful def isInnerControl(e: Ctrl): Boolean = e.isInner || isInnerControl(e.node)
  @stateful def isInnerPipeline(e: Ctrl): Boolean = e.isInner || isInnerPipeline(e.node)

  @stateful def isInnerPipe(e: Exp[_]): Boolean = styleOf(e) == InnerPipe || (styleOf(e) == MetaPipe && isInnerControl(e))
  @stateful def isInnerPipe(e: Ctrl): Boolean = e.isInner || isInnerPipe(e.node)
  @stateful def isMetaPipe(e: Exp[_]): Boolean = styleOf(e) == MetaPipe && !willBeFullyUnrolled(e) // Fully unrolled doesn't need pipelining
  @stateful def isSeqPipe(e: Exp[_]): Boolean = styleOf(e) == SeqPipe
  @stateful def isStreamPipe(e: Exp[_]): Boolean = e match {
    case Def(Hwblock(_,isFrvr)) => isFrvr
    case _ => styleOf(e) == StreamPipe
  }
  @stateful def isMetaPipe(e: Ctrl): Boolean = !e.isInner && isMetaPipe(e.node)
  @stateful def isStreamPipe(e: Ctrl): Boolean = !e.isInner && isStreamPipe(e.node)

  @stateful def isInnerSwitch(e: Exp[_]): Boolean = isInnerControl(e) && isSwitch(e)

  @stateful def isSwitch(e: Exp[_]): Boolean = getDef(e).exists(isSwitch)
  def isSwitch(d: Def): Boolean = d.isInstanceOf[Switch[_]]

  @stateful def isSwitchCase(e: Exp[_]): Boolean = getDef(e).exists(isSwitchCase)
  def isSwitchCase(d: Def): Boolean = d.isInstanceOf[SwitchCase[_]]

  @stateful def isUnitPipe(e: Exp[_]): Boolean = getDef(e).exists(isUnitPipe)
  def isUnitPipe(d: Def): Boolean = d.isInstanceOf[UnitPipe]

  @stateful def isIfThenElse(e: Exp[_]): Boolean = getDef(e).exists(isIfThenElse)
  def isIfThenElse(d: Def): Boolean = d.isInstanceOf[IfThenElse[_]]

  @stateful def isControlNode(e: Exp[_]): Boolean = getDef(e).exists(isControlNode)
  def isControlNode(d: Def): Boolean = d.isInstanceOf[ControlNode[_]]

  @stateful def isDRAMTransfer(e: Exp[_]): Boolean = getDef(e).exists(isDRAMTransfer)
  def isDRAMTransfer(d: Def): Boolean = d.isInstanceOf[DRAMTransfer]

  @stateful def isPipeline(e: Exp[_]): Boolean = getDef(e).exists(isPipeline)
  def isPipeline(d: Def): Boolean = d.isInstanceOf[Pipeline]

  @stateful def isLoop(e: Exp[_]): Boolean = getDef(e).exists(isLoop)
  def isLoop(d: Def): Boolean = d.isInstanceOf[Loop]



  /** Determines if a given controller is forever or has any children that are **/
  @stateful def willRunForever(e: Exp[_]): Boolean = getDef(e).exists(isForever) || childrenOf(e).exists(willRunForever)

  /** Determines if just the given node is forever (has Forever counter) **/
  @stateful def isForever(e: Exp[_]): Boolean = getDef(e).exists(isForever)
  @stateful def isForever(d: Def): Boolean = d match {
    case _: Forever             => true
    case CounterChainNew(ctrs)  => ctrs.exists(isForever)
    case e: Hwblock             => e.isForever
    case e: OpForeach           => isForever(e.cchain)
    case e: OpReduce[_]         => isForever(e.cchain)
    case e: OpMemReduce[_,_]    => isForever(e.cchainMap) // This should probably never happen?
    case e: UnrolledForeach     => isForever(e.cchain)
    case e: UnrolledReduce[_,_] => isForever(e.cchain)
    case _ => false
  }

  @stateful def isParallel(e: Exp[_]): Boolean = getDef(e).exists(isParallel)
  def isParallel(d: Def): Boolean = d.isInstanceOf[ParallelPipe]

  @stateful def isFringeNode(e: Exp[_]): Boolean = getDef(e).exists(isFringeNode)
  def isFringeNode(d: Def): Boolean = d.isInstanceOf[FringeNode[_]]

  /** Counters **/
  @stateful def isUnitCounter(x: Exp[Counter]): Boolean = x match {
    case Op(CounterNew(Const(0), Const(1), Const(1), _)) => true
    case _ => false
  }

  @stateful def countersOf(x: Exp[CounterChain]): Seq[Exp[Counter]] = x match {
    case Op(CounterChainNew(ctrs)) => ctrs
    case _ => Nil
  }

  @stateful def counterStarts(x: Exp[CounterChain]): Seq[Option[Exp[Index]]] = countersOf(x) map {
    case Def(CounterNew(start,_,_,_)) => Some(start)
    case _ => None
  }

  @stateful def canFullyUnroll(cc: Exp[CounterChain]): Boolean = countersOf(cc).forall{
    case Def(CounterNew(Exact(start),Exact(end),Exact(stride),Exact(par))) =>
      val nIters = (BigDecimal(end) - BigDecimal(start))/BigDecimal(stride)
      BigDecimal(par) >= nIters
    case _ => false
  }

  @stateful def isUnitCounterChain(x: Exp[CounterChain]): Boolean = countersOf(x).forall(isUnitCounter)

  /** Registers **/
  @stateful def isArgIn(x: Exp[_]): Boolean = getDef(x).exists{case ArgInNew(_) => true; case _ => false }
  @stateful def isArgOut(x: Exp[_]): Boolean = getDef(x).exists{case ArgOutNew(_) => true; case _ => false }
  @stateful def isHostIO(x: Exp[_]): Boolean = getDef(x).exists{case HostIONew(_) => true; case _ => false }

  @stateful def resetValue[T](x: Exp[Reg[T]]): Exp[T] = x match {
    case Op(RegNew(init))    => init
    case Op(ArgInNew(init))  => init
    case Op(ArgOutNew(init)) => init
    case Op(HostIONew(init)) => init
  }

  /** Allocations **/
  @stateful def stagedDimsOf(x: Exp[_]): Seq[Exp[Index]] = x match {
    // Hack for making memory analysis code easier
    case Def(ArgOutNew(_)) =>
      implicit val ctx: SrcCtx = x.ctx
      Seq(int32s(1))
    case Def(ArgInNew(_))  =>
      implicit val ctx: SrcCtx = x.ctx
      Seq(int32s(1))
    case Def(HostIONew(_)) =>
      implicit val ctx: SrcCtx = x.ctx
      Seq(int32s(1))
    case Def(RegNew(_))    =>
      implicit val ctx: SrcCtx = x.ctx
      Seq(int32s(1))
    case Def(BufferedOutNew(dims,_)) => dims
    case Def(LUTNew(dims,_)) =>
      implicit val ctx: SrcCtx = x.ctx
      dims.map{d => int32s(d) }
    case Def(SRAMNew(dims)) => dims
    case Def(DRAMNew(dims,_)) => dims
    case Def(LineBufferNew(rows,cols,stride)) => Seq(rows, cols)
    case Def(RegFileNew(dims,_)) => dims
    case Def(FIFONew(size)) => Seq(size)
    case Def(FILONew(size)) => Seq(size)
    case _ => throw new spatial.UndefinedDimensionsException(x, None)(x.ctx, state)
  }

  @stateful def constDimsOf(x: Exp[_]): Seq[Int] = stagedDimsOf(x).map{
    case Exact(c) => c.toInt
    case dim => throw new spatial.UndefinedDimensionsException(x, Some(dim))(x.ctx, state)
  }

  @stateful def stagedSizeOf(fifo: FIFO[_]): Index = wrap(stagedSizeOf(fifo.s))
  @stateful def stagedSizeOf(fifo: FILO[_]): Index = wrap(stagedSizeOf(fifo.s))
  @stateful def stagedSizeOf(x: Exp[_]): Exp[Index] = x match {
    case Def(FIFONew(size)) => size
    case Def(FILONew(size)) => size
    case _ => throw new spatial.UndefinedDimensionsException(x, None)(x.ctx, state)
  }
  @stateful def constSizeOf(x: Exp[_]): Int = stagedSizeOf(x) match {
    case Literal(c) => c.toInt
    case Exact(c) => c.toInt
    case _ => throw new spatial.UndefinedDimensionsException(x, None)(x.ctx, state)
  }

  @stateful def lenOf(x: Exp[_]): Int = x.tp match {
    case tp: VectorType[_] => tp.width
    case _ => throw new spatial.UndefinedDimensionsException(x, None)(x.ctx, state)
  }

  @stateful def rankOf(x: Exp[_]): Int = constDimsOf(x).length
  @stateful def rankOf(x: MetaAny[_]): Int = rankOf(x.s)

  @stateful def isAllocation(e: Exp[_]): Boolean = getDef(e).exists(isAllocation)
  def isAllocation(d: Def): Boolean = d.isInstanceOf[Alloc[_]] || isDynamicAllocation(d)

  // Allocations which can depend on local, dynamic values
  @stateful def isDynamicAllocation(e: Exp[_]): Boolean = getDef(e).exists(isDynamicAllocation)
  def isDynamicAllocation(d: Def): Boolean = d.isInstanceOf[DynamicAlloc[_]] || isPrimitiveAllocation(d)

  // Dynamic allocations which can be directly used in primitive logic
  @stateful def isPrimitiveAllocation(e: Exp[_]): Boolean = getDef(e).exists(isPrimitiveAllocation)
  def isPrimitiveAllocation(d: Def): Boolean = d.isInstanceOf[StructAlloc[_]] || d.isInstanceOf[PrimitiveAlloc[_]]

  def isDRAM(e: Exp[_]): Boolean = e.tp.isInstanceOf[DRAMType[_]]
  def isFIFO(e: Exp[_]): Boolean = e.tp.isInstanceOf[FIFOType[_]]
  def isFILO(e: Exp[_]): Boolean = e.tp.isInstanceOf[FILOType[_]]
  def isLUT(e: Exp[_]): Boolean  = e.tp.isInstanceOf[LUTType[_]]
  def isSRAM(e: Exp[_]): Boolean = e.tp.isInstanceOf[SRAMType[_]]
  def isReg(e: Exp[_]): Boolean  = e.tp.isInstanceOf[RegType[_]]
  def isRegFile(e: Exp[_]): Boolean = e.tp.isInstanceOf[RegFileType[_]]
  def isLineBuffer(e: Exp[_]): Boolean = e.tp.isInstanceOf[LineBufferType[_]]
  def isStreamIn(e: Exp[_]): Boolean = e.tp.isInstanceOf[StreamInType[_]]
  def isStreamOut(e: Exp[_]): Boolean = e.tp.isInstanceOf[StreamOutType[_]] || e.tp.isInstanceOf[BufferedOutType[_]]
  def isBufferedOut(e: Exp[_]): Boolean = e.tp.isInstanceOf[BufferedOutType[_]]
  def isStream(e: Exp[_]): Boolean = isStreamIn(e) || isStreamOut(e)
  def isVector(e:Exp[_]): Boolean = e.tp.isInstanceOf[VectorType[_]]

  @stateful def isHostIn(e: Exp[_]): Boolean = isHostIO(e) && writersOf(e).isEmpty
  @stateful def isHostOut(e: Exp[_]): Boolean = isHostIO(e) && readersOf(e).isEmpty

  @stateful def isStreamLoad(e: Exp[_]): Boolean = getDef(e).exists(_.isInstanceOf[FringeDenseLoad[_]])

  @stateful def isTileTransfer(e: Exp[_]): Boolean = e match {
    case Def(_:FringeDenseLoad[_]) => true
    case Def(_:FringeDenseStore[_]) => true
    case Def(_:FringeSparseLoad[_]) => true
    case Def(_:FringeSparseStore[_]) => true
    case _ => false
  }

  @stateful def isParEnq(e: Exp[_]): Boolean = e match {
    case Def(_:ParFIFOEnq[_]) => true
    case Def(_:ParFILOPush[_]) => true
    case Def(_:ParSRAMStore[_]) => true
    case Def(_:FIFOEnq[_]) => true
    case Def(_:FILOPush[_]) => true
    case Def(_:SRAMStore[_]) => true
    case Def(_:ParLineBufferEnq[_]) => true
    case _ => false
  }

  @stateful def isStreamStageEnabler(e: Exp[_]): Boolean = e match {
    case Def(_:FIFODeq[_]) => true
    case Def(_:ParFIFODeq[_]) => true
    case Def(_:FILOPop[_]) => true
    case Def(_:ParFILOPop[_]) => true
    case Def(_:StreamRead[_]) => true
    case Def(_:ParStreamRead[_]) => true
    case Def(_:DecoderTemplateNew[_]) => true
    case Def(_:DMATemplateNew[_]) => true
    case _ => false
  }

  @stateful def isStreamStageHolder(e: Exp[_]): Boolean = e match {
    case Def(_:FIFOEnq[_]) => true
    case Def(_:ParFIFOEnq[_]) => true
    case Def(_:FILOPush[_]) => true
    case Def(_:ParFILOPush[_]) => true
    case Def(_:StreamWrite[_]) => true
    case Def(_:ParStreamWrite[_]) => true
    case Def(_:BufferedOutWrite[_]) => true
    case Def(_:DecoderTemplateNew[_]) => true
    case _ => false
  }

  @stateful def isLocalMemory(e: Exp[_]): Boolean = e.tp match {
    case _:SRAMType[_] | _:FIFOType[_] | _:FILOType[_] | _:RegType[_] | _:LineBufferType[_] | _:RegFileType[_] => true
    case _:LUTType[_] => true
    case _:StreamInType[_]  => true
    case _:StreamOutType[_] => true
    case _:BufferedOutType[_] => true
    case _ => false
  }

  @stateful def isOffChipMemory(e: Exp[_]): Boolean = e.tp match {
    case _:DRAMType[_]        => true
    case _:StreamInType[_]    => true
    case _:StreamOutType[_]   => true
    case _:BufferedOutType[_] => true
    case _:RegType[_]         => isArgIn(e) || isArgOut(e) || isHostIO(e)
    case _ => false
  }
  @stateful def isInternalStreamMemory(e: Exp[_]): Boolean = e.tp match { // For finding the streams generated from tile transfers
    case _:DRAMType[_]        => false
    case _:StreamInType[_]    => if (parentOf(e).isDefined) true else false
    case _:StreamOutType[_]   => if (parentOf(e).isDefined) true else false
    case _:BufferedOutType[_] => true
    case _:RegType[_]         => false
    case _ => false
  }



  @stateful def isFringe(e:Exp[_]): Boolean = getDef(e).exists(isFringe)
  def isFringe(d:Def): Boolean = d.isInstanceOf[FringeNode[_]]

  /** Host Transfer **/
  @stateful def isTransfer(e: Exp[_]): Boolean = isTransferToHost(e) || isTransferFromHost(e)

  @stateful def isTransferToHost(e: Exp[_]): Boolean = getDef(e).exists(isTransferToHost)
  def isTransferToHost(d: Def): Boolean = d match {
    case _: GetMem[_] => true
    case _: GetArg[_] => true
    case _ => false
  }

  @stateful def isTransferFromHost(e: Exp[_]): Boolean = getDef(e).exists(isTransferFromHost)
  def isTransferFromHost(d: Def): Boolean = d match {
    case _: SetMem[_] => true
    case _: SetArg[_] => true
    case _ => false
  }

  /** Stateless Nodes **/
  @stateful def isRegisterRead(e: Exp[_]): Boolean = getDef(e).exists(isRegisterRead)
  def isRegisterRead(d: Def): Boolean = d.isInstanceOf[RegRead[_]] || d.isInstanceOf[VarRegRead[_]]

  // Nodes which operate on primitives but are allowed to appear outside inner controllers
  // Register reads are considered to be "stateless" because the read is itself akin to creating a wire
  // attached to the output of a register, not to the register itself
  @stateful def isStateless(e: Exp[_]): Boolean = getDef(e).exists(isStateless)
  def isStateless(d: Def): Boolean = isRegisterRead(d) || isDynamicAllocation(d)

  /** Primitive Nodes **/
  @stateful def isPrimitiveNode(e: Exp[_]): Boolean = e match {
    case Const(_) => false
    case Param(_) => false
    case _        => !isControlNode(e) && !isAllocation(e) && !isStateless(e) && !isGlobal(e) && !isFringeNode(e)
  }

  @stateful def isNestedPrimitive(e: Exp[_]): Boolean = (isSwitch(e) || isSwitchCase(e)) && isInnerControl(e)

  /** Accesses **/
  implicit class AccessOps(x: Access) {
    def node: Exp[_] = x._1
    def ctrl: Ctrl = x._2 // read or write enabler
    def ctrlNode: Exp[_] = x._2._1 // buffer control toggler
    def ctrlBlock: Int = x._2._2
    @stateful def isInner: Boolean = x._2.isInner
  }

  implicit class StreamInfoOps(x: StreamInfo) {
    def memory: Exp[_] = x._1
    def access: Exp[_] = x._2
  }

  implicit class PortMapOps(x: PortMap) {
    def memId: Int = x._1
    def argInId: Int = x._2
    def argOutId: Int = x._3
  }

  // Memory, optional value, optional indices, optional enable
  type LocalWrite = (Exp[_], Option[Exp[_]], Option[Seq[Exp[Index]]], Option[Exp[Bit]])
  implicit class LocalWriteOps(x: LocalWrite) {
    def mem = x._1
    def data = x._2
    def addr = x._3
    def en = x._4
  }

  // Memory, optional indices, optional enable
  type LocalRead = (Exp[_], Option[Seq[Exp[Index]]], Option[Exp[Bit]])
  implicit class LocalReadOps(x: LocalRead) {
    def mem = x._1
    def addr = x._2
    def en = x._3
  }

  type LocalReset = (Exp[_], Option[Exp[Bit]])
  implicit class LocalResetOps(x: LocalReset) {
    def mem = x._1
    def en = x._2
  }

  object LocalWrite {
    def apply(mem: Exp[_]): List[LocalWrite] = List( (mem, None, None, None) )
    def apply(mem: Exp[_], value: Exp[_] = null, addr: Seq[Exp[Index]] = null, en: Exp[Bit] = null) = {
      List( (mem, Option(value), Option(addr), Option(en)) )
    }
  }

  object LocalRead {
    def apply(mem: Exp[_]): List[LocalRead] = List( (mem, None, None) )
    def apply(mem: Exp[_], addr: Seq[Exp[Index]] = null, en: Exp[Bit] = null): List[LocalRead] = {
      List( (mem, Option(addr), Option(en)) )
    }
  }

  object LocalReset {
    def apply(mem: Exp[_]): List[LocalReset] = List( (mem, None) )
    def apply(mem: Exp[_], en: Exp[Bit] = null): List[LocalReset] = {
      List( (mem, Option(en)) )
    }
  }

  // Memory, optional value, optional indices, optional enable
  type ParLocalWrite = (Exp[_], Option[Seq[Exp[_]]], Option[Seq[Seq[Exp[Index]]]], Option[Seq[Exp[Bit]]])
  implicit class ParLocalWriteOps(x: ParLocalWrite) {
    def mem = x._1
    def data = x._2
    def addrs = x._3
    def ens = x._4
  }

  // Memory, optional indices, optional enable
  type ParLocalRead = (Exp[_], Option[Seq[Seq[Exp[Index]]]], Option[Seq[Exp[Bit]]])
  implicit class ParLocalReadOps(x: ParLocalRead) {
    def mem = x._1
    def addrs = x._2
    def ens = x._3
  }

  object ParLocalWrite {
    def apply(mem: Exp[_]): List[ParLocalWrite] = List( (mem, None, None, None) )
    def apply(mem: Exp[_], values: Seq[Exp[_]] = null, addrs: Seq[Seq[Exp[Index]]] = null, ens: Seq[Exp[Bit]] = null) = {
      List( (mem, Option(values), Option(addrs), Option(ens)) )
    }
  }
  object ParLocalRead {
    def apply(mem: Exp[_]): List[ParLocalRead] = List( (mem, None, None) )
    def apply(mem: Exp[_], addrs: Seq[Seq[Exp[Index]]] = null, ens: Seq[Exp[Bit]] = null): List[ParLocalRead] = {
      List( (mem, Option(addrs), Option(ens)) )
    }
  }

  @stateful def isAccess(x: Exp[_]): Boolean = isReader(x) || isWriter(x)

  @stateful def isReader(x: Exp[_]): Boolean = LocalReader.unapply(x).isDefined
  def isReader(d: Def): Boolean = LocalReader.unapply(d).isDefined

  @stateful def isWriter(x: Exp[_]): Boolean = LocalWriter.unapply(x).isDefined
  def isWriter(d: Def): Boolean = LocalWriter.unapply(d).isDefined

  @stateful def isReadModify(x: Exp[_]): Boolean = LocalReadModify.unapply(x).isDefined
  def isReadModify(d: Def): Boolean = LocalReadModify.unapply(d).isDefined

  @stateful def isResetter(x: Exp[_]): Boolean = LocalResetter.unapply(x).isDefined
  def isResetter(d: Def): Boolean = LocalResetter.unapply(d).isDefined
  /*@stateful def getAccess(x:Exp[_]):Option[Access] = x match {
    case LocalReader(reads) =>
      val ras = reads.flatMap{ case (mem, _, _) => readersOf(mem).filter { _.node == x } }
      assert(ras.size==1)
      Some(ras.head)
    case LocalWriter(writes) =>
      val was = writes.flatMap{ case (mem, _, _, _) => writersOf(mem).filter {_.node == x} }
      assert(was.size==1)
      Some(was.head)
    case LocalResetter(resetters) =>
      val ras = resetters.flatMap{ case (mem, _) => resettersOf(mem).filter {_.node == x} }
      assert(ras.size==1)
      Some(ras.head)
    case _ => None
  }*/

  @stateful def isAccessWithoutAddress(e: Exp[_]): Boolean = e match {
    case LocalReader(reads) => reads.exists(_.addr.isEmpty)
    case LocalWriter(write) => write.exists(_.addr.isEmpty)
    case ParLocalReader(reads) => reads.exists(_.addrs.isEmpty)
    case ParLocalWriter(write) => write.exists(_.addrs.isEmpty)
    case _ => false
  }

  @stateful def accessWidth(e: Access): Int = accessWidth(e.node)
  @stateful def accessWidth(e: Exp[_]): Int = e match {
    case Def(e: EnabledAccess[_]) => e.accessWidth
    case _ => e.tp match {
      case t: VectorType[_] => t.width
      case _ => 1
    }
  }

}
