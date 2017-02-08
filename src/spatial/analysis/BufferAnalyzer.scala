package spatial.analysis

import argon.traversal.CompilerPass
import spatial.SpatialExp
import org.virtualized.SourceContext

trait BufferAnalyzer extends CompilerPass {
  val IR: SpatialExp
  import IR._

  override val name = "Buffer Analyzer"
  def localMems: Seq[Exp[_]]

  override protected def process[S: Staged](block: Block[S]) = {
    localMems.foreach{mem =>
      val readers = readersOf(mem)
      val writers = writersOf(mem)
      val duplicates = duplicatesOf(mem)

      dbg(u"Setting buffer controllers for $mem: ")
      duplicates.zipWithIndex.foreach{case (dup, i) => dbg(c"  Instance #$i: $dup") }
      readers.zipWithIndex.foreach{case (reader, i) => dbg(c"  Reader #$i: ${str(reader.node)} [${reader.ctrl}]") }
      writers.zipWithIndex.foreach{case (writer, i) => dbg(c"  Writer #$i: ${str(writer.node)} [${writer.ctrl}]") }

      duplicates.zipWithIndex.foreach{case (dup, i) =>
        dbg(c"  #$i:")
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
          warn(ctxOrHere(mem), u"Memory $mem, instance #$i has no associated accesses")
        }
      }
    }

    block
  }
}
