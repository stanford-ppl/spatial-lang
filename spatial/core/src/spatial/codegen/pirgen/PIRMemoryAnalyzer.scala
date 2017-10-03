package spatial.codegen.pirgen

import argon.core._
import argon.nodes._
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._
import org.virtualized.SourceContext

import scala.collection.mutable

class PIRMemoryAnalyzer(implicit val codegen:PIRCodegen) extends PIRTraversal {
  override val name = "PIR Memory Analyzer"
  var IR = codegen.IR

  override def preprocess[S:Type](b: Block[S]): Block[S] = {
    super.preprocess(b)
  }

  override def postprocess[S:Type](b: Block[S]): Block[S] = {
    super.postprocess(b)
  }

  override protected def visit(lhs: Sym[_], rhs: Op[_]) = {
    rhs match {
      case ParLocalReader(reads)  =>
        val (mem, addrs, _) = reads.head
        addrs.foreach { addrs => 
          markInnerDim(mem, addrs.head)
        }

      case ParLocalWriter(writes)  => 
        val (mem, value, addrs, ens) = writes.head
        addrs.foreach { addrs => 
          markInnerDim(mem, addrs.head)
        }

      case _ => 
    }
    super.visit(lhs, rhs)
  }

  def containsInnerInd(ind:Expr):Boolean = {
    ind match {
      case _:Bound[_] => isInnerControl(ctrlOf(ind).get.node)
      case e:Sym[_] => e.dependents.exists(containsInnerInd)
      case e => false
    }
  }

  def markInnerDim(mem:Expr, inds:Seq[Expr]) = {
    inds.zipWithIndex.foreach { case (ind, dim) =>
      if (containsInnerInd(ind)) {
        innerDimOf(mem) = dim
        dbgs(s"${qdef(mem)}")
        dbgs(s"innerDimOf($mem) = $dim")
      }
    }
  }

}
