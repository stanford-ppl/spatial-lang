package spatial.codegen.pirgen

import argon.core._

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
    dbgs("\n")
    dbgs(style + " " + cu.toString)
    dbgs("  isPMU: " + cu.isPMU)
    dbgs("  isPCU: " + cu.isPCU)
    dbgs("  Parent: " + cu.parentCU.map(_.name).getOrElse("None"))
    dbgs("  Lanes: " + cu.lanes)
    dbgs("  Counter chains:")
    cu.cchains.foreach{cchain => dbgs(s"    ${cchain.longString}") }
    dbgs("  Memories:")
    cu.mems.foreach{mem => dbgs(s"    $mem") }
    dbgs("  Compute stages:")
    cu.computeStages.foreach{stage => dbgs(s"    $stage") }
    dbgs("  Read stages:")
    cu.readStages.foreach { case (mems, stages) =>
      dbgs("    Mems: " + mems.mkString(", "))
      stages.foreach { stage => dbgs(s"      $stage") }
    }
    dbgs("  Write stages:")
    cu.writeStages.foreach { case (mems, stages) =>
      dbgs("    Mems: " + mems.mkString(", "))
      stages.foreach { stage => dbgs(s"      $stage") }
    }
    dbgs("  Control stages:")
    cu.controlStages.foreach{stage => dbgs(s"    $stage") }

    val inputs = groupBuses(globalInputs(cu))
    val outputs = groupBuses(globalOutputs(cu))
    dbgs("  Scalar Inputs: ")
    (inputs.args ++ inputs.scalars).foreach{bus => dbgs(s"    $bus") }
    dbgs("  Scalar Outputs: ")
    (outputs.args ++ outputs.scalars).foreach{bus => dbgs(s"    $bus") }
    dbgs("  Vector Inputs: ")
    inputs.vectors.foreach{bus => dbgs(s"    $bus") }
    dbgs("  Vector Outputs: ")
    outputs.vectors.foreach{bus => dbgs(s"    $bus") }

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
