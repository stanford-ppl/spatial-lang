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
        emit(lhs, src"ReduceAccumOp(op=$op, input=${accumInput}, accum=${accumAccess})", rhs)
      //case Some(op) if isReduce(lhs) => 
        //val (accumAccesses, inputs) = rhs.expInputs.partition { in => isReduceStarter(in) }
        //dbgs(s"accumAccesses=$accumAccesses, inputs=$inputs")
        //val (accumAccess::_, input::_) = (accumAccesses, inputs)
        //var accumInput = input
        //emit(lhs, src"AccumOp(op=$op, input=${accumInput}, accum=${accumAccess})", rhs)
      case Some(op) if inHwBlock =>
        val inputs = rhs.productIterator.toList
        emit(lhs, s"OpDef(op=$op, inputs=${inputs})", rhs)
      case Some(op) =>
      case None => 
        rhs match {
          case FixConvert(x) if !inHwBlock => 
          case FixConvert(x) => 
            val FixPtType(s1, i1, f1) = x.tp
            lhs.tp match {
              case tp if tp == x.tp => 
                alias(lhs, x, s"$rhs (Same Type. No op)")
              case FixPtType(`s1`,`i1`,f2) =>
                alias(lhs, x, s"$rhs (fraction difference. Ignored on plasticine)")
              case LongType() =>
                warn(s"Plasticine only support 32 bit wordwidth. FixConvert to LongType. Use single precision instead ${lhs.ctx}")
                dbg(s"Plasticine only support 32 bit wordwidth. FixConvert to Use single precision instead")
                alias(lhs, x,rhs)
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
                emit(src"// ${lhs} = $rhs x.tp=${x.tp} {")
                emit(lhs, src"""OpDef(op=$op, inputs=List($x, Const("$sftamt")))""", rhs)
                emit(src"// }")
            }
          case FltConvert(x) if !inHwBlock => 
          case FltConvert(x) =>  //TODO
            alias(lhs, x, src"$rhs //TODO")
          case FltPtToFixPt(x) if !inHwBlock=>
          case rhs:FltPtToFixPt[_,_,_,_,_] => 
            emit(lhs, src"OpDef(op=FltPtToFixPt, inputs=${List(rhs.x, rhs.s, rhs.i, rhs.f)})", rhs)
          case FixPtToFltPt(x) if !inHwBlock=>
          case rhs:FixPtToFltPt[_,_,_,_,_] =>
            emit(lhs, src"OpDef(op=FixPtToFltPt, inputs=${List(rhs.x, rhs.g, rhs.e)})", rhs)
          case VectorApply(vec, idx) =>
            if (idx != 0) throw new Exception(src"Expected parallelization of 1 in inner loop in PIRgen idx=$idx")
            decompose(vec).zip(decompose(lhs)).foreach { case (dvec, dlhs) =>
              alias(dlhs, dvec, rhs)
            }
          case VectorSlice(vec, end, start) =>
            val mask = (List.fill(start)(0) ++ List.fill(end - start)(1) ++ List.fill(32 - end)(0)).reverse
            val strMask = mask.mkString
            val integer = Integer.parseInt(strMask, 2)
            emit(lhs, src"OpDef(op=BitAnd, inputs=List($vec, Const($integer)))", src"$rhs strMask=$strMask")
          case SimpleStruct(elems) => emit(src"// $lhs = $rhs")
          case DataAsBits(a) => emit(src"val $lhs = $a // $lhs = $rhs")
          case BitsAsData(a, tp) => emit(src"val $lhs = $a // $lhs = $rhs")
          case FieldApply(coll, field) =>
            val exp = lookupField(coll, field).get
            alias(lhs, exp, rhs)
          case _ => super.emitNode(lhs, rhs)
        }
    }
  }
}

