package spatial.analysis

import argon.core._
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

import java.io.PrintWriter


trait TreeGenSpatial extends SpatialTraversal {
  override val name = "Tree Gen"
  var controllerStack = scala.collection.mutable.Stack[Exp[_]]()
  // def getStages(blks: Block[_]*): Seq[Sym[_]] = blks.flatMap(blockContents).flatMap(_.lhs)

  // def getPrimitiveNodes(blks: Block[_]*): Seq[Sym[_]] = getStages(blks:_*).filter(isPrimitiveNode)
  // def getControlNodes(blks: Block[_]*): Seq[Sym[_]] = getStages(blks:_*).filter(isControlNode)
  // def getAllocations(blks: Block[_]*): Seq[Sym[_]] = getStages(blks:_*).filter(isAllocation)

  // def hasPrimitiveNodes(blks: Block[_]*): Boolean = blks.exists{blk => getControlNodes(blk).nonEmpty }
  // def hasControlNodes(blks: Block[_]*): Boolean = blks.exists{blk => getControlNodes(blk).nonEmpty }

  def indent():String = {"  " * controllerStack.length}

  var controller_tree: PrintWriter = _
  val table_init = """<TABLE BORDER="3" CELLPADDING="10" CELLSPACING="10">"""

  def getScheduling(sym: Sym[_]): String = {
    styleOf(sym) match {
        case MetaPipe => s"Meta."
        case StreamPipe => "Stream."
        case InnerPipe => "Inner."
        case SeqPipe => s"Seq."
        case ForkJoin => s"Para."
        case _ => ""
      }
  }
  override protected def preprocess[S:Type](block: Block[S]): Block[S] = {
    new java.io.File(config.genDir).mkdirs()
    controller_tree = { new PrintWriter(config.genDir + "/controller_tree.html") }
  	controller_tree.write("""<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<link rel="stylesheet" href="http://code.jquery.com/mobile/1.4.5/jquery.mobile-1.4.5.min.css">
<script src="http://code.jquery.com/jquery-1.11.3.min.js"></script>
<script src="http://code.jquery.com/mobile/1.4.5/jquery.mobile-1.4.5.min.js"></script>
</head><body>

  <div data-role="main" class="ui-content" style="overflow-x:scroll;">
    <h2>Controller Diagram for """)
    controller_tree.write(config.name)
    if (spatialConfig.enableSyncMem) {controller_tree.write(" syncMem")}
    else if (spatialConfig.enableRetiming) {controller_tree.write(" retimed")}
    controller_tree.write("""</h2>
<TABLE BORDER="3" CELLPADDING="10" CELLSPACING="10">""")
  	super.preprocess(block)
  }

  override protected def postprocess[S:Type](block: Block[S]): Block[S] = {
    controller_tree.write(s"""  </TABLE>
</body>
</html>""")
    controller_tree.close
    super.postprocess(block)
  }

  def print_stage_prefix(title: String, ctr: String, node: String, ctx: String, inner: Boolean = false, collapsible: Boolean = true) {
    controller_tree.write(s"""
${indent()}<!--Begin $node -->
${indent()}<TD><font size = "6">$title<br><font size = "2">$ctx</font><br><b>$node</b></font><br><font size = "1">Counter: $ctr</font>""")
    if (!inner & !collapsible) {controller_tree.write("""<br><font size = "1"><b>**Stages below are route-through (think of cycle counts as duty-cycles)**</b></font>""")}
    controller_tree.write("""
""")
    if (!inner) {
      val coll = if (collapsible) "data-role=\"collapsible\""
      controller_tree.write(s"""${indent()}<div $coll>
${indent()}<h4> </h4>${table_init}
""")
    }
  }
  def print_stream_info(sym: Exp[_]) {
    if (listensTo(sym).distinct.toList.length + pushesTo(sym).distinct.toList.length > 0){
      controller_tree.write(s"""${indent()}<div style="border:1px solid black">Stream Info<br>""")
      val listens = listensTo(sym).distinct.map{a => s"${a.memory}"}.mkString(",")
      val pushes = pushesTo(sym).distinct.map{a => s"${a.memory}"}.mkString(",")
      if (listens != "") controller_tree.write(s"""<p align="left">----->$listens""")
      if (listens != "" & pushes != "") controller_tree.write(s"<br>")
      if (pushes != "") controller_tree.write(s"""<p align="right">$pushes----->""")
      controller_tree.write("""</div>
""")
    }
  }

