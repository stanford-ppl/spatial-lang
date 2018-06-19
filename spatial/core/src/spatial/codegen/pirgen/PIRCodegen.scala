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
    warn(s"// $lhs = $rhs TODO: Unmatched Node")
    rhs.blocks.foreach(emitBlock)
  }

  override protected def emitFat(lhs: Seq[Sym[_]], rhs: Def): Unit = { }

  val controlStack = mutable.Stack[Exp[_]]()
  def currCtrl = controlStack.top
  def inHwBlock = controlStack.nonEmpty

  trait Lhs {
    val lhs:Exp[_]
  }
  case class LhsSym(dlhs:Exp[_], postFix:Option[String]=None) extends Lhs {
    val lhs = compose(dlhs)
    override def toString = postFix match {
      case Some(postFix) => s"${quote(dlhs)}_$postFix"
      case None => s"${quote(dlhs)}"
    }
  }
  implicit def sym_to_lhs(sym:Exp[_]) = LhsSym(sym)
  case class LhsMem(dmem:Exp[_], instId:Int=0, bankId:Option[Int]=None) extends Lhs {
    val lhs = compose(dmem)

    override def toString = bankId match {
      case Some(bankId) => s"${dmem}_d${instId}_b$bankId"
      case None => if (duplicatesOf(lhs).size==1) s"$dmem" else s"${dmem}_d${instId}"
    }
  }
  object LhsMem {
    def apply(dmem:Exp[_], instId:Int, bankId:Int):LhsMem = LhsMem(dmem, instId, Some(bankId))
  }

  def quoteCtrl = {
    if (controlStack.isEmpty) ".ctrl(top)"
    else s".ctrl($currCtrl)"
  }

  def quoteCtx(lhs:Lhs) = {
    lhs.lhs.ctx match {
      case virtualized.EmptyContext => ""
      case ctx => s""".srcCtx("${ctx}")"""
    }
  }

  def emitMeta(lhs:LhsMem) = {
    val mem = compose(lhs.dmem)
    val insts = duplicatesOf(mem)
    val inst = insts(lhs.instId)
    emit(s"isAccum($lhs) = ${inst.isAccum}")
    emit(s"bufferDepthOf($lhs) = ${inst.depth}")
  }


  def emit(lhs:Any):Unit = {
    super.emit(s"$lhs")
  }

  def emit(lhs:Lhs, rhsExp:Any, comment:Any):Unit = {
    lhs match {
      case lhs:LhsMem =>
        emit(s"""val $lhs = $rhsExp.name("$lhs")${quoteCtrl}${quoteCtx(lhs)} // $comment""")
        emitMeta(lhs)
      case lhs =>
        emit(s"""val $lhs = $rhsExp.name("$lhs")${quoteCtrl}${quoteCtx(lhs)} // $comment""")
    }
  }

  def error(x: => Any) = {
    argon.core.error(x)
    sys.exit()
  }

  def assert(pred:Boolean, x: => Any) = {
    if (!pred) error(x)
  }
}
