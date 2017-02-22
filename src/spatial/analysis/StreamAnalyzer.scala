package spatial.analysis

import argon.traversal.CompilerPass
import spatial.SpatialExp
import org.virtualized.SourceContext

trait StreamAnalyzer extends CompilerPass {
  val IR: SpatialExp
  import IR._

  override val name = "Stream Analyzer"
  def streamPipes: Seq[Exp[_]]
  def streamEnablers: Seq[Exp[_]]

  override protected def process[S: Staged](block: Block[S]) = {
    streamPipes.foreach{pipe =>
      val childs = childrenOf(pipe)
      dbg(u"Stream pipe $pipe with immediate children $childs")

      streamEnablers.foreach{ deq => // Once fifo ens 
        val (fifo, en) = deq match {
            case Def(FIFODeq(stream,en,_)) => (stream, en)
            case Def(ParFIFODeq(stream,en,_)) => (stream, en)
            case Def(StreamDeq(stream,en)) => (stream, en)
        } 
        dbg(c"  # Trying to fit dequeuer $deq from fifo $fifo")
        var nextLevel: Option[Exp[_]] = parentOf(deq)
        while (nextLevel.isDefined) {
            dbg(c"    # Checking if ${nextLevel.get} is a stream child")
            if (childs.contains(nextLevel.get)) {
                dbg(c"    # MATCH on ${nextLevel.get}")
                listensTo(nextLevel.get) = fifo +: listensTo(nextLevel.get)
                nextLevel = None
            } else {
                nextLevel = parentOf(nextLevel.get)
            }
        }
      }
    }
    


    block
  }
}