  def print_stage_suffix(name: String, inner: Boolean = false) {
    if (!inner) {
      controller_tree.write(s"""${indent()}</TABLE></div>
""")
    }
    controller_tree.write(s"""${indent()}</TD>
${indent()}<!-- Close $name -->

""")
  }

  override protected def visit(sym: Sym[_], rhs: Op[_]): Unit = rhs match {
    case Hwblock(func,_) =>
      controllerStack.push(sym)
      val inner = levelOf(sym) match { 
      	case InnerControl => childrenOf(sym).length == 0 // To catch when we have switch as a child
      	case _ => false
      }
      print_stage_prefix(s"Hwblock",s"",s"$sym", s"${sym.ctx}", inner)
      print_stream_info(sym)
      val children = getControlNodes(func)
      children.foreach { s =>
        val Op(d) = s
        visit(s,d)
      }
      print_stage_suffix(s"$sym", inner)
      controllerStack.pop()
    /*case BurstLoad(dram, fifo, ofs, ctr, i) =>
      print_stage_prefix(s"BurstLoad",s"",s"$sym", true)
      print_stage_suffix(s"$sym", true)

    case BurstStore(dram, fifo, ofs, ctr, i) =>
      print_stage_prefix(s"BurstStore",s"",s"$sym", true)
      print_stage_suffix(s"$sym", true)*/

    case UnitPipe(_,func) =>
      controllerStack.push(sym)
      var collapsible = true
      val inner = levelOf(sym) match { 
      	case InnerControl if childrenOf(sym).length == 0 => true
        case InnerControl if childrenOf(sym).length > 0 => collapsible = false; false // To catch when we have switch as a child
      	case _ => false
      }
      val tileXfer = childrenOf(sym).map{c => c match {case Def(FringeSparseLoad(_,_,_)) => "Gather."; case Def(FringeSparseStore(_,_,_)) => "Scatter."; 
                                                       case Def(FringeDenseLoad(_,_,_)) => "Load."; case Def(FringeDenseStore(_,_,_,_)) => "Store."; 
                                                       case _ => ""}}.mkString("")
      val ctxline = if (transferChannel(sym) >= 0) {s"${sym.ctx}, channel ${transferChannel(sym)}"} else {s"${sym.ctx}"}
      print_stage_prefix(s"${getScheduling(sym)}${tileXfer}Unitpipe",s"",s"$sym", s"${ctxline}", inner, collapsible)
      print_stream_info(sym)
      val children = getControlNodes(func)
      children.foreach { s =>
        val Op(d) = s
        visit(s,d)
      }
      print_stage_suffix(s"$sym", inner)
      controllerStack.pop()

    case OpForeach(en, cchain, func, iters) =>
      val inner = levelOf(sym) match { 
      	case InnerControl => true
      	case _ => false
      }
      print_stage_prefix(s"${getScheduling(sym)}OpForeach",s"${cchain}",s"$sym", s"${sym.ctx}", inner)
      print_stream_info(sym)
      val children = getControlNodes(func)
      children.foreach { s =>
        val Op(d) = s
        visit(s,d)
      }
      print_stage_suffix(s"$sym", inner)

    case _: OpReduce[_] =>
      val inner = levelOf(sym) match { 
      	case InnerControl => true
      	case _ => false
      }
      print_stage_prefix(s"${getScheduling(sym)}OpReduce",s"",s"$sym", s"${sym.ctx}", inner)
      print_stream_info(sym)
      print_stage_suffix(s"$sym", inner)

    case _: OpMemReduce[_,_] =>
      val inner = levelOf(sym) match { 
      	case InnerControl => true
      	case _ => false
      }
      print_stage_prefix(s"${getScheduling(sym)}OpMemReduce",s"",s"$sym", s"${sym.ctx}", inner)
      print_stream_info(sym)
      print_stage_suffix(s"$sym", inner)

    case UnrolledForeach(en,cchain,func,iters,valids) =>
      controllerStack.push(sym)
      val inner = levelOf(sym) match { 
      	case InnerControl => childrenOf(sym).length == 0 // To catch when we have switch as a child
      	case _ => false
      }
      val tileXfer = childrenOf(sym).map{c => c match {case Def(FringeSparseLoad(_,_,_)) => "Gather."; case Def(FringeSparseStore(_,_,_)) => "Scatter."; 
                                                       case Def(FringeDenseLoad(_,_,_)) => "Load."; case Def(FringeDenseStore(_,_,_,_)) => "Store."; 
                                                       case _ => ""}}.mkString("")
      val ctxline = if (transferChannel(sym) >= 0) {s"${sym.ctx}, channel ${transferChannel(sym)}"} else {s"${sym.ctx}"}
      print_stage_prefix(s"${getScheduling(sym)}${tileXfer}UnrolledForeach",s"${cchain}",s"$sym", s"${ctxline}", inner)
      print_stream_info(sym)
      val children = getControlNodes(func)
      children.foreach { s =>
        val Op(d) = s
        visit(s,d)
      }
      print_stage_suffix(s"$sym", inner)
      controllerStack.pop()

    case ParallelPipe(_,func) =>
      controllerStack.push(sym)
      val inner = false
      print_stage_prefix(s"Parallel",s"",s"$sym", s"${sym.ctx}", inner)
      print_stream_info(sym)
      val children = getControlNodes(func)
      children.foreach { s =>
        val Op(d) = s
        visit(s,d)
      }
      print_stage_suffix(s"$sym", inner)
      controllerStack.pop()

    case UnrolledReduce(en,cchain,_,func,iters,valids) =>
      controllerStack.push(sym)
      val inner = levelOf(sym) match { 
        case InnerControl => childrenOf(sym).length == 0 // To catch when we have switch as a child
        case _ => false
      }
      print_stage_prefix(s"${getScheduling(sym)}UnrolledReduce",s"${cchain}",s"$sym", s"${sym.ctx}", inner)
      print_stream_info(sym)
      val children = getControlNodes(func)
      children.foreach { s =>
        val Op(d) = s
        visit(s,d)
      }
      print_stage_suffix(s"$sym", inner)
      controllerStack.pop()

    case op@Switch(_,selects, cases) =>
      controllerStack.push(sym)
      val inner = false
      val collapsible = levelOf(sym) match { 
        case InnerControl => false 
        case _ => true
      }
      val ohm = if (Bits.unapply(op.mT).isDefined) {"OHM."} else ""
      print_stage_prefix(s"${getScheduling(sym)}${ohm}Switch",s"",s"$sym", s"${sym.ctx}", inner, collapsible)
      print_stream_info(sym)
      cases.foreach{c => getStm(c).foreach(visitStm) }
      print_stage_suffix(s"$sym", inner)
      controllerStack.pop()

    case op@SwitchCase(func) =>
      controllerStack.push(sym)
      val inner = if (childrenOf(sym).length > 0) false else true
      val ohm = if (Bits.unapply(op.mT).isDefined) {"OHM."} else ""
      print_stage_prefix(s"${getScheduling(sym)}${ohm}SwitchCase",s"",s"$sym", s"${sym.ctx}", inner)
      print_stream_info(sym)
      val children = getControlNodes(func)
      children.foreach { s =>
        val Op(d) = s
        visit(s,d)
      }
      print_stage_suffix(s"$sym", inner)
      controllerStack.pop()

    case StateMachine(ens,start,notDone,func,nextState,_) =>
      controllerStack.push(sym)
      val inner = if (childrenOf(sym).length > 0) false else true
      print_stage_prefix(s"${getScheduling(sym)}FSM",s"",s"$sym", s"${sym.ctx}", inner)
      print_stream_info(sym)
      val children = getControlNodes(func)
      children.foreach { s =>
        val Op(d) = s
        visit(s,d)
      }
      print_stage_suffix(s"$sym", inner)
      controllerStack.pop()


    case _: FringeDenseLoad[_] => 
      print_stage_prefix("FringeDenseLoad (Fake Node)", "", s"$sym", s"${sym.ctx}", true)
      print_stream_info(sym)
      print_stage_suffix(s"$sym", true)
    case _: FringeDenseStore[_] => 
      print_stage_prefix("FringeDenseStore (Fake Node)", "", s"$sym", s"${sym.ctx}", true)
      print_stream_info(sym)
      print_stage_suffix(s"$sym", true)
    case _: FringeSparseLoad[_] => 
      print_stage_prefix("FringeSparseLoad (Fake Node)", "", s"$sym", s"${sym.ctx}", true)
      print_stream_info(sym)
      print_stage_suffix(s"$sym", true)
    case _: FringeSparseStore[_] => 
      print_stage_prefix("FringeSparseStore (Fake Node)", "", s"$sym", s"${sym.ctx}", true)
      print_stream_info(sym)
      print_stage_suffix(s"$sym", true)

    case _ => // Do not visit super because we don't care to traverse everything
  }

}
