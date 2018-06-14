package spatial.codegen.pirgen

import argon.core._
import spatial.nodes._
import spatial.utils._
import spatial.metadata._

trait PIRGenMem extends PIRCodegen {
  def quote(dmem:Exp[_], instId:Int, bankId:Int) = {
    s"${dmem}_d${instId}_b$bankId"
  }

  def quote(dmem:Exp[_], instId:Int) = {
    if (duplicatesOf(compose(dmem)).size==1) s"$dmem" else s"${dmem}_d${instId}"
  }

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

  def emitMeta(name:String, mem:Memory) = {
    emit(s"isAccum($name) = ${mem.isAccum}")
    emit(s"bufferDepthOf($name) = ${mem.depth}")
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = {
    rhs match {
      case SRAMNew(dims) =>
        decompose(lhs).foreach { dlhs => 
          duplicatesOf(lhs).zipWithIndex.foreach { case (inst, instId) =>
            val size = constDimsOf(lhs).product / inst.totalBanks //TODO: should this be number of outer banks?
            val numOuterBanks = numOuterBanksOf((lhs, instId))
            (0 until numOuterBanks).map { bankId =>
              val innerBanks = getInnerBank(lhs, inst, instId)
              emit(quote(dlhs, instId, bankId), s"SRAM(size=$size, banking=$innerBanks)", s"$lhs = $rhs")
              emitMeta(quote(dlhs, instId, bankId), inst)
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
              emitMeta(quote(dlhs, instId, bankId), inst)
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
              emit(quote(dlhs, instId, bankId), s"LUT(inits=${inits}, banking=$innerBanks)", s"$lhs = $rhs")
              emitMeta(quote(dlhs, instId, bankId), inst)
            }
          }
        }

      case RegNew(init) =>
        decompose(lhs).zip(decompose(init)).foreach { case (dlhs, dinit) => 
          duplicatesOf(lhs).zipWithIndex.foreach { case (inst, instId) =>
            emit(quote(dlhs, instId), s"Reg(init=${getConstant(init)})", s"$lhs = $rhs")
            emitMeta(quote(dlhs, instId), inst)
          }
        }

      case FIFONew(size) =>
        decompose(lhs).foreach { dlhs => 
          val size = constDimsOf(lhs).product
          duplicatesOf(lhs).zipWithIndex.foreach { case (inst, instId) =>
            emit(quote(dlhs, instId), s"FIFO(size=$size)", s"$lhs = $rhs")
            emitMeta(quote(dlhs, instId), inst)
          }
        }

      case ArgInNew(init) =>
        emit(quote(lhs, 0), s"ArgIn(init=${getConstant(init).get})", rhs)
        boundOf.get(lhs).foreach { bound =>
          emit(s"boundOf(${quote(lhs, 0)}) = ${bound}")
        }

      case ArgOutNew(init) =>
        emit(quote(lhs, 0), s"ArgOut(init=${getConstant(init).get})", rhs)

      case GetDRAMAddress(dram) =>
        emit(lhs, s"DramAddress($dram)", rhs)

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

      case _ => super.emitNode(lhs, rhs)
    }
  }

}

