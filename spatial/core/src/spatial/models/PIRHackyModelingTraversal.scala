package spatial.models

import spatial.analysis.ModelingTraversal

import scala.collection.mutable

trait PIRHackyModelingTraversal extends ModelingTraversal {
  import IR._

  // Not a true traversal. Should it be?
  override def pipeDelaysAndGaps(b: Block[_], oos: Map[Exp[_],Long] = Map.empty) = {
    super.pipeDelaysAndGaps(b, oos)
  }

}
