package spatial.codegen.chiselgen

import argon.codegen.chiselgen.ChiselCodegen
import spatial.api.UnrolledExp
import spatial.SpatialConfig
import spatial.SpatialExp


trait ChiselGenUnrolled extends ChiselCodegen with ChiselGenController {
  val IR: SpatialExp
  import IR._


  def emitParallelizedLoop(iters: Seq[Seq[Bound[Index]]], cchain: Exp[CounterChain], suffix: String = "") = {
    val Def(CounterChainNew(counters)) = cchain

    iters.zipWithIndex.foreach{ case (is, i) =>
      if (is.size == 1) { // This level is not parallelized, so assign the iter as-is
        emit(src"${is(0)}${suffix} := ${counters(i)}${suffix}(0)");
        emitGlobal(src"val ${is(0)}${suffix} = Wire(UInt(32.W))")
      } else { // This level IS parallelized, index into the counters correctly
        is.zipWithIndex.foreach{ case (iter, j) =>
          emit(src"${iter}${suffix} := ${counters(i)}${suffix}($j)")
          emitGlobal(src"val ${iter}${suffix} = Wire(UInt(32.W))")
        }
      }
    }
  }

  def emitValids(cchain: Exp[CounterChain], iters: Seq[Seq[Bound[Index]]], valids: Seq[Seq[Bound[Bool]]], suffix: String = "") {
    valids.zip(iters).zipWithIndex.foreach{ case ((layer,count), i) =>
      layer.zip(count).foreach{ case (v, c) =>
        emit(src"val ${v}${suffix} = ${c}${suffix} < ${cchain}${suffix}_maxes(${i})")
      }
    }
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
    case UnrolledForeach(en,cchain,func,iters,valids) =>
      val parent_kernel = controllerStack.head
      controllerStack.push(lhs)
      emitController(lhs, Some(cchain), Some(iters.flatten)) // If this is a stream, then each child has its own ctr copy
      if (styleOf(lhs) != StreamPipe) { 
        emitValids(cchain, iters, valids)
        withSubStream(src"${lhs}", src"${parent_kernel}", styleOf(lhs) == InnerPipe) {
          emit(s"// Controller Stack: ${controllerStack}")
          emitParallelizedLoop(iters, cchain)
          emitBlock(func)
        }
      } else {
        childrenOf(lhs).zipWithIndex.foreach { case (c, idx) =>
          emitValids(cchain, iters, valids, src"_copy$c")
        }
        withSubStream(src"${lhs}", src"${parent_kernel}", styleOf(lhs) == InnerPipe) {
          emit(s"// Controller Stack: ${controllerStack}")
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
      controllerStack.pop()

    case UnrolledReduce(en,cchain,accum,func,_,iters,valids,rV) =>
      val parent_kernel = controllerStack.head
      controllerStack.push(lhs)
      emitController(lhs, Some(cchain), Some(iters.flatten))
      emitValids(cchain, iters, valids)
      // Set up accumulator signals
      emit(s"""val ${quote(lhs)}_redLoopCtr = Module(new RedxnCtr());""")
      emit(s"""${quote(lhs)}_redLoopCtr.io.input.enable := ${quote(lhs)}_datapath_en""")
      emit(s"""${quote(lhs)}_redLoopCtr.io.input.max := 1.U //TODO: Really calculate this""")
      emit(s"""val ${quote(lhs)}_redLoop_done = ${quote(lhs)}_redLoopCtr.io.output.done;""")
      emit(src"""${cchain}_en := ${lhs}_sm.io.output.ctr_inc""")
      if (styleOf(lhs) == InnerPipe) {
        emit(src"val ${accum}_wren = ${cchain}_en & ~ ${lhs}_done // TODO: Skeptical these codegen rules are correct")
        emit(src"val ${accum}_resetter = ${lhs}_rst_en")
      } else {
        accum match { 
          case Def(_:RegNew[_]) => 
            if (childrenOf(lhs).length == 1) {
              emit(src"val ${accum}_wren = ${childrenOf(lhs).last}_done // TODO: Skeptical these codegen rules are correct")
            } else {
              emit(src"val ${accum}_wren = ${childrenOf(lhs).dropRight(1).last}_done // TODO: Skeptical these codegen rules are correct")              
            }
          case Def(_:SRAMNew[_]) => 
            emit(src"val ${accum}_wren = ${childrenOf(lhs).last}_done // TODO: SRAM accum is managed by SRAM write node anyway, this signal is unused")
        }
        emit(src"val ${accum}_resetter = Utils.delay(${parentOf(lhs).get}_done, 2)")
      }
      emit(src"val ${accum}_initval = 0.U // TODO: Get real reset value.. Why is rV a tuple?")
      withSubStream(src"${lhs}", src"${parent_kernel}", styleOf(lhs) == InnerPipe) {
        emit(s"// Controller Stack: ${controllerStack}")
        emitParallelizedLoop(iters, cchain)
        emitBlock(func)
      }
      controllerStack.pop()

    case ParSRAMLoad(sram,inds) =>
      val dispatch = dispatchOf(lhs, sram)
      val rPar = inds.indices.length
      val dims = stagedDimsOf(sram)
      dispatch.foreach{ i => 
        emit(s"""// Assemble multidimR vector""")
        emit(src"""val ${lhs}_rVec = Wire(Vec(${rPar}, new multidimR(${dims.length}, 32)))""")
        val parent = readersOf(sram).find{_.node == lhs}.get.ctrlNode
        inds.zipWithIndex.foreach{ case (ind, i) =>
          emit(src"${lhs}_rVec($i).en := ${parent}_en")
          ind.zipWithIndex.foreach{ case (a, j) =>
            emit(src"""${lhs}_rVec($i).addr($j) := $a """)
          }
        }
        val p = portsOf(lhs, sram, i).head
        emit(src"""${sram}_$i.connectRPort(Vec(${lhs}_rVec.toArray), $p)""")
        sram.tp.typeArguments.head match { 
          case FixPtType(s,d,f) => if (hasFracBits(sram.tp.typeArguments.head)) {
              emit(s"""val ${quote(lhs)} = (0 until ${rPar}).map{i => Utils.FixedPoint($s,$d,$f, ${quote(sram)}_$i.io.output.data(${rPar}*${p}+i))}""")
            } else {
              emit(src"""val $lhs = (0 until ${rPar}).map{i => ${sram}_$i.io.output.data(${sram}_${i}.rPar*${p}+i) }""")
            }
          case _ => emit(src"""val $lhs = (0 until ${rPar}).map{i => ${sram}_$i.io.output.data(${rPar}*${p}+i) }""")
        }
        
      }

    case ParSRAMStore(sram,inds,data,ens) =>
      val dims = stagedDimsOf(sram)
      emit(s"""// Assemble multidimW vector""")
      emit(src"""val ${lhs}_wVec = Wire(Vec(${inds.indices.length}, new multidimW(${dims.length}, 32))) """)
      sram.tp.typeArguments.head match { 
        case FixPtType(s,d,f) => if (hasFracBits(sram.tp.typeArguments.head)) {
            emit(src"""${lhs}_wVec.zip($data).foreach{ case (port, dat) => port.data := dat.number }""")
          } else {
            emit(src"""${lhs}_wVec.zip($data).foreach{ case (port, dat) => port.data := dat }""")
          }
        case _ => emit(src"""${lhs}_wVec.zip($data).foreach{ case (port, dat) => port.data := dat }""")
      }
      inds.zipWithIndex.foreach{ case (ind, i) =>
        emit(src"${lhs}_wVec($i).en := ${ens}($i)")
        ind.zipWithIndex.foreach{ case (a, j) =>
          emit(src"""${lhs}_wVec($i).addr($j) := $a """)
        }
      }
      duplicatesOf(sram).zipWithIndex.foreach{ case (mem, i) => 
        val p = portsOf(lhs, sram, i).mkString(",")
        val parent = writersOf(sram).find{_.node == lhs}.get.ctrlNode
        val enabler = if (loadCtrlOf(sram).contains(parent)) src"${parent}_enq" else src"${parent}_datapath_en"
        emit(src"""${sram}_$i.connectWPort(${lhs}_wVec, ${enabler}, List(${p}))""")
      }

    case ParFIFODeq(fifo, ens, z) =>
      val reader = readersOf(fifo).head.ctrlNode  // Assuming that each fifo has a unique reader
      emit(src"""${quote(fifo)}_readEn := ${reader}_datapath_en & ${ens}.reduce{_&_}""")
      fifo.tp.typeArguments.head match { 
        case FixPtType(s,d,f) => if (hasFracBits(fifo.tp.typeArguments.head)) {
            emit(s"""val ${quote(lhs)} = (0 until ${quote(ens)}.length).map{ i => Utils.FixedPoint($s,$d,$f,${quote(fifo)}_rdata(i)) } // Ignore $z""")
          } else {
            emit(src"""val ${lhs} = ${fifo}_rdata // Ignore $z""")
          }
        case _ => emit(src"""val ${lhs} = ${fifo}_rdata // Ignore $z""")
      }

    case ParFIFOEnq(fifo, data, ens) =>
      val writer = writersOf(fifo).head.ctrlNode  
      // Check if this is a tile consumer

      val enabler = if (loadCtrlOf(fifo).contains(writer)) src"${writer}_enq" else src"${writer}_sm.io.output.ctr_inc"
      emit(src"""${fifo}_writeEn := $enabler & ${ens}.reduce{_&_}""")
      fifo.tp.typeArguments.head match { 
        case FixPtType(s,d,f) => if (hasFracBits(fifo.tp.typeArguments.head)) {
            emit(src"""${fifo}_wdata := (0 until ${ens}.length).map{ i => ${data}(i).number }""")
          } else {
            emit(src"""${fifo}_wdata := ${data}""")
          }
        case _ => emit(src"""${fifo}_wdata := ${data}""")
      }

    case e@ParStreamDeq(strm, ens, zero) =>
      val zstring = lhs.tp.typeArguments.head match {
        case FixPtType(s,d,f) => 
          if (hasFracBits(lhs.tp.typeArguments.head)) {
            val num = zero match {
              case Const(cc: BigDecimal) => cc.toInt.toString
            }
            src"Utils.FixedPoint($s,$d,$f,$num)"            
          } else {
            src"$zero"
          }
        case _ => src"$zero"
      }
      emit(src"val $lhs = $ens.zipWithIndex.map{case (en, i) => Mux(en, ${strm}_data(i), $zstring) }")

    case ParStreamEnq(strm, data, ens) =>
      val par = ens match {
        case Op(ListVector(elems)) => elems.length
        case _ => 1
      }
      emitGlobal(src"val ${strm}_data = Wire(Vec($par, UInt(32.W)))")
      emit(src"${strm}_data := $data")
      emit(src"${strm}_en := ${ens}.reduce{_&_} & ${parentOf(lhs).get}_datapath_en & ~${parentOf(lhs).get}_done /*mask off double-enq for sram loads*/")

    case _ => super.emitNode(lhs, rhs)
  }
}
