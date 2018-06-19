package spatial.codegen.pirgen

import argon.core._
import spatial.nodes._
import spatial.utils._
import spatial.metadata._

trait PIRGenAccess extends PIRCodegen with PIRGenMem {

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = {
    rhs match {
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
        decompose(lhs).foreach { dlhs => emit(dlhs, s"DRAM()", s"$lhs = $rhs") }

      // SRAMs, RegFile, LUT
      case ParLocalReader((mem, Some(addrs::_), _)::_) =>
        val instId::Nil = getDispatches(mem, lhs)
        decompose(lhs).zip(decompose(mem)).foreach { case (dlhs, dmem) =>
          val banks = staticBanksOf((lhs, instId)).map { bankId => LhsMem(dmem, instId, bankId) }
          emit(dlhs, s"LoadBanks($banks, ${quote(addrs)})", rhs)
        }
      case ParLocalWriter((mem, Some(value::_), Some(addrs::_), _)::_) =>
        val instIds = getDispatches(mem, lhs).toList
        decompose(lhs).zip(decompose(mem)).zip(decompose(value)).foreach { case ((dlhs, dmem), dvalue) =>
          val mems = instIds.flatMap { instId =>
            staticBanksOf((lhs, instId)).map { bankId => LhsMem(dmem, instId, bankId) }
          }
          emit(dlhs, s"StoreBanks($mems, ${quote(addrs)}, ${quote(dvalue)})", rhs)
        }

      // Reg, FIFO, Stream
      case ParLocalReader((mem, None, _)::_) =>
        val instId::Nil = getDispatches(mem, lhs)
        decompose(lhs).zip(decompose(mem)).foreach { case (dlhs, dmem) =>
          val mem = LhsMem(dmem, instId)
          emit(dlhs, s"ReadMem($mem)", rhs)
        }
      case ParLocalWriter((mem, Some(value::_), None, _)::_) =>
        val instIds = getDispatches(mem, lhs)
        decompose(lhs).zip(decompose(mem)).zip(decompose(value)).foreach { case ((dlhs, dmem), dvalue) =>
          val mems = instIds.map { instId => LhsMem(dmem, instId) }
          mems.foreach { mem => emit(LhsSym(dlhs, Some(s"$mem")), s"WriteMem($mem, ${quote(dvalue)})", rhs) }
        }

      case FIFOPeek(mem) => 
        decompose(lhs).zip(decompose(mem)).foreach { case (dlhs, dmem) =>
          emit(dlhs, s"FIFOPeek(${LhsMem(dmem)})", rhs)
        }
      case FIFOEmpty(mem) =>
        decompose(lhs).zip(decompose(mem)).foreach { case (dlhs, dmem) =>
          emit(dlhs, s"FIFOEmpty(${LhsMem(dmem)})", rhs)
        }
      case FIFOFull(mem) => 
        decompose(lhs).zip(decompose(mem)).foreach { case (dlhs, dmem) =>
          emit(dlhs, s"FIFOFull(${LhsMem(dmem)})", rhs)
        }
      //case FIFOAlmostEmpty(mem) =>
        //decompose(lhs).zip(decompose(mem)).foreach { case (dlhs, dmem) =>
          //emit(dlhs, s"FIFOAlmostEmpty(${LhsMem(dmem)})", rhs)
        //}
      //case FIFOAlmostFull(mem) => 
        //decompose(lhs).zip(decompose(mem)).foreach { case (dlhs, dmem) =>
          //emit(dlhs, s"FIFOAlmostFull(${LhsMem(dmem)})", rhs)
        //}
      case FIFONumel(mem) => 
        decompose(lhs).zip(decompose(mem)).foreach { case (dlhs, dmem) =>
          emit(dlhs, s"FIFONumel(${LhsMem(dmem)})", rhs)
        }
      case _ => super.emitNode(lhs, rhs)
    }
  }

}

