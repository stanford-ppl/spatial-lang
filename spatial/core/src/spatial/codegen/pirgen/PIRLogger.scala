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
  def dbgpcu(pcu:PseudoComputeUnit) = {
    dbgblk(s"${qdef(pcu.pipe)} -> ${pcu.name}") {
      dbgl(s"regs:") {
        for ((s,r) <- pcu.regTable) { dbgs(s"$s -> $r") }
      }
      dbgl(s"cchains:") {
        pcu.cchains.foreach { cchain => dbgs(s"$cchain") }
      }
      dbgl(s"MEMs:") {
        for ((exp, mem) <- pcu.memMap) {
          dbgs(s"""$mem (mode: ${mem.mode}) ${qdef(exp)}""")
        }
      }
      dbgl(s"Write stages:") {
        pcu.writeStages.foreach { stage => dbgs(s"  $stage") }
      }
      dbgl(s"Read stages:") {
        pcu.readStages.foreach { stage => dbgs(s"  $stage") }
      }
      dbgl(s"FringeGlobals:") {
        pcu.fringeGlobals.foreach { case (f, vec) => dbgs(s"$f -> $vec") }
      }
      dbgl(s"Compute stages:") { pcu.computeStages.foreach { stage => dbgs(s"$stage") } }
    }
  }

  def dbgcu(cu:ComputeUnit):Unit = dbgblk(s"Generated CU: $cu") {
    dbgblk(s"cchains: ") {
      cu.cchains.foreach{cchain => dbgs(s"$cchain") }
    }
    if (cu.mems.nonEmpty) {
      dbgblk(s"mems: ") {
        for (mem <- cu.mems) {
          dbgl(s"""$mem [${mem.mode}] (exp: ${mem.mem})""") {
            dbgs(s"""banking   = ${mem.banking.map(_.toString).getOrElse("N/A")}""")
            dbgs(s"""writePort    = ${mem.writePort.map(_.toString).mkString(",")}""")
            dbgs(s"""readPort    = ${mem.readPort.map(_.toString).getOrElse("N/A")}""")
            dbgs(s"""writeAddr = ${mem.writeAddr.map(_.toString).mkString(",")}""")
            dbgs(s"""readAddr  = ${mem.readAddr.map(_.toString).mkString(",")}""")
            dbgs(s"""start     = ${mem.writeStart.map(_.toString).getOrElse("N/A")}""")
            dbgs(s"""end       = ${mem.writeEnd.map(_.toString).getOrElse("N/A")}""")
            dbgs(s"""producer = ${mem.producer.map(_.toString).getOrElse("N/A")}""")
            dbgs(s"""consumer  = ${mem.consumer.map(_.toString).getOrElse("N/A")}""")
          }
        }
      }
    }
    dbgl(s"Generated write stages: ") {
      cu.writeStages.foreach(stage => dbgs(s"  $stage"))
    }
    dbgl(s"Generated read stages: ") {
      cu.readStages.foreach(stage => dbgs(s"$stage"))
    }
    dbgl("Generated compute stages: ") {
      cu.computeStages.foreach(stage => dbgs(s"$stage"))
    }
    dbgl(s"CU global inputs:") {
      globalInputs(cu).foreach{in => dbgs(s"$in") }
    }
  }

  def qdef(lhs:Any):String = {
    val rhs = lhs match {
      case lhs:Expr if (composed.contains(lhs)) => s"-> ${qdef(compose(lhs))}"
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

}
