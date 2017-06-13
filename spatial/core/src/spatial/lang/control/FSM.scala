package spatial.lang
package control

import forge._
import spatial.metadata._
import spatial.nodes._

object FSM {
  @api def apply[A,T:Bits](init: A)(notDone: T => Bit)(action: T => MUnit)(next: T => T)(implicit lift: Lift[A,T]) = {
    implicit val mT: Type[T] = lift.staged
    fsm(lift(init), notDone, action, next, SeqPipe)
  }
  @api def apply[T:Type:Bits](notDone: T => Bit)(action: T => MUnit)(next: T => T) = {
    fsm(implicitly[Bits[T]].zero, notDone, action, next, SeqPipe)
  }

  @internal def fsm[T:Type:Bits](start: T, notDone: T => Bit, action: T => MUnit, nextState: T => T, style: ControlStyle) = {
    val state = fresh[T]
    val dBlk = {state: Exp[T] => notDone(wrap(state)).s }
    val aBlk = {state: Exp[T] => action(wrap(state)).s }
    val nBlk = {state: Exp[T] => nextState(wrap(state)).s }
    val fsm = op_state_machine(Nil, start.s, dBlk, aBlk, nBlk, state)
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
    state:     Bound[T]
  ) = {
    // TODO: Do these need to be sealed?
    val dBlk = stageSealedLambda1(state){ notDone(state) }
    val aBlk = stageSealedLambda1(state){ action(state) }
    val nBlk = stageSealedLambda1(state){ nextState(state) }
    val effects = dBlk.effects andAlso aBlk.effects andAlso nBlk.effects
    stageEffectful(StateMachine(enable, start, dBlk, aBlk, nBlk, state), effects)(ctx)
  }
}

