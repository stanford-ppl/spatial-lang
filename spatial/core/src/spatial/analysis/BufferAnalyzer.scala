package spatial.analysis

import argon.traversal.CompilerPass
import spatial.SpatialExp

trait BufferAnalyzer extends CompilerPass {
  val IR: SpatialExp
  import IR._

  override val name = "Buffer Analyzer"
  def localMems: Seq[Exp[_]]

  override protected def process[S: Type](block: Block[S]) = {
    localMems.foreach{mem =>
      val readers = readersOf(mem)
      val writers = writersOf(mem)
      val duplicates = duplicatesOf(mem)

      dbg(u"Memory $mem: ")

      duplicates.zipWithIndex.foreach{case (dup, i) =>
        dbg(c"  #$i: $dup")
        val reads = readers.filter{read => dispatchOf(read, mem) contains i }
        val writes = writers.filter{write => dispatchOf(write, mem) contains i }
        val accesses = reads ++ writes
        if (accesses.nonEmpty) {
          val (metapipe, _) = findMetaPipe(mem, reads, writes)
          if (metapipe.isDefined && dup.depth > 1) {
            val parent = metapipe.get
            accesses.foreach { access =>
              val child = lca(access.ctrl, parent).get
              if (child == parent) {
                val swap = childContaining(parent, access)
                topControllerOf(access, mem, i) = swap
                dbg(c"  -PORT ACCESS $access [swap = $swap]")
              }
              else dbg(c"  -MUX ACCESS $access [lca = $child]")
            }
          }
        }
        else {
          warn(mem.ctx, u"Memory $mem, instance #$i has no associated accesses")
        }
        // HACK
        readers.collect{case read@(Def(_:BufferedOutWrite[_]),_) => read }.foreach{read =>
          val parents = allParents[Ctrl](read.ctrl, {x => parentOf(x)})
          val i = parents.indexWhere{ctrl => ctrl.isDefined && isStreamPipe(ctrl.get) }
          if (i > 0 && i < parents.length-1) {
            dispatchOf(read, mem).foreach{ d =>
              topControllerOf(read.node, mem, d) = parents(i + 1).get
            }
          }
        }
      }
      dbg("\n")
    }

    dbg(s"--------")
    dbg(s"Results")
    dbg(s"--------")
    localMems.foreach{mem =>
      dbg("\n")
      ctxsOf(mem).headOption match {
        case Some(ctx) =>
          dbg(ctx.fileName + ":" + ctx.line + u": Memory $mem")
          if (ctx.lineContent.isDefined) {
            dbg(ctx.lineContent.get)
            dbg(" "*(ctx.column-1) + "^")
          }

        case None =>
          dbg(u"Memory: $mem")
      }

      dbg(c"  ${str(mem)}")

      val readers = readersOf(mem)
      val writers = writersOf(mem)
      val duplicates = duplicatesOf(mem)

      readers.zipWithIndex.foreach{case (reader, i) => dbg(c"  Reader #$i: ${str(reader.node)} [${reader.ctrlNode}]") }
      writers.zipWithIndex.foreach{case (writer, i) => dbg(c"  Writer #$i: ${str(writer.node)} [${writer.ctrlNode}]") }

      duplicates.zipWithIndex.foreach{case (dup,i) =>
        val reads = readers.filter{read => dispatchOf(read, mem) contains i }
        val writes = writers.filter{write => dispatchOf(write, mem) contains i }
        val accesses = reads ++ writes
        dbg("")
        dbg(c"  #$i: $dup")
        (0 until dup.depth).foreach{port =>
          val portAccesses = accesses.filter{a => portsOf(a, mem, i).contains(port) }.map(_.node)
          val accs = if (dup.depth > 1) {
            val portSwaps = portAccesses.map{a => topControllerOf(a, mem, i) }
            portAccesses.zip(portSwaps).map{case (a,c) => c"$a " + (if (c.isDefined) c"[${c.get.node}]" else "[???]")}
          }
          else {
            portAccesses.map{a => c"$a"}
          }
          dbg(c"    $port: " + accs.mkString(", "))
        }
      }

    }


    block
  }
}
  