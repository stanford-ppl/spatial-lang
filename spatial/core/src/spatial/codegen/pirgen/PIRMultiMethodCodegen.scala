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
    super.emit(lhs, rhsExp, comment)
    remit(s"""nameMap += "$lhs" -> $lhs""")
  }

  override def alias(lhs:Lhs, rhsExp:Any, comment:Any):Unit = {
    scope.head += lhs.lhs
    super.alias(lhs, rhsExp, comment)
    remit(s"""nameMap += "$lhs" -> $lhs""")
  }

  override def emit(x: String, forceful: Boolean = false): Unit = { 
    remit(x, forceful)
    if (splitting) {
      lineCount += 1
      if (lineCount > splitThreshold) {
        splitCount += 1
        lineCount = 0
        remit(s"def split${splitCount} = {")
      }
    }
  }

  override protected def emitMain[S:Type](b: Block[S]): Unit = { 
    scope.clear
    scope.push(ListBuffer.empty)
    lineCount = 0
    splitting = true
    remit(s"val nameMap = scala.collection.mutable.Map[String,Any]()")
    remit(s"def typed[T](x:Any) = x.asInstanceOf[T]")
    super.emitMain(b)
    splitting = false
    lineCount = 0
    (splitCount until 0 by -1).foreach { splitCount =>
      remit(s"}; split${splitCount}")
    }
  }

  //override protected def quote(s: Exp[_]): String = {
    //val q = super.quote(s)
    //if (!scope.head.contains(s)) s"""typed(nameMap("$q"))""" else q
  //}

  def rquote(s: Exp[_]): String = super.quote(s)

}
