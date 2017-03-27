package spatial.dse

import argon.traversal.CompilerPass
import spatial.{SpatialConfig, SpatialExp}
import org.virtualized.SourceContext

import spatial.analysis._
//import spatial.models._

trait DSE extends CompilerPass { dse =>
  val IR: SpatialExp
  import IR._

  override val name = "Design Space Exploration"

  lazy val scalarAnalyzer = new ScalarAnalyzer{val IR: dse.IR.type = dse.IR }
  lazy val memoryAnalyzer = new MemoryAnalyzer{val IR: dse.IR.type = dse.IR; def localMems = dse.localMems }

  def restricts: Set[Restrict]
  def tileSizes: Set[Param[Index]]
  def parFactors: Set[Param[Index]]
  def localMems: Seq[Exp[_]]
  def metapipes: Seq[Exp[_]]
  def top: Exp[_]

  override protected def process[S: Type](block: Block[S]) = {
    if (SpatialConfig.enableDSE) {
      dbg("Tile sizes: ")
      tileSizes.foreach{t => dbg(u"${ctx(t)}: $t")}
      dbg("Parallelization factors:")
      parFactors.foreach{p => dbg(u"${ctx(p)}: $p")}
      dbg("Metapipelining toggles:")
      metapipes.foreach{m => dbg(u"${ctx(m)}: $m")}
      // TODO: prune space, dse
    }
    dbg("Freezing parameters")
    tileSizes.foreach{t => t.makeFinal() }
    parFactors.foreach{p => p.makeFinal() }
    block
  }
}
