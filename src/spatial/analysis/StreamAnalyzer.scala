package spatial.analysis

import argon.traversal.CompilerPass
import spatial.SpatialExp
import org.virtualized.SourceContext

trait StreamAnalyzer extends CompilerPass {
  val IR: SpatialExp
  import IR._

  override val name = "Stream Analyzer"
  def streamPipes: Seq[Exp[_]]
  def streamLoadCtrls: Seq[Exp[_]]
  def streamParEnqs: Seq[Exp[_]]
  def streamEnablers: Seq[Exp[_]]
  def streamHolders: Seq[Exp[_]]

  override protected def process[S: Staged](block: Block[S]) = {
    // Set metadata for tileloads
    streamLoadCtrls.foreach{ ctrl =>  // So hacky, please fix
      dbg(u"Trying to match ctrl $ctrl to a ParFifo or ParSRAM write")
      val a = parentOf(ctrl).get
      val b = childrenOf(a)
      if (b.length > 0) {
        val c = childrenOf(b.last)
        if (c.length > 0) {
          val d = c.last
          val specificCtrl = d
          var connectLoad: Option[Exp[Any]] = None
          streamParEnqs.foreach { pe => 
            dbg(u"  Attempting to match $pe (parent ${parentOf(pe).get} to $d")
            pe match {
              case Def(ParFIFOEnq(fifo, data, ens)) => 
                if (s"${parentOf(pe).get}" == s"$d") {
                  loadCtrlOf(fifo) = List(specificCtrl)
                  dbg(u"  It's a match! $fifo to $specificCtrl")
                }
              case Def(ParSRAMStore(sram,inds,data,ens)) => 
                if (s"${parentOf(pe).get}" == s"$d") {
                  loadCtrlOf(sram) = List(specificCtrl)
                  dbg(u"  It's a match! $sram to $specificCtrl")
                }
              case _ =>
            }
          }
        }
      }
    }

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
                listensTo(parentOf(deq).get) = fifo +: listensTo(parentOf(deq).get)
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
                pushesTo(parentOf(enq).get) = fifo +: pushesTo(parentOf(enq).get)
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
