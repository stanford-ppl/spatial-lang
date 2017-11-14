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
          markInnerDim(lhs)
          setOuterDims(lhs)
          setNumOuterBanks(lhs)
          setStaticBank(lhs)
        }
      case _ =>
    }
    super.visit(lhs, rhs)
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
      dbgblk(s"markInnerDim(access=$access)") {
        val inds:Seq[Expr] = access match {
          case Def(ParLocalReader((mem, Some(inds::_), _)::_)) => inds 
          case Def(ParLocalWriter((mem, _, Some(inds::_), _)::_)) => inds
          case Def(ParLocalReader((mem, None, _)::_)) => Nil
          case Def(ParLocalWriter((mem, _, None, _)::_)) => Nil
        }
        if (inds.isEmpty) { //FIFO
          innerDimOf(mem) = 0
          dbgs(s"innerDim = 0")
        }
        inds.zipWithIndex.foreach { case (ind, dim) =>
          if (containsInnerInd(ind)) {
            dbgs(s"innerDim = $dim")
            innerDimOf(mem) = dim
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
    outerDimsOf(mem) = (0 until numDim).toSeq.filterNot { _ == innerDimOf(mem) }
  }

  def setNumOuterBanks(mem:Expr) = numOuterBanksOf(mem) = dbgblk(s"setNumOuterBanks($mem)") {
    duplicatesOf(mem).zipWithIndex.map { case (m, i) =>
      m match {
        case m@BankedMemory(dims, depth, isAccum) =>
          dbgs(s"BankedMemory # banks:${dims.map { 
            case Banking(strides, banks, _) => s"(strides=$strides, banks=$banks)"
          }.mkString(",")}")
          val outerDims = outerDimsOf(mem) 
          outerDims.map{ dim => dims(dim).banks}.product
        case DiagonalMemory(strides, banks, depth, isAccum) =>
          banks
      }
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
    }.map { _._1 }
    val addr = access match {
      case ParLocalReader(List((_, Some(addr), _))) => addr
      case ParLocalWriter(List((_, _, Some(addr), _))) => addr
    }
    insts.flatMap { inst =>
      inst match {
        case m@BankedMemory(dims, depth, isAccum) =>
          val inds = Seq.tabulate(dims.size) { i => addr.map { _(i) } }
          dbgs(s"addr=$addr inds=$inds")
          dbgs(s"BankedMemory # banks:${dims.map { 
            case Banking(strides, banks, _) => s"(strides=$strides, banks=$banks)"
          }.mkString(",")}")
          val outerInds = outerDimsOf(mem).map { dim => (inds(dim), dims(dim), dim) }
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
