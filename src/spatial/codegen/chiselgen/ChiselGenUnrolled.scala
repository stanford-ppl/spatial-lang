package spatial.codegen.chiselgen

import argon.codegen.chiselgen.ChiselCodegen
import spatial.api.UnrolledExp
import spatial.SpatialConfig

trait ChiselGenUnrolled extends ChiselCodegen with ChiselGenController {
  val IR: UnrolledExp
  import IR._

  private def emitUnrolledLoop(
    cchain: Exp[CounterChain],
    iters:  Seq[Seq[Bound[Index]]],
    valids: Seq[Seq[Bound[Bool]]]
  )(func: => Unit): Unit = {

    for (i <- iters.indices) {
      open(src"$cchain($i).foreach{case (is,vs) => ")
      iters(i).zipWithIndex.foreach{case (iter,j) => emit(src"val $iter = is($j)") }
      valids(i).zipWithIndex.foreach{case (valid,j) => emit(src"val $valid = vs($j)") }
    }
    func
    iters.indices.foreach{_ => close("}") }
  }

  override def quote(s: Exp[_]): String = {
    if (SpatialConfig.enableNaming) {
      s match {
        case lhs: Sym[_] =>
          val Op(rhs) = lhs
          rhs match {
            case e: UnrolledForeach=> s"x${lhs.id}_unrForeach"
            case e: UnrolledReduce[_,_] => s"x${lhs.id}_unrRed"
            case e: ParSRAMLoad[_] => s"x${lhs.id}_parLd"
            case e: ParSRAMStore[_] => s"x${lhs.id}_parSt"
            case e: ParFIFODeq[_] => s"x${lhs.id}_parDeq"
            case e: ParFIFOEnq[_] => s"x${lhs.id}_parEnq"
            case _ => super.quote(s)
          }
        case _ => super.quote(s)
      }
    } else {
      super.quote(s)
    }
  } 

  private def flattenAddress(dims: Seq[Exp[Index]], indices: Seq[Exp[Index]]): String = {
    val strides = List.tabulate(dims.length){i => (dims.drop(i+1).map(quote) :+ "1").mkString("*") }
    indices.zip(strides).map{case (i,s) => src"$i*$s"}.mkString(" + ")
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case UnrolledForeach(cchain,func,iters,valids) =>
    emitController(lhs, Some(cchain))
      withSubStream(src"${lhs}") {
        emitUnrolledLoop(cchain, iters, valids){ emitBlock(func) }
      }

    case UnrolledReduce(cchain,_,func,_,iters,valids,_) =>
      emit(src"/** BEGIN UNROLLED REDUCE **/")
      open(src"val $lhs = {")
      emitUnrolledLoop(cchain, iters, valids){ emitBlock(func) }
      close("}")
      emit(src"/** END UNROLLED REDUCE **/")

    case ParSRAMLoad(sram,inds) =>
      val dims = stagedDimsOf(sram)
      open(src"val $lhs = {")
      inds.indices.foreach{i =>
        emit(src"""val a$i = $sram.apply(${flattenAddress(dims, inds(i))})""")
      }
      emit(src"Array(" + inds.indices.map{i => src"a$i"}.mkString(", ") + ")")
      close("}")

    case ParSRAMStore(sram,inds,data,ens) =>
      val dims = stagedDimsOf(sram)
      open(src"val $lhs = {")
      inds.indices.foreach{i =>
        emit(src"if ($ens($i)) $sram.update(${flattenAddress(dims, inds(i))}, $data($i))")
      }
      close("}")

    case ParFIFODeq(fifo, ens, z) =>
      emit(src"val $lhs = $ens.map{en => if (en) $fifo.dequeue() else $z}")

    case ParFIFOEnq(fifo, data, ens) =>
      emit(src"val $lhs = $data.zip($ens).foreach{case (data, en) => if (en) $fifo.enqueue(data) }")

    case _ => super.emitNode(lhs, rhs)
  }
}
