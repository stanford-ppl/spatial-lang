package spatial.analysis

import org.virtualized.SourceContext

trait DimensionAnalyzer extends SpatialTraversal {
  import IR._

  override val name = "Dimension Analyzer"
  override val recurse = Always

  var softValues = Map[Exp[Reg[_]],Exp[_]]()
  var offchips = Set[Exp[DRAM[Any]]]()

  private def checkOnchipDims(dims: Seq[Exp[Index]])(implicit ctx: SrcCtx): Unit = {
    dims.zipWithIndex.foreach{
      case (Exact(_), i) =>
      case (_, i) => new InvalidOnchipDimensionError(i)
    }
  }

  override protected def visit(lhs: Sym[_], rhs: Op[_]) = rhs match {
    case SetArg(reg, value) => softValues += reg -> value
    case DRAMNew(_) => offchips += lhs.asInstanceOf[Exp[DRAM[Any]]]
    case SRAMNew(_) => checkOnchipDims(stagedDimsOf(lhs.asInstanceOf[Exp[SRAM[_]]]))(ctxOrHere(lhs))
    case FIFONew(_) => checkOnchipDims(List(sizeOf(lhs.asInstanceOf[Exp[FIFO[Any]]])))(ctxOrHere(lhs))
    case _ => super.visit(lhs, rhs)
  }

  override protected def postprocess[T:Staged](b: Block[T]) = {
    offchips.foreach{dram =>
      val softDims = dimsOf(dram).zipWithIndex.map{case (dim, i) => dim match {
        case Op(RegRead(reg)) if isArgIn(reg) && softValues.contains(reg) =>
          val softDim = softValues(reg)
          assert(softDim.tp match {case IntType() => true; case _ => false})
          softDim.asInstanceOf[Exp[Index]]
        case _ if isGlobal(dim) => dim.asInstanceOf[Exp[Index]]
        case _ => new InvalidOffchipDimensionError(dram, i); int32(0)
      }}
      softDimsOf(dram) = softDims
    }

    super.postprocess(b)
  }

}
