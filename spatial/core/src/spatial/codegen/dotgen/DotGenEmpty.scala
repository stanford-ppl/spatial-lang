package spatial.codegen.dotgen

import argon.codegen.dotgen.DotCodegen
import argon.core._
import spatial.aliases._

trait DotGenEmpty extends DotCodegen {

  private var missed: Set[String] = Set.empty

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = {
    missed += c"${rhs.getClass}"
  }

  override def postprocess[S:Type](b: Block[S]): Block[S] = {
    if (missed.nonEmpty) {
      warn(s"Dot generation did not have rules for the following node(s): ")
      missed.foreach{node => warn(c"  $node") }
    }

    super.postprocess(b)
  }

}
