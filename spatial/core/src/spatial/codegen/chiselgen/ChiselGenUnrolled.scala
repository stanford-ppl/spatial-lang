package spatial.codegen.chiselgen

import argon.core._
import argon.nodes._
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._
import spatial.targets.DE1._


trait ChiselGenUnrolled extends ChiselGenController {

  override protected def spatialNeedsFPType(tp: Type[_]): Boolean = tp match { // FIXME: Why doesn't overriding needsFPType work here?!?!
    case FixPtType(s,d,f) => if (s) true else if (f == 0) false else true
    case IntType()  => false
    case LongType() => false
    case FloatType() => true
    case DoubleType() => true
    case _ => super.needsFPType(tp)
  }

  override protected def name(s: Dyn[_]): String = s match {
    case Def(_: UnrolledForeach)     => s"${s}_unrForeach"
    case Def(_: UnrolledReduce[_,_]) => s"${s}_unrRed"
    case Def(_: ParSRAMLoad[_])      => s"""${s}_parLd${s.name.getOrElse("")}"""
    case Def(_: ParSRAMStore[_])     => s"""${s}_parSt${s.name.getOrElse("")}"""
    case Def(_: ParFIFODeq[_])       => s"${s}_parDeq"
    case Def(_: ParFIFOEnq[_])       => s"${s}_parEnq"
    case _ => super.name(s)
  } 

  private def flattenAddress(dims: Seq[Exp[Index]], indices: Seq[Exp[Index]]): String = {
    val strides = List.tabulate(dims.length){i => (dims.drop(i+1).map(quote) :+ "1").mkString("*-*") }
    indices.zip(strides).map{case (i,s) => src"$i*-*$s"}.mkString(" + ")
  }


  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case UnrolledForeach(ens,cchain,func,iters,valids) =>
      val parent_kernel = controllerStack.head
      controllerStack.push(lhs)
      emitGlobalWireMap(src"${lhs}_II_done", "Wire(Bool())")
      emitGlobalWireMap(src"${lhs}_inhibitor", "Wire(Bool())")
      // Preallocate valid bound syms
      // if (styleOf(lhs) != StreamPipe) {
      //   allocateValids(lhs, cchain, iters, valids)
      // } else if (childrenOf(lhs).length > 0) {
      //   childrenOf(lhs).zipWithIndex.foreach { case (c, idx) =>
      //     allocateValids(lhs, cchain, iters, valids, src"_copy$c") // Must have visited func before we can properly run this method
      //   }          
      // } else {
      //   emitValidsDummy(iters, valids, src"_copy$lhs") // FIXME: Weird situation with nested stream ctrlrs, hacked quickly for tian so needs to be fixed
      // }
      emitController(lhs, Some(cchain), Some(iters.flatten)) // If this is a stream, then each child has its own ctr copy
      // if (styleOf(lhs) == MetaPipe & childrenOf(lhs).length > 1) allocateRegChains(lhs, iters.flatten, cchain) // Needed to generate these global wires before visiting children who may use them
      if (levelOf(lhs) == InnerControl) emitInhibitor(lhs, Some(cchain), None, None)

