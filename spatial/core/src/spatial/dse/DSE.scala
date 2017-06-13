package spatial.dse

import argon.traversal.CompilerPass
import org.virtualized.SourceContext
import spatial.analysis._
import spatial.compiler._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._
import spatial.SpatialConfig

trait DSE extends CompilerPass { dse =>
  override val name = "Design Space Exploration"

  lazy val scalarAnalyzer = new ScalarAnalyzer{val IR: dse.IR.type = dse.IR }
  lazy val memoryAnalyzer = new MemoryAnalyzer{val IR: dse.IR.type = dse.IR; def localMems = dse.localMems }

  def restricts: Set[Restrict]
  def tileSizes: Set[Param[Index]]
  def parFactors: Set[Param[Index]]
  def localMems: Seq[Exp[_]]
  def metapipes: Seq[Exp[_]]
  def top: Exp[_]

  def prune(params: Seq[Param[Index]], ranges: Map[Param[Index], CRange], restrict: Set[Restrict]) = {
    val pruneSingle = params.map{p =>
      val restricts = restrict.filter(_.dependsOnlyOn(p))
      if (restricts.nonEmpty)
        p -> Domain.restricted(ranges(p), {v: Int => p.c = BigDecimal(v) }, { restricts.forall(_.evaluate) })
      else
        p -> Domain[Int](ranges(p), {v: Int => p.c = BigDecimal(v)})
    }
    pruneSingle.map(_._2)
  }

  override protected def process[S: Type](block: Block[S]) = {
    if (SpatialConfig.enableDSE) {
      dbg("Tile sizes: ")
      tileSizes.foreach{t => dbg(u"${t.ctx}: $t")}
      dbg("Parallelization factors:")
      parFactors.foreach{p => dbg(u"${p.ctx}: $p")}
      dbg("Metapipelining toggles:")
      metapipes.foreach{m => dbg(u"${m.ctx}: $m")}
      // TODO: prune space, dse
    }
    dbg("Freezing parameters")
    tileSizes.foreach{t => t.makeFinal() }
    parFactors.foreach{p => p.makeFinal() }
    block
  }
}
