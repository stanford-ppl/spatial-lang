package spatial.analysis

import spatial._
import forge._

trait NodeUtils { this: SpatialExp =>

  /**
    * Least common multiple of two integers (smallest integer which has integer divisors a and b)
    */
  def lcm(a: Int, b: Int) = {
    val bigA = BigInt(a)
    val bigB = BigInt(b)
    (bigA*bigB / bigA.gcd(bigB)).intValue()
  }

  /**
    * Checks to see if x depends on y (dataflow only, no scheduling dependencies)
    */
  // TODO: This uses the pointer-chasing version of scheduling - could possibly make faster?
  implicit class ExpOps(x: Exp[_]) {
    def dependsOn(y: Exp[_]): Boolean = {
      def dfs(frontier: Seq[Exp[_]]): Boolean = frontier.exists{
        case s if s == y => true
        case Def(d) => dfs(d.inputs)
        case _ => false
      }
      dfs(Seq(x))
    }
    def dependsOnType(y: PartialFunction[Exp[_],Boolean]): Boolean = {
      def dfs(frontier: Seq[Exp[_]]): Boolean = frontier.exists{
        case s if y.isDefinedAt(s) && y(s) => true
        case Def(d) => dfs(d.inputs)
        case _ => false
      }
      dfs(Seq(x))
    }
  }

  /**
    * Returns the least common ancestor of two nodes in some directed, acyclic graph.
    * If the nodes share no common parent at any point in the tree, the LCA is undefined (None).
    * Also returns the paths from the least common ancestor to each node.
    * The paths do not contain the LCA, as it may be undefined.
    */
  def leastCommonAncestorWithPaths[T](x: T, y: T, parent: T => Option[T]): (Option[T], List[T], List[T]) = {
    var pathX: List[Option[T]] = List(Some(x))
    var pathY: List[Option[T]] = List(Some(y))

    var curX: Option[T] = Some(x)
    while (curX.isDefined) { curX = parent(curX.get); pathX ::= curX }

    var curY: Option[T] = Some(y)
    while (curY.isDefined) { curY = parent(curY.get); pathY ::= curY }

    // Choose last node where paths are the same
    val lca = pathX.zip(pathY).filter{case (x,y) => x == y}.lastOption.flatMap(_._1)
    val pathToX = pathX.drop(pathX.indexOf(lca)+1).map(_.get)
    val pathToY = pathY.drop(pathY.indexOf(lca)+1).map(_.get)
    (lca,pathToX,pathToY)
  }

  def leastCommonAncestor[T](x: T, y: T, parent: T => Option[T]): Option[T] = {
    leastCommonAncestorWithPaths(x,y,parent)._1
  }

  def lca(a: Ctrl, b: Ctrl): Option[Ctrl] = leastCommonAncestor[Ctrl](a, b, {x => parentOf(x)})

