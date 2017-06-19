package spatial.analysis

import argon.core._
import argon.traversal.Traversal
import spatial.aliases._
import spatial.utils._

trait SpatialTraversal extends Traversal {
  def getStages(blks: Block[_]*): Seq[Sym[_]] = blks.flatMap(blockContents).flatMap(_.lhs)

  def getPrimitiveNodes(blks: Block[_]*): Seq[Sym[_]] = getStages(blks:_*).filter(isPrimitiveNode)
  def getControlNodes(blks: Block[_]*): Seq[Sym[_]] = getStages(blks:_*).filter(isControlNode)
  def getAllocations(blks: Block[_]*): Seq[Sym[_]] = getStages(blks:_*).filter(isAllocation)

  def hasPrimitiveNodes(blks: Block[_]*): Boolean = blks.exists{blk => getPrimitiveNodes(blk).nonEmpty }
  // Don't count switches or switch cases as control nodes for level analysis
  def hasControlNodes(blks: Block[_]*): Boolean = blks.exists{blk => getControlNodes(blk).nonEmpty }
}
