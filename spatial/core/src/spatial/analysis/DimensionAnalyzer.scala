package spatial.analysis

import argon.core._
import argon.nodes._
import org.virtualized.SourceContext
import spatial.compiler._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

trait DimensionAnalyzer extends SpatialTraversal {
  override val name = "Dimension Analyzer"
  override val recurse = Always

  var softValues = Map[Exp[Reg[_]],Exp[_]]()
  var offchips = Set[Exp[DRAM[Any]]]()

  private def checkOnchipDims(mem: Exp[_], dims: Seq[Exp[Index]])(implicit ctx: SrcCtx): Unit = {
    dbg(u"$mem: " + dims.mkString(", "))
    dims.zipWithIndex.foreach{
      case (x, i) if x.dependsOnType{case LocalReader(_) => true} =>
        new spatial.InvalidOnchipDimensionError(mem,i)
      case (Exact(_), i) =>
      case (_, i) =>
        new spatial.InvalidOnchipDimensionError(mem,i)
    }
  }

  override protected def visit(lhs: Sym[_], rhs: Op[_]) = rhs match {
    case SetArg(reg, value) => softValues += reg -> value
    case _:DRAMNew[_,_]     => offchips += lhs.asInstanceOf[Exp[DRAM[Any]]]
    case _:SRAMNew[_,_]     => checkOnchipDims(lhs, stagedDimsOf(lhs))(lhs.ctx)
    case _:FIFONew[_]       => checkOnchipDims(lhs, List(sizeOf(lhs.asInstanceOf[Exp[FIFO[Any]]])))(lhs.ctx)
    case _:LineBufferNew[_] => checkOnchipDims(lhs, stagedDimsOf(lhs))(lhs.ctx)
    case _:RegFileNew[_,_]  => checkOnchipDims(lhs, stagedDimsOf(lhs))(lhs.ctx)
    case _ => super.visit(lhs, rhs)
  }

  override protected def postprocess[T:Type](b: Block[T]) = {
    offchips.foreach{dram =>
      val softDims = stagedDimsOf(dram).zipWithIndex.map{case (dim, i) => dim match {
        case Op(RegRead(reg)) if isArgIn(reg) && softValues.contains(reg) =>
          val softDim = softValues(reg)
          assert(softDim.tp match {case IntType() => true; case _ => false})
          softDim.asInstanceOf[Exp[Index]]
        case _ if isGlobal(dim) => dim.asInstanceOf[Exp[Index]]
        case _ =>
          new spatial.InvalidOffchipDimensionError(dram, i)(dram.ctx, state)
          int32(0)
      }}
      softDimsOf(dram) = softDims
    }

    super.postprocess(b)
  }

}
