package spatial.banking

import argon.analysis._
import argon.core._
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

class LineBufferConfigurer(override val mem: Exp[_], override val strategy: BankingStrategy) extends MemoryConfigurer(mem,strategy) {

  override def getAccessVector(access: Access): Seq[CompactMatrix] = access.node match {
    case Def(LineBufferColSlice(_, row, col, len)) => accessPatternOf(access.node).last match {
      case Affine(as, is, b) =>
        Seq.tabulate(len.toInt){ c =>
          CompactMatrix(Array(AffineVector(as, is, b + c)), access)
        }
      case _ =>
        // EXPERIMENTAL: Treat the col address as its own index
        Seq.tabulate(len.toInt){ c =>
          CompactMatrix(Array(AffineVector(Array(1), Seq(col), c)), access)
        }
    }
    case _ => accessPatternOf(access.node).last match {
      case Affine(as, is, b) =>
        val matrix = CompactMatrix(Array(AffineVector(as,is,b)), access)
        Seq(matrix)
      case _ => Seq(CompactMatrix(Array(RandomVector), access))
    }
  }

  protected def annotateTransientAccesses(accesses: Seq[Access]): Unit = accesses.foreach{case (node,ctrl) =>
    def unknownRows(): Int = {
      bug(c"Cannot load variable number of rows into linebuffer $mem - cannot statically determine which is transient")
      bug(mem.ctx)
      0
    }

    // Simple check, fragile if load structure ever changes.  If this is ParLineBufferRotateEnq with rows =/= lb stride,
    // then this is transient. We get rows from parent of parent of access' counter
    val rowStride = mem match {case Def(LineBufferNew(_,_,Exact(stride))) => stride.toInt; case _ => 0}
    val rowsWritten = node match {
      case Def(DenseTransfer(_, _, _, tsizes, _, _, _, _)) => tsizes.dropRight(1).last.toInt
      case Def(_: LineBufferLoad[_]) => rowStride       // Not transient
      case Def(_: BankedLineBufferLoad[_]) => rowStride // Not transient
      case Def(_: LineBufferColSlice[_]) => rowStride   // Not transient
      case _ =>
        val grandParent = parentOf(ctrl)
        val counterHolder = grandParent.flatMap{gp => if (isStreamPipe(gp)) Some(gp) else parentOf(gp) }
        counterHolder.map(_.node).map {
          case Def(op: UnrolledForeach) => counterLength(countersOf(op.cchain).last).getOrElse(unknownRows())
          case Def(op: OpForeach) => counterLength(countersOf(op.cchain).last).getOrElse(unknownRows())
          case Def(_: UnitPipe) => 1
          case _ => 0
        }
    }
    // Console.println(s"checking $mem $access for $rowstride $rowsWritten")
    isTransient(node) = rowStride != rowsWritten
  }

  override def bank(readers: Seq[Access], writers: Seq[Access]): Seq[MemoryInstance] = {
    annotateTransientAccesses(readers ++ writers)
    super.bank(readers, writers)
  }

}
