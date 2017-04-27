package spatial.codegen.pirgen

import scala.collection.mutable

trait PIRPrintout extends PIRTraversal {
  import IR._

  override val name = "PIR Printout"
  override val recurse = Always

  val mappingIn  = mutable.HashMap[Expr, List[CU]]()

  val splitMappingIn = mutable.HashMap[Expr, List[List[CU]]]()

  def cusExcept(cu: CU): Iterable[CU] = {
    val cus = if (splitMappingIn.nonEmpty) splitMappingIn.values.flatten.flatten
    else mappingIn.values.flatten
    cus.filterNot(_ == cu)
  }

  def printCU(cu: CU): Unit = {
    val style = cu.style match {
      case _:MemoryCU => "PMU"
      case _:FringeCU => "Fringe"
      case _          => "PCU"
    }
    dbg("\n")
    dbg(style + " " + cu.toString)
    dbg("  Parent: " + cu.parentCU.map(_.name).getOrElse("None"))
    dbg("  Lanes: " + cu.lanes)
    dbg("  Compute stages:")
    cu.computeStages.foreach{stage => dbg(s"    $stage") }
    dbg("  Read stages:")
    cu.readStages.foreach{stage => dbg(s"    $stage") }
    dbg("  Write stages:")
    cu.writeStages.foreach{stage => dbg(s"    $stage") }
    dbg("  Control stages:")
    cu.controlStages.foreach{stage => dbg(s"    $stage") }

    cu.style match {
      case _:MemoryCU =>
        val cost = getUtil(cu, cusExcept(cu))
        reportUtil(cost)
      case _:FringeCU =>
      case _ =>
        val cost = getUtil(cu, cusExcept(cu))
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
