package spatial.analysis

import argon.core._
import argon.nodes._
import spatial.aliases._
import spatial.metadata._
import spatial.models.LatencyModel
import spatial.nodes._
import spatial.utils._

import scala.collection.mutable

case class Cycle(reader: Exp[_], writer: Exp[_], memory: Exp[_], symbols: Set[Exp[_]], length: Double)

trait ModelingTraversal extends SpatialTraversal { traversal =>
  val latencyModel: LatencyModel

  override def silence(): Unit = {
    latencyModel.silence()
    super.silence()
  }
  override def init(): Unit = if (needsInit) {
    latencyModel.init()
    super.init()
  }

  protected override def preprocess[S: Type](block: Block[S]): Block[S] = {
    inHwScope = false
    inReduce = false
    super.preprocess(block)
  }

  // --- State
  var inHwScope = false // In hardware scope
  var inReduce = false  // In tight reduction cycle (accumulator update)
  def latencyOf(e: Exp[_], inReduce: Boolean = false): Double = if (!inHwScope) 0 else {
    // HACK: For now, disable retiming in reduction cycles by making everything have 0 latency
    // This means everything will be purely combinational logic between the accumulator read and write
    //val inReductionCycle = reduceType(e).isDefined
    //if (inReductionCycle) 0 else {
    if (spatialConfig.enableRetiming) latencyModel(e, inReduce) else {
      if (latencyModel.requiresRegisters(e, inReduce)) 0 else latencyModel(e, inReduce)
    }
  }

  def builtInLatencyOf(e: Exp[_]): Double = if (!inHwScope) 0 else {
    latencyModel.builtInLatencyOfNode(e)
  }

  def latencyAndInterval(block: Block[_], verbose: Boolean = true): (Double, Double) = {
    val (latencies, cycles) = latenciesAndCycles(block, verbose = verbose)
    val scope = latencies.keySet
    val latency = latencies.values.fold(0.0){(a,b) => Math.max(a,b) }
    // TODO: Safer way of determining if THIS cycle is the reduceType
    val interval = (cycles.map{c => 
      val scopeContainsSpecial = scope.exists(x => reduceType(x).contains(FixPtSum) )
      val cycleContainsAdd = c.symbols.exists(n => n match {case Def(FixAdd(_,_)) => true; case _ => false})
      val length = if (cycleContainsAdd && scopeContainsSpecial) 1 else c.length
      length
    } + 0
    ).max
    // HACK: Set initiation interval to 1 if it contains a specialized reduction
    // This is a workaround for chisel codegen currently specializing and optimizing certain reduction types
    val compilerII = interval
    (latency, compilerII)
  }

  def latencyOfPipe(block: Block[_]): (Double, Double) = {
    val (latency, interval) = latencyAndInterval(block, verbose = false)
    (latency, interval)
  }

  def latencyOfCycle(b: Block[_]): (Double, Double) = {
    val outerReduce = inReduce
    inReduce = true
    val out = latencyOfPipe(b)
    inReduce = outerReduce
    out
  }

  implicit class GetOrElseUpdateFix[K,V](x: mutable.Map[K,V]) {
    def getOrElseAdd(k: K, v: => V): V = if (x.contains(k)) x(k) else { val value = v; x(k) = value; value }
  }

  def latenciesAndCycles(block: Block[_], verbose: Boolean = true): (Map[Exp[_],Double], Set[Cycle]) = {
    val (scope, result) = blockNestedScopeAndResult(block)
    pipeLatencies(result, scope, verbose = verbose)
  }

