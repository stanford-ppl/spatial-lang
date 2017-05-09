package spatial.codegen.chiselgen

import argon.codegen.chiselgen.ChiselCodegen
import spatial.api.StateMachineExp
import spatial.SpatialConfig
import spatial.analysis.SpatialMetadataExp
import spatial.SpatialExp

trait ChiselGenStateMachine extends ChiselCodegen with ChiselGenController {
  val IR: SpatialExp
  import IR._

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
      emitInhibitor(lhs, None, Some(notDone.result))

      emit("// Emitting notDone")
      emitBlock(notDone)
      emit("// Emitting action")
      withSubStream(src"${lhs}", src"${parent_kernel}", styleOf(lhs) == InnerPipe) {
        emit(s"// Controller Stack: ${controllerStack.tail}")
        visitBlock(action)
      }
      emit("// Emitting nextState")
      visitBlock(nextState)
      emit(src"${lhs}_sm.io.input.enable := ${lhs}_en ")
      emit(src"${lhs}_sm.io.input.nextState := ${nextState.result}.number // Assume always int")
      emit(src"${lhs}_sm.io.input.initState := ${start}.number")
      emitGlobalWire(src"val $state = Wire(UInt(32.W))")
      emit(src"$state := ${lhs}_sm.io.output.state")
      emit(src"${lhs}_sm.io.input.doneCondition := ~${notDone.result}")
      val extraEn = if (ens.length > 0) {src"""List(${ens.map(quote).mkString(",")}).map(en=>en).reduce{_&&_}"""} else {"true.B"}
      emit(src"${lhs}_mask := ${extraEn}")
      
    case _ => super.emitNode(lhs,rhs)
  }
}
