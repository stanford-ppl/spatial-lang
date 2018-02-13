package spatial.codegen.pirgen

import argon.codegen.{Codegen, FileDependencies}
import argon.core._
import argon.nodes._
import spatial.aliases._
import spatial.nodes._
import spatial.utils._
import spatial.metadata._

import scala.collection.mutable
import scala.language.postfixOps

trait PIRCodegen extends Codegen with FileDependencies with PIRLogger with PIRStruct {
  override val name = "PIR Codegen"
  override val lang: String = "pir"
  override val ext: String = "scala"

  implicit def codegen:PIRCodegen = this

  lazy val structAnalyzer = new PIRStructAnalyzer
  lazy val memoryAnalyzer = new PIRMemoryAnalyzer
  lazy val controlAnalyzer = new PIRControlAnalyzer

  val preprocessPasses = mutable.ListBuffer[PIRTraversal]()

  def reset = {
    metadatas.foreach { _.reset }
  }

  override protected def preprocess[S:Type](block: Block[S]): Block[S] = {
    reset
    preprocessPasses += structAnalyzer
    preprocessPasses += memoryAnalyzer
    preprocessPasses += controlAnalyzer

    preprocessPasses.foreach { pass => pass.runAll(block) }
    super.preprocess(block) // generateHeader
  }

  override protected def emitBlock(b: Block[_]): Unit = visitBlock(b)
  override protected def quoteConst(c: Const[_]): String = s"$c"
  override protected def quote(x: Exp[_]): String = {
    x match {
      case x if isConstant(compose(x)) => s"${super.quote(x)}$quoteCtrl"
      case x:Iterable[_] => s"${x.map(quote).toList}"
      case x => super.quote(x)
    }
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = {
    emit(s"// $lhs = $rhs TODO: Unmatched Node")
      FixUnif
    rhs.blocks.foreach(emitBlock)
  }

  override protected def emitFat(lhs: Seq[Sym[_]], rhs: Def): Unit = { }

  val controlStack = mutable.Stack[Expr]()
  def currCtrl = controlStack.top
  def inHwBlock = controlStack.nonEmpty

  def quoteCtrl = {
    if (controlStack.isEmpty) ".ctrl(top)"
    else s".ctrl($currCtrl)"
  }

  def emit(lhs:Any, rhs:Any):Unit = {
    emit(s"""val $lhs = $rhs$quoteCtrl.name("$lhs")""")
  }
  def emit(lhs:Any, rhs:Any, comment:Any):Unit = {
    emit(s"""val $lhs = $rhs.name("$lhs")$quoteCtrl // $comment""")
  }
}

trait PIRGenController extends PIRCodegen {

