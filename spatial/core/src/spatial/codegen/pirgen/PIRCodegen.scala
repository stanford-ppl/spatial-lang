package spatial.codegen.pirgen

import argon.codegen.{Codegen, FileDependencies}
import argon.core._
import argon.nodes._
import spatial.aliases._
import spatial.nodes._
import spatial.utils._
import spatial.metadata._

import scala.collection.mutable
import scala.language.postfixOps

trait PIRCodegen extends Codegen with PIRTraversal with FileDependencies with PIRLogger with PIRStruct {
  override val name = "PIR Codegen"
  override val lang: String = "pir"
  override val ext: String = "scala"

  implicit def codegen:PIRCodegen = this

  lazy val structAnalyzer = new PIRStructAnalyzer
  lazy val memoryAnalyzer = new PIRMemoryAnalyzer

  val preprocessPasses = mutable.ListBuffer[PIRTraversal]()

  def reset = {
    metadatas.foreach { _.reset }
  }

  override protected def preprocess[S:Type](block: Block[S]): Block[S] = {
    reset
    preprocessPasses += structAnalyzer
    preprocessPasses += memoryAnalyzer

    preprocessPasses.foreach { pass => pass.runAll(block) }
    super.preprocess(block) // generateHeader
  }

  override protected def emitBlock(b: Block[_]): Unit = visitBlock(b)
  override protected def quoteConst(c: Const[_]): String = s"$c"
  override protected def quote(x: Exp[_]): String = {
    super[PIRTraversal].quote(x)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = {
    emit(s"// $lhs = $rhs TODO: Unmatched Node")
    rhs.blocks.foreach(emitBlock)
  }

  override protected def emitFat(lhs: Seq[Sym[_]], rhs: Def): Unit = { }

  val controlStack = mutable.Stack[Exp[_]]()
  def currCtrl = controlStack.top
  def inHwBlock = controlStack.nonEmpty

  def quoteCtrl = {
    if (controlStack.isEmpty) ".ctrl(top)"
    else s".ctrl($currCtrl)"
  }

  def emit(lhs:Any):Unit = {
    super.emit(s"$lhs")
  }
  def emit(lhs:Any, rhs:Any):Unit = {
    emit(s"""val ${quote(lhs)} = $rhs$quoteCtrl.name("$lhs")""")
  }
  def emit(lhs:Any, rhs:Any, comment:Any):Unit = {
    emit(s"""val ${quote(lhs)} = $rhs.name("$lhs")$quoteCtrl // $comment""")
  }
}

