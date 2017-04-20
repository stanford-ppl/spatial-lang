package spatial.codegen.cppgen

import argon.codegen.cppgen.CppCodegen
import spatial.{SpatialConfig, SpatialExp}

trait CppGenUnrolled extends CppCodegen {
  val IR: SpatialExp
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
          lhs match {
            case Def(e: UnrolledForeach) => s"x${lhs.id}_unrForeach"
            case Def(e: UnrolledReduce[_,_]) => s"x${lhs.id}_unrRed"
            case Def(e: ParSRAMLoad[_]) => s"x${lhs.id}_parLd"
            case Def(e: ParSRAMStore[_]) => s"x${lhs.id}_parSt"
            case Def(e: ParFIFODeq[_]) => s"x${lhs.id}_parDeq"
            case Def(e: ParFIFOEnq[_]) => s"x${lhs.id}_parEnq"
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
    case UnrolledForeach(en, cchain,func,iters,valids) =>
      emitUnrolledLoop(cchain, iters, valids){ emitBlock(func) }

    case UnrolledReduce(en, cchain,_,func,_,iters,valids,_) =>

    case ParSRAMLoad(sram,inds,ens) =>

    case ParSRAMStore(sram,inds,data,ens) =>

    case ParFIFODeq(fifo, ens) =>

    case ParFIFOEnq(fifo, data, ens) =>

    case _ => super.emitNode(lhs, rhs)
  }
}