      // Console.println(src"""II of $lhs is ${iiOf(lhs)}""")
      if (iiOf(lhs) <= 1) {
        emit(src"""${swap(lhs, IIDone)} := true.B""")
      } else {
        emitGlobalModule(src"""val ${lhs}_IICtr = Module(new RedxnCtr(2 + Utils.log2Up(${swap(lhs, Retime)})));""")
        emit(src"""${swap(lhs, IIDone)} := ${lhs}_IICtr.io.output.done | ${swap(lhs, CtrTrivial)}""")
        emit(src"""${lhs}_IICtr.io.input.enable := ${swap(lhs, DatapathEn)}""")
        emit(src"""${lhs}_IICtr.io.input.stop := ${swap(lhs, Retime)}.S //${iiOf(lhs)}.S""")
        emit(src"""${lhs}_IICtr.io.input.reset := reset.toBool | ${swap(lhs, IIDone)}.D(1)""")
        emit(src"""${lhs}_IICtr.io.input.saturate := false.B""")       
      }
      if (styleOf(lhs) != StreamPipe) { 
        if (styleOf(lhs) == MetaPipe) createValidsPassMap(lhs, cchain, iters, valids)
        withSubStream(src"${lhs}", src"${parent_kernel}", levelOf(lhs) == InnerControl) {
          emit(s"// Controller Stack: ${controllerStack.tail}")
          emitParallelizedLoop(iters, cchain)
          allocateValids(lhs, cchain, iters, valids)
          if (styleOf(lhs) == MetaPipe & childrenOf(lhs).length > 1) allocateRegChains(lhs, iters.flatten, cchain) // Needed to generate these global wires before visiting children who may use them
          emitBlock(func)
        }
        emitValids(lhs, cchain, iters, valids)
      } else {
        // Not sure if these layer slicers belong at beginning or end of this else
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

        // if (childrenOf(lhs).length > 0) {
        //   childrenOf(lhs).zipWithIndex.foreach { case (c, idx) =>
        //     createValidsPassMap(lhs, cchain, iters, valids, src"_copy$c")
        //   }
        // }
        emitGlobalWire(s"// passmap: $validPassMap ")
        emitGlobalWire(s"// cchainpassmap: $cchainPassMap ")
        withSubStream(src"${lhs}", src"${parent_kernel}", levelOf(lhs) == InnerControl) {
          emit(s"// Controller Stack: ${controllerStack.tail}")
          childrenOf(lhs).zipWithIndex.foreach { case (c, idx) =>
            emitParallelizedLoop(iters, cchain, src"_copy$c")
          }
          if (styleOf(lhs) == MetaPipe & childrenOf(lhs).length > 1) allocateRegChains(lhs, iters.flatten, cchain) // Needed to generate these global wires before visiting children who may use them
          if (childrenOf(lhs).length > 0) {
            childrenOf(lhs).zipWithIndex.foreach { case (c, idx) =>
              allocateValids(lhs, cchain, iters, valids, src"_copy$c") // Must have visited func before we can properly run this method
            }          
          } else {
            emitValidsDummy(iters, valids, src"_copy$lhs") // FIXME: Weird situation with nested stream ctrlrs, hacked quickly for tian so needs to be fixed
          }
          // Register the remapping for bound syms in children
          emitBlock(func)
        }
        if (childrenOf(lhs).length > 0) {
          childrenOf(lhs).zipWithIndex.foreach { case (c, idx) =>
            emitValids(lhs, cchain, iters, valids, src"_copy$c") // Must have visited func before we can properly run this method
          }          
        }
      }
      emitChildrenCxns(lhs, Some(cchain), Some(iters.flatten))
      emitCopiedCChain(lhs)
      if (!(styleOf(lhs) == StreamPipe && childrenOf(lhs).length > 0)) {
        connectCtrTrivial(cchain)
      }
      val en = if (ens.isEmpty) "true.B" else ens.map(quote).mkString(" && ")
      emit(src"${swap(lhs, Mask)} := $en")
      controllerStack.pop()

