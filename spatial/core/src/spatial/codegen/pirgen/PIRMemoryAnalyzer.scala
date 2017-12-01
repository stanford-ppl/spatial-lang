package spatial.codegen.pirgen

import argon.core._
import argon.nodes._
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._
import org.virtualized.SourceContext

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
      case lhs if isRemoteMem(lhs) =>
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

  def dbglogs(mem:Expr) = {
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

  def containsInnerInd(ind:Expr):Boolean = dbgblk(s"containsInnerInd($ind)") {
    ind match {
      case b:Bound[_] => 
        val ctrl = ctrlOf(ind).get.node
        extractInnerBounds(ctrl).contains(b)
      case Def(d) => d.allInputs.exists(containsInnerInd)
      case e => false
    }
  }

  //TODO: check parallelization factor here. Only parallel inner loop bound matters
  def extractInnerBounds(ctrl:Expr) = ctrl match {
      case ctrl if !isInnerControl(ctrl) => Nil
      case Def(UnrolledForeach(en, cchain, func, iters, valids)) => 
        iters.last
      case Def(UnrolledReduce(en, cchain, accum, func, iters, valids)) =>
        iters.last
      case _ => Nil
  }

  def markInnerDim(mem:Expr) = {
    (readersOf(mem) ++ writersOf(mem)).map(_.node).foreach { access =>
      val instIds = dispatchOf(access, mem)
      dbgblk(s"markInnerDim(access=$access, dispatch=$instIds)") {
        val inds:Seq[Expr] = access match {
          case Def(ParLocalReader((mem, Some(inds::_), _)::_)) => inds 
          case Def(ParLocalWriter((mem, _, Some(inds::_), _)::_)) => inds
          case Def(ParLocalReader((mem, None, _)::_)) => Nil
          case Def(ParLocalWriter((mem, _, None, _)::_)) => Nil
        }
        if (inds.isEmpty) { //FIFO
          instIds.foreach { instId => 
            innerDimOf((mem, instId)) = 0
            dbgs(s"innerDim(instId=$instId) = 0 (FIFO)")
          }
        }
        inds.zipWithIndex.foreach { case (ind, dim) =>
          if (containsInnerInd(ind)) {
            instIds.foreach { instId => 
              innerDimOf((mem, instId)) = dim
              dbgs(s"innerDim(instId=$instId) = $dim")
            }
          }
        }
      }
    }
  }

  def setOuterDims(mem:Expr) = dbgblk(s"setOuterDims") {
    val numDim = mem match {
      case Def(SRAMNew(dims)) => dims.size
      case Def(FIFONew(size)) => 1
    }
    duplicatesOf(mem).zipWithIndex.foreach { case (inst, instId) =>
      outerDimsOf((mem, instId)) = (0 until numDim).toSeq.filterNot { dim => 
        innerDimOf.get((mem, instId)).fold(false){ dim == _ }
      }
    }
  }

  def setNumOuterBanks(mem:Expr) = dbgblk(s"setNumOuterBanks($mem)") {
    duplicatesOf(mem).zipWithIndex.map { case (m, instId) =>
      val numBanks = m match {
        case m@BankedMemory(dims, depth, isAccum) =>
          val outerDims = outerDimsOf((mem, instId)) 
          outerDims.map{ dim => dims(dim).banks}.product
        case DiagonalMemory(strides, banks, depth, isAccum) =>
          banks
      }
      numOuterBanksOf((mem, instId)) = numBanks
      dbgs(s"numOuterBanksOf(instId=$instId)=$numBanks")
    }
  }

  def setStaticBank(mem:Expr):Unit = {
    (readersOf(mem) ++ writersOf(mem)).map(_.node).foreach { access =>
      if (isFIFO(mem)) staticBanksOf(access) = Seq(0)
      else setStaticBank(mem, access)
    }
  }

  def setStaticBank(mem:Expr, access:Expr):Unit = staticBanksOf(access) = dbgblk(s"setStaticBankOf($mem, $access)") {
    val instIds = getDispatches(mem, access)
    val insts = duplicatesOf(mem).zipWithIndex.filter { case (inst, instId) =>
      instIds.contains(instId)
    }
    val addr = access match {
      case ParLocalReader(List((_, Some(addr), _))) => addr
      case ParLocalWriter(List((_, _, Some(addr), _))) => addr
    }
    insts.flatMap { case (inst, instId) =>
      inst match {
        case m@BankedMemory(dims, depth, isAccum) =>
          val inds = Seq.tabulate(dims.size) { i => addr.map { _(i) } }
          dbgs(s"addr=$addr inds=$inds")
          val outerInds = outerDimsOf((mem, instId)).map { dim => (inds(dim), dims(dim), dim) }
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
          banks
        case DiagonalMemory(strides, banks, depth, isAccum) =>
          //TODO
          throw new Exception(s"Plasticine doesn't support diagonal banking at the moment!")
      }
    }
  }
}