  def pipeLatencies(result: Seq[Exp[_]], scope: Set[Exp[_]], oos: Map[Exp[_],Double] = Map.empty, verbose: Boolean = true): (Map[Exp[_],Double], Set[Cycle]) = {
    val knownCycles = mutable.HashMap[Exp[_],Set[(Exp[_],Exp[_])]]()

    // TODO: FifoDeq appears as LocalReader and LocalReadModify.  Adds "fake" cycles but shouldn't impact final answer since we take max
    val localReads  = scope.collect{case reader @ LocalReader(reads) => reader -> reads.head.mem}
    val localWrites = scope.collect{case writer @ LocalWriter(writes) => writer -> writes.head.mem; case reader @ LocalReadModify(reads) => reader -> reads.head.mem}
    val localStatuses = scope.collect{case reader @ LocalReadStatus(reads) => reader -> reads.head}

    val localAccums = localWrites.flatMap{case (writer,writtenMem) =>
      (localReads ++ localStatuses).flatMap{case (reader,readMem) =>
        if (readMem == writtenMem) {
          val path = writer.getNodesBetween(reader, scope)

          path.foreach{sym =>
            knownCycles += sym -> (knownCycles.getOrElse(sym, Set.empty[(Exp[_],Exp[_])]) + ((reader, writer)) )
          }

          if (verbose && path.nonEmpty) {
            dbgs("Found cycle between: ")
            dbgs(s"  ${str(writer)}")
            dbgs(s"  ${str(reader)}")
            path.foreach{node =>
              dbgs(s"    ${str(node)}")
            }
          }
          else {
            dbgs(s"No cycle between: ")
            dbgs(s"  ${str(writer)}")
            dbgs(s"  ${str(reader)}")
          }

          if (path.nonEmpty) {
            Some((reader,writer,writtenMem))
          }
          else None
        }
        else None
        //readMem == writtenMem && writer.dependsOn(reader)
      }
    }
    val accumReads = localAccums.map(_._1)
    val accumWrites = localAccums.map(_._2)

    val paths = mutable.HashMap[Exp[_],Double]() ++ oos
    val cycles = mutable.HashMap[Exp[_],Set[Exp[_]]]()

    accumReads.foreach{reader => cycles(reader) = Set(reader) }

    def fullDFS(cur: Exp[_]): Double = cur match {
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
            //dbgs(c"cycle deps of $cur: ${cycles(cur)}")
          }

          val inReduce = knownCycles.contains(cur)

          val delay = critical + latencyOf(cur, inReduce) // TODO + inputDelayOf(cur) -- factor in delays which are external to reduction cycles

          if (verbose) dbgs(c"[$delay = max(" + dlys.mkString(", ") + s") + ${latencyOf(cur, inReduce)}] ${str(cur)}" + (if (inReduce) "[cycle]" else ""))
          delay
        }
        else {
          val inReduce = knownCycles.contains(cur)
          val delay = latencyOf(cur, inReduce)
          if (verbose) dbgs(c"[$delay = max(0) + ${latencyOf(cur, inReduce)}] ${str(cur)}" + (if (inReduce) "[cycle]" else ""))
          delay
        }

      case s => paths.getOrElse(s, 0) // Get preset out of scope delay, or assume 0 offset
    }

    // Perform backwards pass to push unnecessary delays out of reduction cycles
    // This can create extra registers, but decreases the initiation interval of the cycle
    def reverseDFS(cur: Exp[_], cycle: Set[Exp[_]]): Unit = cur match {
      case s: Sym[_] if cycle contains cur =>
        val forward = s.dependents.filter(dep => scope.contains(dep))
        if (forward.nonEmpty) {
          if (verbose) dbgs(s"${str(s)} [${paths.getOrElse(s,0)}]")

          val earliestConsumer = forward.map{e =>
            val in = paths.getOrElse(e, 0.0) - latencyOf(e, inReduce=cycle.contains(e))
            if (verbose) dbgs(s"  [$in = ${paths.getOrElse(e, 0.0)} - ${latencyOf(e,inReduce = cycle.contains(e))}] ${str(e)}")
            in
          }.min

          val push = Math.max(earliestConsumer, paths.getOrElse(cur, 0.0))

          if (verbose) dbgs(s"  [$push]")

          paths(cur) = push
        }
        getDef(s).foreach{d => d.allInputs.foreach{in => reverseDFS(in, cycle) }}

      case _ => // Do nothing
    }

    if (scope.nonEmpty) {
      // Perform forwards pass for normal data dependencies
      result.foreach{e => paths.getOrElseAdd(e, fullDFS(e)) }

      // TODO: What to do in case where a node is contained in multiple cycles?
      accumWrites.toList.zipWithIndex.foreach{case (writer,i) =>
        val cycle = cycles.getOrElse(writer, Set.empty)
        if (verbose) dbgs(s"Cycle #$i: ")
        reverseDFS(writer, cycle)
      }
    }

    //val cycleSyms = accumWrites.flatMap{writer => cycles(writer) }

    val allCycles = localAccums.map{case (reader,writer,mem) =>
      val symbols = cycles(writer)
      val cycleLengthExact = paths(writer) - paths(reader)
      val cycleLength = if (localStatuses.toList.map(_._1).contains(reader)) cycleLengthExact + 1.0 else cycleLengthExact // FIFO/Stack operations need extra cycle for status update (?)
      Cycle(reader, writer, mem, symbols, cycleLength)
    }

    (paths.toMap, allCycles)
  }

}

