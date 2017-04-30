package spatial.codegen.chiselgen

import argon.codegen.chiselgen.ChiselCodegen
import spatial.api.{ControllerExp, CounterExp, UnrolledExp}
import spatial.SpatialConfig
import spatial.analysis.SpatialMetadataExp 
import spatial.SpatialExp
import scala.collection.mutable.HashMap
import spatial.targets.DE1._


trait ChiselGenUnrolled extends ChiselGenController {
  val IR: SpatialExp
  import IR._


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

  override protected def spatialNeedsFPType(tp: Type[_]): Boolean = tp match { // FIXME: Why doesn't overriding needsFPType work here?!?!
      case FixPtType(s,d,f) => if (s) true else if (f == 0) false else true
      case IntType()  => false
      case LongType() => false
      case FloatType() => true
      case DoubleType() => true
      case _ => super.needsFPType(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case UnrolledForeach(en,cchain,func,iters,valids) =>
      val parent_kernel = controllerStack.head
      controllerStack.push(lhs)
      emitController(lhs, Some(cchain), Some(iters.flatten)) // If this is a stream, then each child has its own ctr copy
      if (styleOf(lhs) != StreamPipe) { 
        emitValids(cchain, iters, valids)
        withSubStream(src"${lhs}", src"${parent_kernel}", levelOf(lhs) == InnerControl) {
          emit(s"// Controller Stack: ${controllerStack.tail}")
          emitParallelizedLoop(iters, cchain)
          emitBlock(func)
        }
      } else {
        if (childrenOf(lhs).length > 0) {
          childrenOf(lhs).zipWithIndex.foreach { case (c, idx) =>
            emitValids(cchain, iters, valids, src"_copy$c")
          }          
        } else {
          emitValidsDummy(iters, valids, src"_copy$lhs") // FIXME: Weird situatio with nested stream ctrlrs, hacked quickly for tian so needs to be fixed
        }
        withSubStream(src"${lhs}", src"${parent_kernel}", levelOf(lhs) == InnerControl) {
          emit(s"// Controller Stack: ${controllerStack.tail}")
          childrenOf(lhs).zipWithIndex.foreach { case (c, idx) =>
            emitParallelizedLoop(iters, cchain, src"_copy$c")
          }
          // Register the remapping for bound syms in children
          valids.foreach{ layer =>
            layer.foreach{ v =>
              streamCtrCopy = streamCtrCopy :+ v
            }
          }
          iters.foreach{ is =>
            is.foreach{ iter =>
              streamCtrCopy = streamCtrCopy :+ iter
            }
          }

          emitBlock(func)
        }

      }
      if (levelOf(lhs) == InnerControl) emitInhibitor(lhs, Some(cchain))
      controllerStack.pop()

    case UnrolledReduce(en,cchain,accum,func,_,iters,valids,rV) =>
      val parent_kernel = controllerStack.head
      controllerStack.push(lhs)
      emitController(lhs, Some(cchain), Some(iters.flatten))
      emitValids(cchain, iters, valids)
      // Set up accumulator signals
      // emit(s"""val ${quote(lhs)}_redLoopCtr = Module(new RedxnCtr());""")
      // emit(s"""${quote(lhs)}_redLoopCtr.io.input.enable := ${quote(lhs)}_datapath_en""")
      // val redmax = if (SpatialConfig.enableRetiming) {if (bodyLatency(lhs).length > 0) {bodyLatency(lhs).reduce{_+_}} else 1} else 1
      // emit(s"""${quote(lhs)}_redLoopCtr.io.input.max := ${redmax}.U""")
      // emit(s"""${quote(lhs)}_redLoopCtr.io.input.reset := reset | ${quote(cchain)}_resetter""")
      // emit(s"""${quote(lhs)}_redLoopCtr.io.input.saturate := true.B""")
      // emit(s"""val ${quote(lhs)}_redLoop_done = ${quote(lhs)}_redLoopCtr.io.output.done;""")
      emit(src"""${cchain}_en := ${lhs}_sm.io.output.ctr_inc""")
      if (levelOf(lhs) == InnerControl) {
        val dlay = bodyLatency.sum(lhs)
        emit(s"val ${quote(accum)}_wren = chisel3.util.ShiftRegister(${quote(lhs)}_datapath_en & ~${quote(lhs)}_done & ~${quote(lhs)}_inhibitor, ${quote(lhs)}_retime)")
        emit(src"val ${accum}_resetter = ${lhs}_rst_en")
      } else {
        accum match { 
          case Def(_:RegNew[_]) => 
            // if (childrenOf(lhs).length == 1) {
            if (true) {
              emit(src"val ${accum}_wren = chisel3.util.ShiftRegister(${childrenOf(lhs).last}_done, ${quote(lhs)}_retime)// & ${lhs}_redLoop_done // TODO: Skeptical these codegen rules are correct")
            } else {
              emit(src"val ${accum}_wren = chisel3.util.ShiftRegister((${childrenOf(lhs).dropRight(1).last}_done)// & ${lhs}_redLoop_done // TODO: Skeptical these codegen rules are correct")              
            }
          case Def(_:SRAMNew[_,_]) =>
            emit(src"val ${accum}_wren = ${childrenOf(lhs).last}_done // TODO: SRAM accum is managed by SRAM write node anyway, this signal is unused")
        }
        emit(src"val ${accum}_resetter = Utils.delay(${parentOf(lhs).get}_done, 2)")
      }
      if (levelOf(lhs) == InnerControl) emitInhibitor(lhs, Some(cchain))
      // Create SRFF to block destructive reads after the cchain hits the max, important for retiming
      emit(src"//val ${accum}_initval = 0.U // TODO: Get real reset value.. Why is rV a tuple?")
      withSubStream(src"${lhs}", src"${parent_kernel}", levelOf(lhs) == InnerControl) {
        emit(s"// Controller Stack: ${controllerStack.tail}")
        emitParallelizedLoop(iters, cchain)
        emitBlock(func)
      }
      controllerStack.pop()

    case ParSRAMLoad(sram,inds,ens) =>
      val dispatch = dispatchOf(lhs, sram)
      val rPar = inds.length
      val dims = stagedDimsOf(sram)
      emit(s"""// Assemble multidimR vector""")
      emit(src"""val ${lhs}_rVec = Wire(Vec(${rPar}, new multidimR(${dims.length}, 32)))""")
      if (dispatch.toList.length == 1) {
        val k = dispatch.toList.head
        val parent = readersOf(sram).find{_.node == lhs}.get.ctrlNode
        inds.zipWithIndex.foreach{ case (ind, i) =>
          emit(src"${lhs}_rVec($i).en := chisel3.util.ShiftRegister(${parent}_en, ${parent}_retime) & ${ens(i)}")
          ind.zipWithIndex.foreach{ case (a, j) =>
            emit(src"""${lhs}_rVec($i).addr($j) := ${a}.raw """)
          }
        }
        val p = portsOf(lhs, sram, k).head
        emit(src"""val ${lhs}_base = ${sram}_$k.connectRPort(Vec(${lhs}_rVec.toArray), $p)""")
        sram.tp.typeArguments.head match { 
          case FixPtType(s,d,f) => if (spatialNeedsFPType(sram.tp.typeArguments.head)) {
              emit(s"""val ${quote(lhs)} = (0 until ${rPar}).map{i => Utils.FixedPoint($s,$d,$f, ${quote(sram)}_$k.io.output.data(${quote(lhs)}_base+i))}""")
            } else {
              emit(src"""val $lhs = (0 until ${rPar}).map{i => ${sram}_$k.io.output.data(${lhs}_base+i) }""")
            }
          case _ => emit(src"""val $lhs = (0 until ${rPar}).map{i => ${sram}_$k.io.output.data(${lhs}_base+i) }""")
        }
      } else {
        emit(src"""val ${lhs} = Wire(${newWire(lhs.tp)})""")
        dispatch.zipWithIndex.foreach{ case (k,id) => 
          val parent = readersOf(sram).find{_.node == lhs}.get.ctrlNode
          emit(src"${lhs}_rVec($id).en := chisel3.util.ShiftRegister(${parent}_en, ${parent}_retime) & ${ens(id)}")
          inds(k).zipWithIndex.foreach{ case (a, j) =>
            emit(src"""${lhs}_rVec($k).addr($j) := ${a}.raw """)
          }
          val p = portsOf(lhs, sram, k).head
          emit(src"""val ${lhs}_base_$k = ${sram}_$k.connectRPort(Vec(${lhs}_rVec.toArray), $p) // TODO: No need to connect all rVec lanes to SRAM even though only one is needed""")
          sram.tp.typeArguments.head match { 
            case FixPtType(s,d,f) => if (spatialNeedsFPType(sram.tp.typeArguments.head)) {
                emit(s"""${quote(lhs)}($k) := Utils.FixedPoint($s,$d,$f, ${quote(sram)}_$k.io.output.data(${quote(lhs)}_base_$k))""")
              } else {
                emit(src"""$lhs := ${sram}_$k.io.output.data(${lhs}_base_$k)""")
              }
            case _ => emit(src"""$lhs := ${sram}_$k.io.output.data(${lhs}_base_$k)""")
          }

      }
        
      }

    case ParSRAMStore(sram,inds,data,ens) =>
      val dims = stagedDimsOf(sram)
      val parent = writersOf(sram).find{_.node == lhs}.get.ctrlNode
      val enable = if (loadCtrlOf(sram).contains(parent)) src"${parent}_datapath_en" else src"${parent}_datapath_en"
      emit(s"""// Assemble multidimW vector""")
      emit(src"""val ${lhs}_wVec = Wire(Vec(${inds.indices.length}, new multidimW(${dims.length}, 32))) """)
      val datacsv = data.map{d => src"${d}.raw"}.mkString(",")
      data.zipWithIndex.foreach { case (d, i) =>
        emit(src"""${lhs}_wVec($i).data := ${d}.raw""")
      }
      inds.zipWithIndex.foreach{ case (ind, i) =>
        emit(src"${lhs}_wVec($i).en := ${ens(i)} & chisel3.util.ShiftRegister($enable, ${parent}_retime)")
        ind.zipWithIndex.foreach{ case (a, j) =>
          emit(src"""${lhs}_wVec($i).addr($j) := ${a}.raw """)
        }
      }
      duplicatesOf(sram).zipWithIndex.foreach{ case (mem, i) => 
        val p = portsOf(lhs, sram, i).mkString(",")
        emit(src"""${sram}_$i.connectWPort(${lhs}_wVec, List(${p}))""")
      }

    case ParFIFODeq(fifo, ens) =>
      val par = ens.length
      val en = ens.map(quote).mkString("&")
      val reader = readersOf(fifo).head.ctrlNode  // Assuming that each fifo has a unique reader
      emit(src"""${quote(fifo)}.io.pop := chisel3.util.ShiftRegister(${reader}_datapath_en, ${reader}_retime) & $en & ~${reader}_inhibitor""")
      fifo.tp.typeArguments.head match { 
        case FixPtType(s,d,f) => if (spatialNeedsFPType(fifo.tp.typeArguments.head)) {
            emit(s"""val ${quote(lhs)} = (0 until $par).map{ i => Utils.FixedPoint($s,$d,$f,${quote(fifo)}_rdata(i)) }""")
          } else {
            emit(src"""val ${lhs} = ${fifo}_rdata""")
          }
        case _ => emit(src"""val ${lhs} = ${fifo}_rdata""")
      }

    case ParFIFOEnq(fifo, data, ens) =>
      val par = ens.length
      val en = ens.map(quote).mkString("&")
      val writer = writersOf(fifo).head.ctrlNode  
      // Check if this is a tile consumer

      // val enabler = if (loadCtrlOf(fifo).contains(writer)) src"${writer}_datapath_en" else src"${writer}_sm.io.output.ctr_inc"
      val enabler = src"${writer}_datapath_en"
      emit(src"""${fifo}_writeEn := chisel3.util.ShiftRegister($enabler, ${writer}_retime) & $en""")
      val datacsv = data.map{d => src"${d}.raw"}.mkString(",")
      emit(src"""${fifo}_wdata := Vec(List(${datacsv}))""")

    case e@ParStreamRead(strm, ens) =>
      strm match {
        case Def(StreamInNew(bus)) => bus match {
          case VideoCamera => 
            emit(src"""val $lhs = io.stream_in_data""")  // Ignores enable for now
          case SliderSwitch => 
            emit(src"""val $lhs = io.switch_stream_in_data""")
          case _ => 
            val isAck = strm match { // TODO: Make this clean, just working quickly to fix bug for Tian
              case Def(StreamInNew(bus)) => bus match {
                case BurstAckBus => true
                case _ => false
              }
              case _ => false
            }
            emit(src"""${strm}_ready := (${ens.map{a => src"$a"}.mkString(" | ")}) & ${parentOf(lhs).get}_datapath_en // TODO: Definitely wrong thing for parstreamread""")
            if (!isAck) {
              emit(src"""//val $lhs = List(${ens.map{e => src"${e}"}.mkString(",")}).zipWithIndex.map{case (en, i) => ${strm}_data(i) }""")
              emit(src"""val $lhs = (0 until ${ens.length}).map{ i => ${strm}_data(i) }""")
            } else {
              emit(src"""// Do not read from dummy ack stream $strm""")        
            }


        }
      }


    case ParStreamWrite(strm, data, ens) =>
      val par = ens.length
      val datacsv = data.map{d => src"${d}.raw"}.mkString(",")
      val en = ens.map(quote).mkString("&")
      emit(src"${strm}_data := Vec(List(${datacsv}))")
      emit(src"${strm}_valid := $en & ${parentOf(lhs).get}_datapath_en & ~${parentOf(lhs).get}_done /*mask off double-enq for sram loads*/")
      
    case op@ParLineBufferLoad(lb,rows,cols,ens) =>
      rows.zip(cols).zipWithIndex.foreach{case ((row, col),i) => 
        emit(src"$lb.io.col_addr(0) := ${col}.raw // Assume we always read from same col")
        emit(s"val ${quote(lhs)}_$i = ${quote(lb)}.readRow(${row}.raw)")
      }
      emitGlobalWire(s"""val ${quote(lhs)} = Wire(Vec(${rows.length}, UInt(32.W)))""")
      emit(s"""${quote(lhs)} := Vec(${(0 until rows.length).map{i => src"${lhs}_$i"}.mkString(",")})""")

    case op@ParLineBufferEnq(lb,data,ens) => //FIXME: Not correct for more than par=1
      val parent = writersOf(lb).find{_.node == lhs}.get.ctrlNode
      data.zipWithIndex.foreach { case (d, i) =>
        emit(src"$lb.io.data_in($i) := ${d}.raw")
      }
      emit(src"""$lb.io.w_en := ${ens.map{en => src"$en"}.mkString("&")} & ${parent}_datapath_en""")

    case ParRegFileLoad(rf, inds, ens) => //FIXME: Not correct for more than par=1
      val dispatch = dispatchOf(lhs, rf).toList.head
      val port = portsOf(lhs, rf, dispatch).toList.head
      ens.zipWithIndex.foreach { case (en, i) => 
        if (spatialNeedsFPType(lhs.tp.typeArguments.head)) { lhs.tp.typeArguments.head match {
          case FixPtType(s,d,f) => 
            emitGlobalWire(s"""val ${quote(lhs)} = Wire(Vec(${ens.length}, new FixedPoint($s, $d, $f)))""")
            emit(src"""val ${lhs}_$i = Wire(new FixedPoint($s, $d, $f))""")
            emit(src"""${lhs}_$i := ${rf}_${dispatch}.readValue(${inds(i)(0)}.raw, ${inds(i)(1)}.raw, $port)""")
          case _ =>
            emitGlobalWire(s"""val ${quote(lhs)} = Wire(Vec(${ens.length}, UInt(32.W)))""")
            emit(src"""val ${lhs}_$i = ${rf}_${dispatch}.readValue(${inds(i)(0)}.raw, ${inds(i)(1)}.raw, $port)""")
        }} else {
            emitGlobalWire(s"""val ${quote(lhs)} = Wire(Vec(${ens.length}, UInt(32.W)))""")
            emit(src"""val ${lhs}_$i = ${rf}_${dispatch}.readValue(${inds(i)(0)}.raw, ${inds(i)(1)}.raw, $port)""")
        }
      }
      emit(s"""${quote(lhs)} := Vec(${(0 until ens.length).map{i => src"${lhs}_$i"}.mkString(",")})""")


    case ParRegFileStore(rf, inds, data, ens) => //FIXME: Not correct for more than par=1
      val parent = writersOf(rf).find{_.node == lhs}.get.ctrlNode
      duplicatesOf(rf).zipWithIndex.foreach{case (mem, ii) => 
        val port = portsOf(lhs, rf, ii)
        ens.zipWithIndex.foreach{ case (en, i) => 
          emit(s"""${quote(rf)}_${ii}.connectWPort(${data(i)}.raw, ${quote(inds(i)(0))}.raw, ${quote(inds(i)(1))}.raw, ${quote(en)} & ${quote(parent)}_datapath_en, List(${port.toList.mkString(",")}))""")
        }
      }
      



    case _ => super.emitNode(lhs, rhs)
  }
}
