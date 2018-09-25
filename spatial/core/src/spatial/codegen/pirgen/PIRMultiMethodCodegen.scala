package spatial.codegen.pirgen

import argon.codegen.{Codegen, FileDependencies}
import argon.core._
import spatial.metadata._
import scala.collection.mutable

trait PIRMultiMethodCodegen extends PIRCodegen {

  private var splitting = false
  private var lineCount = 0

  val splitThreshold = 800

  var splitCount = 0

  val scope = mutable.ListBuffer[Lhs]()

  private def rawemit(x: String, forceful: Boolean = false): Unit = super.emit(x)

  override def emit(x: String, forceful: Boolean = false): Unit = { 
    rawemit(x, forceful)
    if (splitting) {
      lineCount += 1
      if (lineCount > splitThreshold) {
        splitEnd
        splitStart
      }
    }
  }

  def splitStart = {
    splitCount += 1
    lineCount = 0
    scope.clear
    rawemit(s"def split${splitCount} = {")
  }
  def splitEnd = {
    rawemit(s"}; split${splitCount}")
  }

  override protected def emitMain[S:Type](b: Block[S]): Unit = { 
    splitting = true
    splitCount = 0
    splitStart
    super.emitMain(b)
    splitEnd
    splitting = false
  }

  override protected def emitFileHeader() {
    super.emitFileHeader()
    rawemit(s"val nameSpace = scala.collection.mutable.Map[String,Any]()")
    rawemit(s"def lookup[T](name:String) = nameSpace(name).asInstanceOf[T]")
    emit(s"def withName[T<:IR](x:T, name:String):T = { if (!nameOf.contains(x)) nameOf(x) = name; nameSpace += name -> x; x}")
  }

  override def emitNameFunc = {}

  override def emit(rhs:Rhs):Unit = {
    scope += rhs.lhs
    emit(s"// $rhs")
    super.emit(rhs)
  }

  override protected def quoteRef(x:Any):String =  x match {
    case c:Const[_] => quoteConst(c)
    case e:Exp[_] => 
      quoteRef(sym_to_lhs(e))
    case lhs:Lhs => 
      val q = super.quoteOrRemap(lhs)
      if (!scope.contains(lhs)) {
        val rhs = lookup(lhs)
        s"""lookup[${rhs.tp}]("$q")""" 
      } else q
    case x:Iterable[_] => x.map { quoteRef }.toString
    case x => super.quoteRef(x)
  }

}