    case UnrolledReduce(ens,cchain,accum,func,iters,valids) =>
      val parent_kernel = controllerStack.head
      controllerStack.push(lhs)
      emitGlobalWireMap(src"${lhs}_II_done", "Wire(Bool())")
      emitController(lhs, Some(cchain), Some(iters.flatten))
      // allocateValids(lhs, cchain, iters, valids)
      if (levelOf(lhs) == InnerControl) emitInhibitor(lhs, Some(cchain), None, None)
      // if (styleOf(lhs) == MetaPipe & childrenOf(lhs).length > 1) allocateRegChains(lhs, iters.flatten, cchain) // Needed to generate these global wires before visiting children who may use them
      // if (styleOf(lhs) == MetaPipe) createValidsPassMap(lhs, cchain, iters, valids)
      if (iiOf(lhs) <= 1) {
        emit(src"""${swap(lhs, IIDone)} := true.B""")
      } else {
        emitGlobalModule(src"""val ${lhs}_IICtr = Module(new RedxnCtr(2 + Utils.log2Up(${swap(lhs, Retime)})));""")
        emit(src"""${swap(lhs, IIDone)} := ${lhs}_IICtr.io.output.done | ${swap(lhs, CtrTrivial)}""")
        emit(s"""${quote(lhs)}_IICtr.io.input.enable := ${swap(lhs, DatapathEn)}""")
        emit(s"""${quote(lhs)}_IICtr.io.input.stop := ${swap(lhs, Retime)}.S""")
        emit(s"""${quote(lhs)}_IICtr.io.input.reset := reset.toBool | ${swap(lhs, IIDone)}.D(1)""")
        emit(s"""${quote(lhs)}_IICtr.io.input.saturate := false.B""")       
      }
      val dlay = bodyLatency.sum(lhs)
      accumsWithIIDlay += accum.asInstanceOf[Exp[_]]
      if (levelOf(lhs) == InnerControl) {
        if (spatialConfig.enableRetiming) {
          emitGlobalWire(src"val ${accum}_II_dlay = 0 // Hack to fix Arbitrary Lambda")
        } else {
          emitGlobalWire(src"val ${accum}_II_dlay = 0 // Hack to fix Arbitrary Lambda")        
        }
        emitGlobalWireMap(s"${quote(accum)}_wren", "Wire(Bool())")
        emit(s"${swap(quote(accum), Wren)} := (${swap(lhs, IIDone)} & ${swap(lhs, DatapathEn)} & ~${swap(lhs, Done)} & ~${swap(lhs, Inhibitor)}).D(0,rr)")
        emitGlobalWireMap(src"${accum}_resetter", "Wire(Bool())")
        val rstr = wireMap(src"${accum}_resetter")
        emit(src"$rstr := ${swap(lhs, RstEn)}")
      } else {
        if (spatialConfig.enableRetiming) {
          emitGlobalWire(src"val ${accum}_II_dlay = /*${iiOf(lhs)} +*/ 1 // un-hack to fix Arbitrary Lambda")
        } else {
          emitGlobalWire(src"val ${accum}_II_dlay = 0 // Hack to fix Arbitrary Lambda")        
        }
        emitGlobalWireMap(src"${accum}_wren", "Wire(Bool())")
        emit(src"// Used to be this, but not sure why for outer reduce: val ${accum}_resetter = Utils.delay(${swap(parentOf(lhs).get, Done)}, 2)")
        emitGlobalWireMap(src"${accum}_resetter", "Wire(Bool())")
        val rstr = wireMap(src"${accum}_resetter")
        emit(src"$rstr := ${swap(lhs, RstEn)}.D(0)")
      }
      // Create SRFF to block destructive reads after the cchain hits the max, important for retiming
      emit(src"//val ${accum}_initval = 0.U // TODO: Get real reset value.. Why is rV a tuple?")
      withSubStream(src"${lhs}", src"${parent_kernel}", levelOf(lhs) == InnerControl) {
        emit(s"// Controller Stack: ${controllerStack.tail}")
        emitParallelizedLoop(iters, cchain)
        allocateValids(lhs, cchain, iters, valids)
        if (styleOf(lhs) == MetaPipe & childrenOf(lhs).length > 1) allocateRegChains(lhs, iters.flatten, cchain) // Needed to generate these global wires before visiting children who may use them
        if (styleOf(lhs) == MetaPipe) createValidsPassMap(lhs, cchain, iters, valids)
        emitBlock(func)
      }
      if (levelOf(lhs) != InnerControl) {
        accum match { 
          case Def(_:RegNew[_]) => 
            // if (childrenOf(lhs).length == 1) {
            emitGlobalWireMap(src"${childrenOf(lhs).last}_done", "Wire(Bool())") // Risky
            emit(src"${swap(accum, Wren)} := ${swap(childrenOf(lhs).last, SM)}.io.output.done //(${swap(childrenOf(lhs).last, Done)}).D(0, rr) // TODO: Skeptical these codegen rules are correct ???")
          case Def(_:SRAMNew[_,_]) =>
            emitGlobalWireMap(src"${childrenOf(lhs).last}_done", "Wire(Bool())") // Risky
            emit(src"${swap(accum, Wren)} := ${swap(childrenOf(lhs).last, Done)} // TODO: SRAM accum is managed by SRAM write node anyway, this signal is unused")
          case Def(_:RegFileNew[_,_]) =>
            emitGlobalWireMap(src"${childrenOf(lhs).last}_done", "Wire(Bool())") // Risky
            emit(src"${swap(accum, Wren)} := ${swap(childrenOf(lhs).last, Done)} // TODO: SRAM accum is managed by SRAM write node anyway, this signal is unused")
        }
      }
      emitValids(lhs, cchain, iters, valids)
      emitChildrenCxns(lhs, Some(cchain), Some(iters.flatten))
      emitCopiedCChain(lhs)
      connectCtrTrivial(cchain)
      val en = if (ens.isEmpty) "true.B" else ens.map(quote).mkString(" && ")
      emit(src"${swap(lhs, Mask)} := $en")
      controllerStack.pop()

