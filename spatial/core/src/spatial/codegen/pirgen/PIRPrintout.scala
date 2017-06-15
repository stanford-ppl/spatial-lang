package spatial.codegen.pirgen

import argon.internals._
import spatial.compiler._

import scala.collection.mutable

trait PIRPrintout extends PIRTraversal {
  override val name = "PIR Printout"
  override val recurse = Always

  val mappingIn  = mutable.HashMap[Expr, List[CU]]()

  val splitMappingIn = mutable.HashMap[Expr, List[List[CU]]]()

  def cus = if (splitMappingIn.isEmpty) mappingIn.values.toList.flatten else splitMappingIn.values.toList.flatten.flatten

  def printCU(cu: CU): Unit = {

    val style = cu.style match {
      case _:MemoryCU => "PMU"
      case _:FringeCU => "Fringe"
      case _          => "PCU"
    }
    dbg("\n")
    dbg(style + " " + cu.toString)
    dbg("  isPMU: " + cu.isPMU)
    dbg("  isPCU: " + cu.isPCU)
    dbg("  Parent: " + cu.parentCU.map(_.name).getOrElse("None"))
    dbg("  Lanes: " + cu.lanes)
    dbg("  Counter chains:")
    cu.cchains.foreach{cchain => dbg(s"    ${cchain.longString}") }
    dbg("  Memories:")
    cu.mems.foreach{mem => dbg(s"    $mem") }
    dbg("  Compute stages:")
    cu.computeStages.foreach{stage => dbg(s"    $stage") }
    dbg("  Read stages:")
    cu.readStages.foreach { case (mems, stages) =>
      dbg("    Mems: " + mems.mkString(", "))
      stages.foreach { stage => dbg(s"      $stage") }
    }
    dbg("  Write stages:")
    cu.writeStages.foreach { case (mems, stages) =>
      dbg("    Mems: " + mems.mkString(", "))
      stages.foreach { stage => dbg(s"      $stage") }
    }
    dbg("  Control stages:")
    cu.controlStages.foreach{stage => dbg(s"    $stage") }

    val inputs = groupBuses(globalInputs(cu))
    val outputs = groupBuses(globalOutputs(cu))
    dbg("  Scalar Inputs: ")
    (inputs.args ++ inputs.scalars).foreach{bus => dbg(s"    $bus") }
    dbg("  Scalar Outputs: ")
    (outputs.args ++ outputs.scalars).foreach{bus => dbg(s"    $bus") }
    dbg("  Vector Inputs: ")
    inputs.vectors.foreach{bus => dbg(s"    $bus") }
    dbg("  Vector Outputs: ")
    outputs.vectors.foreach{bus => dbg(s"    $bus") }

    cu.style match {
      case _:MemoryCU =>
        val cost = getUtil(cu, cus)
        reportUtil(cost)
      case _:FringeCU =>
      case _ =>
        val cost = getUtil(cu, cus)
        reportUtil(cost)
    }
  }

  override protected def visit(lhs: Sym[_], rhs: Op[_]) {
    if (splitMappingIn.contains(lhs)) {
      splitMappingIn(lhs).flatten.foreach{cu => printCU(cu) }
    }
    else if (mappingIn.contains(lhs)) {
      mappingIn(lhs).foreach{cu => printCU(cu) }
    }
  }

}
