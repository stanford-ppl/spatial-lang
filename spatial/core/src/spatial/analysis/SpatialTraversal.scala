package spatial.analysis

import argon.core._
import argon.nodes._
import argon.traversal.Traversal
import spatial.aliases._
import spatial.metadata._
import spatial.utils._

trait SpatialTraversal extends Traversal {
  def blockNestedScopeAndResult(block: Block[_]): (Set[Exp[_]], Seq[Exp[_]]) = {
    val scope = blockNestedContents(block).flatMap(_.lhs)
      .filterNot(s => isGlobal(s))
      .filter{e => e.tp == UnitType || Bits.unapply(e.tp).isDefined }
      .map(_.asInstanceOf[Exp[_]]).toSet

    val result = (block +: scope.toSeq.flatMap{case s@Def(d) => d.blocks; case _ => Nil}).flatMap{b => exps(b) }

    (scope, result)
  }

  def getStages(blks: Block[_]*): Seq[Sym[_]] = blks.flatMap(blockContents).flatMap(_.lhs)

  def getPrimitiveNodes(blks: Block[_]*): Seq[Sym[_]] = getStages(blks:_*).filter(isPrimitiveNode)
  def getControlNodes(blks: Block[_]*): Seq[Sym[_]] = getStages(blks:_*).filter(isControlNode)
  def getAllocations(blks: Block[_]*): Seq[Sym[_]] = getStages(blks:_*).filter(isAllocation)

  def hasPrimitiveNodes(blks: Block[_]*): Boolean = blks.exists{blk => getPrimitiveNodes(blk).nonEmpty }
  // Don't count switches or switch cases as control nodes for level analysis
  def hasControlNodes(blks: Block[_]*): Boolean = blks.exists{blk => getControlNodes(blk).nonEmpty }


  def rerun(e: Exp[_], blk: Block[_]): Unit = {
    preprocess(blk)(mtyp(blk.tp))
    e match {
      case lhs @ Op(rhs) => visit(lhs.asInstanceOf[Sym[_]], rhs)
      case lhs @ Def(rhs) => visitFat(Seq(lhs.asInstanceOf[Sym[_]]), rhs)
    }
    postprocess(blk)(mtyp(blk.tp))
  }
}
