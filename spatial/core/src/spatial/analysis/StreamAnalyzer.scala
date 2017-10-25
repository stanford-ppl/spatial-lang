package spatial.analysis

import argon.core._
import argon.traversal.CompilerPass
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

trait StreamAnalyzer extends CompilerPass {
  override val name = "Stream Analyzer"

  protected def getLastChild(lhs: Exp[_]): Exp[_] = {
    var nextLevel = childrenOf(lhs)
    if (nextLevel.isEmpty) {
      lhs
    } else {
      getLastChild(nextLevel.last)
    }
  }

  def streamPipes: Seq[Exp[_]]
  def streamLoadCtrls: Seq[Exp[_]] // List of all FringeDenseLoad nodes
  def tileTransferCtrls: Seq[Exp[_]] // List of all tile transfers
  def streamParEnqs: Seq[Exp[_]]
  def streamEnablers: Seq[Exp[_]]
  def streamHolders: Seq[Exp[_]]

  override protected def process[S: Type](block: Block[S]) = {
    // Color each of the transfers
    // Init all nodes
    tileTransferCtrls.foreach{ a => transferChannel(parentOf(a).get) = 0}
    // Assign correctly
    dbg(u"Coloring tile transfers: ${tileTransferCtrls}")
    tileTransferCtrls.zipWithIndex.foreach{ case(a,i) => 
      val channel = transferChannel(parentOf(a).get)
      tileTransferCtrls.drop(i+1).foreach{ b => 
        val lca =leastCommonAncestorWithPaths[Exp[_]](a, b, {node => parentOf(node)})._1.get
        if (styleOf(lca) == ForkJoin || styleOf(lca) == MetaPipe || styleOf(lca) == StreamPipe) {
          dbg(u"  LCA of $b and $a is $lca (${styleOf(lca)}) so incrementing color of $b to ${transferChannel(parentOf(b).get)} + 1")
          transferChannel(parentOf(b).get) = transferChannel(parentOf(b).get) + 1
        } else { // Can be in same channel
          // Console.println(s"assigning $b to $channel")
          // No change
        }
      }
    }

    // Set metadata for tileloads
    streamLoadCtrls.foreach{ ctrl =>  // So hacky, please fix
      dbg(u"Trying to match ctrl $ctrl to a consuming Fifo or SRAM write")
      val candidate = getLastChild(parentOf(ctrl).get)
      var connectLoad: Option[Exp[Any]] = None
      streamParEnqs.foreach { pe => 
        dbg(u"  Attempting to match enq/store $pe (inside ctrl ${parentOf(pe).get} to $candidate)")
        pe match {
          case Def(ParFIFOEnq(fifo, data, ens)) => 
            if (s"${parentOf(pe).get}" == s"$candidate") {
              loadCtrlOf(fifo) = List(candidate)
              dbg(u"    It's a match! $fifo fifo to ctrl $candidate")
            }
          case Def(FIFOEnq(fifo, data, ens)) => 
            if (s"${parentOf(pe).get}" == s"$candidate") {
              loadCtrlOf(fifo) = List(candidate)
              dbg(u"    It's a match! $fifo fifo to ctrl $candidate")
            }
          case Def(ParFILOPush(filo, data, ens)) => 
            if (s"${parentOf(pe).get}" == s"$candidate") {
              loadCtrlOf(filo) = List(candidate)
              dbg(u"    It's a match! $filo filo to ctrl $candidate")
            }
          case Def(FILOPush(filo, data, ens)) => 
            if (s"${parentOf(pe).get}" == s"$candidate") {
              loadCtrlOf(filo) = List(candidate)
              dbg(u"    It's a match! $filo filo to ctrl $candidate")
            }
          case Def(ParSRAMStore(sram,inds,data,ens)) => 
            if (s"${parentOf(pe).get}" == s"$candidate") {
              loadCtrlOf(sram) = List(candidate)
              dbg(u"    It's a match! $sram sram to ctrl $candidate")
            }
          case Def(SRAMStore(sram, dims, is, ofs, v, en)) => 
            if (s"${parentOf(pe).get}" == s"$candidate") {
              loadCtrlOf(sram) = List(candidate)
              dbg(u"    It's a match! $sram sram to ctrl $candidate")
            }
          case _ =>
        }
      }
    }

    streamPipes.foreach{pipe =>
      val childs = childrenOf(pipe) :+ pipe
      dbg(u"Stream pipe $pipe with immediate children $childs")

      // Link up enables (when data ready on fifo inputs)
      streamEnablers.foreach{ deq => // Once fifo ens 
        val fifo = deq match {
            case Def(FIFODeq(stream,en)) => stream
            case Def(ParFIFODeq(stream,en)) => stream
            case Def(FILOPop(stream,en)) => stream
            case Def(ParFILOPop(stream,en)) => stream
            case Def(StreamRead(stream,en)) => stream
            case Def(ParStreamRead(stream,en)) => stream
            case Def(DecoderTemplateNew(popFrom, _)) => popFrom
            case Def(DMATemplateNew(popFrom, _)) => popFrom
        } 
        dbg(c"  # Trying to fit dequeuer $deq from fifo $fifo")
        var nextLevel: Option[Exp[_]] = parentOf(deq)
        while (nextLevel.isDefined) {
            dbg(c"    # Checking if ${nextLevel.get} is a stream child")
            if (childs.contains(nextLevel.get)) {
                dbg(c"    # MATCH on ${nextLevel.get}, so ${parentOf(deq).get} listens to $fifo")
                listensTo(parentOf(deq).get) = (fifo,deq) +: listensTo(parentOf(deq).get)
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
            case Def(FILOPush(stream,en,_)) => stream
            case Def(ParFILOPush(stream,en,_)) => stream
            case Def(StreamWrite(stream,en,_)) => stream
            case Def(ParStreamWrite(stream,en,_)) => stream
            case Def(BufferedOutWrite(buffer,_,_,_)) => buffer
            case Def(DecoderTemplateNew(_, pushTo)) => pushTo
        } 
        dbg(c"  # Trying to fit enqueuer $enq from fifo $fifo")
        var nextLevel: Option[Exp[_]] = parentOf(enq)
        while (nextLevel.isDefined) {
            dbg(c"    # Checking if ${nextLevel.get} is a stream child")
            if (childs.contains(nextLevel.get)) {
                dbg(c"    # MATCH on ${nextLevel.get}, so ${parentOf(enq).get} pushes to to $fifo")
                pushesTo(parentOf(enq).get) = (fifo,enq) +: pushesTo(parentOf(enq).get)
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
