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

trait PIRLogger extends SpatialTraversal {

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
  def dbgcu(cu:ComputeUnit):Unit = dbgblk(s"Generated CU: $cu") {
    dbgblk(s"cchains: ") {
      cu.cchains.foreach{cchain => dbgs(s"${cchain.longString}") }
    }
    dbgblk(s"mems: ") {
      for (mem <- cu.mems) {
        dbgl(s"""$mem [${mem.tpe}] (exp: ${mem.mem})""") {
          dbgs(s"""banking   = ${mem.banking.map(_.toString).getOrElse("N/A")}""")
          dbgs(s"""writePort    = ${mem.writePort.map(_.toString).mkString(",")}""")
          dbgs(s"""readPort    = ${mem.readPort.map(_.toString).mkString(",")}""")
          //dbgs(s"""writeAddr = ${mem.writeAddr.map(_.toString).mkString(",")}""")
          //dbgs(s"""readAddr  = ${mem.readAddr.map(_.toString).mkString(",")}""")
          producerOf.get(mem).foreach { _.foreach { case (writer, producer) =>
            dbgs(s"writer=$writer, producer=$producer")
          } }
          consumerOf.get(mem).foreach { _.foreach { case (reader, consumer) =>
            dbgs(s"reader=$reader, consumer=$consumer")
          } }
        }
      }
    }
    dbgl("Generated PseudoStage: ") {
      cu.pseudoStages.foreach { stage => dbgs(quote(stage)) }
    }
    dbgl("Generated compute stages: ") {
      cu.computeStages.foreach(stage => dbgs(quote(stage)))
    }
    dbgl(s"CU global inputs:") {
      globalInputs(cu).foreach{in => dbgs(s"$in") }
    }
    dbgl(s"regTable:") {
      cu.regTable.foreach { case (exp, comp) => 
        dbgs(s"$exp -> $comp [${comp.getClass.getSimpleName}]")
      }
    }
  }

}
