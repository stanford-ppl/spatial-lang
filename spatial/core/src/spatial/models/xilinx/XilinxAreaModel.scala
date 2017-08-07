package spatial.models
package xilinx

import argon.core._
import argon.nodes._
import forge._
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

abstract class XilinxAreaModel extends AreaModel {
  @stateful override def summarize(area: Area): Area = {
    val area = AreaMap[Double]()
    area
  }
}
