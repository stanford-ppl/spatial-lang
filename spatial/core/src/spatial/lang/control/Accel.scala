package spatial.lang
package control

import forge._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

object Accel {
  @api def apply(func: => MUnit): Controller = accel_blk(() => func, false)
  @api def apply(ctr: Counter)(func: => MUnit): Controller = accel_blk(() => func, ctr)

  @internal def accel_blk(func: () => MUnit, ctr: Counter): Controller = {
    if (isForever(ctr.s)) {
      accel_blk(func, isForever = true)
    }
    else {
      accel_blk(() => { Foreach.alloc(Seq(ctr), {_: List[Index] => func() }, SeqPipe); MUnit() }, false)
    }
  }

  @internal def accel_blk(func: () => MUnit, isForever: Boolean): Controller = {
    val pipe = op_accel(() => func().s, isForever)
    styleOf(pipe) = SeqPipe
    levelOf(pipe) = InnerControl
    Controller(pipe)
  }

  /** Constructors **/
  def op_accel(func: () => Exp[MUnit], isForever: Boolean): Sym[Controller] = {
    val fBlk = stageIsolatedBlock{ func() }
    val effects = fBlk.effects andAlso Simple
    stageEffectful( Hwblock(fBlk, isForever), effects)(ctx)
  }
}

