package spatial.codegen.pirgen

import argon.core._
import argon.nodes._

import spatial.nodes._
import spatial.utils._
import spatial.metadata._

trait PIRGenOp extends PIRCodegen {
  def isInnerReduce(lhs:Sym[_]) = dbgblk(s"isInnerReduce($lhs)"){
    if (isReduce(lhs)) {
      val ctrl = parentOf(lhs).get
      dbgs(s"reduceType=${reduceType(lhs)} ctrl=$ctrl ctrlReduceType=${reduceType(ctrl)}")
      reduceType(ctrl).nonEmpty
    } else false
  }

  def isReduce(lhs:Sym[_]) = dbgblk(s"isReduce($lhs)") {
    reduceType(lhs).nonEmpty
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = {
    nodeToOp(rhs) match {
      case Some(op) if isInnerReduce(lhs) => 
        val inputs = rhs.expInputs
        val (accumAccess::_, input::_) = inputs.partition { in => isReduceStarter(in) }
        var accumInput = input
        emit(lhs, s"ReduceAccumOp(op=$op, input=${quote(accumInput)}, accum=${quote(accumAccess)})", rhs)
      //case Some(op) if isReduce(lhs) => 
        //val (accumAccesses, inputs) = rhs.expInputs.partition { in => isReduceStarter(in) }
        //dbgs(s"accumAccesses=$accumAccesses, inputs=$inputs")
        //val (accumAccess::_, input::_) = (accumAccesses, inputs)
        //var accumInput = input
        //emit(lhs, s"AccumOp(op=$op, input=${quote(accumInput)}, accum=${quote(accumAccess)})", rhs)
      case Some(op) if inHwBlock =>
        val inputs = rhs.productIterator.toList
        emit(lhs, s"OpDef(op=$op, inputs=${inputs.map(quote)})", rhs)
      case Some(op) =>
      case None => 
        rhs match {
          case FixConvert(x) if !inHwBlock => 
          case FixConvert(x) => 
            val FixPtType(s1, i1, f1) = x.tp
            lhs.tp match {
              case tp if tp == x.tp => 
                emit(s"val ${quote(lhs)} = ${quote(x)} // $rhs (Same Type. No op)")
              case FixPtType(`s1`,`i1`,f2) =>
                emit(s"val ${quote(lhs)} = ${quote(x)} // $rhs (fraction difference. Ignored on plasticine)")
              case LongType() =>
                warn(s"Plasticine only support 32 bit wordwidth. FixConvert to LongType. Use single precision instead ${lhs.ctx}")
                dbg(s"Plasticine only support 32 bit wordwidth. FixConvert to Use single precision instead")
                emit(s"val ${quote(lhs)} = ${quote(x)} // $rhs")
              case FixPtType(s2, i2, f2) =>
                val bitWidth = i2 + f2
                if (bitWidth > 32) {
                  warn(s"Plasticine only support up to 32 bit wordwidth. FixConvert to ${lhs.tp}, bitWidth=$bitWidth ${lhs.ctx}")
                  dbg(s"Plasticine only support up to 32 bit wordwidth. FixConvert to ${lhs.tp}, bitWidth=$bitWidth ${lhs.ctx}")
                }
                /*
                 * [ s1 | i1 |   f1 ]
                 * [ s2 | i2   | f2 ]
                 * */
                val sftamt = Math.abs(i2 - i1)
                val op = if (i2 > i1) PIRFixSra else PIRFixSla
                val iMask = Array.fill(32)(0)
                val fMask = Array.fill(32)(0)
                (if (s1) (1 until i1) else (0 until i1)).foreach { i => iMask(i) = 1 }
                (i1 until 32).foreach { i => fMask(i) = 1 }
                val iMaskStr = iMask.mkString
                val fMaskStr = fMask.mkString
                emit(s"// ${quote(lhs)} = $rhs x.tp=${x.tp} {")
                //emit(LhsSym(lhs, Some("int1")), s"""OpDef(op=BitAnd, inputs=List(${quote(x)}, Const("$iMaskStr")))""", rhs)
                //emit(LhsSym(lhs, Some("int2")), s"OpDef(op=$op, inputs=List(${quote(lhs)}_int1, Const($sftamt)))", rhs)
                //emit(LhsSym(lhs, Some("frac1")), s"""OpDef(op=BitAnd, inputs=List(${quote(x)}, Const("$fMaskStr")))""", rhs)
                //emit(LhsSym(lhs, Some("frac2")), s"OpDef(op=$op, inputs=List(${quote(lhs)}_frac1, Const($sftamt)))", rhs)
                //emit(lhs, s"OpDef(op=BitOr, inputs=List(${quote(lhs)}_int2, ${quote(lhs)}_frac2))", rhs)
                emit(lhs, s"""OpDef(op=$op, inputs=List(${quote(x)}, Const("$sftamt")))""", rhs)
                emit(s"// }")
            }
          case VectorApply(vec, idx) =>
            if (idx != 0) throw new Exception(s"Expected parallelization of 1 in inner loop in PIRgen idx=$idx")
            decompose(vec).zip(decompose(lhs)).foreach { case (dvec, dlhs) =>
              emit(s"val ${quote(dlhs)} = ${quote(dvec)} // $lhs = $rhs")
            }
          case VectorSlice(vec, end, start) =>
            val mask = (List.fill(start)(0) ++ List.fill(end - start)(1) ++ List.fill(32 - end)(0)).reverse
            val strMask = mask.mkString
            val integer = Integer.parseInt(strMask, 2)
            emit(lhs, s"OpDef(op=BitAnd, inputs=List(${quote(vec)}, Const($integer)))", s"$rhs strMask=$strMask")
          case SimpleStruct(elems) => emit(s"// $lhs = $rhs")
          case DataAsBits(a) => emit(s"val $lhs = $a // $lhs = $rhs")
          case BitsAsData(a, tp) => emit(s"val $lhs = $a // $lhs = $rhs")
          case FieldApply(coll, field) =>
            val exp = lookupField(coll, field).get
            emit(s"val ${quote(lhs)} = ${quote(exp)} // $lhs = $rhs")
          case _ => super.emitNode(lhs, rhs)
        }
    }
  }
}

