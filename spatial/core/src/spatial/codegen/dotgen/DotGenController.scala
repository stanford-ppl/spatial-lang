package spatial.codegen.dotgen

import argon.codegen.dotgen.DotCodegen
import argon.codegen.dotgen._
import argon.core._
import spatial.aliases._
import spatial.nodes._
import spatial.utils._

trait DotGenController extends DotCodegen {

    //val smStr = styleOf(sym) match {
      //case MetaPipe => s"Metapipe"
      //case StreamPipe => "Streampipe"
      //case InnerPipe => "Innerpipe"
      //case SeqPipe => s"Seqpipe"
      //case ForkJoin => s"Parallel"
    //}

  def emitValids(valids: Seq[Seq[Bound[Bit]]]): Unit = valids.foreach{v =>
    v.foreach{vv => emitVert(vv) }
  }
  
  override def attr(n: Exp[_]):DotAttr = n match {
    case n if isOuterControl(n) => super.attr(n).shape(box).style(dashed)
    case n if isInnerControl(n) => super.attr(n).shape(box).style(dashed)
    case n => super.attr(n)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case _:Hwblock =>
      toggleEn()
      emitSubGraph(lhs, DotAttr().label(quote(lhs)).style(rounded)){
        if (config.dotDetail == 0) emitVert(lhs)
        rhs.blocks.foreach(emitBlock)
      }
      toggleEn()

    case _ if isControlNode(lhs) && config.dotDetail == 0 =>
      emitSubGraph(lhs, DotAttr().label(quote(lhs)).style(rounded)){
        emitVert(lhs)
        rhs.blocks.foreach(emitBlock)
      }

    case _ if isControlNode(lhs) =>
      emitSubGraph(lhs, DotAttr().label(quote(lhs)).style(rounded)){ 
        if (config.dotDetail == 0) emitVert(lhs)
        rhs.blocks.foreach(emitBlock) 
      }

    case UnrolledForeach(en,cchain,func,iters,valids) =>
      emitValids(valids)
      emitSubGraph(lhs, DotAttr().label(quote(lhs)).style(rounded)){
        emitVert(lhs)
        iters.flatten.foreach{i => emitVert(i); emitEdge(cchain, i)}
        rhs.blocks.foreach(emitBlock)
      }

    case UnrolledReduce(en,cchain,accum,func,iters,valids) =>
      emitValids(valids)
      emitSubGraph(lhs, DotAttr().label(quote(lhs)).style(rounded)){
        emitVert(lhs)
        iters.flatten.foreach{i => emitVert(i); emitEdge(cchain, i)}
        rhs.blocks.foreach(emitBlock)
      }

    //case UnitPipe(en,func) =>

    //case ParallelPipe(en,func) =>

    case _ => super.emitNode(lhs, rhs)
  }
}
