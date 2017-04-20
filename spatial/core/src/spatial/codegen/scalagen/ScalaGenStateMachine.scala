package spatial.codegen.scalagen

import argon.codegen.scalagen.ScalaCodegen
import spatial.SpatialExp
import spatial.api.StateMachineExp

trait ScalaGenStateMachine extends ScalaCodegen {
  val IR: SpatialExp
  import IR._

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case StateMachine(ens,start,notDone,action,nextState,state) =>
      val en = if (ens.isEmpty) "true" else ens.map(quote).mkString(" && ")

      open(src"""val $lhs = if ($en) {""")
        emit(src"var $state = $start")
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
