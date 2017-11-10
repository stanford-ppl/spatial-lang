package spatial.codegen.chiselgen

import argon.core._
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
        case Def(LineBufferColSlice(_,_,_,len)) => len
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
      val empty_stages_to_buffer = {bcInfo.length - 1} max 1 // TODO: min 1 in case lca is sequential
      val row_rPar = row_banks // Could be wrong
      val accessors = bcInfo.length
      val row_wPar = 1 // TODO: Do correct analysis here!
      val width = bitWidth(lhs.tp.typeArguments.head)
      emitGlobalModule(src"""val $lhs = Module(new templates.LineBuffer(${getConstValue(rows)}, ${getConstValue(cols)}, $empty_stages_to_buffer, ${getConstValue(stride)},
        $col_wPar, $col_rPar, $col_banks,
        $row_wPar, $row_rPar, $transient_wPar, $accessors, $width))  // Data type: ${op.mT}""")
      emitGlobalModule(src"$lhs.io.reset := reset")
      linebufs = linebufs :+ lhs

    case op@LineBufferRotateEnq(lb,row,data,en) => throw new Exception(s"Cannot generate unbanked LineBuffer enqueue.\n${str(lhs)}")
      //throw new Exception(src"Non-parallelized LineBufferRotateEnq not implemented yet!  It isn't hard, just ask matt to do it")
      /*val dispatch = dispatchOf(lhs, lb).toList.distinct
      if (dispatch.length > 1) { throw new Exception(src"This is an example where lb dispatch > 1. Please use as test case! (node $lhs on lb $lb)") }
      val i = dispatch.head
      val parent = writersOf(lb).find{_.node == lhs}.get.ctrlNode
      emit(src"${lb}_$i.io.data_in(${row}) := ${data}.raw")
      emit(src"${lb}_$i.io.w_en(${row}) := $en & Utils.getRetimed(${swap(parent, DatapathEn)} & ~${swap(parent, Inhibitor)}, ${symDelay(lhs)})")*/

    case BankedLineBufferRotateEnq(lb,data,ens,row) =>
      val parent = ctrlOf(lhs).node
      if (!isTransient(lhs)) {
        val stride = lb match {case Def(LineBufferNew(_,_,Exact(s))) => s.toInt}
        (0 until stride).foreach{r =>
          data.zipWithIndex.foreach { case (d, i) =>
            emit(src"$lb.io.data_in($r * ${data.length} + $i) := $d.raw")
          }
          emit(src"""$lb.io.w_en($r) := ${ens.map{en => src"$en"}.mkString("&")} & (${swap(parent, DatapathEn)} & ~${swap(parent, Inhibitor)}).D(${symDelay(lhs)}, rr) & $row === $r.U($row.getWidth.W)""")
        }        
      }
      else {
        emit(src"""val ${lb}_transient_base = $lb.col_wPar*$lb.rstride""")
        data.zipWithIndex.foreach { case (d, i) =>
          emit(src"$lb.io.data_in(${lb}_transient_base + $i) := $d.raw")
        }
        emit(src"""$lb.io.w_en($lb.rstride) := ${ens.map{en => src"$en"}.mkString("&")} & (${swap(parent, DatapathEn)} & ~${swap(parent, Inhibitor)}).D(${symDelay(lhs)}, rr)""")
        emit(src"""$lb.io.transientDone := ${swap(parent, Done)}""")
        emit(src"""$lb.io.transientSwap := ${swap(parentOf(parent).get, Done)}""")
      }
        
      // TODO: Multiple cycles
      // Copied from ScalaGen:
      // open(src"val $lhs = Array.tabulate($len){i => ")
        // oobApply(op.mT, lb, lhs, Seq(row,col)){ emit(src"$lb.apply($row+i,$col)") }
      // close("}")
      // Console.println(row)
      // Console.println(src"$row")
      // Console.println(s"$quote(row)")
      
      /*val dispatch = dispatchOf(lhs, lb).toList.distinct
      if (dispatch.length > 1) { throw new Exception(src"This is an example where lb dispatch > 1. Please use as test case! (node $lhs on lb $lb)") }
      val i = dispatch.head
      for ( k <- 0 until lenOf(lhs)) {
        emit(src"${lb}_$i.io.col_addr($k) := ${col}.r + ${k}.U")  
      }
      val rowtext = row match {
        case Const(cc) => s"${cc}.U"
        case _ => src"${row}.r"
      }
      emitGlobalWire(s"""val ${quote(lhs)} = Wire(Vec(${getConstValue(len)}, ${newWire(lhs.tp.typeArguments.head)}))""")
      for ( k <- 0 until lenOf(lhs)) {
        emit(src"${lhs}($k) := ${quote(lb)}_$i.readRowSlice(${rowtext}, ${k}.U).r")  
      }*/
    case _:LineBufferRowSlice[_] => throw new Exception(s"Cannot generate unbanked LineBuffer slice.\n${str(lhs)}")
    case _:LineBufferColSlice[_] => throw new Exception(s"Cannot generate unbanked LineBuffer slice.\n${str(lhs)}")
    case _:LineBufferLoad[_]     => throw new Exception(s"Cannot generate unbanked LineBuffer load.\n${str(lhs)}")
    /*   val dispatch = dispatchOf(lhs, lb).toList.distinct
      if (dispatch.length > 1) { throw new Exception(src"This is an example where lb dispatch > 1. Please use as test case! (node $lhs on lb $lb)") }
      val i = dispatch.head
      emit(src"${lb}_$i.io.col_addr(0) := ${col}.raw")
      val rowtext = row match {
        case Const(cc) => s"$cc"
        case _ => src"${row}.r"
      }
      emit(s"val ${quote(lhs)} = ${quote(lb)}_$i.readRow(${rowtext})")*/

    // TODO
    case BankedLineBufferLoad(lb,bank,ofs,ens) =>
      /*rows.zip(cols).zipWithIndex.foreach{case ((row, col),i) =>
        emit(src"$lb.io.col_addr(0) := $col.raw // Assume we always read from same col")
        val rowtext = row match {
          case Const(cc) => s"$cc"
          case _ => src"$row.r"
        }
        emit(s"val ${quote(lhs)}_$i = ${quote(lb)}.readRow(${rowtext})")
      }
      emitGlobalWire(s"""val ${quote(lhs)} = Wire(Vec(${rows.length}, UInt(32.W)))""")
      emit(s"""${quote(lhs)} := Vec(${rows.indices.map{i => src"${lhs}_$i"}.mkString(",")})""")*/


    // TODO: Shouldn't occur? Can be merged with BankedLineBufferEnq
    case LineBufferEnq(lb,data,en) =>
      val parent = ctrlOf(lhs).node
      emit(src"$lb.io.data_in(0) := $data.raw")
      emit(src"$lb.io.w_en(0) := $en & Utils.getRetimed(${swap(parent, DatapathEn)} & ~${swap(parent, Inhibitor)}, ${symDelay(lhs)})")


    case BankedLineBufferEnq(lb,data,ens) => //FIXME: Not correct for more than par=1
      val parent = ctrlOf(lhs).node

      if (!isTransient(lhs)) {
        data.zipWithIndex.foreach { case (d, i) =>
          emit(src"$lb.io.data_in($i) := $d.raw")
        }
        emit(src"""$lb.io.w_en(0) := ${ens.map{en => src"$en"}.mkString("&")} & (${swap(parent, DatapathEn)} & ~${swap(parent, Inhibitor)}).D(${symDelay(lhs)}, rr)""")
      }
      else {
        emit(src"""val ${lb}_transient_base = $lb.col_wPar*$lb.rstride""")
        data.zipWithIndex.foreach { case (d, i) =>
          emit(src"$lb.io.data_in(${lb}_transient_base + $i) := $d.raw")
        }
        emit(src"""$lb.io.w_en($lb.rstride) := ${ens.map{en => src"$en"}.mkString("&")} & (${swap(parent, DatapathEn)} & ~${swap(parent, Inhibitor)}).D(${symDelay(lhs)}, rr)""")
        emit(src"""$lb.io.transientDone := ${parent}_done""")
        emit(src"""$lb.io.transientSwap := ${parentOf(parent).get}_done""")
      }



    case _ => super.emitNode(lhs, rhs)
  }

  def getStreamAdjustment(c: Exp[Any]): String = {
      // If we are inside a stream pipe, the following may be set
      if (childrenOf(c).isEmpty) {
        getStreamEnablers(c)
      } else {
        childrenOf(c).map(getStreamEnablers).filter(_.trim().length > 0).mkString{" "}
      }

  }

  override protected def emitFileFooter() {
    withStream(getStream("BufferControlCxns")) {
      linebufs.foreach{mem =>
        val info = bufferControlInfo(mem)
        info.zipWithIndex.foreach{ case (inf, port) => 
          emit(src"""$mem.connectStageCtrl(${swap(quote(inf._1), Done)}.D(1,rr), ${swap(quote(inf._1), BaseEn)}, List(${port})) ${inf._2}""")
        }
        // emit(src"""${mem}.lockUnusedCtrl() // Specific method for linebufs, since there is one ctrl port per line, but possibly not an access per line """)
      }
    }

    super.emitFileFooter()
  }

}
