package spatial.dse

import argon.core._
import spatial.SpatialConfig
import spatial.analysis._
import spatial.metadata._
import spatial.models.AreaSummary

case class DSEThread(
  origState: State,
  space:     Seq[Domain[_]],
  restricts: Set[Restrict],
  accel:     Exp[_],
  program:   Block[_],
  localMems: Seq[Exp[_]]
) {
  var areaHeading: List[String] = Nil

  private implicit val state: State = new State

  private final val PROFILING = true
  private var clockRef = 0L
  private def resetClock() { clockRef = System.currentTimeMillis }

  private var setTime = 0L
  private var bndTime = 0L
  private var conTime = 0L
  private var areaTime = 0L
  private var cyclTime = 0L

  private def endSet() { setTime += System.currentTimeMillis - clockRef; resetClock() }
  private def endBnd() { bndTime += System.currentTimeMillis - clockRef; resetClock() }
  private def endCon() { conTime += System.currentTimeMillis - clockRef; resetClock() }
  private def endArea() { areaTime += System.currentTimeMillis - clockRef; resetClock() }
  private def endCycles() { cyclTime += System.currentTimeMillis - clockRef; resetClock() }
  private def resetAllTimers() { setTime = 0; bndTime = 0; conTime = 0; areaTime = 0; cyclTime = 0 }

  private def getPercentages(total: Long) = {
    val t = total.toFloat
    val set = 100*setTime / t
    val bnd = 100*bndTime / t
    val con = 100*conTime / t
    val area = 100*areaTime / t
    val cycl = 100*cyclTime / t
    //debug("set: %.1f, bnd: %.1f, con: %.1f, area: %.1f, cycles: %.1f, pareto: %.1f".format(set, bnd,con,area,cycl,pareto))
  }

  private val target = SpatialConfig.target
  type Sum = target.Sum
  private val capacity: Sum = target.capacity
  private val indexedSpace = space.zipWithIndex
  private val N = space.length
  private val dims = space.map{d => BigInt(d.len) }
  private val prods = List.tabulate(N){i => dims.slice(i+1,N).product }

  private lazy val scalarAnalyzer = new ScalarAnalyzer { val IR: State = state }
  private lazy val memoryAnalyzer = new MemoryAnalyzer {
    def localMems: Seq[Exp[_]] = this.localMems
    var IR: State = state
  }
  private lazy val contentionAnalyzer = new ContentionAnalyzer { var IR: State = state }
  private lazy val areaAnalyzer  = target.areaAnalyzer(state)
  private lazy val cycleAnalyzer = target.cycleAnalyzer(state)



  def init(): Unit = {
    origState.copyTo(state)
    areaAnalyzer.init()
    cycleAnalyzer.init()
    contentionAnalyzer.top = accel

    scalarAnalyzer.silence()
    memoryAnalyzer.silence()
    contentionAnalyzer.silence()
    areaAnalyzer.silence()
    cycleAnalyzer.silence()
    areaAnalyzer.run(program)
    areaHeading = areaAnalyzer.totalArea.headings
  }

  def run(start: BigInt, len: Int): Unit = {
    var i = start
    val end = start + len
    while (i < end) {
      indexedSpace.foreach{case (domain,d) => domain.set( ((i / prods(d)) % dims(d)).toInt ) }

      val (area, runtime) = evaluate()
      val valid = area <= capacity

      i += 1
    }
  }

  private def evaluate(): (Sum, Long) = {
    scalarAnalyzer.rerun(accel, program)
    if (PROFILING) endBnd()

    memoryAnalyzer.run()
    if (PROFILING) endSet()

    contentionAnalyzer.run()
    if (PROFILING) endCon()

    areaAnalyzer.rerun(accel, program)
    if (PROFILING) endArea()

    cycleAnalyzer.rerun(accel, program)
    if (PROFILING) endCycles()
    (areaAnalyzer.totalArea, cycleAnalyzer.totalCycles)
  }

}
