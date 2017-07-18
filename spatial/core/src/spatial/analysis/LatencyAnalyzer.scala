package spatial.analysis

import argon.core._
import spatial.aliases._
import spatial.metadata._
import spatial.models.LatencyModel
import spatial.nodes._
import spatial.utils._
import org.virtualized.SourceContext

case class LatencyAnalyzer(var IR: State, latencyModel: LatencyModel) extends ModelingTraversal {
  override val name = "Latency Analyzer"

  var cycleScope: List[Long] = Nil
  var intervalScope: List[Long] = Nil
  var totalCycles: Long = 0L

  override protected def preprocess[A:Type](b: Block[A]): Block[A] = {
    cycleScope = Nil
    intervalScope = Nil
    super.preprocess(b)
  }

  override protected def postprocess[A:Type](b: Block[A]): Block[A] = {
    val CLK = latencyModel.clockRate
    val startup = latencyModel.baseCycles
    // TODO: Could potentially have multiple accelerator designs in a single program
    // Eventually want to be able to support multiple accel scopes
    totalCycles = cycleScope.sum + startup

    report(s"Estimated cycles: $totalCycles")
    report(s"Estimated runtime (at " + "%.2f".format(CLK) +"MHz): " + "%.8f".format(totalCycles/(CLK*1000000f)) + "s")
    super.postprocess(b)
  }

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

  def latencyOfBlock(b: Block[_], par_mask: Boolean = false): (List[Long], List[Long]) = {
    val outerCycles = cycleScope
    val outerIntervals = intervalScope
    cycleScope = Nil
    intervalScope = Nil
    tab += 1
    getControlNodes(b).foreach{
      case s@Op(d) => visit(s.asInstanceOf[Sym[_]], d)
      case _ =>
    }
    tab -= 1

    val cycles = cycleScope
    val intervals = intervalScope
    cycleScope = outerCycles
    intervalScope = outerIntervals
    (cycles, intervals)
  }

  override protected def visit(lhs: Sym[_], rhs: Op[_]): Unit = {
    val (cycles, ii) = rhs match {
      case Hwblock(blk, isForever) if isInnerControl(lhs) =>
        inHwScope = true
        val (body, _) = latencyOfPipe(blk)

        dbgs(s"Inner Accel $lhs: ")
        dbgs(s"- body = $body")

        inHwScope = false
        (body, 1L)


      case Hwblock(blk, isForever) if isOuterControl(lhs) =>
        inHwScope = true
        val (latencies, iis) = latencyOfBlock(blk)
        val body = latencies.sum

        dbgs(s"Accel $lhs: ")
        dbgs(s"- body = $body")

        inHwScope = false
        (body, 1L)

      case ParallelPipe(en, func) =>
        val (latencies, iis) = latencyOfBlock(func, true)
        val delay = latencies.max + latencyOf(lhs)
        val ii = iis.max

        dbgs(s"Parallel $lhs: (II = $ii)")
        latencies.reverse.zipWithIndex.foreach{case (s,i) => dbgs(s"- $i. $s")}
        (delay, ii)

      // --- Pipe
      case UnitPipe(en, func) if isInnerControl(lhs) =>
        val (latency, ii) = latencyOfPipe(func)

        dbgs(s"Pipe $lhs: (II = $ii)")
        dbgs(s"- pipe = $latency")
        val delay = latency + latencyOf(lhs)

        (delay, ii)

      case OpForeach(en, cchain, func, iters) if isInnerControl(lhs) =>
        val N = nIters(cchain)
        val (latency, ii) = latencyOfPipe(func)

        dbgs(s"Foreach $lhs (N = $N, II = $ii):")
        dbgs(s"- pipe = $latency")

        val delay = latency + (N - 1)*ii + latencyOf(lhs)

        (delay, 1L)

      case OpReduce(en, cchain,accum,map,ld,reduce,store,_,_,rV,iters) if isInnerControl(lhs) =>
        val N = nIters(cchain)
        val P = parsOf(cchain).product

        val fuseMapReduce = false //canFuse(map,reduce,rV,P)

        val (mapLat, mapII) = latencyOfPipe(map)

        val (ldLat, _) = latencyOfCycle(ld)
        val (reduceLat, _) = latencyOfCycle(reduce)
        val (storeLat, _) = latencyOfCycle(store)

        val treeLat = latencyOfPipe(reduce)._1 * reductionTreeHeight(P)

        val cycle = ldLat + reduceLat + storeLat

        val ii = Math.max(mapII, cycle)

        dbgs(s"Reduce $lhs (N = $N, II = $ii):")
        dbgs(s"- map   = $mapLat (ii = $mapII)")
        dbgs(s"- tree  = $treeLat")
        dbgs(s"- cycle = $cycle")

        val delay = mapLat + treeLat + (N - 1)*ii + latencyOf(lhs)

        (delay, 1L)

      case UnrolledForeach(en,cchain,func,iters,valids) if isInnerControl(lhs) =>
        val N = nIters(cchain)
        val (pipe, ii) = latencyOfPipe(func)
        dbgs(s"Unrolled Foreach $lhs (N = $N, II = $ii):")
        dbgs(s"- pipe = $pipe")

        val delay = pipe + (N - 1)*ii + latencyOf(lhs)

        (delay, 1L)

      case UnrolledReduce(en,cchain,accum,func,iters,valids) if isInnerControl(lhs) =>
        val N = nIters(cchain)

        val (body, ii) = latencyOfPipe(func)

        dbgs(s"Unrolled Reduce $lhs (N = $N, II = $ii):")
        dbgs(s"- body  = $body")

        val delay = body + (N - 1)*ii + latencyOf(lhs)

        (delay, 1L)

      case StateMachine(en, start, notDone, action, nextState, _) if isInnerControl(lhs) =>
        // TODO: Any way to predict number of iterations, or annotate expected number?
        val N = 1
        val (cont, contII) = latencyOfPipe(notDone)
        val (act, actII)   = latencyOfPipe(action)
        val (next, nextII) = latencyOfPipe(nextState)

        val ii = List(contII, actII, nextII).max

        dbgs(s"Inner FSM $lhs: (N = $N, II = $ii)")
        dbgs(s"-notDone = $cont (ii = $contII)")
        dbgs(s"-action  = $act (ii = $actII)")
        dbgs(s"-next    = $next (ii = $nextII)")

        val delay = cont + act + next + (N-1)*ii + latencyOf(lhs)

        (delay, 1L)

      // --- Sequential
      case UnitPipe(en, func) if isOuterControl(lhs) =>
        val (stages, iis) = latencyOfBlock(func)

        dbgs(s"Outer Pipe $lhs:")
        stages.reverse.zipWithIndex.foreach{case (s,i) => dbgs(s"- $i. $s")}

        val delay = stages.sum + latencyOf(lhs)
        val ii = (1L +: iis).max

        (delay, ii)


      // --- Metapipeline and Sequential
      case OpForeach(en, cchain, func, _) =>
        val N = nIters(cchain)
        val (stages, iis) = latencyOfBlock(func)
        val ii = (1L +: iis).max

        dbgs(s"Outer Foreach $lhs (N = $N, II = $ii):")
        stages.reverse.zipWithIndex.foreach{case (s,i) => dbgs(s"- $i. $s")}

        val delay = if (isMetaPipe(lhs)) { stages.max * (N - 1)*ii + stages.sum + latencyOf(lhs) }
                    else                 { stages.sum * N + latencyOf(lhs) }
        (delay, 1L)

      case OpReduce(en, cchain,accum,map,ld,reduce,store,_,_,rV,iters) =>
        val N = nIters(cchain)
        val P = parsOf(cchain).product

        val (mapStages, mapII) = latencyOfBlock(map)
        val internal = latencyOfPipe(reduce)._1 * reductionTreeHeight(P)
        val cycle = latencyOfCycle(ld)._1 + latencyOfCycle(reduce)._1 + latencyOfCycle(store)._1

        val reduceStage = internal + cycle
        val stages = reduceStage +: mapStages
        val ii = (cycle +: mapII).max

        dbgs(s"Outer Reduce $lhs (N = $N, II = $ii):")
        stages.reverse.zipWithIndex.foreach{case (s,i) => dbgs(s"- $i. $s")}

        val delay = if (isMetaPipe(lhs)) { stages.max * (N - 1)*ii + stages.sum + latencyOf(lhs) }
                    else                 { stages.sum * N + latencyOf(lhs) }
        (delay, 1L)

      case OpMemReduce(en, cchainMap,cchainRed,accum,map,ldRes,ldAcc,reduce,store,_,_,rV,itersMap,itersRed) =>
        val Nm = nIters(cchainMap)
        val Nr = nIters(cchainRed)
        val Pm = parsOf(cchainMap).product // Parallelization factor for map
        val Pr = parsOf(cchainRed).product // Parallelization factor for reduce

        val (mapStages, mapIIs) = latencyOfBlock(map)
        val internal: Long = latencyOfPipe(ldRes)._1 + latencyOfPipe(reduce)._1 * reductionTreeHeight(Pm)
        val accumulate: Long = latencyOfCycle(ldAcc)._1 + latencyOfCycle(reduce)._1 + latencyOfCycle(store)._1
        val reduceStage: Long = internal + accumulate + (Nr - 1)*accumulate
        val stages = reduceStage +: mapStages
        val mapII = (1L +: mapIIs).max

        dbgs(s"Block Reduce $lhs (Nm = $Nm, IIm = $mapII, Nr = $Nr, IIr = $accumulate)")
        stages.reverse.zipWithIndex.foreach{case (s,i) => dbgs(s"- $i. $s")}

        val delay = if (isMetaPipe(lhs)) { stages.max * (Nm - 1)*mapII + stages.sum + latencyOf(lhs) }
                    else                 { stages.sum * Nm + latencyOf(lhs) }

        (delay, 1L)

      case UnrolledForeach(en,cchain,func,iters,valids) =>
        val N = nIters(cchain)
        val (stages, iis) = latencyOfBlock(func)
        val ii = (1L +: iis).max

        dbgs(s"Unrolled Outer Foreach $lhs (N = $N, II = $ii):")
        stages.reverse.zipWithIndex.foreach{case (s,i) => dbgs(s"- $i. $s")}

        val delay = if (isMetaPipe(lhs)) { stages.max * (N - 1)*ii + stages.sum + latencyOf(lhs) }
                    else                 { stages.sum * N + latencyOf(lhs) }

        (delay, 1L)

      case UnrolledReduce(en,cchain,accum,func,iters,valids) =>
        val N = nIters(cchain)
        val (stages, iis) = latencyOfBlock(func)
        val ii = (1L +: iis).max

        dbgs(s"Unrolled Outer Reduce $lhs (N = $N, II = $ii):")
        stages.reverse.zipWithIndex.foreach{case (s,i) => dbgs(s"- $i. $s")}

        val delay = if (isMetaPipe(lhs)) { stages.max * (N - 1)*ii + stages.sum + latencyOf(lhs) }
                    else                 { stages.sum * N + latencyOf(lhs) }
        (delay, 1L)

      case StateMachine(en, start, notDone, action, nextState, _) =>
        val N = 1 // TODO
        val (cont, contII) = latencyOfPipe(notDone)
        val (acts, actIIs) = latencyOfBlock(action)
        val (next, nextII) = latencyOfPipe(nextState)

        val ii = (List(contII, nextII) ++ actIIs).max

        dbgs(s"Outer FSM $lhs: (N = $N, II = $ii)")
        dbgs(s" - notDone = $cont")
        dbgs(s" - action: ")
        acts.reverse.zipWithIndex.foreach{case (s,i) => dbgs(s"   - $i. $s") }
        dbgs(s" - next    = $next")

        val delay = (cont + acts.sum + next) * N + latencyOf(lhs)

        (delay, 1L)

      case Switch(body,selects,cases) =>
        val (stages, iis) = latencyOfBlock(body)
        val delay = stages.max
        val ii = (1L +: iis).max
        dbgs(s"Switch $lhs: (II = $ii)")
        stages.reverse.zipWithIndex.foreach{case (dly, i) => dbgs(s" - $i. $dly") }
        (delay, ii)

      case SwitchCase(body) if isInnerControl(lhs) =>
        val (latency, ii) = latencyOfPipe(body)
        dbgs(s"Case $lhs: (II = $ii)")
        dbgs(s" - body: $latency")
        (latency, ii)

      case SwitchCase(body) if isOuterControl(lhs) =>
        val (stages, iis) = latencyOfBlock(body)
        val latency = if (styleOf(lhs) == SeqPipe) stages.sum else (0L +: stages).max
        val ii = (1L +: iis).max
        dbgs(s"Case $lhs: (II = $ii)")
        stages.reverse.zipWithIndex.foreach{case (dly,i) => dbgs(s" - $i. $dly") }
        (latency, ii)

      case _ =>
        // No general rule for combining blocks
        rhs.blocks.foreach{blk => visitBlock(blk) }
        val delay = latencyOf(lhs)
        (delay, 1L)
    }
    cycleScope ::= cycles
    intervalScope ::= ii
  }



}
