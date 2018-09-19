package spatial.codegen.pirgen

import argon.codegen.{Codegen, FileDependencies}
import argon.core._
import argon.lang.typeclasses._
import spatial.metadata._

import scala.collection.mutable

trait PIRCodegen extends Codegen with PIRFormattedCodegen with PIRTraversal with FileDependencies with PIRLogger with PIRStruct {
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
  override protected def quoteConst(c: Const[_]): String = s"Const(${getConstant(c).get})" 
  
  //override protected def quote(x: Exp[_]): String = super[PIRTraversal].quote(x)
  override protected def quote(n:Exp[_]):String = n match {
    case c: Const[_] => quoteConst(c)
    case x => s"${composed.get(x).fold("") {o => s"${o}_"} }$x"
  }

  override protected def quoteOrRemap(arg: Any): String = arg match {
    case x:Iterable[_] => x.map(quoteOrRemap).toList.toString
    case Some(x) => s"Some(${quoteOrRemap(x)})"
    case e: Exp[_] => quote(e)
    case m: Type[_] => remap(m)
    case tp:BOOL[_] if tp.v => s"Const(true)"
    case tp:BOOL[_] if !tp.v => s"Const(false)"
    case tp:INT[_] => s"Const(${tp.v})"
    case x => x.toString
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = {
    emit(s"// $lhs = $rhs TODO: Unmatched Node")
    warn(s"// $lhs = $rhs TODO: Unmatched Node")
    rhs.blocks.foreach(emitBlock)
  }

  override protected def emitFat(lhs: Seq[Sym[_]], rhs: Def): Unit = { }

  def error(x: => Any) = {
    argon.core.error(x)
    sys.exit()
  }

  def assert(pred:Boolean, x: => Any) = {
    if (!pred) error(x)
  }
}
