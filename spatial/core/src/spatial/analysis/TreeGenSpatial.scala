package spatial.analysis

import argon.core._
import argon.core.Config
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

import java.io.PrintWriter


trait TreeGenSpatial extends SpatialTraversal {
  override val name = "Tree Gen"
  // def getStages(blks: Block[_]*): Seq[Sym[_]] = blks.flatMap(blockContents).flatMap(_.lhs)

  // def getPrimitiveNodes(blks: Block[_]*): Seq[Sym[_]] = getStages(blks:_*).filter(isPrimitiveNode)
  // def getControlNodes(blks: Block[_]*): Seq[Sym[_]] = getStages(blks:_*).filter(isControlNode)
  // def getAllocations(blks: Block[_]*): Seq[Sym[_]] = getStages(blks:_*).filter(isAllocation)

  // def hasPrimitiveNodes(blks: Block[_]*): Boolean = blks.exists{blk => getControlNodes(blk).nonEmpty }
  // def hasControlNodes(blks: Block[_]*): Boolean = blks.exists{blk => getControlNodes(blk).nonEmpty }

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
    new java.io.File(Config.genDir).mkdirs()
    controller_tree = { new PrintWriter(Config.genDir + "/controller_tree.html") }
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
    controller_tree.write(Config.name)
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

  def print_stage_prefix(title: String, ctr: String, node: String, inner: Boolean = false) {
    controller_tree.write(s"""<TD><font size = "6">$title<br><b>$node</b></font><br><font size = "1">Counter: $ctr</font> """)
    if (!inner) {
      controller_tree.write(s"""<div data-role="collapsible">
      <h4> </h4>${table_init}""")
    }
  }
  def print_stage_suffix(name: String, inner: Boolean = false) {
    if (!inner) {
      controller_tree.write("""</TABLE></div>""")
    }
    controller_tree.write(s"</TD><!-- Close $name -->")
  }

  override protected def visit(sym: Sym[_], rhs: Op[_]): Unit = rhs match {
    case Hwblock(func,_) =>
      val inner = levelOf(sym) match { 
      	case InnerControl => childrenOf(sym).length == 0 // To catch when we have switch as a child
      	case _ => false
      }
      print_stage_prefix(s"Hwblock",s"",s"$sym", inner)
      val children = getControlNodes(func)
      children.foreach { s =>
        val Op(d) = s
        visit(s,d)
      }
      print_stage_suffix(s"$sym", inner)

    /*case BurstLoad(dram, fifo, ofs, ctr, i) =>
      print_stage_prefix(s"BurstLoad",s"",s"$sym", true)
      print_stage_suffix(s"$sym", true)

    case BurstStore(dram, fifo, ofs, ctr, i) =>
      print_stage_prefix(s"BurstStore",s"",s"$sym", true)
      print_stage_suffix(s"$sym", true)*/

    case UnitPipe(_,func) =>
      val inner = levelOf(sym) match { 
      	case InnerControl => childrenOf(sym).length == 0 // To catch when we have switch as a child
      	case _ => false
      }
      print_stage_prefix(s"${getScheduling(sym)}Unitpipe",s"",s"$sym", inner)
      val children = getControlNodes(func)
      children.foreach { s =>
        val Op(d) = s
        visit(s,d)
      }
      print_stage_suffix(s"$sym", inner)

    case OpForeach(en, cchain, func, iters) =>
      val inner = levelOf(sym) match { 
      	case InnerControl => true
      	case _ => false
      }
      print_stage_prefix(s"${getScheduling(sym)}OpForeach",s"${cchain}",s"$sym", inner)
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
      print_stage_prefix(s"${getScheduling(sym)}OpReduce",s"",s"$sym", inner)
      print_stage_suffix(s"$sym", inner)

    case _: OpMemReduce[_,_] =>
      val inner = levelOf(sym) match { 
      	case InnerControl => true
      	case _ => false
      }
      print_stage_prefix(s"${getScheduling(sym)}OpMemReduce",s"",s"$sym", inner)
      print_stage_suffix(s"$sym", inner)

    case UnrolledForeach(en,cchain,func,iters,valids) =>
      val inner = levelOf(sym) match { 
      	case InnerControl => childrenOf(sym).length == 0 // To catch when we have switch as a child
      	case _ => false
      }
  
      print_stage_prefix(s"${getScheduling(sym)}UnrolledForeach",s"${cchain}",s"$sym", inner)
      val children = getControlNodes(func)
      children.foreach { s =>
        val Op(d) = s
        visit(s,d)
      }
      print_stage_suffix(s"$sym", inner)

    case ParallelPipe(_,func) =>
      val inner = false
      print_stage_prefix(s"Parallel",s"",s"$sym", inner)
      val children = getControlNodes(func)
      children.foreach { s =>
        val Op(d) = s
        visit(s,d)
      }
      print_stage_suffix(s"$sym", inner)

    case UnrolledReduce(en,cchain,_,func,iters,valids) =>
      val inner = levelOf(sym) match { 
        case InnerControl => childrenOf(sym).length == 0 // To catch when we have switch as a child
        case _ => false
      }
      print_stage_prefix(s"${getScheduling(sym)}UnrolledReduce",s"${cchain}",s"$sym", inner)
      val children = getControlNodes(func)
      children.foreach { s =>
        val Op(d) = s
        visit(s,d)
      }
      print_stage_suffix(s"$sym", inner)

    case Switch(_,selects, cases) =>
      val inner = false
      print_stage_prefix(s"${getScheduling(sym)}Switch",s"",s"$sym", inner)
      cases.foreach{c => getStm(c).foreach(visitStm) }
      print_stage_suffix(s"$sym", inner)

    case SwitchCase(func) =>
      val inner = if (childrenOf(sym).length > 0) false else true
      print_stage_prefix(s"${getScheduling(sym)}SwitchCase",s"",s"$sym", inner)
      val children = getControlNodes(func)
      children.foreach { s =>
        val Op(d) = s
        visit(s,d)
      }
      print_stage_suffix(s"$sym", inner)

    case StateMachine(ens,start,notDone,func,nextState,_) =>
      val inner = if (childrenOf(sym).length > 0) false else true
      print_stage_prefix(s"${getScheduling(sym)}FSM",s"",s"$sym", inner)
      val children = getControlNodes(func)
      children.foreach { s =>
        val Op(d) = s
        visit(s,d)
      }
      print_stage_suffix(s"$sym", inner)


    case _: FringeDenseLoad[_] => 
      print_stage_prefix("FringeDenseLoad (Fake Node)", "", s"$sym", true)
      print_stage_suffix(s"$sym", true)
    case _: FringeDenseStore[_] => 
      print_stage_prefix("FringeDenseStore (Fake Node)", "", s"$sym", true)
      print_stage_suffix(s"$sym", true)
    case _: FringeSparseLoad[_] => 
      print_stage_prefix("FringeSparseLoad (Fake Node)", "", s"$sym", true)
      print_stage_suffix(s"$sym", true)
    case _: FringeSparseStore[_] => 
      print_stage_prefix("FringeSparseStore (Fake Node)", "", s"$sym", true)
      print_stage_suffix(s"$sym", true)

    case _ => // Do not visit super because we don't care to traverse everything
  }

}
