package spatial.codegen.dotgen

import argon.codegen.dotgen.DotCodegen
import argon.codegen.dotgen._
import argon.core._
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

import scala.collection.mutable

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
  
  override def attr(n: Exp[_]): DotAttr = n match {
    case n if isOuterControl(n) => super.attr(n).shape(box).style(dashed)
    case n if isInnerControl(n) => super.attr(n).shape(box).style(dashed)
    case n => super.attr(n)
  }

  def emitCtrlHeader(filename: String): Unit = {
    open("digraph G {")
    emit(src"""
    title[label=<
        <FONT POINT-SIZE="10">Graph for: $filename</FONT>
    >,shape="box", color="blue",style="filled",pos="0.0,0.0!"];
""")
  }

  def inFile(filename: String)(blk: => Unit): Unit = {
    val oldEdges = edges.clone()
    edges.clear()
    val file = newStream(filename.replaceAll(":","_"),"dot")
    withStream(file){
      emitCtrlHeader(filename)
      blk
      emitFileFooter()
    }
    file.close()
    edges ++= oldEdges
  }

  private def emitCtrlBlock(lhs: Sym[_], block: Block[_]): Unit = {
    def rescaleX(x: Double): Double = x*30 - 30
    def rescaleY(y: Double): Double = y*50

    if (isOuterControl(lhs) || config.dotDetail > 0) emitBlock(block) else inFile(quote(lhs)) {
      val (inputs,stms) = blockInputsAndNestedContents(block)
      inputs.zipWithIndex.foreach{ case (l,i) =>
        val at = attr(l).pos(rescaleX(i.toDouble), rescaleY(1.0))
        emitVert(l, at, forceful = false)
      }
      if (spatialConfig.enableRetiming) {
        val yToX = mutable.HashMap.empty[Long,Int]
        val renameMap = mutable.HashMap.empty[Exp[_],Exp[_]]
        def rename(x: Exp[_]): Exp[_] = renameMap.getOrElse(x, x)
        stms.foreach {
          case TP(to, DelayLine(_, data)) => renameMap += to -> data
          case TP(l,r) =>
            val dly = (symDelay(l)*10).toLong
            val y = dly.toDouble + 3
            val x = yToX.getOrElse(dly,0)
            yToX += dly -> (x + 1)
            val at = attr(l).pos(rescaleX(x.toDouble),rescaleY(y))
            emitVert(l,at, forceful = false)
            r.inputs.foreach{s => emitEdge(rename(s), l) }

          case TTP(l, r) =>
        }
      }
      else emitBlock(block)
    }
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case _:Hwblock =>
      toggleEn()
      emitSubGraph(lhs, DotAttr().label(quote(lhs)).style(rounded)){
        if (config.dotDetail == 0) emitVert(lhs)
        rhs.blocks.foreach(emitBlock)
      }
      toggleEn()

    case UnrolledForeach(en,cchain,func,iters,valids) =>
      emitValids(valids)
      emitSubGraph(lhs, DotAttr().label(quote(lhs)).style(rounded)){
        emitVert(lhs)
        iters.flatten.foreach{i => emitVert(i); emitEdge(cchain, i)}
        rhs.blocks.foreach(blk => emitCtrlBlock(lhs,blk))
      }

    case UnrolledReduce(en,cchain,func,iters,valids) =>
      emitValids(valids)
      emitSubGraph(lhs, DotAttr().label(quote(lhs)).style(rounded)){
        emitVert(lhs)
        iters.flatten.foreach{i => emitVert(i); emitEdge(cchain, i)}
        rhs.blocks.foreach(blk => emitCtrlBlock(lhs,blk))
      }

    case _ if isControlNode(lhs) =>
      emitSubGraph(lhs, DotAttr().label(quote(lhs)).style(rounded)){
        if (config.dotDetail == 0) emitVert(lhs)
        rhs.blocks.foreach(blk => emitCtrlBlock(lhs,blk))
      }

    //case UnitPipe(en,func) =>

    //case ParallelPipe(en,func) =>

    case _ => super.emitNode(lhs, rhs)
  }
}
