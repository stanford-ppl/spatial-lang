package spatial.analysis

import org.virtualized.SourceContext

trait DimensionAnalyzer extends SpatialTraversal {
  import IR._

  override val name = "Dimension Analyzer"
  override val recurse = Always

  var softValues = Map[Exp[Reg[_]],Exp[_]]()
  var offchips = Set[Exp[DRAM[Any]]]()

  private def checkOnchipDims(mem: Exp[_], dims: Seq[Exp[Index]])(implicit ctx: SrcCtx): Unit = {
    dbg(u"$mem: " + dims.mkString(", "))
    dims.zipWithIndex.foreach{
      case (x, i) if x.dependsOnType{case LocalReader(_) => true} => new InvalidOnchipDimensionError(mem,i)
      case (Exact(_), i) =>
      case (_, i) => new InvalidOnchipDimensionError(mem,i)
    }
  }

  override protected def visit(lhs: Sym[_], rhs: Op[_]) = rhs match {
    case SetArg(reg, value) => softValues += reg -> value
    case DRAMNew(_)         => offchips += lhs.asInstanceOf[Exp[DRAM[Any]]]
    case _:SRAMNew[_]       => checkOnchipDims(lhs, stagedDimsOf(lhs))(lhs.ctx)
    case _:FIFONew[_]       => checkOnchipDims(lhs, List(sizeOf(lhs.asInstanceOf[Exp[FIFO[Any]]])))(lhs.ctx)
    case _:LineBufferNew[_] => checkOnchipDims(lhs, stagedDimsOf(lhs))(lhs.ctx)
    case _:RegFileNew[_]    => checkOnchipDims(lhs, stagedDimsOf(lhs))(lhs.ctx)
    case _ => super.visit(lhs, rhs)
  }

  override protected def postprocess[T:Type](b: Block[T]) = {
    offchips.foreach{dram =>
      val softDims = dimsOf(dram).zipWithIndex.map{case (dim, i) => dim match {
        case Op(RegRead(reg)) if isArgIn(reg) && softValues.contains(reg) =>
          val softDim = softValues(reg)
          assert(softDim.tp match {case IntType() => true; case _ => false})
          softDim.asInstanceOf[Exp[Index]]
        case _ if isGlobal(dim) => dim.asInstanceOf[Exp[Index]]
        case _ => new InvalidOffchipDimensionError(dram, i)(dram.ctx); int32(0)
      }}
      softDimsOf(dram) = softDims
    }

    super.postprocess(b)
  }

}
