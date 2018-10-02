package spatial.codegen.pirgen

import argon.core._
import spatial.nodes._
import spatial.utils._
import spatial.metadata._

trait PIRGenAccess extends PIRCodegen with PIRGenMem {

  def getLhses(lhs:Sym[_]):Seq[Lhs] = lhs match {
    case Def(ParLocalWriter((mem, Some(value::_), None, _)::_)) =>
      val instIds = getDispatches(mem, lhs)
      decompose(lhs).zip(decompose(mem)).flatMap { case (dlhs, dmem) =>
        instIds.map { instId =>
          LhsSym(dlhs, Some(s"${LhsMem(dmem, instId)}"))
        }
      }
    case _ => decompose(lhs).map { dlhs => LhsSym(dlhs) }
  }

  override protected def emitFileHeader() {
    super.emitFileHeader()
    emit(s"def withDeps(x:PIRNode, deps:List[PIRNode]) = { antiDepsOf(x) = deps; x }")
  }

  override protected def quoteOrRemap(arg: Any): String = arg match {
    case x@DefRhs(lhs, name, _*) if isAccess(lhs.sym) =>
      var q = super.quoteOrRemap(x)
      if (depsOf(lhs.sym).nonEmpty) {// anti dependency
        val deps = depsOf(lhs.sym).filter { e => isAccess(e) }.toList.flatMap { e =>
          getLhses(e.asInstanceOf[Sym[_]])
        }
        if (deps.nonEmpty) {
          q = s"withDeps($q, ${quoteRef(deps)})"
        }
      }
      q
    case x => super.quoteOrRemap(x)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = {
    rhs match {
      // SRAMs, RegFile, LUT
      case ParLocalReader((mem, Some(addrs::_), _)::_) =>
        val instId::Nil = getDispatches(mem, lhs)
        decompose(lhs).zip(decompose(mem)).foreach { case (dlhs, dmem) =>
          val banks = staticBanksOf((lhs, instId)).map { bankId => LhsMem(dmem, instId, bankId) }
          emit(DefRhs(dlhs, "LoadBanks", banks, addrs))
        }
      case ParLocalWriter((mem, Some(value::_), Some(addrs::_), _)::_) =>
        val instIds = getDispatches(mem, lhs).toList
        decompose(lhs).zip(decompose(mem)).zip(decompose(value)).foreach { case ((dlhs, dmem), dvalue) =>
          val mems = instIds.map { instId =>
            staticBanksOf((lhs, instId)).map { bankId => LhsMem(dmem, instId, bankId) }.toList
          }
          emit(DefRhs(dlhs, "StoreBanks", mems, addrs, dvalue))
        }

      // Reg, FIFO, Stream
      case ParLocalReader((mem, None, _)::_) =>
        val instId::Nil = getDispatches(mem, lhs)
        decompose(lhs).zip(decompose(mem)).foreach { case (dlhs, dmem) =>
          val mem = LhsMem(dmem, instId)
          emit(DefRhs(dlhs, "ReadMem", mem))
        }
      case ParLocalWriter((mem, Some(value::_), None, _)::_) =>
        val instIds = getDispatches(mem, lhs)
        decompose(lhs).zip(decompose(mem)).zip(decompose(value)).foreach { case ((dlhs, dmem), dvalue) =>
          instIds.foreach { instId =>
            emit(DefRhs(LhsSym(dlhs, Some(src"${LhsMem(dmem, instId)}")), "WriteMem", LhsMem(dmem, instId), dvalue))
          }
        }

      case FIFOPeek(mem) => 
        decompose(lhs).zip(decompose(mem)).foreach { case (dlhs, dmem) =>
          emit(DefRhs(dlhs, "FIFOPeek", LhsMem(dmem, 0)))
        }
      case FIFOEmpty(mem) =>
        decompose(lhs).zip(decompose(mem)).foreach { case (dlhs, dmem) =>
          emit(DefRhs(dlhs, "FIFOEmpty", LhsMem(dmem, 0)))
        }
      case FIFOFull(mem) => 
        decompose(lhs).zip(decompose(mem)).foreach { case (dlhs, dmem) =>
          emit(DefRhs(dlhs, "FIFOFull", LhsMem(dmem, 0)))
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
          emit(DefRhs(dlhs, "FIFONumel", LhsMem(dmem, 0)))
        }
      case _ => super.emitNode(lhs, rhs)
    }
  }

}

