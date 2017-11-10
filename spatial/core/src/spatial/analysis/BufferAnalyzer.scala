package spatial.analysis

import argon.core._
import argon.traversal.CompilerPass
import spatial.aliases._
import spatial.banking._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

case class BufferAnalyzer(var IR: State) extends CompilerPass {
  override val name = "Buffer Analyzer"

  override protected def process[S: Type](block: Block[S]): Block[S] = {
    val localMems = globaldata[LocalMemories].mems

    localMems.foreach{mem =>
      val readers = readersOf(mem)
      val writers = writersOf(mem)
      val accesses = readers ++ writers
      val inst = instanceOf(mem)

      dbg(u"Memory $mem: ")

      // HACK: Only used in Scala generation right now
      if (isBufferedOut(mem)) {
        /*val commonCtrl = writers.map(_.ctrl).reduce{(a,b) => lca(a,b).get }
        val children = childrenOf(commonCtrl)
        val topIndex = children.lastIndexWhere{child => writers.exists(_.ctrl == child) }
        val top = if (topIndex < 0) children.last else children(topIndex)
        writers.foreach{wr =>
          dispatchOf(wr, mem).foreach{d => topControllerOf(wr.node, mem, d) = top }
        }*/
        writers.foreach{ wr =>
          val parents = allParents[Ctrl](wr.ctrl, { x => parentOf(x) })
          val i = parents.indexWhere { ctrl => ctrl.isDefined && isStreamPipe(ctrl.get) }
          if (i >= 0 && i < parents.length - 1) {
            topControllerOf(wr.node, mem) = parents(i + 1).get
          }
        }
      }

      /*accesses.foreach{access =>
        val dups = dispatchOf.get(access, mem)
        dups match {
          case Some(dispatches) =>
            val invalids = dispatches.filter{x => x >= duplicates.length || x < 0}
            if (invalids.nonEmpty) {
              bug(c"Access $access on $mem is set to use invalid instances: ")
              bug("  Instances: " + invalids.mkString(",") + s" (Largest should be ${duplicates.length-1})")
              state.logBug()
            }
          case None =>
            warn(c"Access $access on $mem has no associated instances")
        }
      }*/


      if (accesses.nonEmpty) {
        val (metapipe, _) = findMetaPipe(mem, readers, writers)
        if (metapipe.isDefined && inst.depth > 1) {
          val parent = metapipe.get
          accesses.filter{a => !isTransient(a.node)}.foreach { access =>
            val child = lca(access.ctrl, parent).get
            if (child == parent) {
              val swap = childContaining(parent, access)
              topControllerOf(access, mem) = swap
              dbg(c"  -PORT ACCESS $access [swap = $swap]")
            }
            else {
              // TODO: Ask Matt what he expects here
              //val fakeSwap = childContaining(child, access)
              //topControllerOf(access, mem, i) = fakeSwap
              dbg(c"  -MUX ACCESS $access [lca = $child]")
            }
          }
        }
      }
      else {
        warn(mem.ctx, u"${mem.tp} $mem, has no associated accesses")
      }
      dbg("\n")
    }


    dbg(s"--------")
    dbg(s"Results")
    dbg(s"--------")
    localMems.foreach{mem =>
      dbg("\n")
      val ctx = mem.ctx
      dbg(ctx.fileName + ":" + ctx.line + u": Memory $mem")
      if (ctx.lineContent.isDefined) {
        dbg(ctx.lineContent.get)
        dbg(" "*(ctx.column-1) + "^")
      }
      dbg(c"  ${str(mem)}")

      val readers = readersOf(mem)
      val writers = writersOf(mem)
      val accesses = readers ++ writers
      val inst = instanceOf(mem)

      readers.zipWithIndex.foreach{case (reader, i) => dbg(c"  Reader #$i: ${str(reader.node)} [${reader.ctrlNode}]") }
      writers.zipWithIndex.foreach{case (writer, i) => dbg(c"  Writer #$i: ${str(writer.node)} [${writer.ctrlNode}]") }

      dbg("")
      dbg(c"  $inst")
      (0 until inst.depth).foreach{port =>
        val portAccesses = accesses.filter{a => portsOf(a, mem, 0).contains(port) }.map(_.node)
        val accs = if (inst.depth > 1) {
          val portSwaps = portAccesses.map{a => topControllerOf(a, mem) }
          portAccesses.zip(portSwaps).map{case (a,c) => c"$a " + (if (c.isDefined) c"[${c.get.node}]" else "[???]")}
        }
        else {
          portAccesses.map{a => c"$a"}
        }
        dbg(c"    $port: " + accs.mkString(", "))
      }
    }

    block
  }
}
  