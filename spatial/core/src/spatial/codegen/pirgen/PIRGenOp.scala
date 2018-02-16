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
        val innerPar = getInnerPar(currCtrl)
        val numReduceStages = (Math.log(innerPar) / Math.log(2)).toInt
        (0 until numReduceStages).foreach { i =>
          emit(s"${input}_$i", s"ReduceOp(op=$op, input=$accumInput)", rhs)
          accumInput = s"${input}_$i"
        }
        emit(lhs, s"AccumOp(op=$op, input=$accumInput, accum=$accumAccess)", rhs)
      case Some(op) if inHwBlock =>
        val inputs = rhs.expInputs
        emit(lhs, s"OpDef(op=$op, inputs=${inputs.map(quote)})", rhs)
      case Some(op) =>
      case None => 
        rhs match {
          case FixConvert(x) => emit(s"val $lhs = $x // $rhs")
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

