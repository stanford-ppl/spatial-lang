package spatial.codegen.pirgen

import argon.core._
import argon.nodes._
import spatial.analysis.SpatialTraversal
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._
import virtualized.SourceContext

import scala.collection.mutable
import scala.collection.mutable.WrappedArray
import scala.reflect.runtime.universe.{Block => _, Type => _, _}

trait PIRTraversal extends SpatialTraversal with PIRLogger with PIRStruct {

  implicit val self:PIRTraversal = this

  /*
   * Returns a single id for reader
   * */
  def getDispatches(dmem:Exp[_], daccess:Exp[_]) = {
    val mem = compose(dmem)
    val access = compose(daccess)
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

  def itersOf(pipe:Exp[_]):Option[Seq[Seq[Exp[_]]]] = pipe match {
    case Def(UnrolledForeach(en, cchain, func, iters, valids)) => Some(iters)
    case Def(UnrolledReduce(en, cchain, accum, func, iters, valids)) => Some(iters)
    case _ => None
  }

  def isConstant(x: Exp[_]):Boolean = x match {
    case Const(c) => true
    case Param(c) => true
    case Final(c) => true
    case _ => false
  }

  def getConstant(x: Exp[_]): Option[AnyVal] = x match {
    case Const(c: FixedPoint) if c.fmt.isExactInt => Some(c.toInt)
    case Const(c: FixedPoint) => Some(c.toFloat)
    case Const(c: FloatPoint) => Some(c.toFloat)
    case Const(c: Boolean) => Some(c)

    case Param(c: FixedPoint) if c.fmt.isExactInt => Some(c.toInt)
    case Param(c: FixedPoint) => Some(c.toFloat)
    case Param(c: FloatPoint) => Some(c.toFloat)
    case Param(c: Boolean) => Some(c)

    case Final(c: BigInt)  => Some(c.toInt)
    case _ => None
  }

  // returns (sym of flatten addr, List[Addr Stages])
  //def flattenNDIndices(indices: Seq[Exp[Any]], dims: Seq[Exp[Index]]):(Exp[_], List[OpStage]) = {
    //val cdims:Seq[Int] = dims.map{
      //case Exact(d) => d.toInt
      //case d => throw new Exception(s"Unable to get bound of memory size $d")
    //}
    //val strides:List[Exp[_]] = List.tabulate(dims.length){ d =>
      //if (d == dims.length - 1) int32s(1)
      //else int32s(cdims.drop(d+1).product)
    //}
    //var partialAddr: Exp[Any] = indices.last
    //var addrCompute: List[OpStage] = Nil
    //for (i <- dims.length-2 to 0 by -1) { // If dims.length <= 1 this won't run
      //val mul = OpStage(PIRFixMul, List(indices(i),strides(i)), fresh[Index])
      //val add = OpStage(PIRFixAdd, List(mul.out, partialAddr),  fresh[Index])
      //partialAddr = add.out
      //addrCompute ++= List(mul,add)
    //}
    //(partialAddr, addrCompute)
  //}

  def flattenND(inds:List[Int], dims:List[Int]):Int = { 
    if (inds.isEmpty && dims.isEmpty) 0 
    else { 
      val i::irest = inds
      val d::drest = dims
      assert(i < d && i >= 0, s"Index $i out of bound $d")
      i * drest.product + flattenND(irest, drest)
    }
  }

  def nodeToOp(node: Def): Option[PIROp] = node match {
    case Mux(_,_,_)                      => Some(PIRALUMux)
    case FixAdd(_,_)                     => Some(PIRFixAdd)
    case FixSub(_,_)                     => Some(PIRFixSub)
    case FixMul(_,_)                     => Some(PIRFixMul)
    case FixDiv(_,_)                     => Some(PIRFixDiv)
    case FixMod(_,_)                     => Some(PIRFixMod)
    case FixLt(_,_)                      => Some(PIRFixLt)
    case FixLeq(_,_)                     => Some(PIRFixLeq)
    case FixEql(_,_)                     => Some(PIRFixEql)
    case FixNeq(_,_)                     => Some(PIRFixNeq)
    case FixLsh(_,_)                     => Some(PIRFixSla)
    case FixRsh(_,_)                     => Some(PIRFixSra)
    case FixURsh(_,_)                    => Some(PIRFixUsra)
    case e: Min[_] if isFixPtType(e.mR)  => Some(PIRFixMin)
    case e: Max[_] if isFixPtType(e.mR)  => Some(PIRFixMax)
    case FixNeg(_)                       => Some(PIRFixNeg)
    case FixRandom(_)                    => Some(PIRFixRandom)
    case FixUnif()                       => Some(PIRFixUnif) //TODO random number between 0 and 1

    // Float ops currently assumed to be single op
    case FltAdd(_,_)                     => Some(PIRFltAdd)
    case FltSub(_,_)                     => Some(PIRFltSub)
    case FltMul(_,_)                     => Some(PIRFltMul)
    case FltDiv(_,_)                     => Some(PIRFltDiv)
    case FltLt(_,_)                      => Some(PIRFltLt)
    case FltLeq(_,_)                     => Some(PIRFltLeq)
    case FltEql(_,_)                     => Some(PIRFltEql)
    case FltNeq(_,_)                     => Some(PIRFltNeq)
    case FltNeg(_)                       => Some(PIRFltNeg)

    case FltAbs(_)                       => Some(PIRFltAbs)
    case FltExp(_)                       => Some(PIRFltExp)
    case FltLog(_)                       => Some(PIRFltLog)
    case FltSqrt(_)                      => Some(PIRFltSqrt)
    case e: Min[_] if isFltPtType(e.mR)  => Some(PIRFltMin)
    case e: Max[_] if isFltPtType(e.mR)  => Some(PIRFltMax)

    case Not(_)                          => Some(PIRBitNot)
    case And(_,_)                        => Some(PIRBitAnd)
    case FixAnd(_,_)                     => Some(PIRBitAnd)
    case Or(_,_)                         => Some(PIRBitOr)
    case FixOr(_,_)                      => Some(PIRBitOr)
    case FixXor(_,_)                     => Some(PIRBitXor)
    case _                               => None
  }

  def getInnerPar(n:Exp[_]):Int = n match {
    case Def(Hwblock(func,_)) => 1
    case Def(UnitPipe(en, func)) => 1
    case Def(UnrolledForeach(en, cchain, func, iters, valids)) => 
      getConstant(parFactorsOf(cchain).last).get.asInstanceOf[Int]
    case Def(UnrolledReduce(en, cchain, accum, func, iters, valids)) =>
      getConstant(parFactorsOf(cchain).last).get.asInstanceOf[Int]
    case Def(FringeDenseLoad(dram, _, dataStream)) => getInnerPar(dataStream)
    case Def(FringeDenseStore(dram, _, dataStream, _)) => getInnerPar(dataStream)
    case Def(FringeSparseLoad(dram, _, dataStream)) => getInnerPar(dataStream)
    case Def(FringeSparseStore(dram, cmdStream, _)) => getInnerPar(cmdStream)
    case Def(Switch(body, selects, cases)) => 1 // Outer Controller
    case Def(SwitchCase(body)) => 1 
    case Def(d:StreamInNew[_]) => getInnerPar(readersOf(n).head.node)
    case Def(d:StreamOutNew[_]) => getInnerPar(writersOf(n).head.node)
    case Def(d:ParSRAMStore[_]) => getInnerPar(parentOf(n).get)
    case Def(d:ParSRAMLoad[_]) => getInnerPar(parentOf(n).get)
    case Def(d:ParFIFOEnq[_]) => getInnerPar(parentOf(n).get)
    case Def(d:ParFIFODeq[_]) => getInnerPar(parentOf(n).get)
    case Def(d:ParStreamRead[_]) => getInnerPar(parentOf(n).get)
    case Def(d:ParStreamWrite[_]) => getInnerPar(parentOf(n).get)
    case Def(d:ParFILOPush[_]) => getInnerPar(parentOf(n).get)
    case Def(d:ParFILOPop[_]) => getInnerPar(parentOf(n).get)
    case Def(_:SRAMLoad[_]) => 1 
    case Def(_:SRAMStore[_]) => 1 
    case Def(_:SRAMStore[_]) => 1 
    case Def(_:FIFOEnq[_]) => 1 
    case Def(_:FIFODeq[_]) => 1 
    case Def(_:StreamRead[_]) => 1 
    case Def(_:StreamWrite[_]) => 1 
    case Def(_:FILOPush[_]) => 1 
    case Def(_:FILOPop[_]) => 1 
    case Def(_:RegWrite[_]) => 1 
    case Def(_:RegRead[_]) => 1 
    case n if isArgIn(n) | isArgOut(n) | isGetDRAMAddress(n) => 1
    case n => throw new Exception(s"Undefined getInnerPar for $n")
  }

  def isGetDRAMAddress(mem:Exp[_]) = mem match {
    case Def(_:GetDRAMAddress[_]) => true
    case _ => false
  }

  def isMem(e: Exp[_]):Boolean = {
    isReg(e) || isGetDRAMAddress(e) ||
    isStreamIn(e) || isStreamOut(e) || isFIFO(e)
    isSRAM(e) || isRegFile(e) || isLUT(e) || isLineBuffer(e)
  }

  def nIters(x: Exp[_], ignorePar: Boolean = false): Long = x match {
    case Def(CounterChainNew(ctrs)) =>
      val loopIters = ctrs.map{
        case Def(CounterNew(start,end,stride,par)) =>
          val min = boundOf.get(start).map(_.toDouble).getOrElse(0.0)
          val max = boundOf.get(end).map(_.toDouble).getOrElse(1.0)
          val step = boundOf.get(stride).map(_.toDouble).getOrElse(1.0)
          val p = boundOf.get(par).map(_.toDouble).getOrElse(1.0)
          dbg(s"nIter: bounds: min=$min, max=$max, step=$step, p=$p")

          val nIters = Math.ceil((max - min)/step)
          if (ignorePar) nIters.toLong else Math.ceil(nIters/p).toLong

        case Def(Forever()) => 0L
      }
      loopIters.fold(1L){_*_}
  }

  def runAll[S:Type](b: Block[S]): Block[S] = {
    tic
    init()
    var block = b
    block = preprocess(block)
    block = run(block)
    block = postprocess(block)
    val time = toc("s")
    dbgs(s"===== Pass ${this.name} finished in ${time}s =====")
    block
  }

  def quote(n:Any):String = n match {
    case x:Const[_] => s"Const(${getConstant(x).get})" 
    case x:Exp[_] => s"${composed.get(x).fold("") {o => s"${o}_"} }$x"
    case x:Iterable[_] => x.map(quote).toList.toString
    case n => n.toString
  }

}
