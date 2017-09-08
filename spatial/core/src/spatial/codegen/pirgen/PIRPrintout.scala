package spatial.codegen.pirgen

import argon.core._

import scala.collection.mutable

class PIRPrintout(mapping:mutable.Map[Expr, List[CU]])(implicit val codegen:PIRCodegen) extends PIRTraversal {
  override val name = "PIR Printout"
  override val recurse = Always
  var IR = codegen.IR

  def cus = mapping.values.toList.flatten

  def printCU(cu: CU): Unit = {

    val style = cu.style match {
      case _:MemoryCU => "PMU"
      case _:FringeCU => "Fringe"
      case _          => "PCU"
    }
    dbgblk(style + " " + cu.toString) {
      dbgs("isPMU: " + cu.isPMU)
      dbgs("isPCU: " + cu.isPCU)
      dbgs("Parent: " + cu.parentCU.map(_.name).getOrElse("None"))
      dbgs("Lanes: " + cu.lanes)
      dbgl("Counter chains:") {
        cu.cchains.foreach{cchain => dbgs(s"${cchain.longString}") }
      }
      dbgl("Memories:") {
        cu.mems.foreach{mem => dbgs(s"$mem") }
      }
      dbgl("Compute stages:") {
        cu.computeStages.foreach{stage => dbgs(s"$stage") }
      }
      dbgl("Read stages:") {
        cu.readStages.foreach { stage => dbgs(s"$stage") }
      }
      dbgl("Write stages:") {
        cu.writeStages.foreach { stage => dbgs(s"$stage") }
      }
      dbgl("Control stages:") {
        cu.controlStages.foreach{stage => dbgs(s"$stage") }
      }

      dbgl("Scalar Inputs: ") {
        scalarInputs(cu).foreach{bus => dbgs(s"$bus") }
      }
      dbgl("Scalar Outputs: ") {
        scalarOutputs(cu).foreach{bus => dbgs(s"$bus") }
      }
      dbgl("Vector Inputs: ") {
        vectorInputs(cu).foreach{bus => dbgs(s"$bus") }
      }
      dbgl("Vector Outputs: ") {
        vectorOutputs(cu).foreach{bus => dbgs(s"$bus") }
      }

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
  }

  override protected def visit(lhs: Sym[_], rhs: Op[_]) {
    if (mapping.contains(lhs)) {
      mapping(lhs).foreach{cu => printCU(cu) }
    }
  }

}
