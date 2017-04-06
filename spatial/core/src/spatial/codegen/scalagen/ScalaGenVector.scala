package spatial.codegen.scalagen

import argon.codegen.scalagen.ScalaGenText
import argon.ops.{FixPtExp, FltPtExp}
import spatial.api.{BitOpsExp, VectorExp}

trait ScalaGenVector extends ScalaGenBits with ScalaGenText {
  val IR: VectorExp with BitOpsExp with FltPtExp with FixPtExp
  import IR._

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
        case BoolType() =>
          emit(src"""val $lhs = "0b" + $x.sliding(4,4).map{_.reverse.map{x => if (x) "1" else "0"}.mkString("")}.toList.reverse.mkString(",")""")
        case _ =>
          emit(src"""val $lhs = "Vector(" + $x.reverse.mkString(", ") + ")" """)
      }
    case _ => super.emitToString(lhs,x,tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case ListVector(elems)      => emit(src"val $lhs = Array(" + elems.reverse.map(quote).mkString(",") + ")")
    case VectorApply(vector, i) => emit(src"val $lhs = $vector.apply($i)")
    case VectorSlice(vector, end, start) =>
     emit(src"val $lhs = $vector.slice($start, $end+1)") // end is non-inclusive

    case VectorConcat(vectors) =>
      // val v = concat(a(4::0), b(4::0), c(4::0))
      // v(12) // should give a(2)
      // code generated as v(15 - 1 - 12) = v(2)
      val concat = vectors.reverse.map(quote).mkString(" ++ ")
      emit(src"val $lhs = $concat")

    // Other cases (Structs, Vectors) are taken care of using rewrite rules
    case e@DataAsBits(a) => e.mT match {
      case FltPtType(_,_)   => throw new Exception("Bit-wise operations not supported on floating point values yet")
      case FixPtType(_,_,_) => emit(src"val $lhs = $a.bits")
      case BoolType()       => emit(src"val $lhs = Array[Bit]($a)")
    }

    case BitsAsData(v,mT) => mT match {
      case FltPtType(_,_)   => throw new Exception("Bit-wise operations not supported on floating point values yet")
      case FixPtType(s,i,f) => emit(src"val $lhs = Number($v, FixedPoint($s,$i,$f))")
      case BoolType()       => emit(src"val $lhs = $v.head")
    }

    case _ => super.emitNode(lhs, rhs)
  }
}