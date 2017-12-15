package spatial.codegen.pirgen

import argon.core._
import spatial.analysis.ModelingTraversal
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._
import org.virtualized.SourceContext

trait PIRHackyLatencyAnalyzer extends ModelingTraversal { traversal =>
  override val name = "PIR Hacky Latency Analyzer"
  val FILE_NAME = "HackyLatency.csv"

  override lazy val latencyModel = new PlasticineLatencyModel{}

  // Only count latencies of nodes if they don't have retiming nodes
  override def latencyOf(e: Exp[_], inReduce: Boolean = false): Double = if (inHwScope) e match {
    case Def(DelayLine(size,_)) => size
    case s: Sym[_] if s.dependents.exists{case Def(_:DelayLine[_]) => true; case _ => false} => 0
    case _ => latencyModel.latencyOf(e,inReduce)
  } else 0L

  override def silence() {
    latencyModel.silence()
    super.silence()
  }

  var cycleScope: List[Double] = Nil
  var totalCycles: Double = 0.0

  // TODO: Default number of iterations if bound can't be computed?
  // TODO: Warn user if bounds can't be found?
  // TODO: Move this elsewhere
  def nIters(x: Exp[CounterChain], ignorePar: Boolean = false): Long = x match {
    case Def(CounterChainNew(ctrs)) =>
      val loopIters = ctrs.map{
        case Def(CounterNew(start,end,stride,par)) =>
          val min = boundOf.get(start).map(_.toDouble).getOrElse(0.0)
          val max = boundOf.get(end).map(_.toDouble).getOrElse(1.0)
          val step = boundOf.get(stride).map(_.toDouble).getOrElse(1.0)
          val p = boundOf.get(par).map(_.toDouble).getOrElse(1.0)

          val nIters = Math.ceil(max - min/step)
          if (ignorePar)
            nIters.toLong
          else
            Math.ceil(nIters/p).toLong

        case Def(Forever()) => 0L
      }
      loopIters.fold(1L){_*_}
  }

  def latencyOfBlock(b: Block[_], par_mask: Boolean = false): List[Double] = {
    val outerScope = cycleScope
    cycleScope = Nil
    tab += 1
    getControlNodes(b).foreach{
      case s@Op(d) => visit(s.asInstanceOf[Sym[_]], d)
      case _ =>
    }
    tab -= 1

    val cycles = cycleScope
    cycleScope = outerScope
    cycles
  }

