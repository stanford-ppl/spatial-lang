package spatial.analysis

/**
  * In Spatial, a "global" is any value which is solely a function of input arguments
  * and constants. These are computed prior to starting the main computation, and
  * therefore appear constant to the majority of the program.
  *
  * Note that this is only true for stateless nodes. These rules should not be generated
  * for stateful hardware (e.g. accumulators, pseudo-random generators)
  **/
trait GlobalAnalyzer extends SpatialTraversal {
  import IR._
  override val name = "Global Analyzer"
  override val recurse = Always

  override protected def visit(lhs: Sym[_], rhs: Op[_]) = lhs match {
    case Effectful(_,_) =>
    case Op(RegRead(reg)) if isArgIn(reg) => isGlobal(lhs) = true
    case _ =>
      if (isPrimitiveNode(lhs) && rhs.inputs.nonEmpty && rhs.inputs.forall(isGlobal(_)))
        isGlobal(lhs) = true
  }
}
