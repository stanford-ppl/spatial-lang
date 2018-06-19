package spatial.codegen.pirgen

import argon.core._
import argon.nodes._
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._
import virtualized.SourceContext

import scala.collection.mutable

class PIRMemoryAnalyzer(implicit val codegen:PIRCodegen) extends PIRTraversal {
  override val name = "PIR Memory Analyzer"
  var IR = codegen.IR

  override def preprocess[S:Type](b: Block[S]): Block[S] = {
    super.preprocess(b)
  }

  override def postprocess[S:Type](b: Block[S]): Block[S] = {
    super.postprocess(b)
  }

  override protected def visit(lhs: Sym[_], rhs: Op[_]) = {
    lhs match {
      case lhs if isMem(lhs) => 
        dbgblk(s"${qdef(lhs)}") {
          dbglogs(lhs)
          markInnerDim(lhs)
          setOuterDims(lhs)
          setNumOuterBanks(lhs)
          setStaticBank(lhs)
        }
      case _ =>
    }
    super.visit(lhs, rhs)
  }

  def dbglogs(mem:Exp[_]) = {
    duplicatesOf(mem).zipWithIndex.foreach { case (inst, i) =>
      dbgblk(s"inst $i") {
        inst match {
          case BankedMemory(dims, depth, isAccum) =>
            dbgl(s"Strided Bankings:") {
              dims.zipWithIndex.foreach { case (Banking(stride, banks, isOuter), dimIdx) => 
                dbgs(s"dim $dimIdx: Banking(stride=$stride, banks=$banks, isOuter=$isOuter)")
              }
            }
          case DiagonalMemory(strides, banks, depth, isAccum) =>
            dbgs(s"Diagonal Banking, strides=$strides, banks=$banks, depth=$depth, isAccum=$isAccum")
        }
      }
    }
  }

  // If contains the inner dimension, returns the controller that parallelized the inner dimension
  def ctrlOfParInnerInd(ind:Exp[_]):Set[Exp[_]] = dbgblk(s"ctrlOfParInnerInd($ind)") {
    ind match {
      case b:Bound[_] => 
        val ctrl = ctrlOf(b).get.node
        if (extractParInnerBounds(ctrl).contains(b)) Set(ctrl) else Set()
      case ParLocalReader(_) => Set()
      case Def(d) => d.allInputs.map(ctrlOfParInnerInd).reduceOption { _ union _ }.getOrElse(Set())
      case e => Set()
    }
  }

  def extractParInnerBounds(ctrl:Exp[_]) = ctrl match {
    case ctrl if !isInnerControl(ctrl) => Nil
    case Def(UnrolledForeach(en, cchain, func, iters, valids)) => 
      val innerPar = getConstant(parFactorsOf(cchain).last).get.asInstanceOf[Int]
      dbgs(s"innerPar of ctrl=$ctrl: $innerPar")
      if (innerPar > 1) iters.last else Nil
    case Def(UnrolledReduce(en, cchain, accum, func, iters, valids)) =>
      val innerPar = getConstant(parFactorsOf(cchain).last).get.asInstanceOf[Int]
      dbgs(s"innerPar of ctrl=$ctrl: $innerPar")
      if (innerPar > 1) iters.last else Nil
    case _ => Nil
  }

  def markInnerDim(mem:Exp[_]) = dbgblk(s"markInnerDim(${qdef(mem)})") {
    val accesses = (readersOf(mem) ++ writersOf(mem)).map(_.node).toList
    val dims:List[(Int,Int,Set[Exp[_]])] = accesses.flatMap { access =>
      val instIds = getDispatches(mem, access)
      val inds:Seq[Exp[_]] = access match {
        case Def(ParLocalReader((mem, Some(inds::_), _)::_)) => inds 
        case Def(ParLocalWriter((mem, _, Some(inds::_), _)::_)) => inds
        case Def(ParLocalReader((mem, None, _)::_)) => Nil
        case Def(ParLocalWriter((mem, _, None, _)::_)) => Nil
      }
      inds.zipWithIndex.flatMap { case (ind, dim) => 
        val ctrls = ctrlOfParInnerInd(ind)
        dbgs(s"access=$access, instIds=$instIds, dim=$dim, parInnerCtrls=$ctrls")
        instIds.map { instId => (instId, dim, ctrls) }
      }
    }
    val dimMap:Map[Int,Map[Int,Set[Exp[_]]]] = dims.groupBy { case (instId, dim, ctrls) => 
      instId 
    }.map { case (instId, dims) =>
      (instId, dims.groupBy { case (instId, dim, ctrls) => dim }.map { case (dim, dims) =>
        (dim, dims.map { case (instId, dim, ctrls) => ctrls }.flatten.toSet)
      })
    }

    duplicatesOf(mem).zipWithIndex.foreach { case (inst, instId) =>
      innerDimOf((mem, instId)) = dbgblk(s"innerDimOf($mem, $instId)") {
        dimMap.get(instId).fold { // No addr calculation on the mem
          0
        } { dimCtrls => 
          val (parInnerDims, otherDims) = dimCtrls.partition { 
            case (dim, ctrls) => ctrls.nonEmpty
          }
          if (parInnerDims.size > 1) {
            error(s"More than 1 parallelized inner dimenion is not allowed in plasticine")
            error(s"These controller cannot be parallelized at the same time. ${mem.name}:")
            parInnerDims.foreach { case (dim, ctrls) =>
              error(s"dim=$dim, ctrls=${ctrls}")
            }
            throw new Exception(s"Invalid Parallelization Factor for Plasticine")
          } else if (parInnerDims.nonEmpty) {
            parInnerDims.head._1
          } else { // Pick a dim to be inner dim
            otherDims.lastOption.map { _._1 }.getOrElse(0)
          }
        }
      }
    }
  }

