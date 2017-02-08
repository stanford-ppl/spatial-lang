package spatial.codegen.chiselgen

import argon.codegen.chiselgen.ChiselCodegen
import spatial.api.UnrolledExp
import spatial.SpatialConfig
import spatial.SpatialExp


trait ChiselGenUnrolled extends ChiselCodegen with ChiselGenController {
  val IR: SpatialExp
  import IR._


  def emitParallelizedLoop(iters: Seq[Seq[Bound[Index]]], cchain: Exp[CounterChain]) = {
    val Def(CounterChainNew(counters)) = cchain

    iters.zipWithIndex.foreach{ case (is, i) =>
      if (is.size == 1) { // This level is not parallelized, so assign the iter as-is
        emit(src"${is(0)} := ${counters(i)}(0)");
        emitGlobal(src"val ${is(0)} = Wire(UInt(32.W))")
      } else { // This level IS parallelized, index into the counters correctly
        is.zipWithIndex.foreach{ case (iter, j) =>
          emit(src"${iter} := ${counters(i)}($j)")
          emitGlobal(src"val ${iter} = Wire(UInt(32.W))")
        }
      }
    }
  }

  def emitRegChains(controller: Sym[Any], inds:Seq[Bound[Index]]) = {
    styleOf(controller) match {
      case MetaPipe =>
        val stages = childrenOf(controller)
        inds.foreach { idx =>
          emit(src"""val ${idx}_chain = Module(new NBufFF(${stages.size}, 32))""")
          stages.zipWithIndex.foreach{ case (s, i) =>
            emit(src"""${idx}_chain.io.sEn($i) := ${s}_en; ${idx}_chain.io.sDone($i) := ${s}_done""")
          }
          emit(src"""${idx}_chain.write(${idx}, ${stages(0)}_done, false.B, 0) // TODO: Maybe wren should be tied to en and not done?""")
        }
      case _ =>
    }
  }

  def emitValids(cchain: Exp[CounterChain], iters: Seq[Seq[Bound[Index]]], valids: Seq[Seq[Bound[Bool]]]) {
    valids.zip(iters).zipWithIndex.map { case ((layer,count), i) =>
      layer.zip(count).map { case (v, c) =>
        emit(src"val ${v} = ${c} < ${cchain}_maxes(${i})")
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
    case UnrolledForeach(cchain,func,iters,valids) =>
      emitController(lhs, Some(cchain))
      emitValids(cchain, iters, valids)
      withSubStream(src"${lhs}", styleOf(lhs) == InnerPipe) {
        emitParallelizedLoop(iters, cchain)
        emitRegChains(lhs, iters.flatten)
        emitBlock(func)
      }

    case UnrolledReduce(cchain,accum,func,_,iters,valids,rV) =>
      emitController(lhs, Some(cchain))
      emitValids(cchain, iters, valids)
      // Set up accumulator signals
      emit(s"""val ${quote(lhs)}_redLoopCtr = Module(new RedxnCtr());""")
      emit(s"""${quote(lhs)}_redLoopCtr.io.input.enable := ${quote(lhs)}_datapath_en""")
      emit(s"""${quote(lhs)}_redLoopCtr.io.input.max := 1.U //TODO: Really calculate this""")
      emit(s"""val ${quote(lhs)}_redLoop_done = ${quote(lhs)}_redLoopCtr.io.output.done;""")
      emit(src"""${cchain}_ctr_en := ${lhs}_datapath_en & ${lhs}_redLoop_done""")
      emit(src"val ${accum}_wren = ${cchain}_ctr_en & ~ ${lhs}_done")
      emit(src"val ${accum}_resetter = ${lhs}_rst_en")
      emit(src"val ${accum}_initval = 0.U // TODO: Get real reset value.. Why is rV a tuple?")
      withSubStream(src"${lhs}", styleOf(lhs) == InnerPipe) {
        emitParallelizedLoop(iters, cchain)
        emitRegChains(lhs, iters.flatten)
        emitBlock(func)
      }

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
        emit(src"""val $lhs = ${sram}_$i.io.output.data(${rPar}*$p)""")
      }

    case ParSRAMStore(sram,inds,data,ens) =>
      val dims = stagedDimsOf(sram)
      emit(s"""// Assemble multidimW vector""")
      emit(src"""val ${lhs}_wVec = Wire(Vec(${inds.indices.length}, new multidimW(${dims.length}, 32))) """)
      emit(src"""${lhs}_wVec.zip($data).foreach{ case (port, dat) => port.data := dat }""")
      inds.zipWithIndex.foreach{ case (ind, i) =>
        emit(src"${lhs}_wVec($i).en := ${ens}($i)")
        ind.zipWithIndex.foreach{ case (a, j) =>
          emit(src"""${lhs}_wVec($i).addr($j) := $a """)
        }
      }
      duplicatesOf(sram).zipWithIndex.foreach{ case (mem, i) => 
        val p = portsOf(lhs, sram, i).mkString(",")
        val parent = writersOf(sram).find{_.node == lhs}.get.ctrlNode
        emit(src"""${sram}_$i.connectWPort(${lhs}_wVec, ${parent}_en, List(${p}))""")
      }

    case ParFIFODeq(fifo, ens, z) =>
      val reader = readersOf(fifo).head.ctrlNode  // Assuming that each fifo has a unique reader
      emit(src"""${quote(fifo)}_readEn := ${reader}_sm.io.output.ctr_inc // Ignore $ens""")
      emit(src"""val ${lhs} = ${fifo}_rdata // Ignore $z""")

    case ParFIFOEnq(fifo, data, ens) =>
      val writer = writersOf(fifo).head.ctrlNode  // Not using 'en' or 'shuffle'
      emit(src"""${fifo}_writeEn := ${writer}_sm.io.output.ctr_inc // Ignore $ens""")
      emit(src"""${fifo}_wdata := ${data}""")

    case _ => super.emitNode(lhs, rhs)
  }
}
