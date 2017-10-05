package spatial.codegen.pirgen

import java.io.PrintWriter
import java.nio.file.{Files, Paths}

import argon.codegen.{Codegen, FileDependencies}
import argon.core._
import spatial.aliases._
import spatial.nodes._
import spatial.utils._

import scala.collection.mutable
import scala.language.postfixOps

trait PIRCodegen extends Codegen with FileDependencies with PIRTraversal {
  override val name = "PIR Codegen"
  override val lang: String = "pir"
  override val ext: String = "scala"
  implicit val self:PIRCodegen = this

  val globals    = mutable.Set[GlobalComponent]()
  val decomposed = mutable.Map[Expr, Seq[(String, Expr)]]()
  val composed   = mutable.Map[Expr, Expr]()
  val pcus       = mutable.Map[Expr, List[PCU]]()
  val cus        = mutable.Map[Expr,List[CU]]()

  lazy val allocater = new PIRAllocation{ 
    var IR = PIRCodegen.this.IR
    def mapping = PIRCodegen.this.pcus
    def globals = PIRCodegen.this.globals
    def composed = PIRCodegen.this.composed
    def decomposed = PIRCodegen.this.decomposed
  }
  lazy val scheduler = new PIRScheduler {
    var IR = PIRCodegen.this.IR
    def mappingIn = PIRCodegen.this.pcus
    def mappingOut = PIRCodegen.this.cus
    def globals = PIRCodegen.this.globals
    def composed = PIRCodegen.this.composed
    def decomposed = PIRCodegen.this.decomposed
  }
  lazy val optimizer = new PIROptimizer {
    var IR = PIRCodegen.this.IR
    def mapping = PIRCodegen.this.cus
    def globals = PIRCodegen.this.globals
    def composed = PIRCodegen.this.composed
    def decomposed = PIRCodegen.this.decomposed
  }
  lazy val splitter  = new PIRSplitter {
    var IR = PIRCodegen.this.IR
    def mapping = PIRCodegen.this.cus
    def globals = PIRCodegen.this.globals
    def composed = PIRCodegen.this.composed
    def decomposed = PIRCodegen.this.decomposed
  }
  lazy val dse       = new PIRDSE {
    var IR = PIRCodegen.this.IR
    def mapping = PIRCodegen.this.cus
    def globals = PIRCodegen.this.globals
    def composed = PIRCodegen.this.composed
    def decomposed = PIRCodegen.this.decomposed
  }
  lazy val printout = new PIRPrintout {
    var IR = PIRCodegen.this.IR
    def mapping = PIRCodegen.this.cus
    def globals = PIRCodegen.this.globals
    def composed = PIRCodegen.this.composed
    def decomposed = PIRCodegen.this.decomposed
  }
  lazy val areaModel = new PIRAreaModelHack {
    var IR = PIRCodegen.this.IR
    def mapping = PIRCodegen.this.cus
    def globals = PIRCodegen.this.globals
    def composed = PIRCodegen.this.composed
    def decomposed = PIRCodegen.this.decomposed
  }

  override protected def preprocess[S:Type](block: Block[S]): Block[S] = {
    globals.clear
    allocater.run(block)
    scheduler.run(block)
    optimizer.run(block)

    emitCUStats(cus.values.flatten)

    areaModel.run(block)

    if (spatialConfig.enableSplitting) {
      printout.run(block)
      splitter.run(block)
    }
    printout.run(block)

    //HACK remove unused copy from parent after splitting
    cus.foreach { case (sym, cus) => cus.foreach { cu => optimizer.removeUnusedCChainCopy(cu) } }

    if (spatialConfig.enableArchDSE) {
      dse.run(block)
    } else {
      tallyCUs(cus.values.toList.flatten)
    }

    super.preprocess(block) // generateHeader
  }

  override protected def postprocess[S:Type](block: Block[S]): Block[S] = {
    super.postprocess(block)
    block
  }

  override protected def emitBlock(b: Block[_]): Unit = visitBlock(b)
  override protected def quoteConst(c: Const[_]): String = s"Const($c)"
  override protected def quote(x: Exp[_]): String = super[PIRTraversal].quote(x)

  final def emitCUs(lhs: Exp[_]): Unit = cus(lhs).foreach{cu => emitCU(lhs, cu) }
  def emitCU(lhs: Exp[_], cu: CU): Unit

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = {
    dbgblk(s"Emitting $lhs = $rhs") {
      dbgs(s"isFringe=${isFringe(lhs)} cus.contains(lhs)=${cus.contains(lhs)}")
      dbgs(s"isSRAM=${isSRAM(lhs)} cus.contains(lhs)=${cus.contains(lhs)}")
      dbgs(s"isController=${isControlNode(lhs)} cus.contains(lhs)=${cus.contains(lhs)}")
      rhs match {
        case _: SRAMNew[_, _] if cus.contains(lhs)        => emitCUs(lhs)
        case _ if isFringe(lhs) && cus.contains(lhs)      => emitCUs(lhs)
        case _ if isControlNode(lhs) && cus.contains(lhs) => emitCUs(lhs)
        case _ =>
      }
      rhs.blocks.foreach(emitBlock)
    }
  }

  override protected def emitFat(lhs: Seq[Sym[_]], rhs: Def): Unit = { }

  def emitCUStats(cus: Iterable[CU]) = {
    val pwd = sys.env("SPATIAL_HOME")
    val dir = s"$pwd/csvs"
    Files.createDirectories(Paths.get(dir))
    val file = new PrintWriter(s"$dir/${config.name}_unsplit.csv")
    cus.filter{cu => cu.allStages.nonEmpty || cu.isPMU}.foreach{cu =>
      val isPCU = if (cu.isPCU) 1 else 0
      val util = getUtil(cu, cus)
      val line = s"$isPCU, ${cu.lanes},${util.stages},${util.addr},${util.regsMax},${util.vecIn},${util.vecOut},${util.sclIn},${util.sclOut}"
      file.println(line)
    }
    file.close()
  }
}

