package spatial.analysis

trait UnrolledControlAnalyzer extends ControlSignalAnalyzer {
  import IR._

  override val name = "Unrolled Control Analyzer"

  var memStreams = Set[(Exp[_], Int, Int)]()
  var genericStreams = Set[(Exp[_], String)]()
  var argPorts = Set[(Exp[_], String)]()

  private def visitUnrolled(ctrl: Exp[_])(blk: => Unit) = {
    visitCtrl((ctrl,false))(blk)
  }

  override protected def preprocess[S:Type](block: Block[S]) = {
    memStreams = Set[(Exp[_], Int, Int)]()
    genericStreams = Set[(Exp[_], String)]()
    argPorts = Set[(Exp[_], String)]()
    super.preprocess(block)
  }

  override def addCommonControlData(lhs: Sym[_], rhs: Op[_]) = {
    rhs match {
      case DRAMNew(dims,zero) =>
        memStreams += ((lhs, 0, 0))
      case FringeDenseLoad(dram,_,_) =>
        val prevLoads = memStreams.toList.filter{_._1 == dram}.head._2
        val prevStores = memStreams.toList.filter{_._1 == dram}.head._3
        memStreams -= ((dram, prevLoads, prevStores))
        memStreams += ((dram, prevLoads+1, prevStores))
      case FringeDenseStore(dram,_,_,_) =>
        val prevLoads = memStreams.toList.filter{_._1 == dram}.head._2
        val prevStores = memStreams.toList.filter{_._1 == dram}.head._3
        memStreams -= ((dram, prevLoads, prevStores))
        memStreams += ((dram, prevLoads, prevStores+1))
      case StreamInNew(bus) => 
        bus match {
          case BurstDataBus() =>
          case BurstAckBus =>
          case ScatterAckBus =>
          case _ =>
            genericStreams += ((lhs, "input"))
        }
      case StreamOutNew(bus) => 
        bus match {
          case BurstFullDataBus() =>
          case BurstCmdBus =>
          case _ =>
            genericStreams += ((lhs, "output"))
        }
      case e: ArgInNew[_] => argPorts += ((lhs, "input"))
      case e: ArgOutNew[_] => argPorts += ((lhs, "output"))
      case e: HostIONew[_] => argPorts += ((lhs, "bidirectional"))
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
