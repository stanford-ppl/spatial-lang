package spatial.analysis

import argon.core._
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

trait UnrolledControlAnalyzer extends ControlSignalAnalyzer {
  override val name = "Unrolled Control Analyzer"

  var memStreams = Set[(Exp[_], Int, Int)]()
  var genericStreams = Set[(Exp[_], String)]()
  var argPorts = Set[(Exp[_], String)]()

  private def visitUnrolled(e: Exp[_])(blk: => Unit) = visitBlk(e)(blk)

  override protected def preprocess[S:Type](block: Block[S]): Block[S] = {
    memStreams = Set[(Exp[_], Int, Int)]()
    genericStreams = Set[(Exp[_], String)]()
    argPorts = Set[(Exp[_], String)]()
    super.preprocess(block)
  }

  override protected def postprocess[S:Type](block: Block[S]): Block[S] = {
    // Eliminate duplicates which no longer have readers as long as at least one duplicate is retained
    localMems.foreach{mem =>
      val orig = duplicatesOf(mem)
      val inds: Set[Int] = orig.indices.toSet
      val readers = readersOf(mem)
      val writers = writersOf(mem)

      val readDuplicates = readers.flatMap{read => dispatchOf(read,mem) }.toSet
      val unreadDuplicates = inds diff readDuplicates

      var removedDuplicates = unreadDuplicates
      writers.foreach{write =>
        val dispatch = dispatchOf(write, mem)
        val remaining = dispatch diff removedDuplicates
        if (remaining.isEmpty) {
          removedDuplicates = removedDuplicates - dispatch.head
        }
      }
      if (removedDuplicates.nonEmpty) {
        dbgs(u"Memory $mem: ")
        dbgs(c"  ${str(mem)}")
        dbgs("  Removing dead duplicates: " + removedDuplicates.mkString(", "))

        val accesses = readers ++ writers
        val duplicates = orig.zipWithIndex.filter{case (dup,i) => !removedDuplicates.contains(i) }
        val mapping = duplicates.map(_._2).zipWithIndex.toMap
        duplicatesOf(mem) = duplicates.map(_._1)

        accesses.foreach{access =>
          dispatchOf(access,mem) = dispatchOf(access,mem).flatMap{o => mapping.get(o) }
          portsOf.set(access.node,mem, {
            portsOf(access,mem).flatMap{case (i,ps) => mapping.get(i).map{i2 => i2 -> ps}}
          })
        }
      }
    }
    super.postprocess(block)
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
