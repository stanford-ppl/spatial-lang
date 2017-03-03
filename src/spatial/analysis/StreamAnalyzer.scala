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
  def streamHolders: Seq[Exp[_]]

  override protected def process[S: Staged](block: Block[S]) = {
    streamPipes.foreach{pipe =>
      val childs = childrenOf(pipe)
      dbg(u"Stream pipe $pipe with immediate children $childs")

      // Link up enables (when data ready on fifo inputs)
      streamEnablers.foreach{ deq => // Once fifo ens 
        val fifo = deq match {
            case Def(FIFODeq(stream,en,_)) => stream
            case Def(ParFIFODeq(stream,en,_)) => stream
            case Def(StreamDeq(stream,en,_)) => stream
            case Def(DecoderTemplateNew(popFrom, _)) => popFrom
            case Def(DMATemplateNew(popFrom, _)) => popFrom
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

      // Link up holds (when fifo outputs are full)
      streamHolders.foreach{ enq => // Once fifo ens 
        val fifo = enq match {
            case Def(FIFOEnq(stream,en,_)) => stream
            case Def(ParFIFOEnq(stream,en,_)) => stream
            case Def(StreamEnq(stream,en,_)) => stream
            case Def(DecoderTemplateNew(_, pushTo)) => pushTo
        } 
        dbg(c"  # Trying to fit enqueuer $enq from fifo $fifo")
        var nextLevel: Option[Exp[_]] = parentOf(enq)
        while (nextLevel.isDefined) {
            dbg(c"    # Checking if ${nextLevel.get} is a stream child")
            if (childs.contains(nextLevel.get)) {
                dbg(c"    # MATCH on ${nextLevel.get}")
                pushesTo(nextLevel.get) = fifo +: pushesTo(nextLevel.get)
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