  def setOuterDims(mem:Exp[_]) = {
    duplicatesOf(mem).zipWithIndex.foreach { case (inst, instId) =>
      outerDimsOf((mem, instId)) = dbgblk(s"outerDimsOf($mem, $instId)") {
        val numDim = mem match {
          case Def(SRAMNew(dims)) => dims.size
          case Def(RegFileNew(dims, inits)) => dims.size
          case Def(LUTNew(dims, elems)) => dims.size
          case Def(mem) => 1
        }
        (0 until numDim).toSeq.filterNot { dim => 
          dim == innerDimOf((mem, instId))
        }
      }
    }
  }

  def setNumOuterBanks(mem:Exp[_]) = dbgblk(s"setNumOuterBanks($mem)") {
    duplicatesOf(mem).zipWithIndex.map { case (inst, instId) =>
      val numBanks = inst match {
        case inst@BankedMemory(dims, depth, isAccum) =>
          val outerDims = outerDimsOf(mem, instId) 
          outerDims.map{ dim => dims(dim).banks}.product
        case DiagonalMemory(strides, banks, depth, isAccum) =>
          banks
      }
      numOuterBanksOf((mem, instId)) = numBanks
      dbgs(s"numOuterBanksOf(instId=$instId)=$numBanks")
    }
  }

  def setStaticBank(mem:Exp[_]):Unit = {
    (readersOf(mem) ++ writersOf(mem)).map(_.node).foreach { access =>
      setStaticBank(mem, access)
    }
  }

  def setStaticBank(mem:Exp[_], access:Exp[_]):Unit = dbgblk(s"setStaticBankOf($mem, $access)") {
    val instIds = getDispatches(mem, access)
    val duplicates = duplicatesOf(mem)
    val insts = instIds.map { instId => (duplicates(instId), instId) }
    val addr = access match {
      case ParLocalReader(List((_, addr, _))) => addr
      case ParLocalWriter(List((_, _, addr, _))) => addr
    }
    insts.foreach { case (inst, instId) =>
      addr match {
        case None =>
          staticBanksOf((access, instId)) = (0 until numOuterBanksOf((mem, instId))).toList
        case Some(addr) => setStaticBank(mem, inst, instId, access, addr)
      }
    }
  }

  def setStaticBank(mem:Exp[_], inst:Memory, instId:Int, access:Exp[_], addr:Seq[Seq[Exp[_]]]):Unit = dbgblk(s"setStaticBankOf($mem, $access)") {
    inst match {
      case m@BankedMemory(dims, depth, isAccum) =>
        val inds = Seq.tabulate(dims.size) { i => addr.map { _(i) } }
        dbgs(s"addr=$addr inds=$inds")
        val outerInds = outerDimsOf(mem, instId).map { dim => (inds(dim), dims(dim), dim) }
        // A list of (bankIndex, # banks) for each outer dimension
        val bankInds = outerInds.map { case (inds, memory, dim) =>
          val vind::_ = inds
          val Banking(stride, banks, _) = memory
          dbgs(s"ctrlOf($vind)=${ctrlOf(vind)}")
          val bankInds = ctrlOf(vind) match {
            case Some((ctrl, _)) => 
              val parIdxs = itersOf(ctrl).get.map { iters => 
                (iters.indexOf(vind), iters.size)
              }.filter { _._1 >= 0 }
              dbgs(s"itersOf($ctrl)=${itersOf(ctrl)}")
              assert(parIdxs.size == 1 , s"$ctrl doesn't belong to $ctrl but ctrlOf($vind) = $ctrl!")
              val (iterIdx, iterPar) = parIdxs.head
              if (iterPar==1) {
                (0 until banks).map { b => (b, banks)}.toList
              } else {
                List((iterIdx, banks))
              }
            case None => 
              (0 until banks).map { b => (b, banks)}.toList
          }
          dbgs(s"dim=$dim banks=${bankInds}")
          bankInds
        }
        dbgs(s"bankInds=$bankInds")

        // Compute the combination of flatten bankIndex
        def indComb(inds:List[List[(Int, Int)]], prevDims:List[(Int, Int)]):List[Int] = { 
          if (inds.isEmpty) {
            val (inds, banks) = prevDims.unzip
            List(flattenND(inds, banks)); 
          } else {
            val headDim::restDims = inds 
            headDim.flatMap { bank => indComb(restDims, prevDims :+ bank) }
          }
        }

        val banks = indComb(bankInds.toList, Nil)
        dbgs(s"access=$access uses banks=$banks for inst=$inst")
        staticBanksOf((access, instId)) = banks
      case DiagonalMemory(strides, banks, depth, isAccum) =>
        //TODO
        throw new Exception(s"Plasticine doesn't support diagonal banking at the moment!")
    }
  }
}
