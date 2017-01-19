package spatial.analysis

import spatial.SpatialExp

trait NodeClasses extends SpatialMetadataExp {
  this: SpatialExp =>

  /** Control Nodes **/
  def isControlNode(e: Exp[_]): Boolean = isOuterControl(e) || isInnerControl(e)
  def isOuterControl(e: Exp[_]): Boolean = isOuterPipeline(e)
  def isInnerControl(e: Exp[_]): Boolean = isInnerPipeline(e) || isDRAMTransfer(e)

  def isOuterPipeline(e: Exp[_]): Boolean = isPipeline(e) && styleOf(e) != InnerPipe
  def isInnerPipeline(e: Exp[_]): Boolean = isPipeline(e) && styleOf(e) == InnerPipe

  def isDRAMTransfer(e: Exp[_]): Boolean = getDef(e).exists(isDRAMTransfer)
  def isDRAMTransfer(d: Def): Boolean = d match {
    case _:BurstLoad[_]  => true
    case _:BurstStore[_] => true
    case _:Gather[_]     => true
    case _:Scatter[_]    => true
    case _ => false
  }

  def isPipeline(e: Exp[_]): Boolean = getDef(e).exists(isPipeline)
  def isPipeline(d: Def): Boolean = d match {
    case _:Hwblock          => true
    case _:UnitPipe         => true
    case _:OpForeach        => true
    case _:OpReduce[_]      => true
    case _:OpMemReduce[_,_] => true
    case _ => false
  }

  def isLoop(e: Exp[_]): Boolean = getDef(e).exists(isLoop)
  def isLoop(d: Def): Boolean = d match {
    case _:OpForeach        => true
    case _:OpReduce[_]      => true
    case _:OpMemReduce[_,_] => true
    case _ => false
  }

  /** Allocations **/
  def isAllocation(e: Exp[_]): Boolean = getDef(e).exists(isAllocation)
  def isAllocation(d: Def): Boolean = d match {
    case _ => false
  }

  /** Stateless Nodes **/
  def isRegisterRead(e: Exp[_]): Boolean = getDef(e).exists(isRegisterRead)
  def isRegisterRead(d: Def): Boolean = d match {
    case _:RegRead[_] => true
    case _ => false
  }

  /** Primitive Nodes **/
  def isPrimitiveNode(e: Exp[_]): Boolean = e match {
    case Const(_) => false
    case _        => !isControlNode(e) && !isAllocation(e) && !isRegisterRead(e)
  }
}
