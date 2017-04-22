package spatial.analysis

import spatial.SpatialExp

trait InnerpipeRetimeAnalyzer extends SpatialTraversal {
  val IR: SpatialExp
  import IR._

  override val name = "Innerpipe Retime"

  // override def silence() {
  //   //IR.silenceLatencyModel()
  //   super.silence()
  // }

  // var cycleScope: List[Long] = Nil
  // var totalCycles: Long = 0L

  // def latencyOfBlock(b: Block[_], par_mask: Boolean = false): List[Long] = {
  //   val outerScope = cycleScope
  //   cycleScope = Nil
  //   tab += 1
  //   //traverseBlock(b) -- can cause us to see things like counters as "stages"
  //   getControlNodes(b).zipWithIndex.foreach{ case (n, i) =>
  //     n match {
  //       case s@Op(d) => visit(s.asInstanceOf[Sym[_]], d)
  //       case _ =>
  //     }
  //   }
  //   tab -= 1

  //   val cycles = cycleScope
  //   cycleScope = outerScope
  //   cycles
  // }

  override protected def visit(lhs: Sym[_], rhs: Op[_]): Unit = {
    rhs match {
      case Hwblock(func,isForever) =>
        if (isInnerPipe(lhs)) {
          aggregateLatencyOf(lhs) = 0
        } else {
          visitBlock(func)
        }


      case UnitPipe(en,func) =>
        if (isInnerPipe(lhs)) {
          aggregateLatencyOf(lhs) = 0
        } else {
          visitBlock(func)
        }


      case UnrolledForeach(en,cchain,func,iters,valids) =>
        if (isInnerPipe(lhs)) {
          aggregateLatencyOf(lhs) = 0
        } else {
          visitBlock(func)
        }


      case UnrolledReduce(en,cchain,accum,func,_,iters,valids,rV) =>
        if (isInnerPipe(lhs)) {
          aggregateLatencyOf(lhs) = 0
        } else {
          visitBlock(func)
        }

      case _ =>
        super.visit(lhs,rhs)

    }

  }

  // override protected def preprocess[A:Type](b: Block[A]) = {
    // cycleScope = Nil
  //   super.preprocess(b)
  // }

  // override protected def postprocess[A:Type](b: Block[A]) = {
    // val CLK = latencyModel.clockRate
    // val startup = latencyModel.baseCycles
    // // TODO: Could potentially have multiple accelerator designs in a single program
    // // Eventually want to be able to support multiple accel scopes
    // totalCycles = cycleScope.sum + startup

    // report(s"Estimated cycles: $totalCycles")
    // report(s"Estimated runtime (at " + "%.2f".format(CLK) +"MHz): " + "%.8f".format(totalCycles/(CLK*1000000f)) + "s")
  //   super.postprocess(b)
  // }

}
