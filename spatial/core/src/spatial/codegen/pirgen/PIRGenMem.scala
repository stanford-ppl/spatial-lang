package spatial.codegen.pirgen

import argon.core._
import spatial.nodes._
import spatial.utils._
import spatial.metadata._
import spatial.aliases._

trait PIRGenMem extends PIRCodegen {

  override protected def emitFileHeader() {
    super.emitFileHeader()
    emit(s"def withAcc[T<:Memory](x:T, accum:Boolean) = { isAccum(x) = accum; x }")
    emit(s"def withBD[T<:Memory](x:T, depth:Int) = { bufferDepthOf(x) = depth; x }")
    emit(s"def withCount[T](x:T, count:Long) = { countOf(x) = Some(count); x }")
    emit(s"def withDims[T<:Memory](x:T, dims:List[Int]) = { staticDimsOf(x) = dims; x }")
    emit(s"def withBound[T<:Memory](x:T, bound:Any) = { boundOf(x) = bound; x }")
    emit(s"def withFile[T<:Memory](x:T, path:String) = { fileNameOf(x) = path; x }")
  }

  override protected def quoteOrRemap(arg: Any): String = arg match {
    case x@DefRhs(lhs:LhsMem, name, _*) =>
      var q = super.quoteOrRemap(x)
      val mem = lhs.sym
      val insts = duplicatesOf(mem)
      if (insts.nonEmpty) {
        val inst = insts(lhs.instId)
        q = s"withBD($q, ${inst.depth})"
        q = s"withAcc($q, ${inst.isAccum})"
        countOf(mem).foreach { count =>
          q = s"withCount($q, $count)"
        }
        mem match {
          case mem if isSRAM(mem) | isRegFile(mem) | isLUT(mem) => 
            q = s"withDims($q, ${constDimsOf(mem).toList})"
          case mem if isArgIn(mem) =>
            boundOf.get(mem).foreach { bound =>
              q = s"withBound($q, $bound)"
            }
          case mem if isDRAM(mem) =>
            fileNameOf(mem).foreach { fn =>
              q = s"""withFile($q, "$fn")"""
            }
          case _ =>
        }
      }
      q
    case x => super.quoteOrRemap(x)
  }

  def getInnerBank(mem:Exp[_], inst:Memory, instId:Int) = {
    val dim = innerDimOf((mem, instId))
    inst match {
      case BankedMemory(dims, depth, isAccum) =>
        dims(dim) match { case Banking(stride, banks, isOuter) =>
          // Inner loop dimension 
          val vec = spatialConfig.plasticineSpec.vec
          assert(banks<=vec, s"Plasticine only support banking <= $vec within PMU banks=$banks")
          s"Strided(banks=$banks, stride=$stride)"
        }
      case DiagonalMemory(strides, banks, depth, isAccum) =>
        throw new Exception(s"Plasticine doesn't support diagonal banking at the moment!")
    }
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = {
    rhs match {
      case SRAMNew(dims) =>
        val cdims = constDimsOf(lhs).toList
        decompose(lhs).foreach { dlhs => 
          duplicatesOf(lhs).zipWithIndex.foreach { case (inst, instId) =>
            val numOuterBanks = numOuterBanksOf((lhs, instId))
            val size = cdims.product / numOuterBanks
            (0 until numOuterBanks).map { bankId =>
              val innerBanks = getInnerBank(lhs, inst, instId)
              emit(DefRhs(LhsMem(dlhs, instId, bankId), s"SRAM", "size"->size, "banking"->innerBanks))
            }
          }
        }

      case RegFileNew(dims, inits) =>
        val cdims = constDimsOf(lhs).toList
        decompose(lhs).foreach { dlhs => 
          duplicatesOf(lhs).zipWithIndex.foreach { case (inst, instId) =>
            val sizes = constDimsOf(lhs)
            dbgs(s"sizes=$sizes")
            dbgs(s"inits=$inits")
            val numOuterBanks = numOuterBanksOf((lhs, instId))
            val size = cdims.product / numOuterBanks
            (0 until numOuterBanks).map { bankId =>
              val innerBanks = getInnerBank(lhs, inst, instId)
              emit(DefRhs(LhsMem(dlhs, instId, bankId), s"RegFile", "size"->size, "inits"->inits))
            }
          }
        }

      case LUTNew(dims, elems) =>
        val cdims = constDimsOf(lhs).toList
        val inits = elems.map { elem => getConstant(elem).get }.toList
        decompose(lhs).foreach { dlhs => 
          duplicatesOf(lhs).zipWithIndex.foreach { case (inst, instId) =>
            val numOuterBanks = numOuterBanksOf((lhs, instId))
            val size = cdims.product / numOuterBanks
            (0 until numOuterBanks).map { bankId =>
              val innerBanks = getInnerBank(lhs, inst, instId)
              emit(DefRhs(LhsMem(dlhs, instId, bankId), s"LUT", "inits"->Nil, "banking"->innerBanks))
              //inits.sliding(size=10).foreach { inits =>
                //emit(s"${quoteRef(LhsMem(dlhs, instId, bankId))}.inits = ${quoteRef(LhsMem(dlhs, instId, bankId))}.inits ++ ${inits}")
              //}
            }
          }
        }

      case RegNew(init) =>
        decompose(lhs).zip(decompose(init)).foreach { case (dlhs, dinit) => 
          duplicatesOf(lhs).zipWithIndex.foreach { case (inst, instId) =>
            emit(DefRhs(LhsMem(dlhs, instId), s"Reg", "init"->getConstant(init)))
          }
        }

      case FIFONew(size) =>
        decompose(lhs).foreach { dlhs => 
          val size = constDimsOf(lhs).product
          duplicatesOf(lhs).zipWithIndex.foreach { case (inst, instId) =>
            emit(DefRhs(LhsMem(dlhs, instId), s"FIFO", "size"->size))
          }
        }

      case ArgInNew(init) =>
        duplicatesOf(lhs).zipWithIndex.foreach { case (inst, instId) =>
          emit(DefRhs(LhsMem(lhs, instId), s"ArgIn", "init"->getConstant(init).get))
        }

      case ArgOutNew(init) =>
        duplicatesOf(lhs).zipWithIndex.foreach { case (inst, instId) =>
          emit(DefRhs(LhsMem(lhs, instId), s"ArgOut", "init"->getConstant(init).get))
        }

      case GetDRAMAddress(dram) =>
        emit(DefRhs(LhsMem(lhs,0), s"DramAddress", "dram"->dram))

      case _:StreamInNew[_] =>
        duplicatesOf(lhs).zipWithIndex.foreach { case (inst, instId) =>
          decomposed(lhs).right.get.foreach { case (field, dlhs) =>
            emit(DefRhs(LhsMem(dlhs, instId), s"StreamIn", "field"->s""""$field""""))
          }
        }

      case _:StreamOutNew[_] =>
        duplicatesOf(lhs).zipWithIndex.foreach { case (inst, instId) =>
          decomposed(lhs).right.get.foreach { case (field, dlhs) =>
            emit(DefRhs(LhsMem(dlhs, instId), s"StreamOut", "field"->s""""$field""""))
          }
        }

      case DRAMNew(dims, zero) =>
        decompose(lhs).foreach { dlhs => 
          emit(DefRhs(LhsSym(dlhs), s"DRAM", "dims"->s"""${dims.toList}"""))
        }

      case _ => super.emitNode(lhs, rhs)
    }
  }

}