  def emitIters(cchain:Expr, iters:Seq[Seq[Expr]], valids:Seq[Seq[Expr]], isInnerControl:Boolean) = {
    val Def(CounterChainNew(counters)) = cchain
    counters.zip(iters.zip(valids)).foreach { case (counter, (iters, valids)) =>
      iters.zip(valids).zipWithIndex.foreach { case ((iter, valid), i) =>
        val offset = if (isInnerControl && counter == counters.last) None else Some(i)
        emit(iter, s"CounterIter($counter, $offset)")
        emit(valid, s"DummyOp()")
      }
    }
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = {
    if (isControlNode(lhs)) {
      rhs match {
        case UnrolledForeach(en, cchain, func, iters, valids) => 
          emit(lhs, s"LoopController(style=${styleOf(lhs)}, level=${levelOf(lhs)}, cchain=$cchain)", rhs)
          controlStack.push(lhs)
          emitIters(cchain, iters, valids, isInnerControl(lhs))
          emitBlock(func)
          controlStack.pop
        case UnrolledReduce(en, cchain, accum, func, iters, valids) => 
          emit(lhs, s"LoopController(style=${styleOf(lhs)}, level=${levelOf(lhs)}, cchain=$cchain)", rhs)
          controlStack.push(lhs)
          emitIters(cchain, iters, valids, isInnerControl(lhs))
          emitBlock(func)
          controlStack.pop
        case StateMachine(en, start, notDone, action, nextState, state) =>
          emit(lhs, s"UnitController(style=${styleOf(lhs)}, level=${levelOf(lhs)})", s"//TODO $rhs")
          controlStack.push(lhs)
          emit(s"// $lhs.notDone")
          emitBlock(notDone)
          emit(s"// $lhs.action")
          emitBlock(action)
          emit(s"// $lhs.nextState")
          emitBlock(nextState)
          controlStack.pop
        case UnitPipe(en, func) =>
          emit(lhs, s"UnitController(style=${styleOf(lhs)}, level=${levelOf(lhs)})", rhs)
          controlStack.push(lhs)
          emitBlock(func)
          controlStack.pop
        case Switch(body, selects, cases) =>
          emit(lhs, s"UnitController(style=${styleOf(lhs)}, level=${levelOf(lhs)})", s"//TODO $rhs")
          controlStack.push(lhs)
          cases.collect{case s: Sym[_] => stmOf(s)}.foreach(visitStm)
          controlStack.pop
        case SwitchCase(block) =>
          emit(lhs, s"UnitController(style=${styleOf(lhs)}, level=${levelOf(lhs)})", s"//TODO $rhs")
          controlStack.push(lhs)
          emitBlock(block)
          controlStack.pop
        case Hwblock(block, isForever) =>
          emit(lhs, s"UnitController(style=${styleOf(lhs)}, level=${levelOf(lhs)})", rhs)
          controlStack.push(lhs)
          emitBlock(block)
          controlStack.pop
        case _ =>
          emit(lhs, s"UnitController(style=${styleOf(lhs)}, level=${levelOf(lhs)})", s"//TODO $rhs")
          super.emitNode(lhs, rhs)
          controlStack.push(lhs)
          rhs.blocks.foreach(emitBlock)
          controlStack.pop
      }
    } else {
      super.emitNode(lhs, rhs)
    }
  }
}

trait PIRGenFringe extends PIRCodegen {
  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = {
    lhs match {
      case _ if isFringe(lhs) =>
        val children = rhs.allInputs.filter { e => isDRAM(e) || isStream(e) || isStreamOut(e) }.flatMap { e => decompose(e) }
        emit(lhs, s"FringeContainer(${children.mkString(s",")})", rhs)
      case _ => super.emitNode(lhs, rhs)
    }
  }
}

trait PIRGenCounter extends PIRCodegen {
  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = {
    rhs match {
      case CounterNew(start, end, step, par) =>
        val parInt = getConstant(par).get.asInstanceOf[Int]
        emit(lhs, s"Counter(min=${quote(start)}, max=${quote(end)}, step=${quote(step)}, par=$parInt)", rhs)
      case CounterChainNew(counters) =>
        emit(lhs, s"CounterChain(List(${counters.mkString(",")}))", rhs)
      case _ => super.emitNode(lhs, rhs)
    }
  }
}

trait PIRGenOp extends PIRCodegen {
  def isInnerReduce(lhs:Sym[_], rhs:Op[_]) = {
    val inputs = rhs.expInputs
    reduceType(lhs).isDefined && inputs.contains(isReduceStarter)
  }
  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = {
    nodeToOp(rhs) match {
      case Some(op) if isInnerReduce(lhs, rhs) => 
        val inputs = rhs.expInputs
        val (accumAccess::_, input::_) = inputs.partition { in => isReduceStarter(in) }
        var accumInput = s"$input"
        val innerPar = getInnerPar(currCtrl)
        val numReduceStages = (Math.log(innerPar) / Math.log(2)).toInt
        (0 until numReduceStages).foreach { i =>
          emit(s"${input}_$i", s"ReduceOp(op=$op, input=$accumInput)", rhs)
          accumInput = s"${input}_$i"
        }
        emit(lhs, s"AccumOp(op=$op, input=$accumInput, accum=$accumAccess)", rhs)
      case Some(op) if inHwBlock =>
        val inputs = rhs.expInputs
        emit(lhs, s"OpDef(op=$op, inputs=${inputs.map(quote)})", rhs)
      case Some(op) =>
      case None => 
        rhs match {
          case FixConvert(x) => emit(s"val $lhs = $x // $rhs")
          case VectorApply(vec, idx) =>
            if (idx != 0) throw new Exception(s"Expected parallelization of 1 in inner loop in PIRgen idx=$idx")
            decompose(vec).zip(decompose(lhs)).foreach { case (dvec, dlhs) =>
              emit(s"val $dlhs = $dvec // $lhs = $rhs")
            }
          case VectorSlice(vec, end, start) =>
            val mask = (List.fill(start)(0) ++ List.fill(end - start)(1) ++ List.fill(32 - end)(0)).reverse
            val strMask = mask.mkString
            val integer = Integer.parseInt(strMask, 2)
            emit(lhs, s"OpDef(op=BitAnd, inputs=List(${vec}, Const($integer)))", s"$rhs strMask=$strMask")
          case SimpleStruct(elems) => emit(s"// $lhs = $rhs")
          case DataAsBits(a) => emit(s"val $lhs = $a // $lhs = $rhs")
          case BitsAsData(a, tp) => emit(s"val $lhs = $a // $lhs = $rhs")
          case FieldApply(coll, field) =>
            emit(s"val $lhs = ${lookupField(coll, field).get} // $lhs = $rhs")
          case _ => super.emitNode(lhs, rhs)
        }
    }
  }
}

trait PIRGenMem extends PIRCodegen {
  def quote(dmem:Expr, instId:Int, bankId:Int) = {
    s"${dmem}_d${instId}_b$bankId"
  }

