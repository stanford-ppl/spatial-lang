package spatial.codegen.scalagen

import spatial.compiler._
import spatial.nodes._
import spatial.utils._

trait ScalaGenUnrolled extends ScalaGenMemories with ScalaGenSRAM with ScalaGenController {

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
        iters(i).zipWithIndex.foreach { case (iter, j) => emit(src"val $iter = Number(BigInt(1),true,FixedPoint(true,32,0))") }
        valids(i).zipWithIndex.foreach { case (valid, j) => emit(src"val $valid = Bit(true,true)") }
      }
      else {
        open(src"$cchain($i).foreach{case (is,vs) => ")
        iters(i).zipWithIndex.foreach { case (iter, j) => emit(src"val $iter = is($j)") }
        valids(i).zipWithIndex.foreach { case (valid, j) => emit(src"val $valid = vs($j)") }
      }
    }

    func
    iters.indices.foreach{_ => close("}") }
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case UnrolledForeach(ens,cchain,func,iters,valids) =>
      emit(src"/** BEGIN UNROLLED FOREACH $lhs **/")
      val en = ens.map(quote).mkString(" && ")
      open(src"val $lhs = if ($en) {")
      emitUnrolledLoop(lhs, cchain, iters, valids){ emitControlBlock(lhs, func) }
      close("}")
      dumpBufferedOuts(lhs)
      emit(src"/** END UNROLLED FOREACH $lhs **/")

    case UnrolledReduce(ens,cchain,_,func,_,iters,valids,_) =>
      emit(src"/** BEGIN UNROLLED REDUCE $lhs **/")
      val en = ens.map(quote).mkString(" && ")
      open(src"val $lhs = if ($en) {")
      emitUnrolledLoop(lhs, cchain, iters, valids){ emitControlBlock(lhs, func) }
      close("}")
      dumpBufferedOuts(lhs)
      emit(src"/** END UNROLLED REDUCE $lhs **/")

    case _ => super.emitNode(lhs, rhs)
  }
}
