package spatial.analysis
import scala.collection.mutable.HashMap

trait UnrolledControlAnalyzer extends ControlSignalAnalyzer {
  import IR._

  override val name = "Unrolled Control Analyzer"

  var memStreams = Set[(Exp[_], String)]()
  var genericStreams = Set[(Exp[_], String)]()
  var argPorts = Set[(Exp[_], String)]()

  private def visitUnrolled(ctrl: Exp[_])(blk: => Unit) = {
    visitCtrl((ctrl,false))(blk)
  }

  override protected def preprocess[S:Type](block: Block[S]) = {
    memStreams = Set[(Exp[_], String)]()
    genericStreams = Set[(Exp[_], String)]()
    argPorts = Set[(Exp[_], String)]()
    super.preprocess(block)
  }

  override def addCommonControlData(lhs: Sym[_], rhs: Op[_]) = {
    rhs match {
      case GetMem(dram, _) => 
        memStreams += ((dram, "output"))
      case SetMem(dram, _) => 
        memStreams += ((dram, "input"))
      case StreamInNew(bus) => 
        bus match {
          case BurstDataBus() =>
          case BurstAckBus =>
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

    case e: UnrolledReduce[_,_] =>
      visitUnrolled(lhs){ visitBlock(e.func) }
      isAccum(e.accum) = true
      parentOf(e.accum) = lhs

    case _ => super.analyze(lhs,rhs)
  }
}