  def quote(dmem:Expr, instId:Int) = {
    if (duplicatesOf(compose(dmem)).size==1) s"$dmem" else s"${dmem}_d${instId}"
  }

  def getInnerBank(mem:Expr, inst:Memory, instId:Int) = {
    innerDimOf.get((mem, instId)).fold { s"NoBanking()" } { case (dim, ctrls) =>
      inst match {
        case BankedMemory(dims, depth, isAccum) =>
          dims(dim) match { case Banking(stride, banks, isOuter) =>
            // Inner loop dimension 
            assert(banks<=16, s"Plasticine only support banking <= 16 within PMU banks=$banks")
            s"Strided(banks=$banks, stride=$stride)"
          }
        case DiagonalMemory(strides, banks, depth, isAccum) =>
          throw new Exception(s"Plasticine doesn't support diagonal banking at the moment!")
      }
    }
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = {
    dbgs(s"emitNode ${qdef(lhs)}")
    rhs match {
      case SRAMNew(dims) =>
        decompose(lhs).foreach { dlhs => 
          duplicatesOf(lhs).zipWithIndex.foreach { case (inst, instId) =>
            val size = constDimsOf(lhs).product / inst.totalBanks //TODO: should this be number of outer banks?
            val numOuterBanks = numOuterBanksOf((lhs, instId))
            (0 until numOuterBanks).map { bankId =>
              val innerBanks = getInnerBank(lhs, inst, instId)
              emit(quote(dlhs, instId, bankId), s"SRAM(size=$size, banking=$innerBanks)", s"$lhs = $rhs")
            }
          }
        }
      case RegFileNew(dims, inits) =>
        decompose(lhs).foreach { dlhs => 
          duplicatesOf(lhs).zipWithIndex.foreach { case (inst, instId) =>
            val sizes = constDimsOf(lhs)
            dbgs(s"sizes=$sizes")
            dbgs(s"inits=$inits")
            val size = constDimsOf(lhs).product / inst.totalBanks //TODO: should this be number of outer banks?
            val numOuterBanks = numOuterBanksOf((lhs, instId))
            (0 until numOuterBanks).map { bankId =>
              val innerBanks = getInnerBank(lhs, inst, instId)
              emit(quote(dlhs, instId, bankId), s"RegFile(sizes=${quote(sizes)}, inits=$inits)", s"$lhs = $rhs banking:${innerBanks}")
            }
          }
        }
      case RegNew(init) =>
        decompose(lhs).zip(decompose(init)).foreach { case (dlhs, dinit) => 
          duplicatesOf(lhs).zipWithIndex.foreach { case (inst, instId) =>
            emit(quote(dlhs, instId), s"Reg(init=${getConstant(init).get})", s"$lhs = $rhs")
          }
        }
      case FIFONew(size) =>
        decompose(lhs).foreach { dlhs => 
          val size = constDimsOf(lhs).product
          duplicatesOf(lhs).zipWithIndex.foreach { case (inst, instId) =>
            emit(quote(dlhs, instId), s"FIFO(size=$size)", s"$lhs = $rhs")
          }
        }
      case ArgInNew(init) =>
        emit(quote(lhs, 0), s"top.argIn(init=${getConstant(init).get})", rhs)

      case ArgOutNew(init) =>
        emit(quote(lhs, 0), s"top.argOut(init=${getConstant(init).get})", rhs)

      case GetDRAMAddress(dram) =>
        emit(lhs, s"top.dramAddress($dram)", rhs)

      case _:StreamInNew[_] =>
        decomposed(lhs).right.get.foreach { case (field, dlhs) =>
          emit(quote(dlhs, 0), s"""StreamIn(field="$field")""", s"$lhs = $rhs")
        }

      case _:StreamOutNew[_] =>
        decomposed(lhs).right.get.foreach { case (field, dlhs) =>
          emit(quote(dlhs, 0), s"""StreamOut(field="$field")""", s"$lhs = $rhs")
        }

      case DRAMNew(dims, zero) =>
        decompose(lhs).foreach { dlhs => emit(dlhs, s"DRAM()", s"$lhs = $rhs") }

      // SRAMs, RegFile, LUT
      case ParLocalReader((mem, Some(addrs::_), _)::_) =>
        val instIds = getDispatches(lhs, mem)
        assert(instIds.size==1)
        val instId = instIds.head
        decompose(lhs).zip(decompose(mem)).foreach { case (dlhs, dmem) =>
          val banks = staticBanksOf((lhs, instId)).map { bankId => quote(dmem, instId, bankId) }
          emit(dlhs, s"LoadBanks($banks, ${quote(addrs)})", rhs)
        }
      case ParLocalWriter((mem, Some(value::_), Some(addrs::_), _)::_) =>
        val instIds = getDispatches(lhs, mem).toList
        decompose(lhs).zip(decompose(mem)).zip(decompose(value)).foreach { case ((dlhs, dmem), dvalue) =>
          val mems = instIds.flatMap { instId =>
            staticBanksOf((lhs, instId)).map { bankId => quote(dmem, instId, bankId) }
          }
          emit(dlhs, s"StoreBanks($mems, ${quote(addrs)}, $dvalue)", rhs)
        }

      // Reg, FIFO, Stream
      case ParLocalReader((mem, None, _)::_) =>
        val instIds = getDispatches(lhs, mem)
        assert(instIds.size==1)
        val instId = instIds.head
        decompose(lhs).zip(decompose(mem)).foreach { case (dlhs, dmem) =>
          val mem = quote(dmem, instId)
          emit(dlhs, s"LoadMem($mem, None)", rhs)
        }
      case ParLocalWriter((mem, Some(value::_), None, _)::_) =>
        val instIds = getDispatches(lhs, mem)
        decompose(lhs).zip(decompose(mem)).zip(decompose(value)).foreach { case ((dlhs, dmem), dvalue) =>
          val mems = instIds.map { instId => quote(dmem, instId) }
          emit(dlhs, s"StoreMem($mems, None, $dvalue)", rhs)
        }

      case FIFOPeek(mem) => 
        decompose(lhs).zip(decompose(mem)).foreach { case (dlhs, dmem) =>
          emit(dlhs, s"FIFOPeek(${quote(dmem)})", rhs)
        }
      case FIFOEmpty(mem) =>
        decompose(lhs).zip(decompose(mem)).foreach { case (dlhs, dmem) =>
          emit(dlhs, s"FIFOEmpty(${quote(dmem)})", rhs)
        }
      case FIFOFull(mem) => 
        decompose(lhs).zip(decompose(mem)).foreach { case (dlhs, dmem) =>
          emit(dlhs, s"FIFOFull(${quote(dmem)})", rhs)
        }
      //case FIFOAlmostEmpty(mem) =>
        //decompose(lhs).zip(decompose(mem)).foreach { case (dlhs, dmem) =>
          //emit(dlhs, s"FIFOAlmostEmpty(${quote(dmem)})", rhs)
        //}
      //case FIFOAlmostFull(mem) => 
        //decompose(lhs).zip(decompose(mem)).foreach { case (dlhs, dmem) =>
          //emit(dlhs, s"FIFOAlmostFull(${quote(dmem)})", rhs)
        //}
      case FIFONumel(mem) => 
        decompose(lhs).zip(decompose(mem)).foreach { case (dlhs, dmem) =>
          emit(dlhs, s"FIFONumel(${quote(dmem)})", rhs)
        }
      case _ => super.emitNode(lhs, rhs)
    }
  }
  def getDispatches(access:Expr, mem:Expr) = {
    val instIds = if (isStreamOut(mem) || isArgOut(mem) || isGetDRAMAddress(mem) || isArgIn(mem) || isStreamIn(mem)) {
      List(0)
    } else {
      dispatchOf(access, mem).toList
    }
    if (isReader(access)) {
      assert(instIds.size==1, 
        s"number of dispatch = ${instIds.size} for reader $access but expected to be 1")
    }
    instIds
  }
}

trait PIRGenDummy extends PIRCodegen {
  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = {
    rhs match {
      case _:ArrayNew[_] =>
      case _:ArrayApply[_] =>
      case _:ArrayZip[_, _, _] =>
      case _:ArrayReduce[_] =>
      case _:ArrayLength[_] =>
      case _:ArrayFromSeq[_] =>
      case _:ArrayMap[_,_] =>
      case _:ArrayFlatMap[_,_] =>
      case _:StringToFixPt[_, _, _] =>
      case _:MapIndices[_] =>
      case _:FixRandom[_, _, _] =>
      case _:SetArg[_] =>
      case _:GetArg[_] =>
      case _:SetMem[_] =>
      case _:GetMem[_] =>
      case _:InputArguments =>
      case _:PrintlnIf =>
      case _:PrintIf =>
      case _:StringConcat =>
      case _:ToString[_] =>
      case _:RangeForeach =>
      case _:OpenFile =>
      case _:CloseFile =>
      case _:ReadTokens =>
      case _:AssertIf =>
      case _:FixPtToFltPt[_,_,_,_,_] =>
      case _:FltPtToFixPt[_,_,_,_,_] =>
      case _:VarRegNew[_] =>
      case _:VarRegRead[_] =>
      case _:VarRegWrite[_] =>
      case _ => super.emitNode(lhs, rhs)
    }
  }
}
