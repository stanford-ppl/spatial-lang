package spatial.lang
package control

import argon.core._
import forge._
import spatial.metadata._
import spatial.nodes._

object Parallel {
  @api def apply(func: => Any): MUnit = {
    val f = () => { func; MUnit() }
    parallel_pipe(f()); ()
  }

  @internal def parallel_pipe(func: => MUnit): Controller = {
    val pipe = op_parallel_pipe(Nil, () => func.s) // Sets ForkJoin and OuterController
    Controller(pipe)
  }
  @internal def op_parallel_pipe(en: Seq[Exp[Bit]], func: () => Exp[MUnit]): Sym[Controller] = {
    val fBlk = stageSealedBlock{ func() }
    val effects = fBlk.effects
    val pipe = stageEffectful( ParallelPipe(en, fBlk), effects)(ctx)
    styleOf(pipe) = ForkJoin
    levelOf(pipe) = OuterControl
    pipe
  }
}