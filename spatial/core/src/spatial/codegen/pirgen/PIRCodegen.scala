package spatial.codegen.pirgen

import argon.codegen.{Codegen, FileDependencies}
import argon.core.Config
import argon.core._
import spatial.nodes._
import spatial.utils._
import spatial.SpatialConfig

import scala.collection.mutable
import scala.language.postfixOps

trait PIRCodegen extends Codegen with FileDependencies with PIRStruct with PIRLogger {
  override val name = "PIR Codegen"
  override val lang: String = "pir"
  override val ext: String = "scala"

  implicit def codegen:PIRCodegen = this

  val globals    = mutable.Set[GlobalComponent]()
  val decomposed = mutable.Map[Expr, Seq[(String, Expr)]]()
  val composed   = mutable.Map[Expr, Expr]()
  val pcus       = mutable.Map[Expr, List[PCU]]()
  val cus        = mutable.Map[Expr, List[CU]]()

  lazy val allocater = new PIRAllocation(pcus)
  lazy val scheduler = new PIRScheduler(pcus, cus)
  lazy val optimizer = new PIROptimizer(cus)
  lazy val pirStats  = new PIRStats(cus)
  lazy val splitter  = new PIRSplitter(cus)
  lazy val dse       = new PIRDSE(cus)
  lazy val printout  = new PIRPrintout(cus)
  lazy val areaModel = new PIRAreaModelHack(cus)

  val preprocessPasses = mutable.ListBuffer[PIRTraversal]()

  override protected def preprocess[S:Type](block: Block[S]): Block[S] = {
    globals.clear
    
    preprocessPasses += allocater
    preprocessPasses += scheduler
    preprocessPasses += optimizer

    preprocessPasses += areaModel
    preprocessPasses += pirStats

    if (SpatialConfig.enableSplitting) {
      preprocessPasses += printout
      preprocessPasses += splitter
      preprocessPasses += optimizer
    }
    preprocessPasses += printout

    if (SpatialConfig.enableArchDSE) {
      preprocessPasses += dse
    } else {
      preprocessPasses += pirStats
    }

    preprocessPasses.foreach { pass => pass.runAll(block) }

    super.preprocess(block) // generateHeader
  }

  override protected def emitBlock(b: Block[_]): Unit = visitBlock(b)
  override protected def quoteConst(c: Const[_]): String = s"Const($c)"
  override protected def quote(x: Exp[_]): String = spatial.codegen.pirgen.quote(x) 

  final def emitCUs(lhs: Exp[_]): Unit = cus(lhs).foreach{cu => emitCU(lhs, cu) }
  def emitCU(lhs: Exp[_], cu: CU): Unit

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = {
    dbgblk(s"Emitting $lhs = $rhs") {
      if (cus.contains(lhs)) emitCUs(lhs)
      rhs.blocks.foreach(emitBlock)
    }
  }

  override protected def emitFat(lhs: Seq[Sym[_]], rhs: Def): Unit = { }

}

