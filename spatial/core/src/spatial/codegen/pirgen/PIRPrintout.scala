package spatial.codegen.pirgen

import argon.core._

import scala.collection.mutable

class PIRPrintout(implicit val codegen:PIRCodegen) extends PIRTraversal {
  override val name = "PIR Printout"
  override val recurse = Always
  var IR = codegen.IR

  def cus = mappingOf.values.flatten.collect{ case cu:CU => cu }.toList

  def printCU(cu: CU): Unit = {

    val style = cu.style match {
      case MemoryCU => "PMU"
      case _:FringeCU => "Fringe"
      case _          => "PCU"
    }
    dbgblk(style + " " + cu.toString) {
      dbgs("isPMU: " + cu.isPMU)
      dbgs("isPCU: " + cu.isPCU)
      dbgs("Parent: " + cu.parentCU.map(_.name).getOrElse("None"))
      dbgs("Lanes: " + cu.lanes)
      dbgl("Counter chains:") {
        cu.cchains.foreach{cchain => dbgs(s"${quote(cchain)}") }
      }
      dbgl("Memories:") {
        cu.mems.foreach{mem => dbgs(s"$mem") }
      }
      dbgl("Compute stages:") {
        cu.computeStages.foreach{stage => dbgs(s"$stage") }
      }
      dbgl("Control stages:") {
        cu.controlStages.foreach{stage => dbgs(s"$stage") }
      }

      dbgl("Scalar Inputs: ") {
        collectInput[ScalarBus](cu).foreach{bus => dbgs(s"$bus") }
      }
      dbgl("Scalar Outputs: ") {
        collectOutput[ScalarBus](cu).foreach{bus => dbgs(s"$bus") }
      }
      dbgl("Vector Inputs: ") {
        collectInput[VectorBus](cu).foreach{bus => dbgs(s"$bus") }
      }
      dbgl("Vector Outputs: ") {
        collectOutput[VectorBus](cu).foreach{bus => dbgs(s"$bus") }
      }

      cu.style match {
        case MemoryCU =>
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
    cus.foreach(printCU)
  }

}
