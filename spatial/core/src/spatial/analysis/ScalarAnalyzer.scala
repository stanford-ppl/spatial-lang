package spatial.analysis

import argon.core._
import argon.nodes._
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

trait ScalarAnalyzer extends SpatialTraversal {
  override val name = "Bound Analyzer"
  override val recurse = Never
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

  /**
    * In Spatial, a "global" is any value which is solely a function of input arguments
    * and constants. These are computed prior to starting the main computation, and
    * therefore appear constant to the majority of the program.
    *
    * Note that this is only true for stateless nodes. These rules should not be generated
    * for stateful hardware (e.g. accumulators, pseudo-random generators)
    **/
  def checkForGlobals(lhs: Sym[_], rhs: Op[_]): Unit = lhs match {
    case Effectful(_,_) =>
    case Op(RegRead(reg)) if isArgIn(reg) => isGlobal(lhs) = true
    case _ =>
      if (isPrimitiveNode(lhs) && rhs.inputs.nonEmpty && rhs.inputs.forall(isGlobal(_)))
        isGlobal(lhs) = true
  }

  /**
    * Propagates symbol maximum bounds. Generally assumes non-negative values, e.g. for index calculation
    */
  def analyzeBounds(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case RegRead(Bounded(b)) =>
      dbgs(s"Bounded register read $lhs: $b")
      boundOf(lhs) = b

    case RegRead(reg) =>
      dbgs(s"Register read of $reg")

    case RegWrite(reg@Bounded(b1), Bounded(b2), _) if !insideLoop && !isHostIO(reg) =>
      dbgs(s"Reg write outside loop")
      boundOf(reg) = Bound((b1 meet b2).bound)
    case RegWrite(reg, Bounded(b), _) if !insideLoop && !isHostIO(reg) =>
      dbgs(s"Reg write outside loop")
      boundOf(reg) = Bound(b.bound)

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

    case FixDiv(Final(a),Final(b)) => boundOf(lhs) = Exact(a / b + (if ( (a mod b) > 0) 1 else 0))
    case FixDiv(Exact(a),Exact(b)) => boundOf(lhs) = Exact(a / b + (if ( (a mod b) > 0) 1 else 0))
    case FixDiv(Bound(a),Bound(b)) => boundOf(lhs) = Bound(a / b + (if ( (a mod b) > 0) 1 else 0))


    case FixSub(Def(FixAdd(Def(FixConvert(b)), Bounded(x))), a) if a == b => boundOf(lhs) = x
    case _ =>
  }

  override protected def visit(lhs: Sym[_], rhs: Op[_]) = {
    checkForGlobals(lhs,rhs)
    analyzeBounds(lhs,rhs)
    dbgs(s"Visiting $lhs = $rhs [isLoop: ${isLoop(lhs)}]")
    maybeLoop(isLoop(lhs)){ rhs.blocks.foreach(blk => visitBlock(blk)) }
  }
}
