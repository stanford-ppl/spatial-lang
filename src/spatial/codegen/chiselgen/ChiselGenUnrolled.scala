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
      withSubStream(src"${lhs}", styleOf(lhs) == InnerPipe) {
        emitParallelizedLoop(iters, cchain)
        emitRegChains(lhs, iters.flatten)
        emitBlock(func)
      }

    case UnrolledReduce(cchain,accum,func,_,iters,valids,_) =>
      emitController(lhs, Some(cchain))
      // Set up accumulator signals
      emit(s"""val ${quote(lhs)}_redLoopCtr = Module(new RedxnCtr());""")
      emit(s"""${quote(lhs)}_redLoopCtr.io.input.enable := ${quote(lhs)}_datapath_en""")
      emit(s"""${quote(lhs)}_redLoopCtr.io.input.max := 1.U //TODO: Really calculate this""")
      emit(s"""val ${quote(lhs)}_redLoop_done = ${quote(lhs)}_redLoopCtr.io.output.done;""")
      val ctrEn = s"${quote(lhs)}_datapath_en & ${quote(lhs)}_redLoop_done"
      emit(s"""${quote(cchain)}_ctr_en := $ctrEn""")
      val rstStr = s"${quote(lhs)}_done"
      emit(src"val ${accum}_wren = $ctrEn")
      emit(src"val ${accum}_resetter = $rstStr")
      withSubStream(src"${lhs}", styleOf(lhs) == InnerPipe) {
        emitParallelizedLoop(iters, cchain)
        emitRegChains(lhs, iters.flatten)
        emitBlock(func)
      }

    case ParSRAMLoad(sram,inds) =>
      val dims = stagedDimsOf(sram)
      open(src"val $lhs = {")
      inds.indices.foreach{i =>
        emit(src"""val a$i = $sram.apply(${flattenAddress(dims, inds(i))})""")
      }
      emit(src"Array(" + inds.indices.map{i => src"a$i"}.mkString(", ") + ")")
      close("}")

    case ParSRAMStore(sram,inds,data,ens) =>
      val dims = stagedDimsOf(sram)
      open(src"val $lhs = {")
      inds.indices.foreach{i =>
        emit(src"if ($ens($i)) $sram.update(${flattenAddress(dims, inds(i))}, $data($i))")
      }
      close("}")

    case ParFIFODeq(fifo, ens, z) =>
      emit(src"val $lhs = $ens.map{en => if (en) $fifo.dequeue() else $z}")

    case ParFIFOEnq(fifo, data, ens) =>
      emit(src"val $lhs = $data.zip($ens).foreach{case (data, en) => if (en) $fifo.enqueue(data) }")

    case _ => super.emitNode(lhs, rhs)
  }
}