    case ParSRAMLoad(sram,inds,ens) =>
      val dispatch = dispatchOf(lhs, sram)
      val width = bitWidth(sram.tp.typeArguments.head)
      val rPar = inds.length
      val dims = stagedDimsOf(sram)
      disableSplit = true
      emit(s"""// Assemble multidimR vector""")
      emitGlobalWireMap(src"""${lhs}_rVec""", src"""Wire(Vec(${rPar}, new multidimR(${dims.length}, List(${constDimsOf(sram)}), ${width})))""")
      if (dispatch.toList.length == 1) {
        val k = dispatch.toList.head 
        val parent = readersOf(sram).find{_.node == lhs}.get.ctrlNode
        inds.zipWithIndex.foreach{ case (ind, i) =>
          emit(src"${swap(lhs, RVec)}($i).en := (${swap(parent, En)}).D(${symDelay(lhs)},rr) & ${ens(i)}")
          ind.zipWithIndex.foreach{ case (a, j) =>
            emit(src"""${swap(lhs, RVec)}($i).addr($j) := ${a}.raw """)
          }
        }
        val p = portsOf(lhs, sram, k).head
        emit(src"""val ${lhs}_base = ${sram}_$k.connectRPort(Vec(${swap(lhs, RVec)}.toArray), $p)""")
        // sram.tp.typeArguments.head match { 
        //   case FixPtType(s,d,f) => if (spatialNeedsFPType(sram.tp.typeArguments.head)) {
              emit(src"""val ${lhs} = Wire(${newWire(lhs.tp)})""") 
              emit(s"""(0 until ${rPar}).foreach{i => ${quote(lhs)}(i).r := ${quote(sram)}_$k.io.output.data(${quote(lhs)}_base+i) }""")
          //   } else {
          //     emit(src"""val $lhs = (0 until ${rPar}).map{i => ${sram}_$k.io.output.data(${lhs}_base+i) }""")
          //   }
          // case _ => emit(src"""val $lhs = (0 until ${rPar}).map{i => ${sram}_$k.io.output.data(${lhs}_base+i) }""")
        // }
      } else {
        emit(src"""val ${lhs} = Wire(${newWire(lhs.tp)})""")
        dispatch.zipWithIndex.foreach{ case (k,id) => 
          val parent = readersOf(sram).find{_.node == lhs}.get.ctrlNode
          emit(src"${swap(lhs, RVec)}($id).en := (${swap(parent, En)}).D(${swap(parent, Retime)}, rr) & ${ens(id)}")
          inds(id).zipWithIndex.foreach{ case (a, j) =>
            emit(src"""${swap(lhs, RVec)}($id).addr($j) := ${a}.raw """)
          }
          val p = portsOf(lhs, sram, k).head
          emit(src"""val ${lhs}_base_$k = ${sram}_$k.connectRPort(Vec(${swap(lhs, RVec)}($id)), $p) // TODO: No need to connect all rVec lanes to SRAM even though only one is needed""")
          // sram.tp.typeArguments.head match { 
          //   case FixPtType(s,d,f) => if (spatialNeedsFPType(sram.tp.typeArguments.head)) {
                emit(s"""${quote(lhs)}($id).r := ${quote(sram)}_$k.io.output.data(${quote(lhs)}_base_$k)""")
            //   } else {
            //     emit(src"""${lhs}($id) := ${sram}_$k.io.output.data(${lhs}_base_$k)""")
            //   }
            // case _ => emit(src"""${lhs}($id) := ${sram}_$k.io.output.data(${lhs}_base_$k)""")
          // }
        }
      }
      disableSplit = false

