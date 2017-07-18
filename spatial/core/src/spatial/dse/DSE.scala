package spatial.dse

import argon.core._
import argon.traversal.CompilerPass
import org.virtualized.SourceContext
import spatial.analysis._
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._
import spatial.SpatialConfig

trait DSE extends CompilerPass with SpaceGenerator { dse =>
  override val name = "Design Space Exploration"
  final val PROFILING = true

  abstract class SearchMode
  case object BruteForce extends SearchMode
  case object Randomized extends SearchMode

  // lazy val scalarAnalyzer = new ScalarAnalyzer{var IR = dse.IR }
  // lazy val memoryAnalyzer = new MemoryAnalyzer{var IR = dse.IR; def localMems = dse.localMems }
  def restricts: Set[Restrict]
  def tileSizes: Set[Param[Index]]
  def parFactors: Set[Param[Index]]
  def localMems: Seq[Exp[_]]
  def metapipes: Seq[Exp[_]]
  def top: Exp[_]

  override protected def process[S: Type](block: Block[S]): Block[S] = {
    if (SpatialConfig.enableDSE) {
      msg("Tile sizes: ")
      tileSizes.foreach{t => msg(u"${t.ctx}: $t")}
      msg("Parallelization factors:")
      parFactors.foreach{p => msg(u"${p.ctx}: $p")}
      msg("Metapipelining toggles:")
      metapipes.foreach{m => msg(u"${m.ctx}: $m")}

      val intParams = (tileSizes ++ parFactors).toSeq
      val intSpace = createIntSpace(intParams, restricts)
      val ctrlSpace = createCtrlSpace(metapipes)
      val space = intSpace ++ ctrlSpace

      if (PRUNE) {
        msg("Found the following space restrictions: ")
        restricts.foreach{r => msg(s"  $r") }
        msg("")
        msg("Pruned space: ")
        (intParams ++ metapipes).zip(space).foreach{case (p,d) => msg(s"  $p: $d") }
      }

      val restrictions: Set[Restrict] = if (PRUNE) restricts.filter{_.deps.size > 1} else Set.empty

      dse(space, restrictions)
    }
    dbg("Freezing parameters")
    tileSizes.foreach{t => t.makeFinal() }
    parFactors.foreach{p => p.makeFinal() }
    block
  }

  def dse(space: Seq[Domain[_]], restrictions: Set[Restrict]): Unit = {
    val N = space.size
    val P = space.map{d => BigInt(d.len) }.product

    msg("Space Statistics: ")
    msg("-------------------------")
    msg(s"  # of parameters: $N")
    msg(s"  # of points:     $P")
  }

}
