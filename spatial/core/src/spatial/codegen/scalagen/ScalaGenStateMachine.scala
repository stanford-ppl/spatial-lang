package spatial.codegen.scalagen

import argon.core._
import argon.codegen.scalagen.ScalaCodegen
import spatial.aliases._
import spatial.nodes._

trait ScalaGenStateMachine extends ScalaCodegen {

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case StateMachine(ens,start,notDone,action,nextState,state) =>
      val en = if (ens.isEmpty) "true" else ens.map(quote).mkString(" && ")

      open(src"""val $lhs = if ($en) {""")
        emit(src"var ${state}: ${state.tp} = $start")
        open(src"def notDone() = {")
          emitBlock(notDone)
        close("}")

        open(src"while( notDone() ){")
          visitBlock(action)
          visitBlock(nextState)
          emit(src"$state = ${nextState.result}")
        close("}")

      close("}")

    case _ => super.emitNode(lhs,rhs)
  }
}
