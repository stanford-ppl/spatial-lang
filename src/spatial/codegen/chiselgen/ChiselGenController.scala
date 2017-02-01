package spatial.codegen.chiselgen

import argon.codegen.chiselgen.ChiselCodegen
import spatial.api.ControllerExp

trait ChiselGenController extends ChiselCodegen {
  val IR: ControllerExp
  import IR._

  private def emitNestedLoop(cchain: Exp[CounterChain], iters: Seq[Bound[Index]])(func: => Unit): Unit = {
    for (i <- iters.indices)
      open(src"$cchain($i).foreach{case (is,vs) => is.zip(vs).foreach{case (${iters(i)},v) => if (v) {")

    func

    iters.indices.foreach{_ => close("}}}") }
  }

  override def quote(s: Exp[_]): String = {
    // val Def(rhs) = s 
    s match {
      case lhs: Sym[_] =>
        val Op(rhs) = lhs
        rhs match {
          case Hwblock(_)=> 
            s"AccelController"
          case UnitPipe(_) =>
            s"x${lhs.id}_UnitPipe"
          case OpForeach(_,_,_) =>
            s"x${lhs.id}_ForEach"
          case OpReduce(_,_,_,_,_,_,_,_) =>
            s"x${lhs.id}_Reduce"
          case OpMemReduce(_,_,_,_,_,_,_,_,_,_,_) =>
            s"x${lhs.id}_MemReduce"
          case _ =>
            super.quote(s)
        }
      case _ =>
        super.quote(s)
    }
  } 

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case Hwblock(func) =>
      emit("/** BEGIN HARDWARE BLOCK **/")
      open(src"val $lhs = {")
      emitBlock(func)
      close("}")
      emit("/** END HARDWARE BLOCK **/")

    case UnitPipe(func) =>
      emit("/** BEGIN UNIT PIPE **/")
      open(src"val $lhs = {")
      emitBlock(func)
      close("}")
      emit("/** END UNIT PIPE **/")

    case OpForeach(cchain, func, iters) =>
      emit("/** BEGIN FOREACH **/")
      open(src"val $lhs = {")
      emitNestedLoop(cchain, iters){ emitBlock(func) }
      close("}")
      emit("/** END FOREACH **/")

    case OpReduce(cchain, accum, map, load, reduce, store, rV, iters) =>
      emit("/** BEGIN REDUCE **/")
      open(src"val $lhs = {")
      emitNestedLoop(cchain, iters){
        visitBlock(map)
        visitBlock(load)
        emit(src"val ${rV._1} = ${load.result}")
        emit(src"val ${rV._2} = ${map.result}")
        visitBlock(reduce)
        emitBlock(store)
      }
      close("}")
      emit("/** END REDUCE **/")

    case OpMemReduce(cchainMap,cchainRed,accum,map,loadRes,loadAcc,reduce,storeAcc,rV,itersMap,itersRed) =>
      emit("/** BEGIN MEM REDUCE **/")
      open(src"val $lhs = {")
      emitNestedLoop(cchainMap, itersMap){
        visitBlock(map)
        emitNestedLoop(cchainRed, itersRed){
          visitBlock(loadRes)
          visitBlock(loadAcc)
          emit(src"val ${rV._1} = ${loadRes.result}")
          emit(src"val ${rV._2} = ${loadAcc.result}")
          visitBlock(reduce)
          visitBlock(storeAcc)
        }
      }
      close("}")
      emit("/** END MEM REDUCE **/")

    case _ => super.emitNode(lhs, rhs)
  }
}
