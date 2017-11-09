package spatial.analysis

import argon.core._
import argon.traversal.CompilerPass
import spatial.aliases._
import spatial.banking._

import scala.language.existentials

case class MemoryReporter(var IR: State, localMems: () => Seq[Exp[_]]) extends CompilerPass {
  override val name = "Memory Reporter"
  override def shouldRun: Boolean = config.verbosity > 0

  override protected def process[S:Type](block: Block[S]): Block[S] = {
    val target = spatialConfig.target
    val areaModel = target.areaModel

    withLog(config.logDir, "Memories.report") {
      localMems().map{case mem @ Def(d) =>
        val area = areaModel.areaOf(mem, d, inHwScope = true, inReduce = false)
        mem -> area
      }.sortWith((a,b) => a._2 < b._2).foreach{case (mem,area) =>
        dbg(u"${mem.ctx}: ${mem.tp}: $mem")
        dbg(mem.ctx.lineContent.getOrElse(""))
        dbg(c"  ${str(mem)}")
        val duplicates = duplicatesOf(mem)
        dbg(c"Duplicates: ${duplicates.length}")
        dbg(c"Area: " + area.toString)
        duplicates.zipWithIndex.foreach{
          case (Memory(banking,depth,isAccum),id) =>
            dbg(c"  #$id: Depth: $depth, isAccum: $isAccum")
            banking.foreach{bank => dbg(c"    $bank") }
        }
        dbg("")
        dbg("")
      }
    }

    block
  }

}
