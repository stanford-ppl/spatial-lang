package spatial.codegen.chiselgen

import argon.core._
import argon.codegen.chiselgen.ChiselCodegen
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._

trait ChiselGenStateMachine extends ChiselCodegen with ChiselGenController {

  override protected def name(s: Dyn[_]): String = s match {
    case Def(_: StateMachine[_]) => s"${s}_FSM"
    case _ => super.name(s)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case StateMachine(ens,start,notDone,action,nextState,state) =>
      val parent_kernel = controllerStack.head 
      controllerStack.push(lhs)
      alphaconv_register(src"$state")

      emitController(lhs, None, None, true)
      emit("// Emitting notDone")
      emitBlock(notDone)
      // notDone.result match {
      //   case b: Bound[_] => 
      //   case s: Sym[_] => emitGlobalWireMap(src"${notDone.result}", "Wire(Bool())") // Hack but so what
      //   case c: Const[_] =>
      // }
      emitInhibitor(lhs, None, Some(notDone.result), None)

      emit(src"${swap(lhs, CtrTrivial)} := ${swap(controllerStack.tail.head, CtrTrivial)}.D(1,rr) | false.B")
      if (iiOf(lhs) <= 1 | levelOf(lhs) == OuterControl) {
        emitGlobalWire(src"""val ${swap(lhs, IIDone)} = true.B""")
      } else {
        emit(src"""val ${lhs}_IICtr = Module(new RedxnCtr());""")
        emitGlobalWire(src"""val ${swap(lhs, IIDone)} = Wire(Bool())""")
        emit(src"""${swap(lhs, IIDone)} := ${lhs}_IICtr.io.output.done | ${swap(lhs, CtrTrivial)}""")
        emit(src"""${lhs}_IICtr.io.input.enable := ${swap(lhs, En)}""")
        val stop = if (levelOf(lhs) == InnerControl) { iiOf(lhs) + 1} else {iiOf(lhs)} // I think innerpipes need one extra delay because of logic inside sm
        emit(src"""${lhs}_IICtr.io.input.stop := ${stop}.S // ${swap(lhs, Retime)}.S""")
        emit(src"""${lhs}_IICtr.io.input.reset := reset.toBool | ${swap(lhs, IIDone)}.D(1)""")  
        emit(src"""${lhs}_IICtr.io.input.saturate := false.B""")       
      }
      // emitGlobalWire(src"""val ${swap(lhs, IIDone)} = true.B // Maybe this should handled differently""")

      emit("// Emitting action")
      // emitGlobalWire(src"val ${notDone.result}_doneCondition = Wire(Bool())")
      // emit(src"${notDone.result}_doneCondition := ~${notDone.result} // Seems unused")
      withSubStream(src"${lhs}", src"${parent_kernel}", styleOf(lhs) == InnerPipe) {
        emit(s"// Controller Stack: ${controllerStack.tail}")
        visitBlock(action)
      }
      emit("// Emitting nextState")
      visitBlock(nextState)
      emit(src"${lhs}_sm.io.input.enable := ${swap(lhs, En)} ")
      emit(src"${lhs}_sm.io.input.nextState := Mux(${swap(lhs, IIDone)}.D(1 max ${swap(lhs, Retime)} - 1), ${nextState.result}.r.asSInt, ${lhs}_sm.io.output.state.r.asSInt) // Assume always int")
      emit(src"${lhs}_sm.io.input.initState := ${start}.r.asSInt")
      emitGlobalWire(src"val $state = Wire(${newWire(state.tp)})")
      emit(src"${state}.r := ${lhs}_sm.io.output.state.r")
      emitGlobalWire(src"val ${lhs}_doneCondition = Wire(Bool())")
      emit(src"${lhs}_doneCondition := ~${notDone.result}")
      emit(src"${lhs}_sm.io.input.doneCondition := ${lhs}_doneCondition")
      val extraEn = if (ens.length > 0) {src"""List($ens).map(en=>en).reduce{_&&_}"""} else {"true.B"}
      emit(src"${swap(lhs, Mask)} := ${extraEn}")
      emitChildrenCxns(lhs, None, None, true)
      controllerStack.pop()
      
    case _ => super.emitNode(lhs,rhs)
  }
}
