package spatial.codegen.pirgen

import scala.collection.mutable

trait PIRPrintout extends PIRTraversal {
  import IR._

  override val name = "PIR Printout"
  override val recurse = Always

  val mappingIn  = mutable.HashMap[Expr, List[CU]]()

  override protected def visit(lhs: Sym[_], rhs: Op[_]) {
    if (mappingIn.contains(lhs)) {
      mappingIn(lhs).foreach{cu =>
        val style = cu.style match {
          case _:MemoryCU => "PMU"
          case _:FringeCU => "Fringe"
          case _          => "PCU"
        }
        dbg("\n\n")
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
      }
    }
  }

}
