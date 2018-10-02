package spatial.codegen.pirgen

import argon.core._
import argon.nodes._
import argon.lang.typeclasses._

import spatial.nodes._
import spatial.utils._
import spatial.metadata._

trait PIRGenOp extends PIRCodegen with PIRGenController {
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

  override protected def quoteRef(x:Any):String =  x match {
    case tp:BOOL[_] if tp.v => s"Const(true)"
    case tp:BOOL[_] if !tp.v => s"Const(false)"
    case tp:INT[_] => s"Const(${tp.v})"
    case x:PIROp => x.toString
    case x => super.quoteRef(x)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = {
    nodeToOp(rhs) match {
      case Some(op) if isInnerReduce(lhs) => 
        val inputs = rhs.expInputs
        val (accumAccess::_, input::_) = inputs.partition { in => isReduceStarter(in) }
        var accumInput = input
        emit(DefRhs(lhs, "ReduceAccumOp", "op"->op, "input"->accumInput, "accum"->accumAccess))
      //case Some(op) if isReduce(lhs) => 
        //val (accumAccesses, inputs) = rhs.expInputs.partition { in => isReduceStarter(in) }
        //dbgs(s"accumAccesses=$accumAccesses, inputs=$inputs")
        //val (accumAccess::_, input::_) = (accumAccesses, inputs)
        //var accumInput = input
        //emit(lhs, src"AccumOp(op=$op, input=${accumInput}, accum=${accumAccess})", rhs)
      case Some(op) if inHwBlock =>
        val inputs = rhs.productIterator.toList
        emit(DefRhs(lhs, "OpDef", "op"->op, "inputs"->inputs))
      case Some(op) =>
      case None => 
        rhs match {
          case FixConvert(x) if !inHwBlock => 
          case FixConvert(x) => 
            val FixPtType(s1, i1, f1) = x.tp
            lhs.tp match {
              case tp if tp == x.tp => 
                emit(AliasRhs(lhs, x).comment("(Same Type. No op)"))
              case FixPtType(`s1`,`i1`,f2) =>
                emit(AliasRhs(lhs, x).comment("(fraction difference. Ignored on plasticine)"))
              case LongType() =>
                warn(s"Plasticine only support 32 bit wordwidth. FixConvert to LongType. Use single precision instead ${lhs.ctx}")
                dbg(s"Plasticine only support 32 bit wordwidth. FixConvert to Use single precision instead")
                emit(AliasRhs(lhs, x))
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
                emit(DefRhs(lhs, "OpDef", "op"->op, "inputs"->List(x, s"""Const("$sftamt")""")))
            }
          case FltConvert(x) if !inHwBlock => 
          case FltConvert(x) =>
            emit(AliasRhs(lhs, x).comment(c"TODO"))
          case FltPtToFixPt(x) if !inHwBlock=>
          case rhs:FltPtToFixPt[_,_,_,_,_] => 
            emit(DefRhs(lhs, "OpDef", "op"->"FltPtToFixPt", "inputs"->List(rhs.x, rhs.s, rhs.i, rhs.f)))
          case FixPtToFltPt(x) if !inHwBlock=>
          case rhs:FixPtToFltPt[_,_,_,_,_] =>
            emit(DefRhs(lhs, c"OpDef","op"->"FixPtToFltPt", "inputs"->List(rhs.x, rhs.g, rhs.e)))
          case VectorApply(vec, idx) =>
            if (idx != 0) throw new Exception(src"Expected parallelization of 1 in inner loop in PIRgen idx=$idx")
            decompose(vec).zip(decompose(lhs)).foreach { case (dvec, dlhs) =>
              emit(AliasRhs(dlhs, dvec))
            }
          case VectorSlice(vec, end, start) =>
            val mask = (List.fill(start)(0) ++ List.fill(end - start)(1) ++ List.fill(32 - end)(0)).reverse
            val strMask = mask.mkString
            val integer = Integer.parseInt(strMask, 2)
            emit(DefRhs(lhs, "OpDef","op"->"BitAnd", "inputs"->List(vec, s"Const($integer)")).comment(s"$rhs strMask=$strMask"))
          case SimpleStruct(elems) => emit(src"// $lhs = $rhs")
          case DataAsBits(a) => emit(AliasRhs(lhs, a))
          case BitsAsData(a, tp) => emit(AliasRhs(lhs, a))
          case FieldApply(coll, field) =>
            val exp = lookupField(coll, field).get
            emit(AliasRhs(lhs, exp))
          case _ => super.emitNode(lhs, rhs)
        }
    }
  }
}