  override protected def visit(lhs: Sym[_], rhs: Op[_]): Unit = {
    val cycles = rhs match {
      case Hwblock(blk, isForever) if isInnerControl(lhs) =>
        inHwScope = true
        val body = latencyOfPipe(blk)._1

        dbgs(s"Inner Accel $lhs: ")
        dbgs(s"- body = $body")

        inHwScope = false
        body


      case Hwblock(blk, isForever) if isOuterControl(lhs) =>
        inHwScope = true
        val body = latencyOfBlock(blk).sum

        dbgs(s"Accel $lhs: ")
        dbgs(s"- body = $body")

        inHwScope = false
        body

      // --- Pipe
      case UnitPipe(en, func) if isInnerControl(lhs) =>
        val (pipe, ii) = latencyOfPipe(func)

        dbgs(s"Pipe $lhs: ")
        dbgs(s"- pipe = $pipe")
        bodyLatency(lhs) = pipe

        pipe + latencyOf(lhs)

      case OpForeach(en, cchain, func, iters) if isInnerControl(lhs) =>
        val N = nIters(cchain)
        val pipe = latencyOfPipe(func)._1

        dbgs(s"Foreach $lhs (N = $N):")
        dbgs(s"- pipe = $pipe")
        bodyLatency(lhs) = pipe

        pipe + N - 1 + latencyOf(lhs)

      case OpReduce(en, cchain,accum,map,ld,reduce,store,_,_,rV,iters) if isInnerControl(lhs) =>
        val N = nIters(cchain)
        val P = parsOf(cchain).product

        val fuseMapReduce = false //canFuse(map,reduce,rV,P)

        val body = latencyOfPipe(map)._1
        val internal = latencyOfPipe(reduce)._1 * reductionTreeHeight(P)

        val cycle = latencyOfCycle(ld)._1 + latencyOfCycle(reduce)._1 + latencyOfCycle(store)._1

        dbgs(s"Reduce $lhs (N = $N):")
        dbgs(s"- body  = $body")
        dbgs(s"- tree  = $internal")
        dbgs(s"- cycle = $cycle")

        body + internal + N*cycle + latencyOf(lhs)


      case UnrolledForeach(en,cchain,func,iters,valids) if isInnerControl(lhs) =>
        val N = nIters(cchain)
        val pipe = latencyOfPipe(func)._1
        dbgs(s"Unrolled Foreach $lhs (N = $N):")
        dbgs(s"- pipe = $pipe")

        pipe + N - 1 + latencyOf(lhs)

      case UnrolledReduce(en,cchain,accum,func,iters,valids) if isInnerControl(lhs) =>
        val N = nIters(cchain)

        val body = latencyOfPipe(func)._1

        dbgs(s"Unrolled Reduce $lhs (N = $N):")
        dbgs(s"- body  = $body")
        bodyLatency(lhs) = body

        body + N - 1 + latencyOf(lhs)

      case StateMachine(en, start, notDone, action, nextState, state) if isInnerControl(lhs) =>
        val cont = latencyOfPipe(notDone)._1
        val act  = latencyOfPipe(action)._1
        val next = latencyOfPipe(nextState)._1

        dbgs(s"Inner FSM $lhs: ")
        dbgs(s"-notDone = $cont")
        dbgs(s"-action  = $act")
        dbgs(s"-next    = $next")

        0L // TODO: Any way to predict number of iterations, or annotate expected number?

      // --- Sequential
      case UnitPipe(en, func) if isOuterControl(lhs) =>
        val stages = latencyOfBlock(func)

        dbgs(s"Outer Pipe $lhs:")
        stages.reverse.zipWithIndex.foreach{case (s,i) => dbgs(s"- $i. $s")}

        stages.sum + latencyOf(lhs)


      // --- Metapipeline and Sequential
      case OpForeach(en, cchain, func, _) =>
        val N = nIters(cchain)
        val stages = latencyOfBlock(func)

        dbgs(s"Outer Foreach $lhs (N = $N):")
        stages.reverse.zipWithIndex.foreach{case (s,i) => dbgs(s"- $i. $s")}

        if (isMetaPipe(lhs)) { stages.max * (N - 1) + stages.sum + latencyOf(lhs) }
        else                 { stages.sum * N + latencyOf(lhs) }

      case OpReduce(en, cchain,accum,map,ld,reduce,store,_,_,rV,iters) =>
        val N = nIters(cchain)
        val P = parsOf(cchain).product

        val mapStages = latencyOfBlock(map)
        val internal = latencyOfPipe(reduce)._1 * reductionTreeHeight(P)
        val cycle = latencyOfCycle(ld)._1 + latencyOfCycle(reduce)._1 + latencyOfCycle(store)._1

        val reduceStage = internal + cycle
        val stages = mapStages :+ reduceStage

        dbgs(s"Outer Reduce $lhs (N = $N):")
        stages.reverse.zipWithIndex.foreach{case (s,i) => dbgs(s"- $i. $s")}

        if (isMetaPipe(lhs)) { stages.max * (N - 1) + stages.sum + latencyOf(lhs) }
        else                 { stages.sum * N + latencyOf(lhs) }

      case OpMemReduce(en, cchainMap,cchainRed,accum,map,ldRes,ldAcc,reduce,store,_,_,rV,itersMap,itersRed) =>
        val Nm = nIters(cchainMap)
        val Nr = nIters(cchainRed)
        val Pm = parsOf(cchainMap).product // Parallelization factor for map
      val Pr = parsOf(cchainRed).product // Parallelization factor for reduce

        val mapStages: List[Double] = latencyOfBlock(map)
        val internal: Double = latencyOfPipe(ldRes)._1 + latencyOfPipe(reduce)._1 * reductionTreeHeight(Pm)
        val accumulate: Double = latencyOfPipe(ldAcc)._1 + latencyOfPipe(reduce)._1 + latencyOfPipe(store)._1

        val reduceStage: Double = internal + accumulate + Nr - 1
        val stages = mapStages :+ reduceStage

        dbgs(s"Block Reduce $lhs (Nm = $Nm, Nr = $Nr)")
        stages.reverse.zipWithIndex.foreach{case (s,i) => dbgs(s"- $i. $s")}

        if (isMetaPipe(lhs)) { stages.max * (Nm - 1) + stages.sum + latencyOf(lhs) }
        else                 { stages.sum * Nm + latencyOf(lhs) }


      case UnrolledForeach(en,cchain,func,iters,valids) =>
        val N = nIters(cchain)
        val stages = latencyOfBlock(func)

        dbgs(s"Unrolled Outer Foreach $lhs (N = $N):")
        stages.reverse.zipWithIndex.foreach{case (s,i) => dbgs(s"- $i. $s")}

        if (isMetaPipe(lhs)) { stages.max * (N - 1) + stages.sum + latencyOf(lhs) }
        else                 { stages.sum * N + latencyOf(lhs) }

      case UnrolledReduce(en,cchain,accum,func,iters,valids) =>
        val N = nIters(cchain)
        val P = parsOf(cchain).product
        val stages = latencyOfBlock(func)

        dbgs(s"Unrolled Outer Reduce $lhs (N = $N):")
        stages.reverse.zipWithIndex.foreach{case (s,i) => dbgs(s"- $i. $s")}

        if (isMetaPipe(lhs)) { stages.max * (N - 1) + stages.sum + latencyOf(lhs) }
        else                 { stages.sum * N + latencyOf(lhs) }

      case StateMachine(en, start, notDone, action, nextState, state) =>
        val cont      = latencyOfPipe(notDone)
        val actStages = latencyOfBlock(action)
        val next      = latencyOfPipe(nextState)

        dbgs(s"Outer FSM $lhs: ")
        dbgs(s"-notDone = $cont")
        dbgs(s"-action: ")
        actStages.reverse.zipWithIndex.foreach{case (s,i) => dbgs(s"  - $i. $s") }
        dbgs(s"-next    = $next")

        0L  // TODO

      case _ =>
        // No general rule for combining blocks
        rhs.blocks.foreach{blk => visitBlock(blk) }
        latencyOf(lhs)
    }
    cycleScope ::= cycles
  }

  override protected def preprocess[A:Type](b: Block[A]) = {
    cycleScope = Nil
    super.preprocess(b)
  }

  override protected def postprocess[A:Type](b: Block[A]) = {
    val CLK = latencyModel.clockRate
    val startup = latencyModel.baseCycles
    // TODO: Could potentially have multiple accelerator designs in a single program
    // Eventually want to be able to support multiple accel scopes
    totalCycles = cycleScope.sum + startup

    report(s"Estimated cycles: $totalCycles")
    report(s"Estimated runtime (at " + "%.2f".format(CLK) +"MHz): " + "%.8f".format(totalCycles/(CLK*1000000f)) + "s")
    super.postprocess(b)
  }

}
