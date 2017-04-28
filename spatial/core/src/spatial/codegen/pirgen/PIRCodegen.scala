package spatial.codegen.pirgen

import argon.codegen.{Codegen, FileDependencies}
import spatial.{SpatialConfig, SpatialExp}

import scala.collection.mutable
import scala.language.postfixOps

trait PIRCodegen extends Codegen with FileDependencies with PIRTraversal {
  val IR: SpatialExp with PIRCommonExp
  import IR._

  override val name = "PIR Codegen"
  override val lang: String = "pir"
  override val ext: String = "scala"

  override protected def emitBlock(b: Block[_]): Unit = visitBlock(b)
  override protected def quoteConst(c: Const[_]): String = s"Const($c)"
  override protected def quote(x: Exp[_]): String = s"$x"

  val globals    = mutable.Set[GlobalComponent]()
  val decomposed = mutable.Map[Expr, Seq[(String, Expr)]]()
  val composed   = mutable.Map[Expr, Expr]()
  val cus        = mutable.Map[Expr,List[List[ComputeUnit]]]()

  lazy val allocater = new PIRAllocation{
    override val IR: PIRCodegen.this.IR.type = PIRCodegen.this.IR
    def globals = PIRCodegen.this.globals
    def decomposed = PIRCodegen.this.decomposed
    def composed = PIRCodegen.this.composed
  }
  lazy val scheduler = new PIRScheduler{
    override val IR: PIRCodegen.this.IR.type = PIRCodegen.this.IR
    def globals = PIRCodegen.this.globals
    def decomposed = PIRCodegen.this.decomposed
    def composed = PIRCodegen.this.composed
  }
  lazy val optimizer = new PIROptimizer{
    override val IR: PIRCodegen.this.IR.type = PIRCodegen.this.IR
    def globals = PIRCodegen.this.globals
    def decomposed = PIRCodegen.this.decomposed
    def composed = PIRCodegen.this.composed
  }
  lazy val splitter  = new PIRSplitter{
    override val IR: PIRCodegen.this.IR.type = PIRCodegen.this.IR
    def globals = PIRCodegen.this.globals
    def decomposed = PIRCodegen.this.decomposed
    def composed = PIRCodegen.this.composed
  }
  lazy val hacks     = new PIRHacks{
    override val IR: PIRCodegen.this.IR.type = PIRCodegen.this.IR
    def globals = PIRCodegen.this.globals
    def decomposed = PIRCodegen.this.decomposed
    def composed = PIRCodegen.this.composed
  }
  lazy val dse       = new PIRDSE{
    override val IR: PIRCodegen.this.IR.type = PIRCodegen.this.IR
    def globals = PIRCodegen.this.globals
    def decomposed = PIRCodegen.this.decomposed
    def composed = PIRCodegen.this.composed
  }

  lazy val printout = new PIRPrintout {
    override val IR: PIRCodegen.this.IR.type = PIRCodegen.this.IR
    def globals = PIRCodegen.this.globals
    def decomposed = PIRCodegen.this.decomposed
    def composed = PIRCodegen.this.composed
  }


  override protected def preprocess[S:Type](block: Block[S]): Block[S] = {
    globals.clear
    // -- CU allocation
    allocater.run(block)
    // -- CU scheduling
    scheduler.mappingIn ++= allocater.mapping
    scheduler.run(block)
    // -- Optimization
    optimizer.mapping ++= scheduler.mappingOut
    optimizer.run(block)

    if (SpatialConfig.enableSplitting) {
      printout.mappingIn ++= optimizer.mapping
      printout.run(block)

      splitter.mappingIn ++= optimizer.mapping
      splitter.run(block)

      hacks.mappingIn ++= splitter.mappingOut
    }
    else {
      for ((s,cus) <- optimizer.mapping) hacks.mappingIn(s) = List(cus)
    }
    hacks.run(block)

    printout.splitMappingIn ++= hacks.mappingOut
    printout.run(block)

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
    else {
      tallyCUs(cus.values.flatten.flatten)
    }

    super.preprocess(block) // generateHeader
  }

  override protected def postprocess[S:Type](block: Block[S]): Block[S] = {
    super.postprocess(block)
    block
  }

  final def emitCUs(lhs: Exp[_]): Unit = cus(lhs).flatten.foreach{cu => emitCU(lhs, cu) }
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
}

