package spatial.codegen.scalagen

import argon.core._
import argon.codegen.scalagen.ScalaGenString
import argon.nodes._
import spatial.aliases._
import spatial.nodes._

trait ScalaGenVector extends ScalaGenBits with ScalaGenString {

  override protected def remap(tp: Type[_]): String = tp match {
    case tp: VectorType[_] => src"Array[${tp.child}]"
    case _ => super.remap(tp)
  }

  override def invalid(tp: Type[_]): String = tp match {
    case tp: VectorType[_] => src"""Array.fill(${tp.width}(${invalid(tp.child)})"""
    case _ => super.invalid(tp)
  }

  override def emitToString(lhs: Sym[_], x: Exp[_], tp: Type[_]) = tp match {
    case vT:VectorType[_] =>
      vT.child match {
        case BooleanType() =>
          emit(src"""val $lhs = "0b" + $x.sliding(4,4).map{_.reverse.map{x => if (x) "1" else "0"}.mkString("")}.toList.reverse.mkString("_")""")
        case _ =>
          emit(src"""val $lhs = "Vector.ZeroFirst(" + $x.mkString(", ") + ")" """)
      }
    case _ => super.emitToString(lhs,x,tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case ListVector(elems)      => emit(src"val $lhs = Array(" + elems.map(quote).mkString(",") + ")")
    case VectorApply(vector, i) => emit(src"val $lhs = $vector.apply($i)")
    case VectorSlice(vector, end, start) =>
     emit(src"val $lhs = $vector.slice($start, $end+1)") // end is non-inclusive

    case VectorConcat(vectors) =>
      // val v = concat(c(4::0), b(4::0), a(4::0))
      // v(12) // should give a(2)
      val concat = vectors.map(quote).mkString(" ++ ")
      emit(src"val $lhs = $concat")

    // Other cases (Structs, Vectors) are taken care of using rewrite rules
    case e@DataAsBits(a) => e.mT match {
      case FltPtType(_,_)   => emit(src"val $lhs = $a.bits")
      case FixPtType(_,_,_) => emit(src"val $lhs = $a.bits")
      case BooleanType()    => emit(src"val $lhs = Array[Bool]($a)")
    }

    case BitsAsData(v,mT) => mT match {
      case FltPtType(g,e)   => emit(src"val $lhs = FloatPoint.fromBits($v, FltFormat(${g-1},$e))")
      case FixPtType(s,i,f) => emit(src"val $lhs = FixedPoint.fromBits($v, FixFormat($s,$i,$f))")
      case BooleanType()    => emit(src"val $lhs = $v.head")
    }

    case _ => super.emitNode(lhs, rhs)
  }
}