package spatial.models
package xilinx

import argon.core._
import argon.nodes._
import forge._
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

abstract class XilinxAreaModel extends AreaModel[XilinxArea,XilinxAreaSummary] {

  @stateful override def areaOfDelayLine(length: Int, width: Int, par: Int): XilinxArea = {
    val nregs = width*length
    XilinxArea(regs = nregs*par)
  }

  @stateful override def areaOfNode(e: Exp[_], d: Def): XilinxArea = d match {
    case _ =>
      warn(e.ctx, s"Don't know area of $e = $d")
      NoArea
  }

}
