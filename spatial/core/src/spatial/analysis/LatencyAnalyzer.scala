package spatial.analysis

import spatial.SpatialExp

trait LatencyAnalyzer extends ModelingTraversal {
  val IR: SpatialExp
  import IR._

  override val name = "Latency Analyzer"

  override def silence() {
    //IR.silenceLatencyModel()
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

  def latencyOfBlock(b: Block[_], par_mask: Boolean = false): List[Long] = {
    val outerScope = cycleScope
    cycleScope = Nil
    tab += 1
    //traverseBlock(b) -- can cause us to see things like counters as "stages"
    getControlNodes(b).zipWithIndex.foreach{ case (n, i) =>
      n match {
        case s@Op(d) => visit(s.asInstanceOf[Sym[_]], d)
        case _ =>
      }
    }
    tab -= 1

    val cycles = cycleScope
    cycleScope = outerScope
    cycles
  }

  override protected def visit(lhs: Sym[_], rhs: Op[_]): Unit = {
    val cycles = rhs match {
      case Hwblock(blk, isForever) =>
        inHwScope = true
        val body = latencyOfBlock(blk).sum
        aggregateLatencyOf(lhs) = body.toInt
        inHwScope = false
        body

      case ParallelPipe(en, func) =>
        dbgs(s"Parallel $lhs: ")
        val blks = latencyOfBlock(func, true)
        blks.reverse.zipWithIndex.foreach{case (s,i) => dbgs(s"- $i. $s")}
        blks.max + latencyOf(lhs)

      // --- Pipe
      case UnitPipe(en, func) if isInnerPipe(lhs) =>
        dbgs(s"Pipe $lhs: ")
        val pipe = latencyOfPipe(func)
        dbgs(s"- pipe = $pipe")
        aggregateLatencyOf(lhs) = pipe.toInt
        pipe + latencyOf(lhs)

      case OpForeach(cchain, func, iters) if isInnerPipe(lhs) =>
        val N = nIters(cchain)
        dbgs(s"Foreach $lhs (N = $N):")
        val pipe = latencyOfPipe(func)
        dbgs(s"- pipe = $pipe")
        aggregateLatencyOf(lhs) = pipe.toInt
        pipe + N - 1 + latencyOf(lhs)

      case OpReduce(cchain,accum,map,ld,reduce,store,_,_,rV,iters) if isInnerPipe(lhs) =>
        val N = nIters(cchain)
        val P = parsOf(cchain).product

        dbgs(s"Reduce $lhs (N = $N):")

        val fuseMapReduce = false //canFuse(map,reduce,rV,P)

        val body = latencyOfPipe(map)
        val internal = if (fuseMapReduce) Math.max(reductionTreeHeight(P) - 2, 0)
        else latencyOfPipe(reduce) * reductionTreeHeight(P)


        val cycle = latencyOfCycle(ld) + latencyOfCycle(reduce) + latencyOfCycle(store)

        dbgs(s"- body  = $body")
        dbgs(s"- tree  = $internal")
        dbgs(s"- cycle = $cycle")

        aggregateLatencyOf(lhs) = (body + internal).toInt // TODO: Body vs internal vs cycle?
        body + internal + N*cycle + latencyOf(lhs)

      // --- Sequential
      case UnitPipe(en, func) if isOuterControl(lhs) =>
        dbgs(s"Outer Pipe $lhs:")

        val stages = latencyOfBlock(func)
        stages.reverse.zipWithIndex.foreach{case (s,i) => dbgs(s"- $i. $s")}

        aggregateLatencyOf(lhs) = stages.reduce{_+_}.toInt
        stages.sum + latencyOf(lhs)


      // --- Metapipeline and Sequential
      case OpForeach(cchain, func, _) =>
        val N = nIters(cchain)
        dbgs(s"Outer Foreach $lhs (N = $N):")
        val stages = latencyOfBlock(func)
        stages.reverse.zipWithIndex.foreach{case (s,i) => dbgs(s"- $i. $s")}
        aggregateLatencyOf(lhs) = stages.reduce{_+_}.toInt

        if (isMetaPipe(lhs)) { stages.max * (N - 1) + stages.sum + latencyOf(lhs) }
        else                 { stages.sum * N + latencyOf(lhs) }

      case OpReduce(cchain,accum,map,ld,reduce,store,_,_,rV,iters) =>
        val N = nIters(cchain)
        val P = parsOf(cchain).product
        dbgs(s"Outer Reduce $lhs (N = $N):")

        val mapStages = latencyOfBlock(map)
        val internal = latencyOfPipe(reduce) * reductionTreeHeight(P)
        val cycle = latencyOfCycle(ld) + latencyOfCycle(reduce) + latencyOfCycle(store)

        val reduceStage = internal + cycle
        val stages = mapStages :+ reduceStage

        stages.reverse.zipWithIndex.foreach{case (s,i) => dbgs(s"- $i. $s")}
        aggregateLatencyOf(lhs) = stages.reduce{_+_}.toInt

        if (isMetaPipe(lhs)) { stages.max * (N - 1) + stages.sum + latencyOf(lhs) }
        else                 { stages.sum * N + latencyOf(lhs) }

      case OpMemReduce(cchainMap,cchainRed,accum,map,ldRes,ldAcc,reduce,store,_,_,rV,itersMap,itersRed) =>
        val Nm = nIters(cchainMap)
        val Nr = nIters(cchainRed)
        val Pm = parsOf(cchainMap).product // Parallelization factor for map
        val Pr = parsOf(cchainRed).product // Parallelization factor for reduce

        dbgs(s"Block Reduce $lhs (Nm = $Nm, Nr = $Nr)")

        val mapStages: List[Long] = latencyOfBlock(map)
        val internal: Long = latencyOfPipe(ldRes) + latencyOfPipe(reduce) * reductionTreeHeight(Pm)
        val accumulate: Long = latencyOfPipe(ldAcc) + latencyOfPipe(reduce) + latencyOfPipe(store)

        val reduceStage: Long = internal + accumulate + Nr - 1
        val stages = mapStages :+ reduceStage

        stages.reverse.zipWithIndex.foreach{case (s,i) => dbgs(s"- $i. $s")}
        aggregateLatencyOf(lhs) = stages.reduce{_+_}.toInt

        if (isMetaPipe(lhs)) { stages.max * (Nm - 1) + stages.sum + latencyOf(lhs) }
        else                 { stages.sum * Nm + latencyOf(lhs) }

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
