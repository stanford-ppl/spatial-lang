package spatial.lang
package control

import argon.core._
import forge._
import spatial.metadata._
import spatial.nodes._

case class Controller(s: Exp[Controller]) extends Template[Controller]

object Controller {
  implicit object ControllerType extends Type[Controller] {
    override def wrapped(x: Exp[Controller]) = Controller(x)
    override def stagedClass = classOf[Controller]
    override def isPrimitive = true
  }
}

case class PipeWithII(ii: Long) {
  def Foreach   = new ForeachClass(InnerPipe, Some(ii))
  def Reduce    = ReduceClass(InnerPipe, Some(ii))
  def Fold      = FoldClass(InnerPipe, Some(ii))
  def MemReduce = MemReduceClass(MetaPipe, Some(ii))
  def MemFold   = MemFoldClass(MetaPipe, Some(ii))

  @api def apply(func: => MUnit): MUnit = { unit_pipe(func, SeqPipe); () }

  @internal def unit_pipe(func: => MUnit, style: ControlStyle): Controller = {
    val pipe = Pipe.op_unit_pipe(Nil, () => func.s)
    styleOf(pipe) = style
    levelOf(pipe) = InnerControl // Fixed in Level Analyzer
    userIIOf(pipe) = Some(ii)
    Controller(pipe)
  }
}

object Pipe extends ForeachClass(InnerPipe) {
  /** "Pipelined" unit controller **/
  @api def apply(func: => MUnit): MUnit = { unit_pipe(func, SeqPipe); () }

  def apply(ii: Long) = PipeWithII(ii)

  def Foreach   = new ForeachClass(InnerPipe)
  def Reduce    = ReduceClass(InnerPipe)
  def Fold      = FoldClass(InnerPipe)
  def MemReduce = MemReduceClass(MetaPipe)
  def MemFold   = MemFoldClass(MetaPipe)

  @internal def unit_pipe(func: => MUnit, style: ControlStyle): Controller = {
    val pipe = op_unit_pipe(Nil, () => func.s)
    styleOf(pipe) = style
    levelOf(pipe) = InnerControl // Fixed in Level Analyzer
    Controller(pipe)
  }
  @internal def op_unit_pipe(en: Seq[Exp[Bit]], func: () => Exp[MUnit]): Sym[Controller] = {
    val fBlk = stageSealedBlock{ func() }
    val effects = fBlk.effects
    stageEffectful( UnitPipe(en, fBlk), effects)(ctx)
  }
}

object Sequential extends ForeachClass(SeqPipe) {
  /** Sequential unit controller **/
  @api def apply(func: => MUnit): MUnit = { Pipe.unit_pipe(func, SeqPipe); () }
  def Foreach   = new ForeachClass(SeqPipe)
  def Reduce    = ReduceClass(SeqPipe)
  def Fold      = FoldClass(SeqPipe)
  def MemReduce = MemReduceClass(SeqPipe)
  def MemFold   = MemFoldClass(SeqPipe)
}

object Stream extends ForeachClass(StreamPipe) {
  /** Streaming unit controller **/
  @api def apply(func: => MUnit): MUnit = { Pipe.unit_pipe(func, StreamPipe); () }
  def Foreach   = new ForeachClass(StreamPipe)
  def Reduce    = ReduceClass(StreamPipe)
  def Fold      = FoldClass(StreamPipe)
  def MemReduce = MemReduceClass(StreamPipe)
  def MemFold   = MemFoldClass(StreamPipe)
}


