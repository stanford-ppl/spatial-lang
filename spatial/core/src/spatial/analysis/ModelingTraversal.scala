package spatial.analysis

import argon.core._
import argon.nodes._
import spatial.aliases._
import spatial.metadata._
import spatial.models.LatencyModel
import spatial.nodes._
import spatial.utils._

import scala.collection.mutable

trait ModelingTraversal extends SpatialTraversal { traversal =>
  val latencyModel: LatencyModel

  override def silence(): Unit = {
    latencyModel.silence()
    super.silence()
  }
  def init(): Unit = {
    latencyModel.init()
  }

  protected override def preprocess[S: Type](block: Block[S]): Block[S] = {
    inHwScope = false
    inReduce = false
    super.preprocess(block)
  }

  // --- State
  var inHwScope = false // In hardware scope
  var inReduce = false  // In tight reduction cycle (accumulator update)
  def latencyOf(e: Exp[_]): Long = {
    // HACK: For now, disable retiming in reduction cycles by making everything have 0 latency
    // This means everything will be purely combinational logic between the accumulator read and write
    val inReductionCycle = reduceType(e).isDefined
    if (inReductionCycle) 0L else {

      if (inHwScope) latencyModel(e, inReduce) else 0L
    }
  }

  def latencyAndInterval(block: Block[_]): (Long, Long) = {
    val (latencies, initInterval) = pipeLatencies(block)
    val scope = latencies.keySet
    val latency = latencies.values.fold(0L){(a,b) => Math.max(a,b) }
    // HACK: Set initiation interval to 1 if it contains a specialized reduction
    // This is a workaround for chisel codegen currently specializing and optimizing certain reduction types
    val compilerII = if (scope.exists(x => reduceType(x).contains(FixPtSum))) 1L else initInterval
    (latency, compilerII)
  }

  def latencyOfPipe(block: Block[_]): (Long, Long) = {
    val (latency, interval) = latencyAndInterval(block)
    (latency, interval)
  }

  def latencyOfCycle(b: Block[_]): (Long, Long) = {
    val outerReduce = inReduce
    inReduce = true
    val out = latencyOfPipe(b)
    inReduce = outerReduce
    out
  }

  implicit class GetOrElseUpdateFix[K,V](x: mutable.Map[K,V]) {
    def getOrElseAdd(k: K, v: => V): V = if (x.contains(k)) x(k) else { val value = v; x(k) = value; value }
  }

  def pipeLatencies(block: Block[_]): (Map[Exp[_],Long], Long) = {
    val (scope, result) = blockNestedScopeAndResult(block)
    pipeLatencies(result, scope)
  }

  def pipeLatencies(result: Seq[Exp[_]], scope: Set[Exp[_]], oos: Map[Exp[_],Long] = Map.empty): (Map[Exp[_],Long], Long) = {
    val localReads  = scope.collect{case reader @ LocalReader(reads) => reader -> reads.head.mem }
    val localWrites = scope.collect{case writer @ LocalWriter(writes) => writer -> writes.head.mem }

    val localAccums = localWrites.flatMap{case (writer,writtenMem) =>
      localReads.find{case (reader,readMem) => readMem == writtenMem && writer.dependsOn(reader) }.map{x => (x._1,writer,writtenMem) }
    }
    val accumReads = localAccums.map(_._1)
    val accumWrites = localAccums.map(_._2)

    val paths = mutable.HashMap[Exp[_],Long]() ++ oos
    val cycles = mutable.HashMap[Exp[_],Set[Exp[_]]]()

    accumReads.foreach{reader => cycles(reader) = Set(reader) }

    def fullDFS(cur: Exp[_]): Long = cur match {
      case Def(d) if scope.contains(cur) =>
        val deps = scope intersect d.allInputs.toSet // Handles effect scheduling, even though there's no data to pass

        if (deps.nonEmpty) {
          val dlys = deps.map{e => paths.getOrElseAdd(e, fullDFS(e)) }

          // Primitives are not allowed to be loops, so the latency of nested symbols must be some function of its blocks
          // e.g. the max of all or the sum of all
          // (For now, all cases are just the max of all inputs)
          val critical = d match {
            case _ => dlys.max
          }

          val cycleSyms = deps intersect cycles.keySet
          if (cycleSyms.nonEmpty) {
            cycles(cur) = cycleSyms.flatMap(cycles) + cur
            dbgs(c"cycle deps of $cur: ${cycles(cur)}")
          }

          dbgs(c"${str(cur)} [delay = max(" + dlys.mkString(", ") + s") + ${latencyOf(cur)}]" + (if (cycles.contains(cur)) "[cycle]" else ""))
          critical + latencyOf(cur) // TODO + inputDelayOf(cur) -- factor in delays which are external to reduction cycles
        }
        else {
          dbgs(c"${str(cur)}" + (if (cycles.contains(cur)) "[cycle]" else ""))
          latencyOf(cur)
        }

      case s => paths.getOrElse(s, 0L) // Get preset out of scope delay, or assume 0 offset
    }

    // Perform backwards pass to push unnecessary delays out of reduction cycles
    // This can create extra registers, but decreases the initiation interval of the cycle
    def reverseDFS(cur: Exp[_], cycle: Set[Exp[_]]): Unit = cur match {
      case s: Sym[_] if cycle contains cur =>
        val forward = s.dependents.filter(dep => scope.contains(dep))
        if (forward.nonEmpty) {
          val earliestConsumer = forward.map{e => paths.getOrElse(e, 0L) - latencyOf(e) }.min
          paths(cur) = Math.max(earliestConsumer, paths.getOrElse(cur, 0L))
        }
        getDef(s).foreach{d => d.allInputs.foreach{in => reverseDFS(in, cycle) }}

      case _ => // Do nothing
    }

    if (scope.nonEmpty) {
      // Perform forwards pass for normal data dependencies
      result.foreach{e => paths.getOrElseAdd(e, fullDFS(e)) }

      // TODO: What to do in case where a node is contained in multiple cycles?
      accumWrites.zipWithIndex.foreach{case (writer,i) =>
        val cycle = cycles(writer)
        val cycleList = cycle.toList
        dbgs(s"Cycle #$i: " + cycleList.map{s => c"($s, ${paths(s)})"}.mkString(", "))

        reverseDFS(writer, cycle)

        dbgs(s"Cycle #$i: " + cycleList.map{s => c"($s, ${paths(s)})"}.mkString(", "))
      }
    }

    val initiationInterval = if (localAccums.isEmpty) 1L else localAccums.map{case (read,write,_) => paths(write) - paths(read) }.max

    (paths.toMap, Math.max(initiationInterval, 1L))
  }

}

