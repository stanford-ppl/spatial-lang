package spatial.codegen.simgen

import argon.ops.ArrayExp

trait SimGenArray extends SimCodegen {
  val IR: ArrayExp
  import IR._

  override protected def remap(tp: Type[_]): String = tp match {
    case tp: ArrayType[_] => src"Array[${tp.typeArguments.head}]"
    case _ => super.remap(tp)
  }

  override protected def quoteConst(c: Const[_]): String = (c.tp, c) match {
    // Array constants are currently disallowed
    case _ => super.quoteConst(c)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op@ArrayNew(size)      => emit(src"val $lhs = new Array[${op.mA}]($size)")
    case ArrayApply(array, i)   => emit(src"val $lhs = $array.apply($i)")
    case ArrayLength(array)     => emit(src"val $lhs = $array.length")
    case InputArguments()       => emit(src"val $lhs = args")
    case _ => super.emitNode(lhs, rhs)
  }
}