    case ParSRAMStore(sram,inds,data,ens) =>
      val dims = stagedDimsOf(sram)
      val width = bitWidth(sram.tp.typeArguments.head)
      val parent = writersOf(sram).find{_.node == lhs}.get.ctrlNode
      val enable = src"${swap(parent, DatapathEn)}"
      emit(s"""// Assemble multidimW vector""")
      emitGlobalWireMap(src"""${lhs}_wVec""", src"""Wire(Vec(${inds.indices.length}, new multidimW(${dims.length}, List(${constDimsOf(sram)}), ${width})))""")
      val datacsv = data.map{d => src"${d}.r"}.mkString(",")
      data.zipWithIndex.foreach { case (d, i) =>
        emit(src"""${swap(lhs, WVec)}($i).data := ${d}.r""")
      }
      inds.zipWithIndex.foreach{ case (ind, i) =>
        emit(src"${swap(lhs, WVec)}($i).en := ${ens(i)} & ($enable & ~${swap(parent, Inhibitor)} & ${swap(parent, IIDone)}).D(${symDelay(lhs)})")
        ind.zipWithIndex.foreach{ case (a, j) =>
          emit(src"""${swap(lhs, WVec)}($i).addr($j) := ${a}.r """)
        }
      }
      duplicatesOf(sram).zipWithIndex.foreach{ case (mem, i) =>
        emit(src"""${sram}_$i.connectWPort(${swap(lhs, WVec)}, List(${portsOf(lhs, sram, i)}))""")
      }

    case ParFIFODeq(fifo, ens) =>
      val par = ens.length
      val reader = readersOf(fifo).find{_.node == lhs}.get.ctrlNode
      emit(src"val ${lhs} = Wire(${newWire(lhs.tp)})")
      if (spatialConfig.useCheapFifos){
        val en = ens.map(quote).mkString("&")
        emit(src"""val ${lhs}_vec = ${quote(fifo)}.connectDeqPort((${swap(reader, DatapathEn)} & ~${swap(reader, Inhibitor)} & ${swap(reader, IIDone)}).D(${symDelay(lhs)}) & $en)""")  
      } else {
        val en = ens.map{i => src"$i & (${swap(reader, DatapathEn)} & ~${swap(reader, Inhibitor)} & ${swap(reader, IIDone)}).D(${symDelay(lhs)})"}
        emit(src"""val ${lhs}_vec = ${quote(fifo)}.connectDeqPort(Vec(List($en)))""")  
      }
      
      emit(src"""(0 until ${ens.length}).foreach{ i => ${lhs}(i).r := ${lhs}_vec(i) }""")

      // fifo.tp.typeArguments.head match { 
      //   case FixPtType(s,d,f) => if (spatialNeedsFPType(fifo.tp.typeArguments.head)) {
      //       emit(s"""val ${quote(lhs)} = (0 until $par).map{ i => Utils.FixedPoint($s,$d,$f,${quote(fifo)}.io.out(i)) }""")
      //     } else {
      //       emit(src"""val ${lhs} = ${fifo}.io.out""")
      //     }
      //   case _ => emit(src"""val ${lhs} = ${fifo}.io.out""")
      // }

