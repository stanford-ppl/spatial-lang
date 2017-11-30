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
  def dbgcu(cu:ComputeUnit):Unit = dbgblk(s"Generated CU: $cu") {
    dbgblk(s"cchains: ") {
      cu.cchains.foreach{cchain => dbgs(s"${quote(cchain)}") }
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
      collectInput[GlobalBus](cu).foreach{in => dbgs(s"$in") }
    }
    dbgs(s"regs:${cu.regs}")
    dbgl(s"regTable:") {
      cu.regTable.foreach { case (exp, comp) => 
        dbgs(s"$exp -> $comp [${comp.getClass.getSimpleName}]")
      }
    }
  }

  def quote(n:Any):String = n match {
    case x:Expr => s"${composed.get(x).fold("") {o => s"${quote(o)}_"} }$x"
    case n:CUCounter => s"ctr(start=${n.start}, end=${n.end}, stride=${n.stride}, par=${n.par})"
    case n:CChainInstance =>  s"${n.name} (${n.counters.map(quote).mkString(",")})"
    case n: UnitCChain => s"${n} [unit]"
    case DefStage(exp, isReduce) => s"DefStage(${qdef(exp)}, isReduce=$isReduce)"
    case x:Iterable[_] => x.map(quote).toString
    case n => n.toString
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
