package spatial.analysis

import argon.analysis.AccessPatternAnalyzer
import spatial.SpatialExp

trait SpatialAccessAnalyzer extends AccessPatternAnalyzer {
  override val IR: SpatialExp
  import IR._

  override val name = "Spatial Affine Analysis"
  override val recurse = Default

  // Pair of symbols for nodes used in address calculation addition nodes
  def indexPlusUnapply(x: Exp[Index]): Option[(Exp[Index], Exp[Index])] = x match {
    case Op(FixAdd(a,b)) => Some((a,b))
    case Op(FixSub(a,b)) => Some((a,b))
    case _ => None
  }
  // Pair of symbols for nodes used in address calculation multiplication nodes
  def indexTimesUnapply(x: Exp[Index]): Option[(Exp[Index], Exp[Index])] = x match {
    case Op(FixMul(a,b)) => Some((a,b))
    case _ => None
  }
  // List of loop scopes. Each scope contains a list of iterators and scopes to traverse for loop nodes
  def loopUnapply(x: Exp[_]): Option[Seq[(Seq[Bound[Index]], Seq[Block[_]])]] = x match {
    case Op(e: OpForeach)        => Some(List(e.iters -> List(e.func)))
    case Op(e: OpReduce[_])      => Some(List(e.iters -> List(e.map,e.load,e.reduce,e.store)))
    case Op(e: OpMemReduce[_,_]) => Some(List(e.itersMap -> List(e.map),
                                             (e.itersMap ++ e.itersRed) -> List(e.loadAcc,e.loadRes,e.reduce,e.storeAcc)))
    case _ => None
  }
  // Memory being read + list of addresses (for N-D access)
  def readUnapply(x: Exp[_]): Option[(Exp[_], Seq[Exp[Index]])] = x match {
    case Op(SRAMLoad(sram,dims,inds,ofs)) => Some((sram, inds))
    case _ => None
  }
  // Memory being written + list of addresses (for N-D access)
  def writeUnapply(x: Exp[_]): Option[(Exp[_], Seq[Exp[Index]])] = x match {
    case Op(SRAMStore(sram,dims,inds,ofs,_,_)) => Some((sram,inds))
    case _ => None
  }

  override def isInvariant(b: Exp[Index], i: Bound[Index]): Boolean = b match {
    case Exact(_) => true
    case Def(RegRead(reg)) =>
      val loop = loopFromIndex(i)
      writersOf(reg).forall{writer =>
        val common = lca(writer.ctrl, (loop,false))
        // Either no common ancestor at all (unlikely), or the common ancestor is not the same as the controller
        // containing the read and the common ancestor is not a streaming controller
        common.isEmpty || (!isStreamPipe(common.get) && common.get.node != loop)
      }

    case _ => super.isInvariant(b, i)
  }

  override protected def visit(lhs: Sym[_], rhs: Op[_]) = rhs match {
    case e: DenseTransfer[_,_] => accessPatternOf(lhs) = e.iters.map{i => LinearAccess(i) }
    case e: SparseTransfer[_]  => accessPatternOf(lhs) = List(LinearAccess(e.i))
    case _ => super.visit(lhs, rhs)
  }

}