    case ParFIFOEnq(fifo, data, ens) =>
      val par = ens.length
      val writer = writersOf(fifo).find{_.node == lhs}.get.ctrlNode
      val enabler = src"${swap(writer, DatapathEn)}"
      val datacsv = data.map{d => src"${d}.r"}.mkString(",")
      if (spatialConfig.useCheapFifos) {
        val en = ens.map(quote).mkString("&")
        emit(src"""${fifo}.connectEnqPort(Vec(List(${datacsv})), ($enabler & ~${swap(writer, Inhibitor)} & ${swap(writer, IIDone)}).D(${symDelay(lhs)}) & $en)""")  
      } else {
        val en = ens.map{i => src"$i & ($enabler & ~${swap(writer, Inhibitor)} & ${swap(writer, IIDone)}).D(${symDelay(lhs)})"}
        emit(src"""${fifo}.connectEnqPort(Vec(List(${datacsv})), Vec(List($en)))""")
      }
      

    case ParFILOPop(filo, ens) =>
      val par = ens.length
      val en = ens.map(quote).mkString("&")
      val reader = readersOf(filo).find{_.node == lhs}.get.ctrlNode
      emit(src"val ${lhs} = Wire(${newWire(lhs.tp)})")
      emit(src"""val ${lhs}_vec = ${quote(filo)}.connectPopPort((${swap(reader, DatapathEn)} & ~${swap(reader, Inhibitor)} & ${swap(reader, IIDone)}).D(${symDelay(lhs)}) & $en).reverse""")
      emit(src"""(0 until ${ens.length}).foreach{ i => ${lhs}(i).r := ${lhs}_vec(i) }""")

    case ParFILOPush(filo, data, ens) =>
      val par = ens.length
      val en = ens.map(quote).mkString("&")
      val writer = writersOf(filo).find{_.node == lhs}.get.ctrlNode
      val enabler = src"${swap(writer, DatapathEn)}"
      val datacsv = data.map{d => src"${d}.r"}.mkString(",")
      emit(src"""${filo}.connectPushPort(Vec(List(${datacsv})), ($enabler & ~${swap(writer, Inhibitor)} & ${swap(writer, IIDone)}).D(${symDelay(lhs)}) & $en)""")

    case e@ParStreamRead(strm, ens) =>
      val parent = parentOf(lhs).get
      emit(src"""val ${lhs}_rId = getStreamInLane("$strm")""")
      strm match {
        case Def(StreamInNew(bus)) => bus match {
          case VideoCamera => 
            emit(src"""val $lhs = Vec(io.stream_in_data)""")  // Ignores enable for now
            emit(src"""${swap(strm, ReadyOptions)}(${lhs}_rId) := ${swap(parent, Done)} & ${ens.mkString("&")} & (${swap(parent, DatapathEn)}).D(${swap(parent, Retime)}, rr) """)
          case SliderSwitch => 
            emit(src"""val $lhs = Vec(io.switch_stream_in_data)""")
          case _ => 
            val isAck = strm match { // TODO: Make this clean, just working quickly to fix bug for Tian
              case Def(StreamInNew(bus)) => bus match {
                case BurstAckBus => true
                case ScatterAckBus => true
                case _ => false
              }
              case _ => false
            }
            emit(src"""${swap(strm, ReadyOptions)}(${lhs}_rId) := (${ens.map{a => src"$a"}.mkString(" | ")}) & (${swap(parent, DatapathEn)} & ~${swap(parent, Inhibitor)}).D(0 /*${symDelay(lhs)}*/) // Do not delay ready because datapath includes a delayed _valid already """)
            // if (!isAck) {
            //   // emit(src"""//val $lhs = List(${ens.map{e => src"${e}"}.mkString(",")}).zipWithIndex.map{case (en, i) => ${strm}(i) }""")
              emit(src"""val $lhs = (0 until ${ens.length}).map{ i => ${strm}(i) }""")
            // } else {
            //   emit(src"""val $lhs = (0 until ${ens.length}).map{ i => ${strm}(i) }""")        
            // }


        }
      }


    case ParStreamWrite(stream, data, ens) =>
      val par = ens.length
      val parent = parentOf(lhs).get
      val datacsv = data.map{d => src"${d}"}.mkString(",")
      val en = ens.map(quote).mkString("&")

