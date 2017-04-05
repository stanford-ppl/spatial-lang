package spatial.api

import argon.core.Staging
import spatial.SpatialExp

trait SwitchApi extends SwitchExp {
  this: SpatialExp =>

}

trait SwitchExp extends Staging {
  this: SpatialExp =>

  def create_switch[T:Type](cases: Seq[() => Exp[T]])(implicit ctx: SrcCtx): Exp[T] = {
    var cs: Seq[Exp[T]] = Nil
    val block = stageBlock{ cs = cases.map{c => c() }; cs.last }
    val effects = block.summary
    stageEffectful(Switch(block, cs), effects)(ctx)
  }

  case class SwitchCase[T:Type](cond: Exp[Bool], body: Block[T]) extends Op[T] {
    def mirror(f:Tx) = op_case(f(cond), f(body))
  }

  case class Switch[T:Type](body: Block[T], cases: Seq[Exp[T]]) extends Op[T] {
    def mirror(f:Tx) = {
      val body2 = stageBlock{ f(body) }
      val cases2 = f(cases)
      op_switch(body2, cases2)
    }
    override def binds = super.binds ++ dyns(cases)
    override def freqs = hot(body)   // Move everything except cases out of body
  }

  def op_case[T:Type](cond: Exp[Bool], body: => Exp[T])(implicit ctx: SrcCtx): Exp[T] = {
    val block = stageBlock{ body }
    val effects = block.summary.star
    stageEffectful(SwitchCase(cond, block), effects)(ctx)
  }

  def op_switch[T:Type](body: Block[T], cases: Seq[Exp[T]])(implicit ctx: SrcCtx): Exp[T] = {
    val effects = body.summary
    stageEffectful(Switch(body, cases), effects)(ctx)
  }


}