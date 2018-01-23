package spatial.codegen.scalagen

import argon.core._
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

trait ScalaGenController extends ScalaGenControl with ScalaGenStream with ScalaGenMemories {
  // In Scala simulation, run a pipe until its read fifos and streamIns are empty
  def getReadStreamsAndFIFOs(ctrl: Exp[_]): List[Exp[_]] = {
    val read = localMems.filter{mem => readersOf(mem).exists(_.ctrlNode == ctrl) }
                        .filter{mem => isStreamIn(mem) || isFIFO(mem) } ++ childrenOf(ctrl).flatMap(getReadStreamsAndFIFOs)

    read.filter{case Def(StreamInNew(bus)) => !bus.isInstanceOf[DRAMBus[_]]; case _ => true}
    //read //diff getWrittenStreamsAndFIFOs(ctrl) // Don't also wait for things we're writing to
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
          val contents = blockNestedContents(block).flatMap(_.lhs)
          val inputs = getReadStreamsAndFIFOs(child) diff contents  // Don't check things we declare inside
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

  private def emitUnrolledLoop(
    lhs:    Exp[_],
    cchain: Exp[CounterChain],
    iters:  Seq[Seq[Bound[Index]]],
    valids: Seq[Seq[Bound[Bit]]]
  )(func: => Unit): Unit = {

    val ctrs = countersOf(cchain)

    for (i <- iters.indices) {
      if (isForever(ctrs(i))) {
        val inputs = getReadStreamsAndFIFOs(lhs)
        if (inputs.nonEmpty) {
          emit(src"def hasItems_$lhs: Boolean = " + inputs.map(quote).map(_ + ".nonEmpty").mkString(" || "))
        }
        else {
          emit(s"""print("No Stream inputs detected for loop at ${lhs.ctx}. Enter number of iterations: ")""")
          emit(src"val ${lhs}_iters_$i = Console.readLine.toInt")
          emit(src"var ${lhs}_ctr_$i = 0")
          emit(src"def hasItems_$lhs: Boolean = { val has = ${lhs}_ctr_$i < ${lhs}_iters_$i ; ${lhs}_ctr_$i += 1; has }")
        }

        open(src"while(hasItems_$lhs) {")
        iters(i).zipWithIndex.foreach { case (iter, j) => emit(src"val $iter = FixedPoint(1)") }
        valids(i).zipWithIndex.foreach { case (valid, j) => emit(src"val $valid = Bool(true,true)") }
      }
      else {
        open(src"$cchain($i).foreach{case (is,vs) => ")
        iters(i).zipWithIndex.foreach { case (iter, j) => emit(src"val $iter = is($j)") }
        valids(i).zipWithIndex.foreach { case (valid, j) => emit(src"val $valid = vs($j)") }
      }
    }

    func
    iters.reverse.foreach{is =>
      emitControlIncrement(lhs, is)
      close("}")
    }
  }

  private def emitControlObject(lhs: Sym[_], ens: Seq[Exp[Bit]], func: Block[_])(contents: => Unit): Unit = {
    val inputs = blockInputs(func)
    val en = if (ens.isEmpty) "true" else ens.map(quote).mkString(" && ")

    withStream(getStream(src"$lhs")) {
      emitFileHeader()
      open(src"object $lhs {")
        open(src"def run(")
          inputs.zipWithIndex.foreach{case (in,i) => emit(src"$in: ${in.tp}" + (if (i == inputs.length-1) "" else ",")) }
        closeopen("): Unit = {")
          open(src"if ($en) {")
            contents
          close("}")
        close("}")
      close("}")
      emitFileFooter()
    }
    emit(src"$lhs.run($inputs)")
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case Hwblock(func,isForever) =>
      localMems.filterNot(isOffChipMemory).foreach{case lhs@Op(rhs) => emit(src"var $lhs: ${lhs.tp} = ${lhs.tp}()") }
      localMems.filter(isInternalStreamMemory).foreach{case lhs@Op(rhs) => emit(src"var $lhs: ${lhs.tp} = ${lhs.tp}()") }

      emit(src"/** BEGIN HARDWARE BLOCK $lhs **/")
      globalMems = true
      if (!willRunForever(lhs)) {
        open(src"def accel(): Unit = {")
        open(src"val $lhs = {")
          emitBlock(func)
        close("}")
        close("}")
        emit("accel()")
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
      emitControlDone(lhs)
      bufferedOuts.foreach{buff => emit(src"close_$buff()") }
      globalMems = false
      emit(src"/** END HARDWARE BLOCK $lhs **/")


    case UnitPipe(ens, func) =>
      emitControlObject(lhs, ens, func) {
        emit(src"/** BEGIN UNIT PIPE $lhs **/")
        emitControlBlock(lhs, func)
        emitControlDone(lhs)
        emit(src"/** END UNIT PIPE $lhs **/")
      }


    case ParallelPipe(ens, func) =>
      emitControlObject(lhs, ens, func){
        emit(src"/** BEGIN PARALLEL PIPE $lhs **/")
        emitBlock(func)
        emitControlDone(lhs)
        emit(src"/** END PARALLEL PIPE $lhs **/")
      }

    case UnrolledForeach(ens,cchain,func,iters,valids) =>
      emitControlObject(lhs, ens, func) {
        emit(src"/** BEGIN UNROLLED FOREACH $lhs **/")
        emitUnrolledLoop(lhs, cchain, iters, valids) {
          emitControlBlock(lhs, func)
        }
        emitControlDone(lhs)
        emit(src"/** END UNROLLED FOREACH $lhs **/")
      }

    case UnrolledReduce(ens,cchain,func,iters,valids) =>
      emitControlObject(lhs, ens, func) {
        emit(src"/** BEGIN UNROLLED REDUCE $lhs **/")
        emitUnrolledLoop(lhs, cchain, iters, valids) {
          emitControlBlock(lhs, func)
        }
        emitControlDone(lhs)
        emit(src"/** END UNROLLED REDUCE $lhs **/")
      }


    // These should no longer exist in the IR at codegen time
    /*
    case _:OpForeach =>
    case _:OpReduce[_] =>
    case _:OpMemReduce[_,_] =>
    */

    case _ => super.emitNode(lhs, rhs)
  }
}
