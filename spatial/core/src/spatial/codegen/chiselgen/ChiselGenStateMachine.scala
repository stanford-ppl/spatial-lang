package spatial.codegen.chiselgen

import argon.core._
import argon.codegen.chiselgen.ChiselCodegen
import spatial.aliases._
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
      alphaconv_register(src"$state")
      emit(src"${lhs}_ctr_trivial := ${controllerStack.tail.head}_ctr_trivial | false.B")

      emitController(lhs, None, None, true)
      if (iiOf(lhs) <= 1) {
        emitGlobalWire(src"""val ${lhs}_II_done = true.B""")
      } else {
        emit(src"""val ${lhs}_IICtr = Module(new RedxnCtr());""")
        emitGlobalWire(src"""val ${lhs}_II_done = Wire(Bool())""")
        emit(src"""${lhs}_II_done := ${lhs}_IICtr.io.output.done | ${lhs}_ctr_trivial""")
        emit(src"""${lhs}_IICtr.io.input.enable := ${lhs}_en""")
        emit(src"""${lhs}_IICtr.io.input.stop := ${iiOf(lhs)}.S // ${lhs}_retime.S""")
        emit(src"""${lhs}_IICtr.io.input.reset := reset | ${lhs}_II_done.D(1)""")
        emit(src"""${lhs}_IICtr.io.input.saturate := false.B""")       
      }
      // emitGlobalWire(src"""val ${lhs}_II_done = true.B // Maybe this should handled differently""")

      emit("// Emitting notDone")
      emitBlock(notDone)
      emit("// Emitting action")
      emitGlobalWire(src"val ${notDone.result}_doneCondition = Wire(Bool())")
      emit(src"${notDone.result}_doneCondition := ~${notDone.result} // Seems unused")
      emitInhibitor(lhs, None, Some(notDone.result), None)
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
      emitGlobalWire(src"val ${lhs}_doneCondition = Wire(Bool())")
      emit(src"${lhs}_doneCondition := ~${notDone.result}")
      emit(src"${lhs}_sm.io.input.doneCondition := ${lhs}_doneCondition")
      val extraEn = if (ens.length > 0) {src"""List($ens).map(en=>en).reduce{_&&_}"""} else {"true.B"}
      emit(src"${lhs}_mask := ${extraEn}")
      
      controllerStack.pop()
      
    case _ => super.emitNode(lhs,rhs)
  }
}
