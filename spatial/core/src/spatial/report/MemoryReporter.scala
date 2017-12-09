package spatial.report

import argon.core._
import argon.traversal.CompilerPass
import spatial.aliases.spatialConfig
import spatial.banking._
import spatial.metadata._
import spatial.utils._

case class MemoryReporter(var IR: State) extends CompilerPass {
  override val name = "Memory Reporter"
  override def shouldRun: Boolean = config.verbosity > 0

  override protected def process[S:Type](block: Block[S]): Block[S] = {
    val target = spatialConfig.target
    val areaModel = target.areaModel

    withLog(config.reportDir, "Memories.report") {
      localMems.grab.map{case mem @ Def(d) =>
        val area = areaModel.areaOf(mem, d, inHwScope = true, inReduce = false)
        mem -> area
      }.sortWith((a,b) => a._2 < b._2).foreach{case (mem,area) =>
        val n = mem.name.getOrElse(c"$mem")
        dbg(s"$n")
        dbg("-" * (n.length+2))

        dbg(u"${mem.ctx}: ")
        dbg(mem.ctx.lineContent.getOrElse("<No Line Found>"))
        dbg("")
        dbg(u"Type: ${mem.tp}")
        dbg(c"Def:  ${str(mem)}")
        dbg(c"Area: " + area.toString)
        val Memory(banking,depth,isAccum) = instanceOf(mem)
        dbg(c"Depth: $depth")
        dbg(c"Accum: $isAccum")
        if (banking.length == 1)                dbg(s"Banking: Flat")
        else if (banking.length == rankOf(mem)) dbg(s"Banking: Hierarchical")
        else                                    dbg(s"Banking: Mixed")
        banking.foreach{case ModBanking(n,b,alpha,dims) =>
            val name = if (b == 1) "Cyclic" else s"Block-$b Cyclic"
            dbg(s"  $name Banking: $n banks, alpha = [" + alpha.mkString(" ") + "] for dims: [" + dims.mkString(", ") + "]")
        }
        dbg("")
        dbg("")
      }
    }

    block
  }

}
