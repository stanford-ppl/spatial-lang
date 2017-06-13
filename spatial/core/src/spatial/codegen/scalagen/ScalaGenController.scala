package spatial.codegen.scalagen

import argon.codegen.scalagen.ScalaCodegen
import spatial.compiler._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

trait ScalaGenController extends ScalaCodegen with ScalaGenStream with ScalaGenMemories {
  def localMems: List[Exp[_]]

  private def emitNestedLoop(lhs: Exp[_], cchain: Exp[CounterChain], iters: Seq[Bound[Index]])(func: => Unit): Unit = {
    for (i <- iters.indices)
      open(src"$cchain($i).foreach{case (is,vs) => is.zip(vs).foreach{case (${iters(i)},v) => if (v) {")

    func

    iters.indices.foreach{_ => close("}}}") }
  }

  // In Scala simulation, run a pipe until its read fifos and streamIns are empty
  def dumpBufferedOuts(ctrl: Exp[_]): Unit = {
    val outs = localMems.filter{mem => writersOf(mem).exists{wr => topControllerOf(wr.node,mem,0).exists(_.node == ctrl) } }
    outs.filter(isBufferedOut).foreach{buff => emit(src"dump_$buff()") }
  }

  def getReadStreamsAndFIFOs(ctrl: Exp[_]): List[Exp[_]] = {
    val read = localMems.filter{mem => readersOf(mem).exists(_.ctrlNode == ctrl) }.filter{mem => isStreamIn(mem) || isFIFO(mem) } ++ childrenOf(ctrl).flatMap(getReadStreamsAndFIFOs)
    read //diff getWrittenStreamsAndFIFOs(ctrl) // Don't also wait for things we're writing to
  }

  def isStreaming(ctrl: Exp[_]): Boolean = {
    isStreamPipe(ctrl) || getDef(ctrl).exists{case Hwblock(_,strm) => strm; case _ => false}
  }

  def emitControlBlock(lhs: Sym[_], block: Block[_]): Unit = {
    if (isOuterControl(lhs) && isStreaming(lhs)) {
      val children = childrenOf(lhs)
      blockContents(block).foreach { stm =>
        val isChild = stm.lhs.exists{s => children.contains(s) }

        if (isChild) {
          val child = stm.lhs.head
          val inputs = getReadStreamsAndFIFOs(child)
          if (inputs.nonEmpty) {
            // HACK: Run streaming children to completion (exhaust inputs) before moving on to the next
            // Note that this won't work for cases with feedback, but this is disallowed for now anyway
            emit(src"def hasItems_${lhs}_$child: Boolean = " + inputs.map(quote).map(_ + ".nonEmpty").mkString(" || "))
            open(src"while (hasItems_${lhs}_$child) {")
              visitStm(stm)
            close("}")
          }
          else {
            visitStm(stm)
          }
        }
        else visitStm(stm)
      }
    }
    else {
      emitBlock(block)
    }
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case Hwblock(func,isForever) =>
      localMems.filterNot(isOffChipMemory).foreach{case lhs@Op(rhs) => emit(src"var $lhs: ${lhs.tp} = null") }

      emit(src"/** BEGIN HARDWARE BLOCK $lhs **/")
      globalMems = true
      if (!willRunForever(lhs)) {
        open(src"val $lhs = {")
          emitBlock(func)
        close("}")
      }
      else {
        if (streamIns.nonEmpty) {
          emit(src"def hasItems = " + streamIns.map(quote).map(_ + ".nonEmpty").mkString(" || "))
        }
        else {
          emit(s"""print("No Stream inputs detected for loop at ${lhs.ctx}. Enter number of iterations: ")""")
          emit(src"val ${lhs}_iters = Console.readLine.toInt")
          emit(src"var ${lhs}_ctr = 0")
          emit(src"def hasItems: Boolean = { val has = ${lhs}_ctr < ${lhs}_iters ; ${lhs}_ctr += 1; has }")
        }
        open(src"while(hasItems) {")
          emitControlBlock(lhs, func)
        close("}")
        emit(src"val $lhs = ()")
      }
      streamOuts.foreach{case x@Def(StreamOutNew(bus)) =>
        if (!bus.isInstanceOf[DRAMBus[_]]) emit(src"print_$x()") // HACK: Print out streams after block finishes running
      }
      dumpBufferedOuts(lhs)
      bufferedOuts.foreach{buff => emit(src"close_$buff()") }
      globalMems = false
      emit(src"/** END HARDWARE BLOCK $lhs **/")


    case UnitPipe(ens, func) =>
      emit(src"/** BEGIN UNIT PIPE $lhs **/")
      val en = if (ens.isEmpty) "true" else ens.map(quote).mkString(" && ")
      open(src"val $lhs = if ($en) {")
        emitControlBlock(lhs, func)
      close("}")
      dumpBufferedOuts(lhs)
      emit(src"/** END UNIT PIPE $lhs **/")

    case ParallelPipe(ens, func) =>
      emit(src"/** BEGIN PARALLEL PIPE $lhs **/")
      val en = if (ens.isEmpty) "true" else ens.map(quote).mkString(" && ")
      open(src"val $lhs = if ($en) {")
      emitBlock(func)
      close("}")
      dumpBufferedOuts(lhs)
      emit(src"/** END PARALLEL PIPE $lhs **/")

    case OpForeach(en, cchain, func, iters) =>
      emit(src"/** BEGIN FOREACH $lhs **/")
      open(src"val $lhs = {")
        emitNestedLoop(lhs, cchain, iters){ emitBlock(func) }
      close("}")
      dumpBufferedOuts(lhs)
      emit(src"/** END FOREACH $lhs **/")

    case OpReduce(en, cchain, accum, map, load, reduce, store, zero, fold, rV, iters) =>
      emit(src"/** BEGIN REDUCE $lhs **/")
      open(src"val $lhs = {")
      emitNestedLoop(lhs, cchain, iters){
        visitBlock(map)
        visitBlock(load)
        emit(src"val ${rV._1} = ${load.result}")
        emit(src"val ${rV._2} = ${map.result}")
        visitBlock(reduce)
        emitBlock(store)
      }
      close("}")
      dumpBufferedOuts(lhs)
      emit(src"/** END REDUCE $lhs **/")

    case OpMemReduce(en, cchainMap,cchainRed,accum,map,loadRes,loadAcc,reduce,storeAcc,zero,fold,rV,itersMap,itersRed) =>
      emit(src"/** BEGIN MEM REDUCE $lhs **/")
      open(src"val $lhs = {")
      emitNestedLoop(lhs, cchainMap, itersMap){
        visitBlock(map)
        emitNestedLoop(lhs, cchainRed, itersRed){
          visitBlock(loadRes)
          visitBlock(loadAcc)
          emit(src"val ${rV._1} = ${loadRes.result}")
          emit(src"val ${rV._2} = ${loadAcc.result}")
          visitBlock(reduce)
          visitBlock(storeAcc)
        }
      }
      close("}")
      dumpBufferedOuts(lhs)
      emit(src"/** END MEM REDUCE $lhs **/")

    case _ => super.emitNode(lhs, rhs)
  }
}
