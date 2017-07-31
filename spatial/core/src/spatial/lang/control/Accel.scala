package spatial.lang
package control

import argon.core._
import forge._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

object Accel {
  @api def apply(func: => MUnit): MUnit = { accel_blk(() => func, false); MUnit() }
  @api def apply(ctr: Counter)(func: => MUnit): MUnit = { accel_blk(() => func, ctr); MUnit() }

  @internal def accel_blk(func: () => MUnit, ctr: Counter): Controller = {
    if (isForever(ctr.s)) {
      accel_blk(func, isForever = true)
    }
    else {
      accel_blk(() => { Foreach.alloc(Seq(ctr), {_: List[Index] => func() }, SeqPipe, None); MUnit() }, false)
    }
  }

  @internal def accel_blk(func: () => MUnit, isForever: Boolean): Controller = {
    val pipe = op_accel(() => func().s, isForever)
    styleOf(pipe) = SeqPipe
    levelOf(pipe) = InnerControl
    Controller(pipe)
  }

  /** Constructors **/
  @internal def op_accel(func: () => Exp[MUnit], isForever: Boolean): Sym[Controller] = {
    val fBlk = stageIsolatedBlock{ func() }
    val effects = fBlk.effects andAlso Simple
    stageEffectful( Hwblock(fBlk, isForever), effects)(ctx)
  }
}

