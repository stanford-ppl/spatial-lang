package spatial.analysis

trait UnrolledControlAnalyzer extends ControlSignalAnalyzer {
  import IR._

  override val name = "Unrolled Control Analyzer"

  var memStreams = Set[Exp[_]]()
  var argIns = Set[Exp[_]]()
  var argOuts = Set[Exp[_]]()

  private def visitUnrolled(ctrl: Exp[_])(blk: => Unit) = {
    visitCtrl((ctrl,false))(blk)
  }

  override protected def preprocess[S:Staged](block: Block[S]) = {
    memStreams = Set[Exp[_]]()
    argIns = Set[Exp[_]]()
    argOuts = Set[Exp[_]]()
    super.preprocess(block)
  }

  override def addCommonControlData(lhs: Sym[_], rhs: Op[_]) = {
    rhs match {
      case e: DRAMNew[_] => memStreams += lhs
      case e: ArgInNew[_] => argIns += lhs
      case e: ArgOutNew[_] => argOuts += lhs
      case _ =>
    }
    super.addCommonControlData(lhs, rhs)
  }

  override protected def analyze(lhs: Sym[_], rhs: Op[_]) = rhs match {
    case e: UnrolledForeach =>
      visitUnrolled(lhs){ visitBlock(e.func) }

    case e: UnrolledReduce[_,_] =>
      visitUnrolled(lhs){ visitBlock(e.func) }
      isAccum(e.accum) = true
      parentOf(e.accum) = lhs

    case _ => super.analyze(lhs,rhs)
  }
}
