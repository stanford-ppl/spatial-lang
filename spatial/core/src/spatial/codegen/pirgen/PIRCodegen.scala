package spatial.codegen.pirgen

import argon.codegen.{Codegen, FileDependencies}
import argon.core._
import spatial.aliases._
import spatial.nodes._
import spatial.utils._

import scala.collection.mutable
import scala.language.postfixOps

trait PIRCodegen extends Codegen with FileDependencies with PIRLogger {
  override val name = "PIR Codegen"
  override val lang: String = "pir"
  override val ext: String = "scala"

  implicit def codegen:PIRCodegen = this

  lazy val memoryAnalyzer = new PIRMemoryAnalyzer
  lazy val allocater      = new PIRAllocation
  lazy val scheduler      = new PIRScheduler
  lazy val optimizer      = new PIROptimizer
  lazy val pirStats       = new PIRStats
  lazy val splitter       = new PIRSplitter
  lazy val dse            = new PIRDSE
  lazy val printout       = new PIRPrintout
  lazy val areaModel      = new PIRAreaModelHack

  val preprocessPasses = mutable.ListBuffer[PIRTraversal]()

  def reset = {
    globals.clear
    metadatas.foreach { _.reset }
  }

  override protected def preprocess[S:Type](block: Block[S]): Block[S] = {
    reset
    
    preprocessPasses += memoryAnalyzer
    preprocessPasses += allocater
    preprocessPasses += scheduler
    preprocessPasses += optimizer

    preprocessPasses += areaModel
    preprocessPasses += pirStats

    if (spatialConfig.enableSplitting) {
      preprocessPasses += printout
      preprocessPasses += splitter
      preprocessPasses += optimizer
    }
    preprocessPasses += printout

    if (spatialConfig.enableArchDSE) {
      preprocessPasses += dse
    } else {
      preprocessPasses += pirStats
    }

    preprocessPasses.foreach { pass => pass.runAll(block) }

    cus.foreach(dbgcu)
    super.preprocess(block) // generateHeader
  }

  override protected def emitBlock(b: Block[_]): Unit = visitBlock(b)
  override protected def quoteConst(c: Const[_]): String = s"Const($c)"
  override protected def quote(x: Exp[_]): String = spatial.codegen.pirgen.quote(x) 

  def emitCU(lhs: Exp[_], cu: CU): Unit

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = {
    dbgblk(s"Emitting $lhs = $rhs") {
      rhs match {
        case Hwblock(_, _) => 
        case _ =>
          mappingOf.getT[CU](lhs).foreach { _.foreach( cu => emitCU(lhs, cu) ) }
      }
    }
    rhs.blocks.foreach(emitBlock)
  }

  override protected def emitFat(lhs: Seq[Sym[_]], rhs: Def): Unit = { }

}

