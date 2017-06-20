package spatial.lang

import argon.core._
import forge._
import spatial.nodes._

object Switches {
  @internal def create_switch[T:Type](selects: Seq[Exp[Bit]], cases: Seq[() => Exp[T]]): Exp[T] = {
    var cs: Seq[Exp[T]] = Nil
    val body = stageHotBlock{ cs = cases.map{c => c() }; cs.last }
    val effects = body.effects
    stageEffectful(Switch(body, selects, cs), effects)(ctx)
  }

  @internal def op_case[T:Type](body: () => Exp[T]): Exp[T] = {
    val block = stageSealedBlock{ body() }
    val effects = block.effects.star andAlso Simple
    stageEffectful(SwitchCase(block), effects)(ctx)
  }

  @internal def op_switch[T:Type](body: Block[T], selects: Seq[Exp[Bit]], cases: Seq[Exp[T]]): Exp[T] = {
    val effects = body.effects
    stageEffectful(Switch(body, selects, cases), effects)(ctx)
  }
}