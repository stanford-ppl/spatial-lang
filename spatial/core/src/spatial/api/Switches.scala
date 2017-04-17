package spatial.api

import argon.core.Staging
import spatial.{SpatialApi, SpatialExp}
import forge._

trait SwitchApi extends SwitchExp {
  this: SpatialApi =>

}

trait SwitchExp {
  this: SpatialExp =>

  @util def create_switch[T:Type](cases: Seq[() => Exp[T]]): Exp[T] = {
    var cs: Seq[Exp[T]] = Nil
    val block = stageHotBlock{ cs = cases.map{c => c() }; cs.last }
    val effects = block.summary
    stageEffectful(Switch(block, cs), effects)(ctx)
  }

  case class SwitchCase[T:Type](cond: Exp[Bool], body: Block[T]) extends Op[T] {
    def mirror(f:Tx) = op_case(f(cond), f(body))
  }

  case class Switch[T:Type](body: Block[T], cases: Seq[Exp[T]]) extends Op[T] {
    def mirror(f:Tx) = {
      val body2 = stageHotBlock{ f(body) }
      val cases2 = f(cases)
      op_switch(body2, cases2)
    }
    override def binds = super.binds ++ dyns(cases)
    override def freqs = hot(body)   // Move everything except cases out of body
  }

  @util def op_case[T:Type](cond: Exp[Bool], body: => Exp[T]): Exp[T] = {
    val block = stageColdBlock{ body }
    val effects = block.summary.star
    stageEffectful(SwitchCase(cond, block), effects)(ctx)
  }

  @util def op_switch[T:Type](body: Block[T], cases: Seq[Exp[T]]): Exp[T] = {
    val effects = body.summary
    stageEffectful(Switch(body, cases), effects)(ctx)
  }


}