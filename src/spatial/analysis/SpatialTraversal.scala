package spatial.analysis

import argon.traversal.Traversal
import spatial.SpatialExp

trait SpatialTraversal extends Traversal {
  val IR: SpatialExp
  import IR._

  def getStages(blks: Scope[_]*): Seq[Sym[_]] = blks.flatMap(scopeContents).flatMap(_.lhs)

  def getPrimitiveNodes(blks: Scope[_]*): Seq[Sym[_]] = getStages(blks:_*).filter(isPrimitiveNode)
  def getControlNodes(blks: Scope[_]*): Seq[Sym[_]] = getStages(blks:_*).filter(isControlNode)
  def getAllocations(blks: Scope[_]*): Seq[Sym[_]] = getStages(blks:_*).filter(isAllocation)

  def hasPrimitiveNodes(blks: Scope[_]*): Boolean = blks.exists{blk => getControlNodes(blk).nonEmpty }
  def hasControlNodes(blks: Scope[_]*): Boolean = blks.exists{blk => getControlNodes(blk).nonEmpty }
}
