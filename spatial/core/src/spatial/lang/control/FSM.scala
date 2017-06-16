package spatial.lang
package control

import argon.core._
import forge._
import spatial.metadata._
import spatial.nodes._

object FSM {
  @api def apply[A,T:Bits](init: A)(notDone: T => Bit)(action: T => MUnit)(next: T => T)(implicit lift: Lift[A,T]): MUnit = {
    implicit val mT: Type[T] = lift.staged
    fsm(lift(init), notDone, action, next, SeqPipe); ()
  }
  @api def apply[T:Type:Bits](notDone: T => Bit)(action: T => MUnit)(next: T => T): MUnit = {
    fsm(implicitly[Bits[T]].zero, notDone, action, next, SeqPipe); ()
  }

  @internal def fsm[T:Type:Bits](start: T, notDone: T => Bit, action: T => MUnit, nextState: T => T, style: ControlStyle) = {
    val cur = fresh[T]
    val dBlk = {cur: Exp[T] => notDone(wrap(cur)).s }
    val aBlk = {cur: Exp[T] => action(wrap(cur)).s }
    val nBlk = {cur: Exp[T] => nextState(wrap(cur)).s }
    val fsm = op_state_machine(Nil, start.s, dBlk, aBlk, nBlk, cur)
    styleOf(fsm) = style
    levelOf(fsm) = OuterControl
    wrap(fsm)
  }

  /** Constructors **/
  @internal def op_state_machine[T:Type:Bits](
    enable:    Seq[Exp[Bit]],
    start:     Exp[T],
    notDone:   Exp[T] => Exp[Bit],
    action:    Exp[T] => Exp[MUnit],
    nextState: Exp[T] => Exp[T],
    cur:       Bound[T]
  ) = {
    // TODO: Do these need to be sealed?
    val dBlk = stageSealedLambda1(cur){ notDone(cur) }
    val aBlk = stageSealedLambda1(cur){ action(cur) }
    val nBlk = stageSealedLambda1(cur){ nextState(cur) }
    val effects = dBlk.effects andAlso aBlk.effects andAlso nBlk.effects
    stageEffectful(StateMachine(enable, start, dBlk, aBlk, nBlk, cur), effects)(ctx)
  }
}

