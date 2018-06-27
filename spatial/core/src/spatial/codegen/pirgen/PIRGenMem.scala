package spatial.codegen.pirgen

import argon.core._
import spatial.nodes._
import spatial.utils._
import spatial.metadata._

trait PIRGenMem extends PIRCodegen {

  def getInnerBank(mem:Exp[_], inst:Memory, instId:Int) = {
    val dim = innerDimOf((mem, instId))
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

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = {
    rhs match {
      case SRAMNew(dims) =>
        decompose(lhs).foreach { dlhs => 
          duplicatesOf(lhs).zipWithIndex.foreach { case (inst, instId) =>
            val numOuterBanks = numOuterBanksOf((lhs, instId))
            val size = constDimsOf(lhs).product / numOuterBanks
            (0 until numOuterBanks).map { bankId =>
              val innerBanks = getInnerBank(lhs, inst, instId)
              emit(LhsMem(dlhs, instId, bankId), s"SRAM(size=$size, banking=$innerBanks)", s"$lhs = $rhs")
            }
          }
        }

      case RegFileNew(dims, inits) =>
        decompose(lhs).foreach { dlhs => 
          duplicatesOf(lhs).zipWithIndex.foreach { case (inst, instId) =>
            val sizes = constDimsOf(lhs)
            dbgs(s"sizes=$sizes")
            dbgs(s"inits=$inits")
            val numOuterBanks = numOuterBanksOf((lhs, instId))
            val size = constDimsOf(lhs).product / numOuterBanks
            (0 until numOuterBanks).map { bankId =>
              val innerBanks = getInnerBank(lhs, inst, instId)
              emit(LhsMem(dlhs, instId, bankId), s"RegFile(size=${quote(size)}, inits=$inits)", s"$lhs = $rhs banking:${innerBanks}")
            }
          }
        }

      case LUTNew(dims, elems) =>
        val inits = elems.map { elem => getConstant(elem).get }.toList
        decompose(lhs).foreach { dlhs => 
          duplicatesOf(lhs).zipWithIndex.foreach { case (inst, instId) =>
            val numOuterBanks = numOuterBanksOf((lhs, instId))
            (0 until numOuterBanks).map { bankId =>
              val innerBanks = getInnerBank(lhs, inst, instId)
              emit(LhsMem(dlhs, instId, bankId), s"LUT(inits=${inits}, banking=$innerBanks)", s"$lhs = $rhs")
            }
          }
        }

      case RegNew(init) =>
        decompose(lhs).zip(decompose(init)).foreach { case (dlhs, dinit) => 
          duplicatesOf(lhs).zipWithIndex.foreach { case (inst, instId) =>
            emit(LhsMem(dlhs, instId), s"Reg(init=${getConstant(init)})", s"$lhs = $rhs")
          }
        }

      case FIFONew(size) =>
        decompose(lhs).foreach { dlhs => 
          val size = constDimsOf(lhs).product
          duplicatesOf(lhs).zipWithIndex.foreach { case (inst, instId) =>
            emit(LhsMem(dlhs, instId), s"FIFO(size=$size)", s"$lhs = $rhs")
          }
        }

      case ArgInNew(init) =>
        emit(LhsMem(lhs), s"ArgIn(init=${getConstant(init).get})", rhs)
        boundOf.get(lhs).foreach { bound =>
          emit(s"boundOf(${LhsMem(lhs, 0)}) = ${bound}")
        }

      case ArgOutNew(init) =>
        emit(LhsMem(lhs), s"ArgOut(init=${getConstant(init).get})", rhs)

      case GetDRAMAddress(dram) =>
        emit(lhs, s"DramAddress($dram)", rhs)

      case _:StreamInNew[_] =>
        decomposed(lhs).right.get.foreach { case (field, dlhs) =>
          emit(LhsMem(dlhs), s"""StreamIn(field="$field")""", s"$lhs = $rhs")
        }

      case _:StreamOutNew[_] =>
        decomposed(lhs).right.get.foreach { case (field, dlhs) =>
          emit(LhsMem(dlhs), s"""StreamOut(field="$field")""", s"$lhs = $rhs")
        }

      case DRAMNew(dims, zero) =>
        decompose(lhs).foreach { dlhs => emit(dlhs, s"DRAM(dims=${dims.toList})", s"$lhs = $rhs") }

      case _ => super.emitNode(lhs, rhs)
    }
  }

}

