package spatial.codegen.chiselgen

import argon.core._
import spatial.aliases._
import spatial.banking._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._


trait ChiselGenLineBuffer extends ChiselGenController {
  private var linebufs: List[Sym[_]] = Nil

  override protected def name(s: Dyn[_]): String = s match {
    case Def(_: LineBufferNew[_]) => s"""${s}_${s.name.getOrElse("linebuf")}"""
    case _ => super.name(s)
  } 

  override protected def remap(tp: Type[_]): String = tp match {
    case tp: LineBufferType[_] => src"LineBuffer[${tp.child}]"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    // TODO: Need to account for stride here
    case op@LineBufferNew(rows, cols, stride) =>
      appPropertyStats += HasLineBuffer
      val mem = instanceOf(lhs)
      val readers = readersOf(lhs)
      val writers = writersOf(lhs)
      val (transientWrites, nonTransientWrites) = writers.partition(write => isTransient(write.node))

      if (readers.isEmpty) warn(lhs.ctx, s"LineBuffer $lhs has no readers.")
      if (nonTransientWrites.isEmpty) warn(lhs.ctx, s"LineBuffer $lhs has no non-transient writers.")

      // // Currently assumes all readers have same par
      // val col_rPar = readers.map(accessWidth).reduce{_+_}/*headOption.getOrElse(1)*/ / {rows match {case Exact(r) => r}}
      // Currently assumes all writers have same par
      val col_wPar = nonTransientWrites.map(accessWidth).headOption.getOrElse(1)
      // Assumes there is either 0 of these (returns 0) or 1 of these
      val transient_wPar = transientWrites.map(accessWidth).sum

      val col_rPars = readers.map(_.node).map{
        case Def(_: LineBufferLoad[_]) => 1
        case Def(op: BankedLineBufferLoad[_]) => op.bank.map(_.apply(1)).distinct.length
        //case Def(LineBufferColSlice(_,_,_,len)) => len
      }
      if (col_rPars.distinct.length != 1) {
        error(lhs.ctx, u"LineBuffer $lhs has differing port widths.")
        error(u"LineBuffers with differing port widths not yet supported in Chisel.")
        error(lhs.ctx)
      }
      val col_rPar = col_rPars.headOption.getOrElse(1)
      /*
      Console.println(s"working on $lhs ${writersOf(lhs)} on $i")
      val col_wPar = writersOf(lhs) // Currently assumes all readers have same par
        .filter{write => dispatchOf(write, lhs) contains i}
        .filter{a => !isTransient(a.node.asInstanceOf[Exp[_]])}
        .map { w => 
          val par = w.node match {
            case Def(_: LineBufferEnq[_]) => 1
            case Def(a@ParLineBufferEnq(_,_,ens)) => ens.length
            case Def(_: LineBufferRotateEnq[_]) => 1
            case Def(op@ParLineBufferRotateEnq(lb,row,data,ens)) => ens.length
          }
          par
        }.head
      val transient_wPar = writersOf(lhs) // Currently assumes all readers have same par
        .filter{write => dispatchOf(write, lhs) contains i}
        .filter{a => isTransient(a.node.asInstanceOf[Exp[_]])}
        .map { w => 
          val par = w.node match {
            case Def(_: LineBufferEnq[_]) => 1
            case Def(a@ParLineBufferEnq(_,_,ens)) => ens.length
            case Def(_: LineBufferRotateEnq[_]) => 1
            case Def(op@ParLineBufferRotateEnq(lb,row,data,ens)) => ens.length
          }
          par
        }.sum // Assumes there is either 0 of these (returns 0) or 1 of these
      */
      val bcInfo = bufferControlInfo(lhs)
      val col_banks = mem.nBanks.last
      val row_banks = mem.nBanks.head
      // rows to buffer is 1 + number of blank stages between the write and the read (i.e.- 1 + buffer_info - 2 )
      val empty_stages_to_buffer = {bufferControlInfo(lhs).length - 1} max 1 // TODO: min 1 in case lca is sequential
      val row_rPar = rows // Could be wrong
      val accessors = bufferControlInfo(lhs).length
      val numWriters = nonTransientWrites.length
      val width = bitWidth(lhs.tp.typeArguments.head)
      emitGlobalModule(src"""val $lhs = Module(new templates.LineBuffer(${getConstValue(rows)}, ${getConstValue(cols)}, $empty_stages_to_buffer, ${getConstValue(stride)},
        $col_wPar, $col_rPar, $col_banks,
        $numWriters, $row_rPar, $transient_wPar, $accessors, $width))  // Data type: ${op.mT}""")
      emitGlobalModule(src"$lhs.io.reset := reset")
      linebufs = linebufs :+ lhs

    case _:LineBufferRowSlice[_]  => throw new Exception(s"Cannot generate unbanked LineBuffer slice.\n${str(lhs)}")
    case _:LineBufferColSlice[_]  => throw new Exception(s"Cannot generate unbanked LineBuffer slice.\n${str(lhs)}")
    case _:LineBufferLoad[_]      => throw new Exception(s"Cannot generate unbanked LineBuffer load.\n${str(lhs)}")
    case _:LineBufferEnq[_]       => throw new Exception(s"Cannot generate unbanked LineBuffer enqueue.\n${str(lhs)}")
    case _:LineBufferRotateEnq[_] => throw new Exception(s"Cannot generate unbanked LineBuffer enqueue.\n${str(lhs)}")

    // TODO: Revised banking!
    case op@BankedLineBufferRotateEnq(lb,row,data,ens) => throw new Exception("Unimplemented - BankedLineBufferRotateEnq")
    case op@BankedLineBufferLoad(lb,rows,cols,ens) => throw new Exception("Unimplemented - BankedLineBufferLoad")
    case op@BankedLineBufferEnq(lb,data,ens) => throw new Exception("Unimplemented - BankedLineBufferEnq")

      /*
    case op@BankedLineBufferRotateEnq(lb,row,data,ens) =>
        emitGlobalModule(src"""val ${lhs}_$i = Module(new templates.LineBuffer(${getConstValue(rows)}, ${getConstValue(cols)}, ${empty_stages_to_buffer}, ${getConstValue(stride)},
          ${col_wPar}, ${col_rPar}, ${col_banks},
          ${numWriters}, ${row_rPar}, ${transient_wPar}, $accessors, $width))  // Data type: ${op.mT}""")
        emitGlobalModule(src"${lhs}_$i.io.reset := reset")
        linebufs = linebufs :+ (lhs.asInstanceOf[Sym[LineBufferNew[_]]], i)
      }

    case op@LineBufferRotateEnq(lb,row,data,en) =>
      throw new Exception(src"Non-parallelized LineBufferRotateEnq not implemented yet!  It isn't hard, just ask matt to do it")
      val dispatch = dispatchOf(lhs, lb).toList.distinct
      if (dispatch.length > 1) { throw new Exception(src"This is an example where lb dispatch > 1. Please use as test case! (node $lhs on lb $lb)") }
      val i = dispatch.head
      val parent = writersOf(lb).find{_.node == lhs}.get.ctrlNode
      // emit(src"${lb}_$i.io.data_in(${row}) := ${data}.raw")
      // emit(src"${lb}_$i.io.w_en(${row}) := $en & ${DL(src"${swap(parent, DatapathEn)} & ~${swap(parent, Inhibitor)}", src"${enableRetimeMatch(en, lhs)}.toInt")}")
      emit(src"${lb}_$i.connectWPort(List(${data}.r), $en & ${DL(src"${swap(parent, DatapathEn)} & ~${swap(parent, Inhibitor)}", src"${enableRetimeMatch(en, lhs)}.toInt")})")


    case op@ParLineBufferRotateEnq(lb,row,data,ens) =>
      if (!isTransient(lhs)) {
        val dispatch = dispatchOf(lhs, lb).toList.distinct
        val stride = lb match {case Def(LineBufferNew(_,_,Exact(s))) => s.toInt}
        if (dispatch.length > 1) { throw new Exception(src"This is an example where lb dispatch > 1. Please use as test case! (node $lhs on lb $lb)") }
        val ii = dispatch.head
        val parent = writersOf(lb).find{_.node == lhs}.get.ctrlNode
        val dStr = (0 until stride).map{j => (0 until data.length).map{i => src"${data(i)}.r"}}.flatten.mkString("List(",",",")")
        val enStr = (0 until stride).map{r => 
          src"""${ens.map{en => src"$en"}.mkString("&")} & ${DL(src"${swap(parent, DatapathEn)} & ~${swap(parent, Inhibitor)}", enableRetimeMatch(ens.head, lhs), true)} & ${row} === ${r}.U(${row}.getWidth.W)"""
        }.mkString("List(",",",")")
        emit(src"${lb}_$ii.connectWPort(${dStr}, $enStr)")
      } else {
        val dispatch = dispatchOf(lhs, lb).toList.distinct
        if (dispatch.length > 1) { throw new Exception(src"This is an example where lb dispatch > 1. Please use as test case! (node $lhs on lb $lb)") }
        val ii = dispatch.head
        val parent = writersOf(lb).find{_.node == lhs}.get.ctrlNode
        emit(src"""val ${lb}_${ii}_transient_base = ${lb}_$ii.col_wPar*${lb}_$ii.rstride*${lb}_$ii.numWriters""")
        data.zipWithIndex.foreach { case (d, i) =>
          emit(src"${lb}_$ii.io.data_in(${lb}_${ii}_transient_base + $i) := ${d}.raw")
        }
        emit(src"""${lb}_$ii.io.w_en(${lb}_$ii.rstride*${lb}_$ii.numWriters) := ${ens.map{en => src"$en"}.mkString("&")} & ${DL(src"${swap(parent, DatapathEn)} & ~${swap(parent, Inhibitor)}", enableRetimeMatch(ens.head, lhs), true)}""")
        emit(src"""${lb}_$ii.io.transientDone := ${swap(parent, Done)}""")
        emit(src"""${lb}_$ii.io.transientSwap := ${swap(parentOf(parent).get, Done)}""")
      }

    case op@ParLineBufferLoad(lb,rows,cols,ens) =>
      val dispatch = dispatchOf(lhs, lb).toList.distinct
      if (dispatch.length > 1) { throw new Exception(src"This is an example where lb dispatch > 1. Please use as test case! (node $lhs on lb $lb)") }
      val ii = dispatch.head
      rows.zip(cols).zipWithIndex.foreach{case ((row, col),i) => 
        emit(src"${lb}_$ii.io.col_addr(0) := ${col}.raw // Assume we always read from same col")
        val rowtext = row match {
          case Const(cc) => s"$cc"
          case _ => src"${row}.r"
        }
        emit(s"val ${quote(lhs)}_$i = ${quote(lb)}_$ii.readRow(${rowtext})")
      }
      emitGlobalWire(s"""val ${quote(lhs)} = Wire(Vec(${rows.length}, UInt(32.W)))""")
      emit(s"""${quote(lhs)} := Vec(${(0 until rows.length).map{i => src"${lhs}_$i"}.mkString(",")})""")

    case op@ParLineBufferEnq(lb,data,ens) => //FIXME: Not correct for more than par=1
      if (!isTransient(lhs)) {
        val dispatch = dispatchOf(lhs, lb).toList.distinct
        if (dispatch.length > 1) { throw new Exception(src"This is an example where lb dispatch > 1. Please use as test case! (node $lhs on lb $lb)") }
        val ii = dispatch.head
        val parent = writersOf(lb).find{_.node == lhs}.get.ctrlNode
        // data.zipWithIndex.foreach { case (d, i) =>
        //   emit(src"${lb}_$ii.io.data_in($i) := ${d}.raw")
        // }
        // emit(src"""${lb}_$ii.io.w_en(0) := ${ens.map{en => src"$en"}.mkString("&")} & ${DL(src"${swap(parent, DatapathEn)} & ~${swap(parent, Inhibitor)}", enableRetimeMatch(ens.head, lhs), true)}""")
        emit(src"${lb}_$ii.connectWPort(${data.map(quote(_)).mkString("List(",".r, ",".r)")}, List(${ens.map{en => src"$en"}.mkString("&")} & ${DL(src"${swap(parent, DatapathEn)} & ~${swap(parent, Inhibitor)}", enableRetimeMatch(ens.head, lhs), true)}))")

      } else {
        val dispatch = dispatchOf(lhs, lb).toList.distinct
        if (dispatch.length > 1) { throw new Exception(src"This is an example where lb dispatch > 1. Please use as test case! (node $lhs on lb $lb)") }
        val ii = dispatch.head
        val parent = writersOf(lb).find{_.node == lhs}.get.ctrlNode
        emit(src"""val ${lb}_${ii}_transient_base = ${lb}_$ii.col_wPar*${lb}_$ii.rstride*${lb}_$ii.numWriters""")
        data.zipWithIndex.foreach { case (d, i) =>
          emit(src"${lb}_$ii.io.data_in(${lb}_${ii}_transient_base + $i) := ${d}.raw")
        }
        emit(src"""${lb}_$ii.io.w_en(${lb}_$ii.rstride*${lb}_$ii.numWriters) := ${ens.map{en => src"$en"}.mkString("&")} & ${DL(src"${swap(parent, DatapathEn)} & ~${swap(parent, Inhibitor)}", enableRetimeMatch(ens.head, lhs), true)}""")
        emit(src"""${lb}_$ii.io.transientDone := ${parent}_done""")
        emit(src"""${lb}_$ii.io.transientSwap := ${parentOf(parent).get}_done""")
      }
    */

    // case op@LineBufferLoad(lb,row,col,en) => 
    //   val dispatch = dispatchOf(lhs, lb).toList.distinct
    //   if (dispatch.length > 1) { throw new Exception(src"This is an example where lb dispatch > 1. Please use as test case! (node $lhs on lb $lb)") }
    //   val i = dispatch.head
    //   emit(src"${lb}_$i.io.col_addr(0) := ${col}.raw")
    //   val rowtext = row match {
    //     case Const(cc) => s"$cc"
    //     case _ => src"${row}.r"
    //   }
    //   emit(s"val ${quote(lhs)} = ${quote(lb)}_$i.readRow(${rowtext})")

    // case op@LineBufferEnq(lb,data,en) =>
    //   val dispatch = dispatchOf(lhs, lb).toList.distinct
    //   if (dispatch.length > 1) { throw new Exception(src"This is an example where lb dispatch > 1. Please use as test case! (node $lhs on lb $lb)") }
    //   val i = dispatch.head
    //   val parent = writersOf(lb).find{_.node == lhs}.get.ctrlNode
    //   emit(src"${lb}_$i.io.data_in(0) := ${data}.raw")
    //   emit(src"${lb}_$i.io.w_en(0) := $en & ${DL(src"${swap(parent, DatapathEn)} & ~${swap(parent, Inhibitor)}", src"${enableRetimeMatch(en, lhs)}.toInt")}")

    case _ => super.emitNode(lhs, rhs)
  }

  def getStreamAdjustment(c: Exp[Any]): String = {
      // If we are inside a stream pipe, the following may be set
      if (childrenOf(c).length == 0) {
        getStreamEnablers(c)
      } else {
        childrenOf(c).map{getStreamEnablers(_)}.filter(_.trim().length > 0).mkString{" "}
      }

  }

  override protected def emitFileFooter() {
    withStream(getStream("BufferControlCxns")) {
      linebufs.foreach{mem =>
        val info = bufferControlInfo(mem)
        info.zipWithIndex.foreach{ case (inf, port) => 
          emit(src"""${mem}.connectStageCtrl(${DL(swap(quote(inf._1), Done), 1, true)}, ${swap(quote(inf._1), BaseEn)}, List(${port})) ${inf._2}""")
        }


        // emit(src"""${mem}.lockUnusedCtrl() // Specific method for linebufs, since there is one ctrl port per line, but possibly not an access per line """)


      }
    }

    super.emitFileFooter()
  }

}
