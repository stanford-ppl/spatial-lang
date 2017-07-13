package spatial.interpreter

import argon.core._
import spatial.nodes._
import argon.interpreter.{Interpreter => AInterpreter}

trait FSMs extends AInterpreter {

  override def matchNode(lhs: Sym[_])  = super.matchNode(lhs).orElse {
    case StateMachine(SeqEB(ens), EAny(start), notDoneB, action, nextState, state) =>
      if (ens.forall(x => x)) {
        updateBound(state, start)
        def notDone = {
          eval[Boolean](interpretBlock(notDoneB))
        }
        while (notDone) {
          interpretBlock(action)
          updateBound(state, eval[Any](interpretBlock(nextState)))
        }
        removeBound(state)
      }
  }


}


