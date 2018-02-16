package spatial.codegen.pirgen

import argon.core._
import argon.nodes._
import spatial.analysis.SpatialTraversal
import spatial.aliases._
import spatial.nodes._
import spatial.utils._

import scala.collection.mutable
import scala.collection.mutable.WrappedArray
import scala.reflect.runtime.universe.{Block => _, Type => _, _}

trait PIRLogger extends SpatialTraversal with PIRStruct {

  var listing = false
  var listingSaved = false
  var tablevel = 0 // Doesn't change tab level with traversal of block

  override protected def dbgs(s: => Any): Unit = dbg(s"${"  "*tablevel}${if (listing) "- " else ""}$s")

  def dbgblk[T](s:String)(block: =>T) = {
    dbgs(s + " {")
    tablevel += 1
    listingSaved = listing
    listing = false
    val res = block
    res match {
      case res:Iterable[_] => 
        dbgl(s"$s res:") { res.foreach { res => dbgs(s"$res")} }
      case _:Unit =>
      case _ =>
        dbgs(s"$s res=$res")
    }
    tablevel -=1
    dbgs(s"}")
    listing = listingSaved
    res
  }
  def dbgl[T](s:String)(block: => T) = {
    dbgs(s)
    tablevel += 1
    listing = true
    val res = block
    listing = false
    tablevel -=1
    res
  }
  def quote(n:Any):String = n match {
    case x:Expr => s"${composed.get(x).fold("") {o => s"${quote(o)}_"} }$x"
    case x:Iterable[_] => x.map(quote).toList.toString
    case n => n.toString
  }

  def qdef(lhs:Any):String = {
    val rhs = lhs match {
      case Def(e:UnrolledForeach) => 
        s"UnrolledForeach(iters=(${e.iters.mkString(",")}), valids=(${e.valids.mkString(",")}))"
      case Def(e:UnrolledReduce[_,_]) => 
        s"UnrolledReduce(iters=(${e.iters.mkString(",")}), valids=(${e.valids.mkString(",")}))"
      case lhs@Def(d) if isControlNode(lhs) => s"${d.getClass.getSimpleName}(binds=${d.binds})"
      case Op(rhs) => s"$rhs"
      case Def(rhs) => s"$rhs"
      case lhs => s"$lhs"
    }
    val name = lhs match {
      case lhs:Expr => compose(lhs).name.fold("") { n => s" ($n)" }
      case _ => ""
    }
    s"$lhs = $rhs$name"
  }

  private def log[T](msg:String, logger:Option[PIRLogger] = None)(f: => T):T = {
    logger.fold(f) { _.dbgblk(msg) { f } }
  }

  val times = scala.collection.mutable.Stack[Long]()
  def tic = {
    times.push(System.nanoTime())
  }
  def toc(unit:String):Double = {
    val startTime = times.pop()
    val endTime = System.nanoTime()
    val timeUnit = unit match {
      case "ns" => 1
      case "us" => 1000
      case "ms" => 1000000
      case "s" => 1000000000
      case _ => throw new Exception(s"Unknown time unit!")
    }
    (endTime - startTime) * 1.0 / timeUnit
  }

  def toc(info:String, unit:String):Unit = {
    val time = toc(unit)
    println(s"$info elapsed time: ${f"$time%1.3f"}$unit")
  }


}