  def dependenciesOf(a: Ctrl): Set[Ctrl] = {
    if (a.isInner) {
      val parent = parentOf(a).get
      val children = childrenOf(parent) filterNot (_ == a)
      val leaves = children.filter{x => !children.exists{child => dependenciesOf(child) contains x}}
      leaves.toSet
    }
    else ctrlDepsOf(a.node).map{node => (node,false) }
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
  def lcaWithDistance(a: Ctrl, b: Ctrl): (Ctrl, Int) = {
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

        // Linear version (using for now)
        val indexA = childrenOf(parent).indexOf(topA)
        val indexB = childrenOf(parent).indexOf(topB)
        if (indexA < 0 || indexB < 0) throw new UndefinedPipeDistanceException(a, b)
        val dist = indexB - indexA

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
  def lcaWithCoarseDistance(a: Access, b: Access): (Ctrl, Int) = {
    val (lca, dist) = lcaWithDistance(a.ctrl, b.ctrl)
    val coarseDistance = if (isMetaPipe(lca)) dist else 0
    (lca, coarseDistance)
  }

  /**
    * @return The child of the top controller which contains the given access.
    * Undefined if the access is not contained within a child of the top controller
    **/
  def childContaining(top: Ctrl, access: Access): Ctrl = {
    val child = access.ctrl
    val (lca, pathA, pathB) = leastCommonAncestorWithPaths[Ctrl](top,child, {node => parentOf(node)})

    if (pathB.isEmpty || lca.isEmpty || top != lca.get)
      throw new UndefinedChildException(top, access)

    pathB.head
  }

  /**
    * Returns metapipe controller for given accesses
    **/
  def findMetaPipe(mem: Exp[_], readers: Seq[Access], writers: Seq[Access]): (Option[Ctrl], Map[Access,Int]) = {
    val accesses = readers ++ writers
    assert(accesses.nonEmpty)

    val anchor = if (readers.nonEmpty) readers.head else writers.head

    val lcas = accesses.map{access =>
      val (lca,dist) = lcaWithCoarseDistance(anchor, access)

      (lca,dist,access)
    }
    // Find accesses which require n-buffering, group by their controller
    val metapipeLCAs = lcas.filter(_._2 != 0).groupBy(_._1).mapValues(_.map(_._3))

    // Hierarchical metapipelining is currently disallowed
    if (metapipeLCAs.keys.size > 1) throw new AmbiguousMetaPipeException(mem, metapipeLCAs)

    val metapipe = metapipeLCAs.keys.headOption

    val minDist = lcas.map(_._2).min

    // Port 0: First stage to write/read
    // Port X: X stage(s) after first stage
    val ports = Map(lcas.map{grp => grp._3 -> (grp._2 - minDist)}:_*)

    dbg("")
    dbg(c"  accesses: $accesses")
    dbg(c"  anchor: $anchor")
    lcas.foreach{case (lca,dist,access) => dbg(c"    lca($anchor, $access) = $lca ($dist)") }
    dbg(s"  metapipe: $metapipe")
    ports.foreach{case (access, port) => dbg(s"    - $access : port #$port")}

    (metapipe, ports)
  }



  /** Error checking methods **/

  def areConcurrent(a: Access, b: Access): Boolean = {
    val (top,dist) = lcaWithDistance(a.ctrl, b.ctrl)
    isInnerPipe(top) || isParallel(top.node)
  }
  def arePipelined(a: Access, b: Access): Boolean = {
    val top = lca(a.ctrl, b.ctrl).get
    isInnerPipe(top) || isMetaPipe(top) || isStreamPipe(top)
  }

  // O(N^2), but number of accesses is typically small
  def checkAccesses(access: List[Access])(func: (Access, Access) => Boolean): Boolean = {
    access.indices.exists {i =>
      (i+1 until access.length).exists{j =>
        access(i) != access(j) && func(access(i), access(j))
      }
    }
  }
  private[spatial] def checkConcurrentReaders(mem: Exp[_])(implicit ctx: SrcCtx): Boolean = checkAccesses(readersOf(mem)){(a,b) =>
    if (areConcurrent(a,b)) {new ConcurrentReadersError(mem, a.node, b.node); true } else false
  }
  private[spatial] def checkConcurrentWriters(mem: Exp[_])(implicit ctx: SrcCtx): Boolean = checkAccesses(writersOf(mem)){(a,b) =>
    if (areConcurrent(a,b)) {new ConcurrentWritersError(mem, a.node, b.node); true } else false
  }
  private[spatial] def checkPipelinedReaders(mem: Exp[_])(implicit ctx: SrcCtx): Boolean = checkAccesses(readersOf(mem)){(a,b) =>
    if (arePipelined(a,b)) {new PipelinedReadersError(mem, a.node, b.node); true } else false
  }
  private[spatial] def checkPipelinedWriters(mem: Exp[_])(implicit ctx: SrcCtx): Boolean = checkAccesses(writersOf(mem)){(a,b) =>
    if (arePipelined(a,b)) {new PipelinedWritersError(mem, a.node, b.node); true } else false
  }
  private[spatial] def checkMultipleReaders(mem: Exp[_])(implicit ctx: SrcCtx): Boolean = if (readersOf(mem).length > 1) {
    new MultipleReadersError(mem, readersOf(mem).map(_.node)); true
  } else false
  private[spatial] def checkMultipleWriters(mem: Exp[_])(implicit ctx: SrcCtx): Boolean = if (writersOf(mem).length > 1) {
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
   *    85 inputs => 3 paths with lengths 2, 1, and 1
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

  def reductionTreeHeight(nLeaves: Int): Int = {
    def treeLevel(nNodes: Int, curHeight: Int): Int = {
      if (nNodes <= 1) curHeight
      else if (nNodes % 2 == 0) treeLevel(nNodes/2, curHeight + 1)
      else treeLevel((nNodes - 1)/2 + 1, curHeight + 1)
    }
    treeLevel(nLeaves, 0)
  }



}
