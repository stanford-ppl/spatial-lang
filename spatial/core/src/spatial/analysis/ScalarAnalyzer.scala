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

  object IterMin {
    def unapply(x: Exp[_]): Option[BigInt] = ctrOf(x) match {
      case Some(Def(CounterNew(start,end,Exact(step),_))) => if (step > 0) boundOf.get(start) else boundOf.get(end)
      case _ => None
    }
  }
  object IterMax {
    def unapply(x: Exp[_]): Option[BigInt] = ctrOf(x) match {
      case Some(Def(CounterNew(start,end,Exact(step),_))) => if (step > 0) boundOf.get(end) else boundOf.get(start)
      case _ => None
    }
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

    case RegWrite(reg@Bounded(b1), Bounded(b2), _) if !isAccum(lhs) && !isHostIO(reg) =>
      dbgs(s"Reg write outside loop")
      boundOf(reg) = Bound((b1 meet b2).bound)
    case RegWrite(reg, Bounded(b), _) if !isAccum(lhs) && !isHostIO(reg) =>
      dbgs(s"Reg write outside loop")
      boundOf(reg) = Bound(b.bound)

    case SetArg(reg@Bounded(b1),Bounded(b2)) => boundOf(reg) = b1 meet b2
    case SetArg(reg, Bounded(b))             => boundOf(reg) = b

    case IfThenElse(c, thenp, elsep) => (thenp.result, elsep.result) match {
      case (Bounded(a), Bounded(b)) => boundOf(lhs) = a meet b
      case _ => // No bound defined otherwise
    }

    case FixNeg(Final(a)) => boundOf(lhs) = Final(-a)
    case FixNeg(Exact(a)) => boundOf(lhs) = Exact(-a)
    case FixNeg(Bound(a)) => boundOf(lhs) = Bound(-a) // TODO: Not really correct

    case FixAdd(Final(a),Final(b)) => boundOf(lhs) = Final(a + b)
    case FixAdd(Exact(a),Exact(b)) => boundOf(lhs) = Exact(a + b)
    case FixAdd(Bound(a),Bound(b)) => boundOf(lhs) = Bound(a + b)
    case FixAdd(Bound(a), IterMax(i)) => boundOf(lhs) = Bound(a + i)
    case FixAdd(IterMax(i),Bound(a)) => boundOf(lhs) = Bound(a + i)
    case FixAdd(IterMax(i),IterMax(j)) => boundOf(lhs) = Bound(i + j)

    case FixSub(Final(a),Final(b)) => boundOf(lhs) = Final(a - b)
    case FixSub(Exact(a),Exact(b)) => boundOf(lhs) = Exact(a - b)
    case FixSub(Bound(a),Bound(b)) => boundOf(lhs) = Bound(a - b)
    case FixSub(Bound(a),IterMin(i)) => boundOf(lhs) = Bound(a - i)
    case FixSub(IterMax(i),Bound(a)) => boundOf(lhs) = Bound(i - a)
    case FixSub(IterMax(i),IterMin(j)) => boundOf(lhs) = Bound(i - j)

    case FixMul(Final(a),Final(b)) => boundOf(lhs) = Final(a * b)
    case FixMul(Exact(a),Exact(b)) => boundOf(lhs) = Exact(a * b)
    case FixMul(Bound(a),Bound(b)) => boundOf(lhs) = Bound(a * b)
    case FixMul(Bound(a),IterMax(i)) => boundOf(lhs) = Bound(a * i)
    case FixMul(IterMax(i),Bound(a)) => boundOf(lhs) = Bound(i * a)
    case FixMul(IterMax(i),IterMax(j)) => boundOf(lhs) = Bound(i * j)

    case FixDiv(Final(a),Final(b)) => boundOf(lhs) = Exact(a / b + (if ( (a mod b) > 0) 1 else 0))
    case FixDiv(Exact(a),Exact(b)) => boundOf(lhs) = Exact(a / b + (if ( (a mod b) > 0) 1 else 0))
    case FixDiv(Bound(a),Bound(b)) => boundOf(lhs) = Bound(a / b + (if ( (a mod b) > 0) 1 else 0))
    case FixMod(_, Bound(b)) => boundOf(lhs) = Bound(b - 1)

    case Min(Final(a),Final(b)) => boundOf(lhs) = Final(a min b)
    case Min(Exact(a),Exact(b)) => boundOf(lhs) = Bound(a min b)
    case Min(Bound(a),Bound(b)) => boundOf(lhs) = Bound(a min b)
    case Min(Bound(a),IterMax(i)) => boundOf(lhs) = Bound(a min i)
    case Min(IterMax(i),Bound(a)) => boundOf(lhs) = Bound(i min a)
    case Min(IterMax(i),IterMax(j)) => boundOf(lhs) = Bound(i min j)

    case Max(Final(a),Final(b)) => boundOf(lhs) = Final(a max b)
    case Max(Exact(a),Exact(b)) => boundOf(lhs) = Bound(a max b)
    case Max(Bound(a),Bound(b)) => boundOf(lhs) = Bound(a max b)
    case Max(Bound(a),IterMax(i)) => boundOf(lhs) = Bound(a max i)
    case Max(IterMax(i),Bound(a)) => boundOf(lhs) = Bound(i max a)
    case Max(IterMax(i),IterMax(j)) => boundOf(lhs) = Bound(i max j)

    case FIFONumel(fifo) => boundOf(lhs) = Bound(constSizeOf(fifo))
    case FILONumel(filo) => boundOf(lhs) = Bound(constSizeOf(filo))

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