      emit(src"""val ${lhs}_wId = getStreamOutLane("$stream")*-*${ens.length}""")
      emit(src"""${swap(stream, ValidOptions)}(${lhs}_wId) := $en & (${swap(parent, DatapathEn)} & ~${swap(parent, Inhibitor)}).D(${symDelay(lhs)}) & ~${swap(parent, Done)} /*mask off double-enq for sram loads*/""")
      (0 until ens.length).map{ i => emit(src"""${swap(stream, DataOptions)}(${lhs}_wId + ${i}) := ${data(i)}""")}
      // emit(src"""${stream} := Vec(List(${datacsv}))""")

      stream match {
        case Def(StreamOutNew(bus)) => bus match {
          case VGA => 
            emitGlobalWire(src"""// EMITTING VGA GLOBAL""")
            // emitGlobalWire(src"""val ${stream} = Wire(UInt(16.W))""")
            // emitGlobalWire(src"""val converted_data = Wire(UInt(16.W))""")
            emitGlobalWireMap(src"""stream_out_startofpacket""", """Wire(Bool())""")
            emitGlobalWireMap(src"""stream_out_endofpacket""", """Wire(Bool())""")
            emit(src"""stream_out_startofpacket := Utils.risingEdge(${swap(parent, DatapathEn)})""")
            emit(src"""stream_out_endofpacket := ${swap(parent, Done)}""")
            emit(src"""// emiiting data for stream ${stream}""")
            // emit(src"""${stream} := ${data.head}""")
            // emit(src"""converted_data := ${stream}""")
            // emit(src"""${stream}_valid := ${ens.mkString("&")} & ShiftRegister(${swap(parent, DatapathEn)} & ~${swap(parent, Inhibitor)}, ${symDelay(lhs)})""")
          case LEDR =>
            // emitGlobalWire(src"""val ${stream} = Wire(UInt(32.W))""")
      //      emitGlobalWire(src"""val converted_data = Wire(UInt(32.W))""")
            // emit(src"""${stream} := $data""")
            // emit(src"""io.led_stream_out_data := ${stream}""")
          case _ =>
            // val datacsv = data.map{d => src"${d}"}.mkString(",")
            // val en = ens.map(quote).mkString("&")
            // emit(src"${stream} := Vec(List(${datacsv}))")
            // emit(src"${stream}_valid := $en & (${swap(parent, DatapathEn)} & ~${swap(parent, Inhibitor)}).D(${symDelay(lhs)}) & ~${parent}_done /*mask off double-enq for sram loads*/")
        }
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
        data.zipWithIndex.foreach { case (d, i) =>
          emit(src"${lb}_$ii.io.data_in($i) := ${d}.raw")
        }
        emit(src"""${lb}_$ii.io.w_en(0) := ${ens.map{en => src"$en"}.mkString("&")} & (${swap(parent, DatapathEn)} & ~${swap(parent, Inhibitor)}).D(${symDelay(lhs)}, rr)""")
      } else {
        val dispatch = dispatchOf(lhs, lb).toList.distinct
        if (dispatch.length > 1) { throw new Exception(src"This is an example where lb dispatch > 1. Please use as test case! (node $lhs on lb $lb)") }
        val ii = dispatch.head
        val parent = writersOf(lb).find{_.node == lhs}.get.ctrlNode
        emit(src"""val ${lb}_${ii}_transient_base = ${lb}_$ii.col_wPar*${lb}_$ii.rstride""")
        data.zipWithIndex.foreach { case (d, i) =>
          emit(src"${lb}_$ii.io.data_in(${lb}_${ii}_transient_base + $i) := ${d}.raw")
        }
        emit(src"""${lb}_$ii.io.w_en(${lb}_$ii.rstride) := ${ens.map{en => src"$en"}.mkString("&")} & (${swap(parent, DatapathEn)} & ~${swap(parent, Inhibitor)}).D(${symDelay(lhs)}, rr)""")
        emit(src"""${lb}_$ii.io.transientDone := ${parent}_done""")
        emit(src"""${lb}_$ii.io.transientSwap := ${parentOf(parent).get}_done""")
      }


    case _ => super.emitNode(lhs, rhs)
  }
}
