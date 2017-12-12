package spatial.analysis

import argon.core._
import argon.nodes._
import argon.traversal.CompilerPass
import spatial.aliases._
import spatial.metadata._
import spatial.utils._

import scala.collection.mutable.ArrayBuffer

trait FunctionScheduler extends CompilerPass {
  override val name = "Function Scheduler"
  def modules: Seq[Exp[_]]

  protected def canHaveSameImpl(a: Trace, b: Trace): Boolean = {
    val pathA = a._2.reverse
    val pathB = b._2.reverse
    val lca = pathA.zip(pathB).filter{case (x,y) => x == y && !isFuncDecl(x.node) }.lastOption.map(_._1)
    //val pathToA = lca.map{p => pathA.drop(pathA.indexOf(p)+1) }
    //val pathToB = lca.map{p => pathB.drop(pathB.indexOf(p)+1) }
    dbgs(s"A: ${a.node}, Path A: " + pathA.mkString(", "))
    dbgs(s"B: ${b.node}, Path B: " + pathB.mkString(", "))
    dbgs(s"LCA:    " + lca)
    // Mutually exclusive if occurs in two separate parts of a switch, or in a sequential
    // TODO: Assumes unit pipe is still pipelined for now (no control signals for sequential execution of inner pipe)
    lca.exists{p => !isInnerControl(p.node) && (isSeqPipe(p.node) || isSwitch(p.node)) }
  }

  protected def createGroups(calls: Seq[Trace]): Seq[Seq[Trace]] = {
    val groups = ArrayBuffer[ArrayBuffer[Trace]]()
    val (hostCalls, accelCalls) = calls.partition(_._2.isEmpty)

    accelCalls.foreach{call =>
      val grpId = groups.indexWhere{grp => grp.forall{r => canHaveSameImpl(call, r) }}
      if (grpId != -1) groups(grpId) += call  else groups += ArrayBuffer(call)
    }
    if (hostCalls.nonEmpty) groups += ArrayBuffer(hostCalls:_*)

    groups.filterNot(_.isEmpty)
  }

  protected def process[S:Type](block: Block[S]): Block[S] = {
    modules.foreach{mod =>
      val calls = callsTo(mod)
      dbg(s"Function ${str(mod)} [${mod.ctx}]")
      dbg(s"Calls: ")

      val groups = createGroups(calls)
      funcInstances(mod) = groups.length
      calls.foreach{call =>
        dbg(s"  ${str(call.node)} [${call.trace}]")
      }
      dbg(s"Instances: ${groups.length}")
      groups.zipWithIndex.foreach{case (grp,id) =>
        grp.foreach{call => funcDispatch(call) = id }
        dbg(s"  Instance #$id: ")
        grp.foreach{call => dbg(s"    ${str(call.node)} [${call.node.ctx}]")}
      }
      dbg("")
      dbg("")
    }
    block
  }
}