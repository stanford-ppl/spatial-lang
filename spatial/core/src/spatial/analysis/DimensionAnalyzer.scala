package spatial.analysis

import argon.core._
import argon.nodes._
import org.virtualized.SourceContext
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

trait DimensionAnalyzer extends SpatialTraversal {
  override val name = "Dimension Analyzer"
  override val recurse = Always

  var softValues = Map[Exp[Reg[_]],Exp[_]]()
  var offchips = Set[Exp[DRAM[Any]]]()

  def isStaticallyKnown(d: Exp[Index]): Boolean = d match {
    case x if x.dependsOnType{case LocalReader(_) => true} => false
    case Exact(_) => true
    case _ => false
  }

  private def checkOnchipDims(mem: Exp[_], dims: Seq[Exp[Index]])(implicit ctx: SrcCtx): Unit = {
    dbg(u"$mem: " + dims.mkString(", "))
    dims.zipWithIndex.foreach{case (x,i) =>
      if (!isStaticallyKnown(x)) {
        new spatial.InvalidOnchipDimensionError(mem,i)
      }
    }
  }

  override protected def visit(lhs: Sym[_], rhs: Op[_]) = rhs match {
    case SetArg(reg, value) => softValues += reg -> value
    case _:DRAMNew[_,_]     => offchips += lhs.asInstanceOf[Exp[DRAM[Any]]]
    case _:SRAMNew[_,_]     => checkOnchipDims(lhs, stagedDimsOf(lhs))(lhs.ctx)
    case _:FIFONew[_]       => checkOnchipDims(lhs, List(stagedSizeOf(lhs.asInstanceOf[Exp[FIFO[Any]]])))(lhs.ctx)
    case lb:LineBufferNew[_] =>
      checkOnchipDims(lhs, stagedDimsOf(lhs))(lhs.ctx)
      if (!isStaticallyKnown(lb.stride)) {
        error(lhs.ctx, c"LineBuffer $lhs has an invalid stride.")
        error(c"Only constants can be used for LineBuffer stride")
        error(lhs.ctx)
      }

    case _:RegFileNew[_,_]   => checkOnchipDims(lhs, stagedDimsOf(lhs))(lhs.ctx)
    case _ => super.visit(lhs, rhs)
  }

  override protected def postprocess[T:Type](b: Block[T]) = {
    offchips.foreach{dram =>
      val softDims = stagedDimsOf(dram).zipWithIndex.map{case (dim, i) => dim match {
        case Op(RegRead(reg)) if isArgIn(reg) =>
          if (!softValues.contains(reg)) {
            warn(reg.ctx, u"ArgIn $reg was not set before used as a dimension of DRAM $dram")
            warn("This will cause the DRAM to have a dimension of size 0")
            warn(reg.ctx)
          }
          val softDim = softValues.getOrElse(reg, int32s(0))
          softDim.asInstanceOf[Exp[Index]]
        case _ if isGlobal(dim) => dim.asInstanceOf[Exp[Index]]
        case _ =>
          new spatial.InvalidOffchipDimensionError(dram, i)(dram.ctx, state)
          int32s(0)
      }}
      softDimsOf(dram) = softDims
    }

    super.postprocess(b)
  }

}
