package spatial.api

import argon.core.Staging
import spatial.SpatialExp

trait SwitchApi extends SwitchExp {
  this: SpatialExp =>

}

trait SwitchExp extends Staging {
  this: SpatialExp =>

  case class SwitchCase[T:Staged](cond: Exp[Bool], body: Block[T]) extends Op[T] {
    def mirror(f:Tx) = op_case(f(cond), f(body))
  }

  case class Switch[T:Staged](cases: Block[T]) extends Op[T] {
    def mirror(f:Tx) = op_switch(f(cases))
  }

  def op_case[T:Staged](cond: Exp[Bool], body: => Exp[T])(implicit ctx: SrcCtx): Exp[T] = {
    val block = stageBlock{ body }
    val effects = block.summary.star
    stageEffectful(SwitchCase(cond, block), effects)(ctx)
  }

  def op_switch[T:Staged](cases: => Exp[T])(implicit ctx: SrcCtx): Exp[T] = {
    val block = stageBlock{ cases }
    val effects = block.summary
    stageEffectful(Switch(block), effects)(ctx)
  }

}