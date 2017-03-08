package spatial.codegen.pirgen

import argon.codegen.pirgen.PIRCodegen
import spatial.api.{ControllerExp, CounterExp, UnrolledExp}
import spatial.SpatialConfig
import spatial.analysis.SpatialMetadataExp
import spatial.SpatialExp
import scala.collection.mutable.Map
import argon.Config
import spatial.codegen._
import scala.collection.mutable

trait PIRPreAnalysis extends PIRCodegen with PIRTraversal {
  val IR: SpatialExp with PIRCommonExp
  import IR.{println => _, _}

  val globals = mutable.Set[GlobalComponent]()
  val decomposed = mutable.Map[Expr, Seq[(String, Expr)]]()
  val composed = mutable.Map[Expr, Expr]()

  lazy val allocater = new PIRAllocation{
    val IR = PIRPreAnalysis.this.IR
    def globals = PIRPreAnalysis.this.globals
    def decomposed = PIRPreAnalysis.this.decomposed
    def composed = PIRPreAnalysis.this.composed
  }
  lazy val scheduler = new PIRScheduler{
    val IR = PIRPreAnalysis.this.IR
    def globals = PIRPreAnalysis.this.globals
    def decomposed = PIRPreAnalysis.this.decomposed
    def composed = PIRPreAnalysis.this.composed
  }
  lazy val optimizer = new PIROptimizer{
    val IR = PIRPreAnalysis.this.IR
    def globals = PIRPreAnalysis.this.globals
    def decomposed = PIRPreAnalysis.this.decomposed
    def composed = PIRPreAnalysis.this.composed
  }
  lazy val splitter  = new PIRSplitter{
    val IR = PIRPreAnalysis.this.IR
    def globals = PIRPreAnalysis.this.globals
    def decomposed = PIRPreAnalysis.this.decomposed
    def composed = PIRPreAnalysis.this.composed
  }
  lazy val hacks     = new PIRHacks{
    val IR = PIRPreAnalysis.this.IR
    def globals = PIRPreAnalysis.this.globals
    def decomposed = PIRPreAnalysis.this.decomposed
    def composed = PIRPreAnalysis.this.composed
  }
  lazy val dse       = new PIRDSE{
    val IR = PIRPreAnalysis.this.IR
    def globals = PIRPreAnalysis.this.globals
    def decomposed = PIRPreAnalysis.this.decomposed
    def composed = PIRPreAnalysis.this.composed
  }

  val cus = Map[Expr,List[List[ComputeUnit]]]()

  override protected def preprocess[S:Staged](block: Block[S]): Block[S] = {
    globals.clear
    // -- CU allocation
    allocater.run(block)
    // -- CU scheduling
    scheduler.mappingIn ++= allocater.mapping
    scheduler.run(block)
    // -- Optimization
    optimizer.mapping ++= scheduler.mappingOut
    //optimizer.run(block) //FIXME optimizer is removing necessary CUs

    if (SpatialConfig.enableSplitting) {
      splitter.mappingIn ++= optimizer.mapping
      splitter.run(block)

      hacks.mappingIn ++= splitter.mappingOut
    } else {
      for ((s,cus) <- optimizer.mapping) hacks.mappingIn(s) = List(cus)
    }
    hacks.run(block)

    cus ++= hacks.mappingOut
    dbgblk(s"Mapping: ") {
      cus.foreach { case (sym, cus) =>
        dbgs(s"$sym -> [${cus.map( cus => s"[${cus.mkString(",")}]").mkString(",")}]")
      }
    }

    if (SpatialConfig.enableArchDSE) {
      dse.mappingIn ++= optimizer.mapping
      dse.run(block)
    }

    msg("Starting traversal PIR Generation")
    super.preprocess(block) // generateHeader
  }

  override protected def postprocess[S:Staged](block: Block[S]): Block[S] = {
    super.postprocess(block)
    msg("Done.")
    val nCUs = cus.values.flatten.flatten.size
    msg(s"NUMBER OF CUS: $nCUs")
    block
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = {
    dbgblk(s"Emitting $lhs = $rhs") {
      dbgs(s"isFringe=${isFringe(lhs)} cus.contains(lhs)=${cus.contains(lhs)}")
      dbgs(s"isSRAM=${isSRAM(lhs)} cus.contains(lhs)=${cus.contains(lhs)}")
      dbgs(s"isController=${isControlNode(lhs)} cus.contains(lhs)=${cus.contains(lhs)}")
      super.emitNode(lhs, rhs)
    }
  }

  override protected def quoteConst(c: Const[_]): String = (c.tp, c) match {
    case _ => s"Const($c)"
  }

  override def quote(x: Expr):String = s"$x" 
}

trait PIRGenEmpty extends PIRCodegen {
  val IR: SpatialExp with PIRCommonExp
  import IR._

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = {
    rhs.blocks.foreach(emitBlock)
  }

}
