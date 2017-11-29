package spatial.analysis

import argon.core._
import argon.nodes._
import spatial.aliases._
import spatial.banking.muxIndexOf
import spatial.metadata._
import spatial.models.LatencyModel
import spatial.nodes._
import spatial.utils._

import scala.collection.mutable

abstract class Cycle {
  def length: Long
  def symbols: Set[Exp[_]]
}

/** Write-after-read (WAR) cycle: Standard read-accumulate loop. **/
case class WARCycle(reader: Exp[_], writer: Exp[_], memory: Exp[_], symbols: Set[Exp[_]], length: Long) extends Cycle

/** Access-after-access (AAA) cycle: Time-multiplexed writes. **/
case class AAACycle(accesses: Set[Exp[_]], memory: Exp[_], length: Long) extends Cycle {
  def symbols = accesses
}


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
  def latencyOf(e: Exp[_], inReduce: Boolean = false): Long = if (!inHwScope) 0L else {
    // HACK: For now, disable retiming in reduction cycles by making everything have 0 latency
    // This means everything will be purely combinational logic between the accumulator read and write
    //val inReductionCycle = reduceType(e).isDefined
    //if (inReductionCycle) 0L else {
    if (spatialConfig.enableRetiming) latencyModel(e, inReduce) else {
      if (latencyModel.requiresRegisters(e, inReduce)) 0L else latencyModel(e, inReduce)
    }
  }

  def latencyAndInterval(block: Block[_], verbose: Boolean = true): (Long, Long) = {
    val (latencies, cycles) = latenciesAndCycles(block, verbose = verbose)
    val scope = latencies.keySet
    val latency = latencies.values.fold(0L){Math.max}
    val interval = cycles.map(_.length).fold(1L){Math.max}  // Lowest II is 1
    // HACK: Set initiation interval to 1 if it contains a specialized reduction
    // This is a workaround for chisel codegen currently specializing and optimizing certain reduction types
    val compilerII = if (scope.exists(x => reduceType(x).contains(FixPtSum))) 1L else interval
    (latency, compilerII)
  }

  def latencyOfPipe(block: Block[_], verbose: Boolean = false): (Long, Long) = {
    val (latency, interval) = latencyAndInterval(block, verbose = verbose)
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

  def latenciesAndCycles(block: Block[_], verbose: Boolean = true): (Map[Exp[_],Long], Set[Cycle]) = {
    val (schedule, result) = blockNestedScheduleAndResult(block)
    pipeLatencies(result, schedule, verbose = verbose)
  }

  def pipeLatencies(result: Seq[Exp[_]], schedule: Seq[Exp[_]], oos: Map[Exp[_],Long] = Map.empty, verbose: Boolean = true): (Map[Exp[_],Long], Set[Cycle]) = {
    val scope = schedule.toSet
    val knownCycles = mutable.HashMap[Exp[_],Set[(Exp[_],Exp[_])]]()

    val localReads  = scope.collect{case reader @ Reader(reads) => reader -> reads.head.mem }
    val localWrites = scope.collect{case writer @ Writer(writes) => writer -> writes.head.mem }

    val localAccums = localWrites.flatMap{case (writer,writtenMem) =>
      localReads.flatMap{case (reader,readMem) =>
        if (readMem == writtenMem) {
          val path = writer.getNodesBetween(reader, scope)

          path.foreach{sym =>
            knownCycles += sym -> (knownCycles.getOrElse(sym, Set.empty[(Exp[_],Exp[_])]) + ((reader, writer)) )
          }

          /*if (verbose && path.nonEmpty) {
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
          }*/

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
            //dbgs(c"cycle deps of $cur: ${cycles(cur)}")
          }

          val inReduce = knownCycles.contains(cur)

          val delay = critical + latencyOf(cur, inReduce) // TODO + inputDelayOf(cur) -- factor in delays which are external to reduction cycles

          //if (verbose) dbgs(c"[$delay = max(" + dlys.mkString(", ") + s") + ${latencyOf(cur, inReduce)}] ${str(cur)}" + (if (inReduce) "[cycle]" else ""))
          delay
        }
        else {
          val inReduce = knownCycles.contains(cur)
          val delay = latencyOf(cur, inReduce)
          //if (verbose) dbgs(c"[$delay = max(0) + ${latencyOf(cur, inReduce)}] ${str(cur)}" + (if (inReduce) "[cycle]" else ""))
          delay
        }

      case s => paths.getOrElse(s, 0L) // Get preset out of scope delay, or assume 0 offset
    }

    // Perform backwards pass to push unnecessary delays out of reduction cycles
    // This can create extra registers, but decreases the initiation interval of the cycle
    def reverseDFS(cur: Exp[_], cycle: Set[Exp[_]]): Unit = cur match {
      case s: Sym[_] if cycle contains cur =>
        val forward = s.dependents.filter(dep => scope.contains(dep))
        if (forward.nonEmpty) {
          //if (verbose) dbgs(s"${str(s)} [${paths.getOrElse(s,0L)}]")

          val earliestConsumer = forward.map{e =>
            val in = paths.getOrElse(e, 0L) - latencyOf(e, inReduce=cycle.contains(e))
            //if (verbose) dbgs(s"  [$in = ${paths.getOrElse(e, 0L)} - ${latencyOf(e,inReduce = cycle.contains(e))}] ${str(e)}")
            in
          }.min

          val push = Math.max(earliestConsumer, paths.getOrElse(cur, 0L))

          //if (verbose) dbgs(s"  [$push]")

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
        //if (verbose) dbgs(s"Cycle #$i: ")
        reverseDFS(writer, cycle)
      }
    }

    //val cycleSyms = accumWrites.flatMap{writer => cycles(writer) }

    val warCycles = localAccums.map{case (reader,writer,mem) =>
      val symbols = cycles(writer)
      WARCycle(reader, writer, mem, symbols, paths(writer) - paths(reader))
    }

    def pushMultiplexedAccesses(accessors: Set[(Exp[_],Exp[_])]) = accessors.groupBy{_._2}.map{case (mem,accesses) =>
      val muxPairs = accesses.map{x =>
        // NOTE: After unrolling there should be only one mux index per access
        val muxes = muxIndexOf.getMem(x._1,x._2)
        (x, paths.getOrElse(x._1,0L), muxes.fold(0){Math.max})
      }.toSeq

      val length = muxPairs.map(_._3).fold(0){Math.max} + 1L

      var writeStage = 0L
      muxPairs.sortBy(_._2).foreach{case (x, dly, _) =>
        val writeDelay = Math.max(writeStage, dly)
        writeStage = writeDelay + 1
        paths(x._1) = writeDelay
      }

      AAACycle(accesses.map(_._1), mem, length)
    }

    val wawCycles = pushMultiplexedAccesses(localWrites)
    val rarCycles = pushMultiplexedAccesses(localReads)
    val allCycles: Set[Cycle] = (wawCycles ++ rarCycles ++ warCycles).toSet

    if (verbose) {
      def dly(x: Exp[_]) = paths.getOrElse(x, 0L)
      schedule.sortWith{(a,b) => dly(a) < dly(b)}.foreach{node =>
        dbgs(s"  [${dly(node)}] ${str(node)}")
      }
    }


    (paths.toMap, allCycles)
  }

}

