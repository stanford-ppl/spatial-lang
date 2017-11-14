package spatial.dse

import argon.core._
import spatial.aliases._
import spatial.analysis._
import spatial.metadata._
import java.util.concurrent.BlockingQueue

import spatial.models._

case class HyperMapperThread(
  threadId:  Int,
  origState: State,
  space:     Seq[Domain[Int]],
  accel:     Exp[_],
  program:   Block[_],
  localMems: Seq[Exp[_]],
  workQueue: BlockingQueue[Seq[Int]],
  outQueue:  BlockingQueue[String]
) extends Runnable { thread =>
  // --- Thread stuff
  private var isAlive: Boolean = true
  private var hasTerminated: Boolean = false
  def requestStop(): Unit = { isAlive = false }

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
  private implicit val state: State = new State

  private val target = spatialConfig.target
  private val capacity: Area = target.capacity
  val areaHeading: Seq[String] = capacity.nonZeroFields

  private lazy val scalarAnalyzer = new ScalarAnalyzer { var IR: State = state }
  private lazy val memoryAnalyzer = new MemoryAnalyzer {
    def localMems: Seq[Exp[_]] = thread.localMems
    var IR: State = state
  }
  private lazy val contentionAnalyzer = new ContentionAnalyzer { var IR: State = state; def top = accel }
  private lazy val areaAnalyzer  = target.areaAnalyzer(state)
  private lazy val cycleAnalyzer = target.cycleAnalyzer(state)

  def init(): Unit = {
    origState.copyTo(state)
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
  }

  def run(): Unit = {
    while(isAlive) {
      val requests = workQueue.take() // Blocking dequeue

      if (requests.nonEmpty) {
        // println(s"#$threadId: Received batch of $len. Working...")
        try {
          val result = run(requests)
          // println(s"#$threadId: Completed batch of $len. ${workQueue.size()} items remain in the queue")
          outQueue.put(result) // Blocking enqueue
        }
        catch {case e: Throwable =>
          // println(s"#$threadId: Encountered error while running: ")
          println(e.getMessage)
          e.getStackTrace.foreach{line => println(line) }
          isAlive = false
        }
      }
      else {
        // println(s"#$threadId: Received kill signal, terminating!")
        requestStop()
      } // Somebody poisoned the work queue!
    }
    hasTerminated = true
  }

  def run(request: Seq[Int]): String = {
    state.resetErrors()
    request.indices.foreach{i => space(i).setValue(request(i)) }

    val (area, runtime) = evaluate()
    val valid = area <= capacity && !state.hadErrors // Encountering errors makes this an invalid design point

    // Only report the area resources that the target gives maximum capacities for
    space.map(_.value).mkString(",") + "," + area.seq(areaHeading:_*).mkString(",") + "," + runtime + "," + valid
  }

  private def evaluate(): (Area, Long) = {
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
    (areaAnalyzer.totalArea, cycleAnalyzer.totalCycles)
  }

}

