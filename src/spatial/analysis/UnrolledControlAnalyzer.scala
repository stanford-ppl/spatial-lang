package spatial.analysis

trait UnrolledControlAnalyzer extends ControlSignalAnalyzer {
  import IR._

  override val name = "Unrolled Control Analyzer"

  var memStreams = Set[Exp[_]]()

  private def visitUnrolled(ctrl: Exp[_])(blk: => Unit) = {
    visitCtrl((ctrl,false))(blk)
  }

  override def addCommonControlData(lhs: Sym[_], rhs: Op[_]) = {
    rhs match {
      case e: DRAMNew[_] => memStreams += lhs
      case _ =>
    }
    super.addCommonControlData(lhs, rhs)
  }

  override protected def analyze(lhs: Sym[_], rhs: Op[_]) = rhs match {
    case e: UnrolledForeach =>
      visitUnrolled(lhs){ visitBlock(e.func) }
      e.iters.flatten.foreach { iter => parentOf(iter) = lhs }
      e.valids.flatten.foreach { vld => parentOf(vld) = lhs }

    case e: UnrolledReduce[_,_] =>
      visitUnrolled(lhs){ visitBlock(e.func) }
      isAccum(e.accum) = true
      parentOf(e.accum) = lhs
      e.iters.flatten.foreach { iter => parentOf(iter) = lhs }
      e.valids.flatten.foreach { vld => parentOf(vld) = lhs }

    case _ => super.analyze(lhs,rhs)
  }
}
