package spatial.nodes

import argon.core._
import argon.nodes._
import spatial.aliases._
import spatial.lang.Math._

/** IR Nodes **/
case class FixMAC[S:BOOL,I:INT,F:INT](
  m1:  Exp[FixPt[S,I,F]],
  m2:  Exp[FixPt[S,I,F]],
  add: Exp[FixPt[S,I,F]]
) extends FixPtOp1[S,I,F] {
  def mirror(f:Tx) = fix_mac(f(m1),f(m2),f(add))
}

case class FixAbs[S:BOOL,I:INT,F:INT](x: Exp[FixPt[S,I,F]]) extends FixPtOp1[S,I,F] { def mirror(f:Tx) = fix_abs(f(x)) }
case class FixFloor[S:BOOL,I:INT,F:INT](x: Exp[FixPt[S,I,F]]) extends FixPtOp1[S,I,F] { def mirror(f:Tx) = fix_floor(f(x)) }
case class FixCeil[S:BOOL,I:INT,F:INT](x: Exp[FixPt[S,I,F]]) extends FixPtOp1[S,I,F] { def mirror(f:Tx) = fix_ceil(f(x)) }

case class FltAbs [G:INT,E:INT](x: Exp[FltPt[G,E]]) extends FltPtOp1[G,E] { def mirror(f:Tx) = flt_abs(f(x)) }
case class FltLog [G:INT,E:INT](x: Exp[FltPt[G,E]]) extends FltPtOp1[G,E] { def mirror(f:Tx) = flt_log(f(x)) }
case class FltExp [G:INT,E:INT](x: Exp[FltPt[G,E]]) extends FltPtOp1[G,E] { def mirror(f:Tx) = flt_exp(f(x)) }
case class FltSqrt[G:INT,E:INT](x: Exp[FltPt[G,E]]) extends FltPtOp1[G,E] { def mirror(f:Tx) = flt_sqrt(f(x)) }

case class FltPow[G:INT,E:INT](x: Exp[FltPt[G,E]], y: Exp[FltPt[G,E]]) extends FltPtOp1[G,E] { def mirror(f:Tx) = math_pow(f(x), f(y)) }

case class FltSin[G:INT,E:INT](x: Exp[FltPt[G,E]]) extends FltPtOp1[G,E] { def mirror(f:Tx) = math_sin(f(x)) }
case class FltCos[G:INT,E:INT](x: Exp[FltPt[G,E]]) extends FltPtOp1[G,E] { def mirror(f:Tx) = math_cos(f(x)) }
case class FltTan[G:INT,E:INT](x: Exp[FltPt[G,E]]) extends FltPtOp1[G,E] { def mirror(f:Tx) = math_tan(f(x)) }
case class FltSinh[G:INT,E:INT](x: Exp[FltPt[G,E]]) extends FltPtOp1[G,E] { def mirror(f:Tx) = math_sinh(f(x)) }
case class FltCosh[G:INT,E:INT](x: Exp[FltPt[G,E]]) extends FltPtOp1[G,E] { def mirror(f:Tx) = math_cosh(f(x)) }
case class FltTanh[G:INT,E:INT](x: Exp[FltPt[G,E]]) extends FltPtOp1[G,E] { def mirror(f:Tx) = math_tanh(f(x)) }
case class FltAsin[G:INT,E:INT](x: Exp[FltPt[G,E]]) extends FltPtOp1[G,E] { def mirror(f:Tx) = math_asin(f(x)) }
case class FltAcos[G:INT,E:INT](x: Exp[FltPt[G,E]]) extends FltPtOp1[G,E] { def mirror(f:Tx) = math_acos(f(x)) }
case class FltAtan[G:INT,E:INT](x: Exp[FltPt[G,E]]) extends FltPtOp1[G,E] { def mirror(f:Tx) = math_atan(f(x)) }

case class OneHotMux[T:Type:Bits](selects: Seq[Exp[Bit]], datas: Seq[Exp[T]]) extends Op[T] {
  def mirror(f:Tx) = onehot_mux(f(selects),f(datas))
  val mT = typ[T]
}

case class Mux[T:Type:Bits](select: Exp[Bit], a: Exp[T], b: Exp[T]) extends Op[T] { def mirror(f:Tx) = math_mux(f(select),f(a),f(b)) }
case class Min[T:Type:Bits:Order](a: Exp[T], b: Exp[T]) extends Op[T] { def mirror(f:Tx) = math_min(f(a),f(b)) }
case class Max[T:Type:Bits:Order](a: Exp[T], b: Exp[T]) extends Op[T] { def mirror(f:Tx) = math_max(f(a),f(b)) }