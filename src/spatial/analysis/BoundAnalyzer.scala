package spatial.analysis

/**
  * Propagates symbol maximum bounds. Generally assumes non-negative values, e.g. for index calculation
  */
trait BoundAnalyzer extends SpatialTraversal {
  import IR._

  override val name = "Bound Analyzer"
  override val recurse = Always
  private var insideLoop = false
  def maybeLoop[T](isLoop: Boolean)(x: => T): T = {
    if (isLoop) {
      val prevLoop = insideLoop
      insideLoop = true
      val result = x
      insideLoop = prevLoop
      result
    }
    else x
  }

  override def visit(lhs: Sym[_], rhs: Op[_]) = rhs match {
    case RegRead(Bounded(b)) => boundOf(lhs) = b

    case RegWrite(reg@Bounded(b1), Bounded(b2), _) if !insideLoop => boundOf(reg) = b1 meet b2
    case RegWrite(reg, Bounded(b), _)              if !insideLoop => boundOf(reg) = b

    case SetArg(reg@Bounded(b1),Bounded(b2)) => boundOf(reg) = b1 meet b2
    case SetArg(reg, Bounded(b))             => boundOf(reg) = b

    case IfThenElse(c, thenp, elsep) => (thenp.result, elsep.result) match {
      case (Bounded(a), Bounded(b)) => boundOf(lhs) = a meet b
      case _ => // No bound defined otherwise
    }

    case FixAdd(Final(a),Final(b)) => boundOf(lhs) = Final(a + b)
    case FixAdd(Exact(a),Exact(b)) => boundOf(lhs) = Exact(a + b)
    case FixAdd(Bound(a),Bound(b)) => boundOf(lhs) = Bound(a + b)

    case FixSub(Final(a),Final(b)) => boundOf(lhs) = Final(a - b)
    case FixSub(Exact(a),Exact(b)) => boundOf(lhs) = Exact(a - b)
    case FixSub(Bound(a),Bound(b)) => boundOf(lhs) = Bound(a - b)

    case FixMul(Final(a),Final(b)) => boundOf(lhs) = Final(a * b)
    case FixMul(Exact(a),Exact(b)) => boundOf(lhs) = Exact(a * b)
    case FixMul(Bound(a),Bound(b)) => boundOf(lhs) = Bound(a * b)

    case FixDiv(Final(a),Final(b)) => boundOf(lhs) = Final(a / b + (if ( (a mod b) > 0) 1 else 0))
    case FixDiv(Exact(a),Exact(b)) => boundOf(lhs) = Exact(a / b + (if ( (a mod b) > 0) 1 else 0))
    case FixDiv(Bound(a),Bound(b)) => boundOf(lhs) = Bound(a / b + (if ( (a mod b) > 0) 1 else 0))

    case _ => maybeLoop(isLoop(lhs)){ super.visit(lhs, rhs) }
  }
}
