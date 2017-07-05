package spatial.analysis

import argon.core._
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._
import org.virtualized.SourceContext

trait LatencyAnalyzer extends ModelingTraversal {
  override val name = "Latency Analyzer"

  override def silence() {
    latencyModel.silence()
    super.silence()
  }

  var cycleScope: List[Long] = Nil
  var totalCycles: Long = 0L

  // TODO: Default number of iterations if bound can't be computed?
  // TODO: Warn user if bounds can't be found?
  // TODO: Move this elsewhere
  def nIters(x: Exp[CounterChain], ignorePar: Boolean = false): Long = x match {
    case Def(CounterChainNew(ctrs)) =>
      val loopIters = ctrs.map{
        case Def(CounterNew(start,end,stride,par)) =>
          val min = boundOf.get(start).map(_.toDouble).getOrElse{/*warn(u"Don't know bound of $start");*/ 0.0}
          val max = boundOf.get(end).map(_.toDouble).getOrElse{/*warn(u"Don't know bound of $end");*/ 1.0}
          val step = boundOf.get(stride).map(_.toDouble).getOrElse{/*warn(u"Don't know bound of $stride");*/ 1.0}
          val p = boundOf.get(par).map(_.toDouble).getOrElse{/*warn(u"Don't know bound of $par");*/ 1.0}

          val nIters = Math.ceil(max - min/step)
          if (ignorePar)
            nIters.toLong
          else
            Math.ceil(nIters/p).toLong

        case Def(Forever()) => 0L
      }
      loopIters.fold(1L){_*_}
  }

  def latencyOfBlock(b: Block[_], par_mask: Boolean = false): List[Long] = {
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
        val body = latencyOfPipe(blk)

        dbgs(s"Inner Accel $lhs: ")
        dbgs(s"- body = $body")
        bodyLatency(lhs) = body

        inHwScope = false
        body


      case Hwblock(blk, isForever) if isOuterControl(lhs) =>
        inHwScope = true
        val body = latencyOfBlock(blk).sum

        dbgs(s"Accel $lhs: ")
        dbgs(s"- body = $body")

        inHwScope = false
        body

      case ParallelPipe(en, func) =>
        val blks = latencyOfBlock(func, true)
        dbgs(s"Parallel $lhs: ")
        blks.reverse.zipWithIndex.foreach{case (s,i) => dbgs(s"- $i. $s")}
        blks.max + latencyOf(lhs)

      // --- Pipe
      case UnitPipe(en, func) if isInnerControl(lhs) =>
        val pipe = latencyOfPipe(func)

        dbgs(s"Pipe $lhs: ")
        dbgs(s"- pipe = $pipe")
        bodyLatency(lhs) = pipe

        pipe + latencyOf(lhs)

      case OpForeach(en, cchain, func, iters) if isInnerControl(lhs) =>
        val N = nIters(cchain)
        val pipe = latencyOfPipe(func)

        dbgs(s"Foreach $lhs (N = $N):")
        dbgs(s"- pipe = $pipe")
        bodyLatency(lhs) = pipe

        pipe + N - 1 + latencyOf(lhs)

      case OpReduce(en, cchain,accum,map,ld,reduce,store,_,_,rV,iters) if isInnerControl(lhs) =>
        val N = nIters(cchain)
        val P = parsOf(cchain).product

        val fuseMapReduce = false //canFuse(map,reduce,rV,P)

        val body = latencyOfPipe(map)
        val internal = if (fuseMapReduce) Math.max(reductionTreeHeight(P) - 2, 0)
        else latencyOfPipe(reduce) * reductionTreeHeight(P)

        val cycle = latencyOfCycle(ld) + latencyOfCycle(reduce) + latencyOfCycle(store)

        dbgs(s"Reduce $lhs (N = $N):")
        dbgs(s"- body  = $body")
        dbgs(s"- tree  = $internal")
        dbgs(s"- cycle = $cycle")
        bodyLatency(lhs) = body + internal + cycle

        body + internal + N*cycle + latencyOf(lhs)


      case UnrolledForeach(en,cchain,func,iters,valids) if isInnerControl(lhs) =>
        val N = nIters(cchain)
        val pipe = latencyOfPipe(func)
        dbgs(s"Unrolled Foreach $lhs (N = $N):")
        dbgs(s"- pipe = $pipe")
        bodyLatency(lhs) = pipe

        pipe + N - 1 + latencyOf(lhs)

      case UnrolledReduce(en,cchain,accum,func,iters,valids) if isInnerControl(lhs) =>
        val N = nIters(cchain)

        val body = latencyOfPipe(func)

        dbgs(s"Unrolled Reduce $lhs (N = $N):")
        dbgs(s"- body  = $body")
        bodyLatency(lhs) = body

        body + N - 1 + latencyOf(lhs)

      case StateMachine(en, start, notDone, action, nextState, _) if isInnerControl(lhs) =>
        val cont = latencyOfPipe(notDone)
        val act  = latencyOfPipe(action)
        val next = latencyOfPipe(nextState)

        dbgs(s"Inner FSM $lhs: ")
        dbgs(s"-notDone = $cont")
        dbgs(s"-action  = $act")
        dbgs(s"-next    = $next")
        bodyLatency(lhs) = cont + act + next

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
        val internal = latencyOfPipe(reduce) * reductionTreeHeight(P)
        val cycle = latencyOfCycle(ld) + latencyOfCycle(reduce) + latencyOfCycle(store)

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

        val mapStages: List[Long] = latencyOfBlock(map)
        val internal: Long = latencyOfPipe(ldRes) + latencyOfPipe(reduce) * reductionTreeHeight(Pm)
        val accumulate: Long = latencyOfPipe(ldAcc) + latencyOfPipe(reduce) + latencyOfPipe(store)

        val reduceStage: Long = internal + accumulate + Nr - 1
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

      case StateMachine(en, start, notDone, action, nextState, _) =>
        val cont      = latencyOfPipe(notDone)
        val actStages = latencyOfBlock(action)
        val next      = latencyOfPipe(nextState)

        dbgs(s"Outer FSM $lhs: ")
        dbgs(s"-notDone = $cont")
        dbgs(s"-action: ")
        actStages.reverse.zipWithIndex.foreach{case (s,i) => dbgs(s"  - $i. $s") }
        dbgs(s"-next    = $next")
        bodyLatency(lhs) = Seq(cont, next)

        0L  // TODO

      case Switch(body,selects,cases) =>
        val stages = latencyOfBlock(body).max
        dbgs(s"Switch $lhs: ")
        dbgs(s"-body = $stages")
        stages

      case SwitchCase(body) if isInnerControl(lhs) =>
        val latency = latencyOfPipe(body)
        dbgs(s"Case $lhs:")
        dbgs(s"-body = $latency")
        latency

      case SwitchCase(body) if isOuterControl(lhs) =>
        val stages = latencyOfBlock(body)
        val latency = if (styleOf(lhs) == SeqPipe) stages.sum else (0L +: stages).max
        dbgs(s"Case $lhs:")
        dbgs(s"-body = $latency")
        latency

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
