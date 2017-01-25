package spatial.analysis

import org.virtualized.SourceContext
import spatial.SpatialExp

trait NodeUtils extends NodeClasses {
  this: SpatialExp =>

  /**
    * Least common multiple of two integers (smallest integer which has integer divisors a and b)
    */
  def lcm(a: Int, b: Int) = {
    val bigA = BigInt(a)
    val bigB = BigInt(b)
    (bigA*bigB / bigA.gcd(bigB)).intValue()
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
      val leaves = children.filter{x => children.exists{child => dependenciesOf(child) contains x}}
      leaves
    }
    else (getDef(a.node), parentOf(a)) match {
      case (None,_) => Set.empty
      case (_,None) => Set.empty
      case (Some(d), Some(parent)) =>
        val children = childrenOf(parent)
        val inputs = allInputs(d)
        children.filter{child => inputs contains child.node}
    }
  }

  /**
    * Pipeline distance between controllers a and b:
    * If a and b have a least common ancestor which is neither a nor b,
    * this is defined as the dataflow distance between the LCA's children which contain a and b
    * When a and b are equal, the distance is defined as zero.
    *
    * The distance is undefined when the LCA is a xor b
    * If a and b occur in parallel, the distance is -1
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
        def dfs(start: Ctrl, end: Ctrl, i: Int): Int = {
          if (start == end) i
          else dependenciesOf(start).map{dep => dfs(dep, end, i+1) }.fold(-1){(a,b) => Math.max(a,b)}
        }
        val aToB = dfs(topA, topB, 0)
        val bToA = dfs(topB, topA, 0)

        (parent, Math.max(aToB, bToA))
      }
      else (parent, 0)
    }
  }

  /**
    * Coarse-grained pipeline distance between accesses a and b.
    * If the LCA controller of a and b is a metapipeline, the pipeline distance
    * of the respective controllers for a and b. Otherwise zero.
    *
    * // TODO: How is this defined for streaming cases?
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
    //debug(s"  anchor = $anchor")

    val lcas = accesses.map{access =>
      val (lca,dist) = lcaWithCoarseDistance(anchor, access)
      //debug(s"    lca($anchor, $access) = $lca ($dist)")
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

    // debug(s"  metapipe = $metapipe")
    // ports.foreach{case (access, port) => debug(s"    - $access : port #$port")}

    (metapipe, ports)
  }



  /** Error checking methods **/

  def areConcurrent(a: Access, b: Access): Boolean = {
    val top = lca(a.ctrl, b.ctrl).get
    isInnerControl(top)
  }
  def arePipelined(a: Access, b: Access): Boolean = {
    val top = lca(a.ctrl, b.ctrl).get
    isMetaPipe(top) || isStreamPipe(top)
  }

  // O(N^2), but number of accesses is typically small
  def checkAccesses(access: List[Access])(func: (Access, Access) => Boolean): Boolean = {
    access.indices.exists {i =>
      (i+1 until access.length).exists{j =>
        access(i) != access(j) && func(access(i), access(j))
      }
    }
  }
  def checkConcurrentReaders(mem: Exp[_]): Boolean = checkAccesses(readersOf(mem)){(a,b) =>
    if (areConcurrent(a,b)) {new ConcurrentReadersError(mem, a.node, b.node); true } else false
  }
  def checkConcurrentWriters(mem: Exp[_]): Boolean = checkAccesses(writersOf(mem)){(a,b) =>
    if (areConcurrent(a,b)) {new ConcurrentWritersError(mem, a.node, b.node); true } else false
  }
  def checkPipelinedReaders(mem: Exp[_]): Boolean = checkAccesses(readersOf(mem)){(a,b) =>
    if (arePipelined(a,b)) {new PipelinedReadersError(mem, a.node, b.node); true } else false
  }
  def checkPipelinedWriters(mem: Exp[_]): Boolean = checkAccesses(writersOf(mem)){(a,b) =>
    if (arePipelined(a,b)) {new PipelinedWritersError(mem, a.node, b.node); true } else false
  }
  def checkMultipleReaders(mem: Exp[_]): Boolean = if (readersOf(mem).length > 1) {
    new MultipleReadersError(mem, readersOf(mem).map(_.node)); true
  } else false
  def checkMultipleWriters(mem: Exp[_]): Boolean = if (writersOf(mem).length > 1) {
    new MultipleWritersError(mem, writersOf(mem).map(_.node)); true
  } else false
}
