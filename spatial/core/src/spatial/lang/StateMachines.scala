package spatial.lang

import spatial._
import forge._

trait StateMachineApi extends StateMachineExp { this: SpatialApi =>

  object FSM {
    @api def apply[A,T:Bits](init: A)(notDone: T => Boolean)(action: T => Void)(next: T => T)(implicit lift: Lift[A,T]) = {
      implicit val mT: Meta[T] = lift.staged
      fsm(lift(init), notDone, action, next, SeqPipe)
    }
    @api def apply[T:Type:Bits](notDone: T => Boolean)(action: T => Void)(next: T => T) = {
      fsm(zero[T], notDone, action, next, SeqPipe)
    }
  }
}

trait StateMachineExp { this: SpatialExp =>

  @internal def fsm[T:Type:Bits](start: T, notDone: T => Bool, action: T => Void, nextState: T => T, style: ControlStyle) = {
    val state = fresh[T]
    val wstate = wrap(state)
    val dBlk = () => unwrap(notDone(wstate))
    val aBlk = () => unwrap(action(wstate))
    val nBlk = () => unwrap(nextState(wstate))
    val fsm = op_state_machine(Nil, start.s, dBlk(), aBlk(), nBlk(), state)
    styleOf(fsm) = style
    levelOf(fsm) = OuterControl
    wrap(fsm)
  }

  case class StateMachine[T:Type:Bits](
    en:        Seq[Exp[Bool]],
    start:     Exp[T],
    notDone:   Block[Bool],
    action:    Block[Void],
    nextState: Block[T],
    state:     Bound[T]
  ) extends EnabledController {
    def mirrorWithEn(f:Tx, addEn: Seq[Exp[Bool]]) = op_state_machine(f(en) ++ addEn,f(start),f(notDone),f(action),f(nextState),state)

    override def binds = state +: super.binds
    val mT = typ[T]
    val bT = bits[T]
  }

  @internal def op_state_machine[T:Type:Bits](
    enable:    Seq[Exp[Bool]],
    start:     Exp[T],
    notDone:   => Exp[Bool],
    action:    => Exp[Void],
    nextState: => Exp[T],
    state:     Bound[T]
  ) = {
    // TODO: Do these need to be cold?
    val dBlk = stageColdBlock{ notDone }
    val aBlk = stageColdBlock{ action }
    val nBlk = stageColdBlock{ nextState }
    val effects = dBlk.summary andAlso aBlk.summary andAlso nBlk.summary
    stageEffectful(StateMachine(enable, start, dBlk, aBlk, nBlk, state), effects)(ctx)
  }

}