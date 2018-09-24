package spatial.codegen.pirgen

import argon.codegen.{Codegen, FileDependencies}
import argon.core._
import spatial.metadata._
import scala.collection.mutable._

trait PIRMultiMethodCodegen extends PIRFileGen with PIRFormattedCodegen with PIRStruct{

  private var splitting = false
  private var lineCount = 0

  val splitThreshold = 500

  var splitCount = 0

  val scope = Stack[ListBuffer[Exp[_]]]()

  def remit(x: String, forceful: Boolean = false): Unit = super.emit(x)

  override def emit(lhs:Lhs, rhsExp:Any, comment:Any):Unit = {
    scope.head += lhs.lhs
    super.emit(lhs, s"""withName("$lhs", ${src"$rhsExp"})""", comment)
  }

  override def alias(lhs:Lhs, rhsExp:Any, comment:Any):Unit = {
    scope.head += lhs.lhs
    super.alias(lhs, s"""withName("$lhs", ${src"$rhsExp"})""", comment)
  }

  override def emit(x: String, forceful: Boolean = false): Unit = { 
    remit(x, forceful)
    if (splitting) {
      lineCount += 1
      if (lineCount > splitThreshold) {
        splitCount += 1
        lineCount = 0
        scope.push(ListBuffer.empty)
        remit(s"def split${splitCount} = {")
      }
    }
  }

  override protected def emitMain[S:Type](b: Block[S]): Unit = { 
    scope.clear
    scope.push(ListBuffer.empty)
    lineCount = 0
    splitting = true
    super.emitMain(b)
    splitting = false
    lineCount = 0
    (splitCount until 0 by -1).foreach { splitCount =>
      remit(s"}; split${splitCount}")
    }
  }

  override protected def emitFileHeader() {
    super.emitFileHeader()
    remit(s"val nameMap = scala.collection.mutable.Map[String,Any]()")
    remit(s"def lookup[T](name:String) = nameMap(name).asInstanceOf[T]")
    remit(s"def withName[T](name:String, x:T):T = { nameMap += name -> x; x}")
  }

  override protected def quote(s: Exp[_]): String = {
    s match {
      case c: Const[_] => quoteConst(c)
      case _ =>
        val q = super.quote(s)
        if (!scope.head.contains(s)) s"""lookup("$q")""" else q
    }
  }

  def quoteRhs(s:Exp[_])

  def rquote(s: Exp[_]): String = super.quote(s)

}
