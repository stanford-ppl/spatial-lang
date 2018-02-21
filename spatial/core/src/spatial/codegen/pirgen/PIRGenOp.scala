package spatial.codegen.pirgen

import argon.core._
import argon.nodes._

import spatial.nodes._
import spatial.utils._
import spatial.metadata._

trait PIRGenOp extends PIRCodegen {
  def isInnerReduce(lhs:Sym[_], rhs:Op[_]) = dbgblk(s"isInnerReduce($lhs)"){
    val inputs = rhs.expInputs
    dbgs(s"reduceType=${reduceType(lhs)} inputs=${inputs}")
    reduceType(lhs).isDefined && inputs.exists(in => isReduceStarter(in))
  }
  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = {
    nodeToOp(rhs) match {
      case Some(op) if isInnerReduce(lhs, rhs) => 
        val inputs = rhs.expInputs
        val (accumAccess::_, input::_) = inputs.partition { in => isReduceStarter(in) }
        var accumInput = s"$input"
        emit(lhs, s"ReduceAccumOp(op=$op, input=$accumInput, accum=$accumAccess)", rhs)
      case Some(op) if inHwBlock =>
        val inputs = rhs.productIterator.toList
        emit(lhs, s"OpDef(op=$op, inputs=${inputs.map(quote)})", rhs)
      case Some(op) =>
      case None => 
        rhs match {
          case FixConvert(x) => 
            val FixPtType(s1, i1, f1) = x.tp
            lhs.tp match {
              case FixPtType(`s1`,`i1`,f2) =>
                emit(s"val $lhs = $x // $rhs")
              case FixPtType(s2,`i1`,f2) =>
                emit(s"val $lhs = $x // $rhs unsigned <-> signed.") //TODO: hardware support?
              case LongType() =>
                warn(s"Plasticine only support 32 bit wordwidth. FixConvert to LongType. Use single precision instead")
                dbg(s"Plasticine only support 32 bit wordwidth. FixConvert to Use single precision instead")
                emit(s"val $lhs = $x // $rhs")
              case FixPtType(s2, i2, f2) =>
                val bitWidth = if (s2) i2 + f2 + 1 else i2 + f2
                if (bitWidth != 32) {
                  warn(s"Plasticine only support 32 bit wordwidth. FixConvert to ${lhs.tp}")
                  dbg(s"Plasticine only support 32 bit wordwidth. FixConvert to ${lhs.tp}")
                }
                /*
                 * [ s1 | i1 |   f1 ]
                 * [ s2 | i2   | f2 ]
                 * */
                val sftamt = Math.abs(i2 - i1)
                val op = if (i2 > i1) FixRsh else FixLsh
                val iMask = Array.fill(32)(0)
                val fMask = Array.fill(32)(0)
                (if (s1) (1 until (i1+1)) else (0 until i1)).foreach { i => iMask(i) = 1 }
                (if (s1) ((i1+1) until 32) else (i1 until 32)).foreach { i => fMask(i) = 1 }
                val iMaskStr = iMask.mkString
                val iMaskInt = Integer.parseInt(iMaskStr)
                val fMaskStr = fMask.mkString
                val fMaskInt = Integer.parseInt(fMaskStr)
                emit(s"// $lhs = $rhs x.tp=${x.tp} {")
                emit(s"${quote(lhs)}_int1", s"OpDef(op=FixAnd, inputs=List(${quote(x)}, Const($iMaskInt)))")
                emit(s"${quote(lhs)}_int2", s"OpDef(op=$op, inputs=List(${quote(lhs)}_int1, Const($sftamt)))")
                emit(s"${quote(lhs)}_frac1", s"OpDef(op=FixAnd, inputs=List(${quote(x)}, Const($fMaskInt)))")
                emit(s"${quote(lhs)}_frac2", s"OpDef(op=$op, inputs=List(${quote(lhs)}_frac1, Const($sftamt)))")
                emit(s"${quote(lhs)}", s"OpDef(op=FixOr, inputs=List(${quote(lhs)}_int2, ${quote(lhs)}_frac2))")
                emit(s"// }")
            }
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

