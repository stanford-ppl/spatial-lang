package spatial.codegen.chiselgen

import argon.core._
import argon.codegen.chiselgen.ChiselCodegen
import spatial.compiler._
import spatial.metadata._
import spatial.nodes._
import spatial.SpatialConfig

trait ChiselGenStateMachine extends ChiselCodegen with ChiselGenController {

  override def quote(s: Exp[_]): String = {
    if (SpatialConfig.enableNaming) {
      s match {
        case lhs: Sym[_] =>
          lhs match {
            case Def(e: StateMachine[_]) =>
              s"x${lhs.id}_FSM"
            case _ =>
              super.quote(s)
          }
        case _ =>
          super.quote(s)
      }
    } else {
      super.quote(s)
    }
  } 
  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case StateMachine(ens,start,notDone,action,nextState,state) =>
      val parent_kernel = controllerStack.head 
      controllerStack.push(lhs)
      emit(src"${lhs}_ctr_trivial := ${controllerStack.tail.head}_ctr_trivial | false.B")

      emitController(lhs, None, None, true)

      emit("// Emitting notDone")
      emitBlock(notDone)
      emit("// Emitting action")
      emitInhibitor(lhs, None, Some(notDone.result))
      withSubStream(src"${lhs}", src"${parent_kernel}", styleOf(lhs) == InnerPipe) {
        emit(s"// Controller Stack: ${controllerStack.tail}")
        visitBlock(action)
      }
      emit("// Emitting nextState")
      visitBlock(nextState)
      emit(src"${lhs}_sm.io.input.enable := ${lhs}_en ")
      emit(src"${lhs}_sm.io.input.nextState := ${nextState.result}.r.asSInt // Assume always int")
      emit(src"${lhs}_sm.io.input.initState := ${start}.r.asSInt")
      emitGlobalWire(src"val $state = Wire(SInt(32.W))")
      emit(src"$state := ${lhs}_sm.io.output.state")
      emit(src"${lhs}_sm.io.input.doneCondition := ~${notDone.result}")
      val extraEn = if (ens.length > 0) {src"""List(${ens.map(quote).mkString(",")}).map(en=>en).reduce{_&&_}"""} else {"true.B"}
      emit(src"${lhs}_mask := ${extraEn}")
      
    case _ => super.emitNode(lhs,rhs)
  }
}
