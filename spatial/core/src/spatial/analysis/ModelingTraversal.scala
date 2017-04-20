package spatial.analysis

import spatial.models.LatencyModel

import scala.collection.mutable

trait ModelingTraversal extends SpatialTraversal { traversal =>
  import IR._

  lazy val latencyModel = new LatencyModel{val IR: traversal.IR.type = traversal.IR }

  protected override def preprocess[S: Type](block: Block[S]) = {
    // latencyOf.updateModel(target.latencyModel) // TODO: Update latency model with target-specific values
    inHwScope = false
    inReduce = false
    super.preprocess(block)
  }

  // --- State
  var inHwScope = false // In hardware scope
  var inReduce = false  // In tight reduction cycle (accumulator update)
  def latencyOf(e: Exp[_]) = if (inHwScope) latencyModel(e, inReduce) else 0L

  // TODO: Could optimize further with dynamic programming
  def latencyOfPipe(b: Block[_]): Long = {
    val scope = getStages(b)
    val paths = mutable.HashMap[Exp[_],Long]()
    //debug(s"Pipe latency $b:")

    def quickDFS(cur: Exp[_]): Long = cur match {
      case Def(d) if scope.contains(cur) && !isGlobal(cur) =>
        //debug(s"Visit $cur in quickDFS")
        val deps = syms(d)
        if (deps.isEmpty) {
          warn(cur.ctx, s"$cur = $d has no dependencies but is not global")
          latencyOf(cur)
        }
        else {
          latencyOf(cur) + deps.map{e => paths.getOrElseUpdate(e, quickDFS(e))}.max
        }
      case _ => 0L
    }
    if (scope.isEmpty) 0L else syms(b).map{e => paths.getOrElseUpdate(e, quickDFS(e)) }.max
  }
  def latencyOfCycle(b: Block[Any]): Long = {
    val outerReduce = inReduce
    inReduce = true
    val out = latencyOfPipe(b)
    inReduce = outerReduce
    out
  }

  // Not a true traversal. Should it be?
  def pipeDelays(b: Block[_], oos: Map[Exp[_],Long] = Map.empty): List[(Exp[_],Long)] = {
    val scope = getStages(b).filterNot(s => isGlobal(s))
    val delays = mutable.HashMap[Exp[_],Long]() ++ scope.map{node => node -> 0L}
    val paths  = mutable.HashMap[Exp[_],Long]() ++ oos

    def fullDFS(cur: Exp[_]): Long = cur match {
      case Def(d) if scope.contains(cur) =>
        val deps = syms(d) filter (scope contains _)

        if (deps.nonEmpty) {
          val dlys = deps.map{e => paths.getOrElseUpdate(e, fullDFS(e)) }
          val critical = dlys.max

          deps.zip(dlys).foreach{ case(dep, path) =>
            if (path < critical && (critical - path) > delays(dep))
              delays(dep) = critical - path
          }
          critical + latencyOf(cur)
        }
        else latencyOf(cur)

      case s => paths.getOrElse(s, 0L) // Get preset out of scope delay
      // Otherwise assume 0 offset
    }
    if (scope.nonEmpty) {
      val deps = syms(b) filter (scope contains _)
      deps.foreach{e => paths.getOrElseUpdate(e, fullDFS(e)) }
    }
    delays.toList
  }

}

