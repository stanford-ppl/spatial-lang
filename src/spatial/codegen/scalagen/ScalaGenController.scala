package spatial.codegen.scalagen

import argon.codegen.scalagen.ScalaCodegen
import spatial.api.ControllerExp

trait ScalaGenController extends ScalaCodegen {
  val IR: ControllerExp
  import IR._

  private def emitNestedLoop(cchain: Exp[CounterChain], iters: Seq[Bound[Index]])(func: => Unit): Unit = {
    for (i <- iters.indices)
      open(src"$cchain($i).foreach{case (is,vs) => is.zip(vs).foreach{case (${iters(i)},v) => if (v) {")

    func

    iters.indices.foreach{_ => close("}}}") }
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case Hwblock(func) =>
      emit(src"/** BEGIN HARDWARE BLOCK $lhs **/")
      open(src"val $lhs = {")
      emitBlock(func)
      close("}")
      emit(src"/** END HARDWARE BLOCK $lhs **/")

    case UnitPipe(ens, func) =>
      emit(src"/** BEGIN UNIT PIPE $lhs **/")
      val en = if (ens.isEmpty) "true" else ens.map(quote).mkString(" && ")
      open(src"val $lhs = if ($en) {")
      emitBlock(func)
      close("}")
      emit(src"/** END UNIT PIPE $lhs **/")

    case ParallelPipe(ens, func) =>
      emit(src"/** BEGIN PARALLEL PIPE $lhs **/")
      val en = if (ens.isEmpty) "true" else ens.map(quote).mkString(" && ")
      open(src"val $lhs = if ($en) {")
      emitBlock(func)
      close("}")
      emit(src"/** END PARALLEL PIPE $lhs **/")

    case OpForeach(cchain, func, iters) =>
      emit(src"/** BEGIN FOREACH $lhs **/")
      open(src"val $lhs = {")
      emitNestedLoop(cchain, iters){ emitBlock(func) }
      close("}")
      emit(src"/** END FOREACH $lhs **/")

    case OpReduce(cchain, accum, map, load, reduce, store, zero, fold, rV, iters) =>
      emit(src"/** BEGIN REDUCE $lhs **/")
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
      emit(src"/** END REDUCE $lhs **/")

    case OpMemReduce(cchainMap,cchainRed,accum,map,loadRes,loadAcc,reduce,storeAcc,zero,fold,rV,itersMap,itersRed) =>
      emit(src"/** BEGIN MEM REDUCE $lhs **/")
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
      emit(src"/** END MEM REDUCE $lhs **/")

    case _ => super.emitNode(lhs, rhs)
  }
}
