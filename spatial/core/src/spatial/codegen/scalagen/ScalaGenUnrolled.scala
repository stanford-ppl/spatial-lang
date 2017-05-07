package spatial.codegen.scalagen

import spatial.SpatialExp

trait ScalaGenUnrolled extends ScalaGenMemories with ScalaGenSRAM with ScalaGenController {
  val IR: SpatialExp
  import IR._

  private def emitUnrolledLoop(
    lhs:    Exp[_],
    cchain: Exp[CounterChain],
    iters:  Seq[Seq[Bound[Index]]],
    valids: Seq[Seq[Bound[Bool]]]
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

  private def flattenAddress(dims: Seq[Exp[Index]], indices: Seq[Exp[Index]]): String = {
    val strides = List.tabulate(dims.length){i => (dims.drop(i+1).map(quote) :+ "1").mkString("*") }
    indices.zip(strides).map{case (i,s) => src"$i*$s"}.mkString(" + ")
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case UnrolledForeach(ens,cchain,func,iters,valids) =>
      emit(src"/** BEGIN UNROLLED FOREACH $lhs **/")
      val en = ens.map(quote).mkString(" && ")
      open(src"val $lhs = if ($en) {")
      emitUnrolledLoop(lhs, cchain, iters, valids){ emitControlBlock(lhs, func) }
      close("}")
      emit(src"/** END UNROLLED FOREACH $lhs **/")

    case UnrolledReduce(ens,cchain,_,func,_,iters,valids,_) =>
      emit(src"/** BEGIN UNROLLED REDUCE $lhs **/")
      val en = ens.map(quote).mkString(" && ")
      open(src"val $lhs = if ($en) {")
      emitUnrolledLoop(lhs, cchain, iters, valids){ emitControlBlock(lhs, func) }
      close("}")
      emit(src"/** END UNROLLED REDUCE $lhs **/")

    case op@ParSRAMLoad(sram,inds,ens) =>
      val dims = stagedDimsOf(sram)
      open(src"val $lhs = {")
      inds.indices.foreach{i =>
        open(src"val a$i = {")
          oobApply(op.mT,sram,lhs,inds(i)){ emit(src"""if (${ens(i)}) $sram.apply(${flattenAddress(dims, inds(i))}) else ${invalid(op.mT)}""") }
        close("}")
      }
      emit(src"Array[${op.mT}](" + inds.indices.map{i => src"a$i"}.mkString(", ") + ")")
      close("}")

    case op@ParSRAMStore(sram,inds,data,ens) =>
      val dims = stagedDimsOf(sram)
      open(src"val $lhs = {")
      inds.indices.foreach{i =>
        oobUpdate(op.mT, sram, lhs,inds(i)){ emit(src"if (${ens(i)}) $sram.update(${flattenAddress(dims, inds(i))}, ${data(i)})") }
      }
      close("}")

    case op@ParFIFODeq(fifo, ens) =>
      open(src"val $lhs = {")
      ens.zipWithIndex.foreach{case (en,i) =>
        emit(src"val a$i = if ($en && $fifo.nonEmpty) $fifo.dequeue() else ${invalid(op.mT)}")
      }
      emit(src"Array[${op.mT}](" + ens.indices.map{i => src"a$i"}.mkString(", ") + ")")
      close("}")

    case ParFIFOEnq(fifo, data, ens) =>
      open(src"val $lhs = {")
      ens.zipWithIndex.foreach{case (en,i) =>
        emit(src"if ($en) $fifo.enqueue(${data(i)})")
      }
      close("}")

    case op@ParFILOPop(filo, ens) =>
      open(src"val $lhs = {")
      ens.zipWithIndex.foreach{case (en,i) =>
        emit(src"val a$i = if ($en && $filo.nonEmpty) $filo.pop() else ${invalid(op.mT)}")
      }
      emit(src"Array[${op.mT}](" + ens.indices.map{i => src"a$i"}.mkString(", ") + ")")
      close("}")

    case ParFILOPush(filo, data, ens) =>
      open(src"val $lhs = {")
      ens.zipWithIndex.foreach{case (en,i) =>
        emit(src"if ($en) $filo.push(${data(i)})")
      }
      close("}")

    case op@ParStreamRead(strm, ens) =>
      open(src"val $lhs = {")
      ens.zipWithIndex.foreach{case (en,i) =>
        emit(src"val a$i = if ($en && $strm.nonEmpty) $strm.dequeue() else ${invalid(op.mT)}")
      }
      emit(src"Array[${op.mT}](" + ens.indices.map{i => src"a$i"}.mkString(", ") + ")")
      close("}")

    case ParStreamWrite(strm, data, ens) =>
      open(src"val $lhs = {")
      ens.zipWithIndex.foreach{case (en,i) =>
        emit(src"if ($en) $strm.enqueue(${data(i)})")
      }
      close("}")

    case op@ParLineBufferEnq(buffer,data,ens) =>
      open(src"val $lhs = {")
      ens.zipWithIndex.foreach{case (en,i) =>
        oobUpdate(op.mT, buffer,lhs, Nil){ emit(src"if ($en) $buffer.enq(${data(i)})") }
      }
      close("}")

    case op@ParLineBufferLoad(buffer,rows,cols,ens) =>
      open(src"val $lhs = {")
      ens.zipWithIndex.foreach{case (en,i) =>
        open(src"val a$i = {")
          oobApply(op.mT, buffer, lhs, List(rows(i),cols(i))){ emit(src"if ($en) $buffer.apply(${rows(i)},${cols(i)}) else ${invalid(op.mT)}") }
        close("}")
      }
      emit(src"Array[${op.mT}](" + ens.indices.map{i => src"a$i"}.mkString(", ") + ")")
      close("}")

    case op@ParRegFileStore(rf,inds,data,ens) =>
      val dims = stagedDimsOf(rf)
      open(src"val $lhs = {")
      ens.zipWithIndex.foreach{case (en,i) =>
        oobUpdate(op.mT,rf,lhs,inds(i)) { emit(src"if ($en) $rf.update(${flattenAddress(dims,inds(i),None)}, ${data(i)})") }
      }
      close("}")

    case op@ParRegFileLoad(rf,inds,ens) =>
      val dims = stagedDimsOf(rf)
      open(src"val $lhs = {")
      ens.zipWithIndex.foreach{case (en,i) =>
        open(src"val a$i = {")
          oobApply(op.mT,rf,lhs,inds(i)){ emit(src"if ($en) $rf.apply(${flattenAddress(dims,inds(i),None)}) else ${invalid(op.mT)}") }
        close("}")
      }
      emit(src"Array[${op.mT}](" + ens.indices.map{i => src"a$i"}.mkString(", ") + ")")
      close("}")



    case _ => super.emitNode(lhs, rhs)
  }
}
