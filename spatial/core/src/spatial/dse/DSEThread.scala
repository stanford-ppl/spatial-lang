package spatial.dse

import argon.core._
import spatial.aliases._
import spatial.analysis._
import spatial.metadata._
import java.util.concurrent.BlockingQueue

import spatial.models._

case class DSEThread(
  threadId:  Int,
  space:     Seq[Domain[_]],
  accel:     Exp[_],
  program:   Block[_],
  localMems: Seq[Exp[_]],
  workQueue: BlockingQueue[Seq[DesignPoint]],
  outQueue:  BlockingQueue[Array[String]],
  areaModel: AreaModel,
  timeModel: LatencyModel
)(implicit val state: State) extends Runnable { thread =>
  // --- Thread stuff
  private var isAlive: Boolean = true
  private var hasTerminated: Boolean = false
  def requestStop(): Unit = { isAlive = false }
  var START: Long = 0

  // --- Profiling
  private final val PROFILING = false
  private var clockRef = 0L
  private def resetClock() { clockRef = System.currentTimeMillis }

  var bndTime = 0L
  var memTime = 0L
  var conTime = 0L
  var areaTime = 0L
  var cyclTime = 0L

  private def endBnd() { bndTime += System.currentTimeMillis - clockRef; resetClock() }
  private def endMem() { memTime += System.currentTimeMillis - clockRef; resetClock() }
  private def endCon() { conTime += System.currentTimeMillis - clockRef; resetClock() }
  private def endArea() { areaTime += System.currentTimeMillis - clockRef; resetClock() }
  private def endCycles() { cyclTime += System.currentTimeMillis - clockRef; resetClock() }
  private def resetAllTimers() { memTime = 0; bndTime = 0; conTime = 0; areaTime = 0; cyclTime = 0 }

  // --- Space Stuff

  private val target = spatialConfig.target
  private val capacity: Area = target.capacity
  val areaHeading: Seq[String] = capacity.nonZeroFields
  private val indexedSpace = space.zipWithIndex
  private val N = space.length
  private val dims = space.map{d => BigInt(d.len) }
  private val prods = List.tabulate(N){i => dims.slice(i+1,N).product }

  private lazy val scalarAnalyzer = new ScalarAnalyzer { var IR: State = state }
  private lazy val memoryAnalyzer = new MemoryAnalyzer {
    def localMems: Seq[Exp[_]] = thread.localMems
    var IR: State = state
  }
  private lazy val contentionAnalyzer = new ContentionAnalyzer {
    var IR: State = state
    def top = accel
  }
  lazy val areaAnalyzer  = new AreaAnalyzer(state, areaModel, timeModel)
  private lazy val cycleAnalyzer = new LatencyAnalyzer(state, timeModel)

  def init(): Unit = {
    areaAnalyzer.init()
    cycleAnalyzer.init()
    scalarAnalyzer.init()
    memoryAnalyzer.init()
    contentionAnalyzer.init()

    config.verbosity = -1
    scalarAnalyzer.silence()
    memoryAnalyzer.silence()
    contentionAnalyzer.silence()
    areaAnalyzer.silence()
    cycleAnalyzer.silence()
    areaAnalyzer.run(program)(mtyp(program.tp))

//    println(s"[#$threadId] Started up Area Analyzer: ")
//    println(s"[#$threadId]   area model: ${areaAnalyzer.areaModel.toString}")
//    println(s"[#$threadId]   time model: ${areaAnalyzer.latencyModel.toString}")
//    println(s"[#$threadId]   verbosity: ${areaAnalyzer.verbosity}")
//    println(s"[#$threadId]   state: ${areaAnalyzer.IR}")
  }

  def run(): Unit = {
    //println(s"[$threadId] Started.")

    while(isAlive) {
      val requests = workQueue.take() // Blocking dequeue

      if (requests.nonEmpty) {
        //val time = System.currentTimeMillis() - START
        //println(s"[$threadId] Received new work batch of size ${requests.length} [$time]")
        try {
          val result = run(requests)
          //val time = System.currentTimeMillis() - START
          //println(s"[$threadId] Completed batch of ${requests.length}. [$time]")
          outQueue.put(result) // Blocking enqueue
        }
        catch {case e: Throwable =>
          println(s"[$threadId] Encountered error while running: ")
          println(e.getMessage)
          e.getStackTrace.foreach{line => println(line) }
          isAlive = false
        }
      }
      else {
        //println(s"[$threadId] Received kill signal, terminating!")
        requestStop()
      } // Somebody poisoned the work queue!
    }

    //println(s"[$threadId] Ending now!")
    hasTerminated = true
  }

  def run(requests: Seq[DesignPoint]): Array[String] = {
    val array = new Array[String](requests.size)
    var i: Int = 0
    requests.foreach{pt =>
      state.resetErrors()
      pt.set(indexedSpace, prods, dims)

      //println(params.map{case p @ Bound(c) => p.name.getOrElse(p.toString) + s": $c" }.mkString(", "))
      val (area, runtime, valid) = evaluate()
      val time = System.currentTimeMillis() - START

      //println(s"[$threadId] Done $i / ${requests.size} [$time]")

      // Only report the area resources that the target gives maximum capacities for
      array(i) = space.map(_.value).mkString(",") + "," + area.seq(areaHeading:_*).mkString(",") + "," + runtime + "," + valid + "," + time

      i += 1
    }
    array
  }

  private def evaluate(): (Area, Long, Boolean) = {
    try {
      if (PROFILING) resetClock()
      scalarAnalyzer.rerun(accel, program)
      if (PROFILING) endBnd()

      memoryAnalyzer.run()
      if (PROFILING) endMem()

      contentionAnalyzer.run()
      if (PROFILING) endCon()

      areaAnalyzer.rerun(accel, program)
      if (PROFILING) endArea()

      cycleAnalyzer.rerun(accel, program)
      if (PROFILING) endCycles()

      val area = areaAnalyzer.totalArea
      val runtime = cycleAnalyzer.totalCycles
      val valid = area <= capacity && !state.hadErrors // Encountering errors makes this an invalid design point
      (area, runtime, valid)
    }
    catch {case _:Throwable =>
      val area = areaAnalyzer.areaModel.NoArea
      val runtime = -1
      (area, runtime, false)
    }
  }

}
